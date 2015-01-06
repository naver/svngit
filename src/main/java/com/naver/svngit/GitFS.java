/**
 * Original From SVNKit (http://svnkit.com/index.html)
 *
 * Modified by Naver Corp. (Author: Yi EungJun <eungjun.yi@navercorp.com>)
 */
package com.naver.svngit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.io.fs.*;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GitFS extends FSFS {
    private Repository myGitRepository;
    public GitFS(File repositoryRoot) {
        super(repositoryRoot);
    }

    public static String findRepositoryRoot(String host, String path) {
        if (path == null) {
            path = "";
        }
        String testPath = host != null ? SVNPathUtil.append("\\\\" + host, path) : path;
        testPath = testPath.replaceFirst("\\|", "\\:");
        File rootPath = new File(testPath).getAbsoluteFile();
        while (!isRepositoryRoot(rootPath)) {
            if (rootPath.getParentFile() == null) {
                return null;
            }
            path = SVNPathUtil.removeTail(path);
            rootPath = rootPath.getParentFile();
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        while (path.endsWith("\\")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static boolean isRepositoryRoot(File rootPath) {
        // TODO: Implement
        return true;
    }

    public long getYoungestRevision() throws SVNException {
        try {
            String prefix = "refs/svn/";
            Ref latest = myGitRepository.getRef(prefix + "latest");
            if (latest == null) {
                try {
                    createSvnRefs();
                    latest = myGitRepository.getRef(prefix + "latest");
                } catch (GitAPIException e) {
                    throw new SVNException(
                            SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Failed to get youngest revision"), e);
                }
                if (latest == null) {
                    throw new SVNException(
                            SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Failed to get youngest revision"));
                }
            }
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "repo " + myGitRepository.getDirectory().getAbsolutePath());
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "ref " + latest);
            Ref target = latest.getTarget();
            long youngestRevision = Long.valueOf(target.getName().replaceFirst(prefix, ""));
            setYoungestRevisionCache(youngestRevision);
            return youngestRevision;
        } catch (IOException e) {
            throw new SVNException(
                    SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Failed to get youngest revision"), e);
        }
    }

    private void checkRefUpdateResult(RefUpdate.Result rc) throws SVNException {
        switch (rc) {
            case REJECTED:
            case NOT_ATTEMPTED:
            case IO_FAILURE:
            case LOCK_FAILURE:
            case REJECTED_CURRENT_BRANCH:
                throw new SVNException(
                        SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Failed to update refs for SVN revisions: " + rc));
            default:
                break;
        }
    }

    // Return the latest ref
    private void createSvnRefs() throws GitAPIException, IOException, SVNException {
        Git git = new Git(myGitRepository);

        Ref latest = myGitRepository.getRef("refs/svn/latest");
        if (latest != null && latest.getObjectId().equals(
                myGitRepository.getRef(Constants.HEAD).getObjectId())) {
            // We don't need to create svn refs
            return;
        }

        LinkedList<RevCommit> commits = new LinkedList<>();
        for(RevCommit commit : git.log().call()) {
            commits.addFirst(commit);
        }

        for(int rev = commits.size(); rev > 0; rev--) {
            // revision to commit id
            RefUpdate refUpdate = myGitRepository.updateRef("refs/svn/" + rev);
            refUpdate.setNewObjectId(commits.get(rev - 1).getId());
            refUpdate.setForceUpdate(true);
            RefUpdate.Result rc = refUpdate.update();
            checkRefUpdateResult(rc);
            if (rc == RefUpdate.Result.NO_CHANGE) {
                break;
            }

            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "Update ref: " + refUpdate.getRef());

            // commit id to revision
            refUpdate = myGitRepository.updateRef("refs/svn/id/" + commits.get(rev - 1).getId().getName());
            checkRefUpdateResult(refUpdate.link("refs/svn/" + rev));
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "Update ref: " + refUpdate.getRef());
        }
        RefUpdate refUpdate = myGitRepository.updateRef("refs/svn/latest");
        checkRefUpdateResult(refUpdate.link("refs/svn/" + commits.size()));
        SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "Update ref: " + refUpdate.getRef());
    }

    @Override
    public void open() throws SVNException {
        try {
            myGitRepository = new RepositoryBuilder().setGitDir(getRepositoryRoot()).build();
        } catch (IOException e) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Failed to open a repository"), e);
        }
    }

    @Override
    public void close() {
        myGitRepository.close();
    }

    public FSRevisionRoot createRevisionRoot(long revision) throws SVNException {
        ensureRevisionsExists(revision);
        return new GitFSRevisionRoot(this, revision);
    }

    private void ensureRevisionsExists(long revision) throws SVNException {
        if (FSRepository.isInvalidRevision(revision)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION,
                    "Invalid revision number ''{0}''", new Long(revision));
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }

        // FIXME: is it slow?
        if (revision <= getYoungestRevision()) {
            return;
        }

        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION,
                "No such revision {0}", String.valueOf(revision));
        SVNErrorManager.error(err, SVNLogType.FSFS);
    }

    public Repository getGitRepository() {
        return myGitRepository;
    }

    @Override
    public Map getDirContents(FSRevisionNode revNode) throws SVNException {
        String path = revNode.getCreatedPath();
        SVNHashMap map = new SVNHashMap();

        try {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "getDirContents - path: " + path);
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "getDirContents - rev: " + revNode.getCreatedRevision());
            ObjectId commitId = SVNGitUtil.getCommitIdFromRevision(myGitRepository, revNode.getCreatedRevision());
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "getDirContents - commitId: " + commitId);
            TreeWalk treeWalk;
            // FIXME: It may be slow.
            RevTree rootTree = new RevWalk(myGitRepository).parseTree(commitId);
            if (path == null || path.isEmpty()) {
                treeWalk = new TreeWalk(myGitRepository);
                treeWalk.addTree(rootTree);
            } else {
                treeWalk = TreeWalk.forPath(myGitRepository, path, rootTree);
                treeWalk.enterSubtree();
            }
            while(treeWalk.next()) {
                String name = treeWalk.getNameString();
                FSEntry entry = new FSEntry();
                entry.setName(name);
                entry.setType(treeWalk.isSubtree() ? SVNNodeKind.DIR : SVNNodeKind.FILE);
                FSID id = FSID.createRevId(treeWalk.getObjectId(0).getName(), null, revNode.getCreatedRevision(), -1); // FIXME
                entry.setId(id);
                map.put(name, entry);
            }
        } catch (IOException e) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Failed to get dir contents"), e);
        }

        // TODO: and so on...
        return map;
    }

    private Map parsePlainRepresentation(SVNProperties entries, boolean mayContainNulls) throws SVNException {
        Map representationMap = new SVNHashMap();

        for (Iterator iterator = entries.nameSet().iterator(); iterator.hasNext();) {
            String name = (String) iterator.next();
            String unparsedEntry = entries.getStringValue(name);

            if (unparsedEntry == null && mayContainNulls) {
                continue;
            }

            FSEntry nextRepEntry = parseRepEntryValue(name, unparsedEntry);
            if (nextRepEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Directory entry corrupt");
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }
            representationMap.put(name, nextRepEntry);
        }
        return representationMap;
    }

    private FSEntry parseRepEntryValue(String name, String value) {
        if (value == null) {
            return null;
        }
        int spaceInd = value.indexOf(' ');
        if (spaceInd == -1) {
            return null;
        }
        String kind = value.substring(0, spaceInd);
        String rawID = value.substring(spaceInd + 1);

        SVNNodeKind type = SVNNodeKind.parseKind(kind);
        FSID id = FSID.fromString(rawID);
        if ((type != SVNNodeKind.DIR && type != SVNNodeKind.FILE) || id == null) {
            return null;
        }
        return new FSEntry(id, type, name);
    }

    @Override
    public SVNProperties getRevisionProperties(long revision) throws SVNException {
        SVNProperties properties = new SVNProperties();
        try {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "getRevisionProperties - revision: " + revision);
            if (revision == 0) {
                // Git does not know the date at which this repository is created.
                properties.put("svn:date", SVNDate.formatDate(new Date(0)));
            } else {
                RevCommit commit = SVNGitUtil.getCommitFromRevision(myGitRepository, revision);
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "getRevisionProperties - commit: " + commit);
                properties.put("svn:log", commit.getFullMessage());
                properties.put("svn:author", commit.getAuthorIdent().getName());
                properties.put("svn:date", SVNDate.formatDate(commit.getAuthorIdent().getWhen()));
            }
        } catch (IOException e) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Failed to get properties from a commit"), e);
        }
        return properties;
    }

    @Override
    public String getUUID() throws SVNException {
        // FIXME
        return "fake-uuid";
    }

    @Override
    public FSRevisionNode getRevisionNode(FSID id) throws SVNException  {
        FSRevisionNode node = new GitFSRevisionNode(myGitRepository);
        node.setId(id);
        FSRepresentation rep = new FSRepresentation();
        rep.setRevision(id.getRevision());
        node.setTextRepresentation(rep);

        try {
            switch(myGitRepository.getObjectDatabase().open(myGitRepository.resolve(id.getNodeID())).getType()) {
                case Constants.OBJ_BLOB:
                    node.setType(SVNNodeKind.FILE);
                    break;
                case Constants.OBJ_TREE:
                    node.setType(SVNNodeKind.DIR);
                    break;
                default:
                    throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Unexpected type"));
            }
        } catch (IOException e) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Failed to get properties from a commit"), e);
        }

        return node;
    }
}
