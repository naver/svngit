package com.naver.svngit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

/**
 * Created by nori on 14. 12. 29.
 */
public class SVNGitUtil {
    public static long getRevisionFromRefName(String refName) {
        int index = refName.lastIndexOf('/');
        return Long.valueOf(refName.substring(index + 1));
    }

    public static ObjectId getCommitIdFromRevision(Repository myGitRepository, long createdRevision) throws IOException {
        Ref ref = myGitRepository.getRef("refs/svn/" + createdRevision);
        if (ref == null) {
            return null;
        }
        return ref.getLeaf().getObjectId();
    }

    public static RevCommit getCommitFromRevision(Repository myGitRepository, long createdRevision) throws IOException {
        ObjectId commitId = getCommitIdFromRevision(myGitRepository, createdRevision);
        if (commitId == null) {
            return null;
        }
        return new RevWalk(myGitRepository).parseCommit(commitId);
    }
}
