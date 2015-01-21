package org.tmatesoft.svn.core.internal.server.dav.handlers;

import com.naver.svngit.GitFS;
import com.naver.svngit.SVNGitUtil;
import com.naver.svngit.TreeBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaReader;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.internal.io.fs.SVNGitRepositoryFactory;
import org.tmatesoft.svn.core.internal.server.dav.DAVPathUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceURI;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

public class SVNGitPutHandler extends DAVPutHandler {

    public SVNGitPutHandler(DAVRepositoryManager repositoryManager, HttpServletRequest request, HttpServletResponse response) {
        super(repositoryManager, request, response);
    }

    public void execute() throws SVNException {
        String versionName = getRequestHeader(SVN_VERSION_NAME_HEADER);
        long version = DAVResource.INVALID_REVISION;
        try {
            version = Long.parseLong(versionName);
        } catch (NumberFormatException e) {
        }

        DAVRepositoryManager manager = getRepositoryManager();

        // use latest version, if no version was specified
        SVNRepository resourceRepository = SVNGitRepositoryFactory.create(SVNURL.parseURIEncoded(manager.getResourceRepositoryRoot()));
        if ( version == -1 )
        {
          version = resourceRepository.getLatestRevision();
        }
        resourceRepository.testConnection(); // to open the repository

        GitFS gitFS = (GitFS) ((FSRepository) resourceRepository).getFSFS();
        DAVResourceURI resourceURI =
                new DAVResourceURI(manager.getResourceContext(), manager.getResourcePathInfo(), null, false, version);
        TreeBuilder treeBuilder = SVNGitPropPatchHandler.treeBuilders.get(resourceURI.getActivityID());
        if (treeBuilder == null) {
            sendError(HttpServletResponse.SC_BAD_REQUEST, null);
            return;
        }

        final Repository repo = gitFS.getGitRepository();

        // get the path to the file to add or update
        String path = resourceURI.getPath();
        path = DAVPathUtil.dropLeadingSlash(path);

        // create the new blob from source blob and the received delta
        final ByteArrayOutputStream targetStream = new ByteArrayOutputStream();
        try {
            RevCommit commitId = SVNGitUtil.getCommitFromRevision(repo, version);
            RevTree tree = new RevWalk(repo).parseTree(commitId);
            final TreeWalk treeWalk = TreeWalk.forPath(repo, path, tree);

            final ObjectId sourceBlobId;
            if (treeWalk == null) {
                sourceBlobId = null;
            } else {
                if (treeWalk.isSubtree()) {
                    sendError(HttpServletResponse.SC_BAD_REQUEST, null);
                }
                sourceBlobId = treeWalk.getObjectId(0);
            }

            ISVNDeltaConsumer deltaConsumer = new ISVNDeltaConsumer() {
                public SVNDeltaProcessor myDeltaProcessor;
                private InputStream sourceStream;

                @Override
                public void applyTextDelta(String path, String baseChecksum) throws SVNException {
                    try {
                        // TODO: Use stream instead of bytes to save memory?
                        if (sourceBlobId != null) {
                            sourceStream = new ByteArrayInputStream(repo.open(sourceBlobId).getBytes());
                        } else {
                            sourceStream = SVNFileUtil.DUMMY_IN;
                        }
                        myDeltaProcessor = new SVNDeltaProcessor();
                        myDeltaProcessor.applyTextDelta(sourceStream, targetStream, false);
                    } catch (IOException e) {
                        throw new SVNException(SVNErrorMessage.create(
                                SVNErrorCode.FS_GENERAL, "Failed to read a blob: " + sourceBlobId), e);
                    }
                }

                @Override
                public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
                    return myDeltaProcessor.textDeltaChunk(diffWindow);
                }

                @Override
                public void textDeltaEnd(String path) throws SVNException {
                    try {
                        sourceStream.close();
                    } catch (IOException e) {
                        throw new SVNException(SVNErrorMessage.create(
                                SVNErrorCode.FS_GENERAL, "Failed to close a input stream"), e);
                    }
                }
            };
            deltaConsumer.applyTextDelta(null, null);

            InputStream inputStream = getRequestInputStream();
            byte[] buffer = new byte[2048];
            int readCount = -1;
            while ((readCount = inputStream.read(buffer)) != -1) {
                if (readCount == 0) {
                    continue;
                }
                SVNDeltaReader deltaReader = new SVNDeltaReader();
                SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
                if (deltaReader != null) {
                    deltaReader.nextWindow(buffer, 0, readCount, path, deltaConsumer);
                } else {
                    deltaGenerator.sendDelta(path, buffer, readCount, deltaConsumer);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // add the blob into the tree builder
        ObjectInserter objectInserter = repo.getObjectDatabase().newInserter();
        byte[] bytes = targetStream.toByteArray();
        ObjectId blobId;
        try {
            blobId = objectInserter.insert(Constants.OBJ_BLOB, bytes, 0, bytes.length);
        } catch (IOException e) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Failed to insert a blob"), e);
        }
        treeBuilder.add(path, blobId);
        boolean isReplaced = treeBuilder.hasBlob(path);

        handleDAVCreated(null, "Resource", isReplaced);
    }
}
