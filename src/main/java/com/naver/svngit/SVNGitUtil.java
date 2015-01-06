/**
 * Original From SVNKit (http://svnkit.com/index.html)
 *
 * Modified by Naver Corp. (Author: Yi EungJun <eungjun.yi@navercorp.com>)
 */
package com.naver.svngit;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

/**
 * SVNGit
 *
 * Copyright 2015 NAVER Corp.
 *
 * @Author Yi EungJun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
