package com.naver.svngit;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCombiner;
import org.tmatesoft.svn.core.internal.io.fs.*;
import org.tmatesoft.svn.core.internal.server.dav.DAVPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by nori on 14. 12. 28.
 */
public class GitFSRevisionRoot extends FSRevisionRoot {
    public GitFSRevisionRoot(FSFS owner, long revision) {
        super(owner, revision);
    }

    public FSRevisionNode getRevisionNode(String path) throws SVNException {
        path = SVNPathUtil.canonicalizeAbsolutePath(path);
        Repository repository = ((GitFS)getOwner()).getGitRepository();
        FSRevisionNode node = new GitFSRevisionNode(repository);

        path = DAVPathUtil.dropLeadingSlash(path);

        // TODO: Need refactoring?
        if (path.isEmpty()) {
            node.setCreatedPath(path);
            node.setType(SVNNodeKind.DIR);
            // TODO: 여기서 디렉토리 정보 읽어오는 작업도 수행해야한다. svnkit에서는 여기서 호출하는 openPath가 그걸 함
            // 그 정보를 FSRevisionNode.setTextRepresentation 로 revsiionNode에 집어넣을 것
            // TODO: node.setTextRepresentation();
            FSRepresentation rep = new FSRepresentation();
            rep.setRevision(getRevision());
            // TODO: textRep.setMD5HexDigest();
            // textRep.setTxnId(myTxnID);
            // String uniqueSuffix = getNewTxnNodeId();
            // String uniquifier = myTxnID + '/' + uniqueSuffix;
            // textRep.setUniquifier(uniquifier);
            node.setTextRepresentation(rep);
            return node;
        }

        try {
            RevTree tree = new RevWalk(repository).parseTree(repository.resolve("refs/svn/" + getRevision()));
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "path: " + path);
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "tree: " + tree);
            TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree);
            node.setCreatedPath(path);
            if (treeWalk.isSubtree()) {
                node.setType(SVNNodeKind.DIR);
            } else {
                node.setType(SVNNodeKind.FILE);
            }
            FSRepresentation rep = new FSRepresentation();
            rep.setRevision(getRevision());
            node.setTextRepresentation(rep);
            // TODO: textRep.setMD5HexDigest();
            // TODO: node.setTextRepresentation(textRep);
            treeWalk.release();
            return node;
        } catch (IOException e) {
            node.setType(SVNNodeKind.NONE);
            return node;
        }
    }

    @Override
    public InputStream getFileStreamForPath(SVNDeltaCombiner combiner, String path) throws SVNException {
        /*
        FSRevisionNode fileNode = getRevisionNode(path);
        return GitFSInputStream.createDeltaStream(combiner, fileNode, getOwner());
        */
        // combiner 따위 무시하고 그냥 input stream을 돌려주면 어떻게 될까?
        FSRevisionNode fileNode = getRevisionNode(path);
        Repository repository = ((GitFS)getOwner()).getGitRepository();
        try {
            path = DAVPathUtil.dropLeadingSlash(path);
            RevCommit commit = SVNGitUtil.getCommitFromRevision(repository, getRevision());
            TreeWalk treeWalk = new TreeWalk(repository).forPath(repository, path, commit.getTree());
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "stream path: " + path);
            return repository.open(treeWalk.getObjectId(0)).openStream();
        } catch (IOException e) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Failed to stream a file"), e);
        }
    }
}
