/**
 * Original From SVNKit (http://svnkit.com/index.html)
 *
 * Modified by Naver Corp. (Author: Yi EungJun <eungjun.yi@navercorp.com>)
 */
package com.naver.svngit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
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
    private Repository myGitRepository;

    public GitFSRevisionNode(Repository gitRepository) {
        myGitRepository = gitRepository;
    }

    public long getCreatedRevision() {
        String path = getCreatedPath();
        try {
            long revision = getTextRepresentation().getRevision(); // FIXME: Fix NPE
            ObjectId commitId = SVNGitUtil.getCommitIdFromRevision(myGitRepository, revision);
            LogCommand logCommand = new Git(myGitRepository).log().add(commitId);
            if (path != null && !path.isEmpty()) {
                logCommand.addPath(path);
            }
            Iterable<RevCommit> log = logCommand.call();
            // Get revision number from name of the ref which is targeted by refs/svn/id/:id
            // e.g. refs/svn/id/3bf3247be67c6c918e9ee301ee23294b587452cd
            String prefix = "refs/svn/id/";
            Ref ref = myGitRepository.getRef(prefix + log.iterator().next().getId().getName());
            Ref target = ref.getTarget();
            return SVNGitUtil.getRevisionFromRefName(target.getName());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get the created revision", e);
        }
    }

    @Override
    public String getFileSHA1Checksum() throws SVNException {
        String path = getCreatedPath();
        long revision = getTextRepresentation().getRevision();
        try {
            RevTree tree = new RevWalk(myGitRepository).parseTree(myGitRepository.resolve("refs/svn/" + revision));
            TreeWalk treeWalk = TreeWalk.forPath(myGitRepository, path, tree);
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
