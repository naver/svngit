/**
 * Original From SVNKit (http://svnkit.com/index.html)
 *
 * Modified by Naver Corp. (Author: Yi EungJun <eungjun.yi@navercorp.com>)
 */
package com.naver.svngit;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionNode;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.IOException;

public class GitFSRevisionNode extends FSRevisionNode {
    private final GitFS myGitFS;
    private long myCreatedRevision = -1;

    public GitFSRevisionNode(GitFS gitFS) {
        myGitFS = gitFS;
    }

    public long getCreatedRevision() {
        if (myCreatedRevision == -1) {
            myCreatedRevision = myGitFS.getCreatedRevision(
                    getCreatedPath(),
                    getTextRepresentation().getRevision()); // FIXME: NPE?
        }

        return myCreatedRevision;
    }

    @Override
    public String getFileSHA1Checksum() throws SVNException {
        String path = getCreatedPath();
        long revision = getTextRepresentation().getRevision();
        Repository gitRepository = myGitFS.getGitRepository();
        try {
            RevTree tree = new RevWalk(gitRepository).parseTree(gitRepository.resolve("refs/svn/" + revision));
            TreeWalk treeWalk = TreeWalk.forPath(gitRepository, path, tree);
            if (treeWalk.isSubtree()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, "Attempted to get checksum of a *non*-file node");
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }
            return treeWalk.getObjectId(0).getName();
        } catch (IOException e) {
            SVNDebugLog.getDefaultLog().logError(SVNLogType.DEFAULT, e.getMessage());
            return "";
        }
    }
}
