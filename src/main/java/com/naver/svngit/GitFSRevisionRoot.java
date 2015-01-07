/**
 * Original From SVNKit (http://svnkit.com/index.html)
 *
 * Modified by Naver Corp. (Author: Yi EungJun <eungjun.yi@navercorp.com>)
 */
package com.naver.svngit;

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
            FSRepresentation rep = new FSRepresentation();
            rep.setRevision(getRevision());
            node.setTextRepresentation(rep);
            FSID id = FSID.createRevId(null, null, node.getCreatedRevision(), -1); // FIXME
            node.setId(id);
            return node;
        }

        try {
            RevTree tree = new RevWalk(repository).parseTree(repository.resolve("refs/svn/" + getRevision()));
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "path: " + path);
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "tree: " + tree);
            TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree);
            node.setCreatedPath(path);
            if (treeWalk == null) {
                throw new SVNException(FSErrors.errorNotFound(this, path));
            } else if (treeWalk.isSubtree()) {
                node.setType(SVNNodeKind.DIR);
                treeWalk.release();
            } else {
                node.setType(SVNNodeKind.FILE);
                treeWalk.release();
            }
            FSRepresentation rep = new FSRepresentation();
            rep.setRevision(getRevision());
            node.setTextRepresentation(rep);
            FSID id = FSID.createRevId(null, null, node.getCreatedRevision(), -1); // FIXME
            node.setId(id);
            // TODO: textRep.setMD5HexDigest();
            // TODO: node.setTextRepresentation(textRep);
            return node;
        } catch (IOException e) {
            node.setType(SVNNodeKind.NONE);
            return node;
        }
    }

    @Override
    public InputStream getFileStreamForPath(SVNDeltaCombiner combiner, String path) throws SVNException {
        Repository repository = ((GitFS)getOwner()).getGitRepository();
        try {
            path = DAVPathUtil.dropLeadingSlash(path);
            RevCommit commit = SVNGitUtil.getCommitFromRevision(repository, getRevision());
            TreeWalk treeWalk = TreeWalk.forPath(repository, path, commit.getTree());
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "stream path: " + path);
            return repository.open(treeWalk.getObjectId(0)).openStream();
        } catch (IOException e) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Failed to stream a file"), e);
        }
    }

    @Override
    public FSRevisionNode getRootRevisionNode() throws SVNException {
        // FIXME: Is this correct?
        return getRevisionNode("");
    }
}
