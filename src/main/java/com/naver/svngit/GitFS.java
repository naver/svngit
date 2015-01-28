/**
 * Original From SVNKit (http://svnkit.com/index.html)
 *
 * Modified by Naver Corp. (Author: Yi EungJun <eungjun.yi@navercorp.com>)
 */
package com.navercorp.svngit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.io.fs.*;
import org.tmatesoft.svn.core.internal.server.dav.DAVPathUtil;
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
    private static Map<String, FSTransactionInfo> transactions = new HashMap<>();

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

    @Override
    public long getYoungestRevision() throws SVNException {
        try {
            String prefix = "refs/svn/";
            Ref latest;
            try {
                updateSvnRefs();
                latest = myGitRepository.getRef(prefix + "latest");
            } catch (GitAPIException e) {
                throw new SVNException(
                        SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Failed to get youngest revision"), e);
            }
            if (latest == null) {
                throw new SVNException(
                        SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Failed to get youngest revision"));
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

    public void updateSvnRefs() throws GitAPIException, IOException, SVNException {
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

    @Override
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
        String path = DAVPathUtil.dropLeadingSlash(revNode.getCreatedPath());
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
        FSRevisionNode node = new GitFSRevisionNode(this);
        node.setId(id);
        FSRepresentation rep = new FSRepresentation();
        rep.setRevision(id.getRevision());
        node.setTextRepresentation(rep);

        // FIXME: We should set created path to the node
        try {
            ObjectId objectId = myGitRepository.resolve(id.getNodeID());
            ObjectLoader objectLoader = myGitRepository.getObjectDatabase().open(objectId);
            switch(objectLoader.getType()) {
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

    //          commit-id     path    commit-id
    private static Map<ObjectId, Map<String, ObjectId>> lastModifiedCommits = new HashMap<>();

    private void fillLastModifiedCommits(ObjectId start, String basePath) throws IOException, GitAPIException {
        Map<String, ObjectId> objects = lastModifiedCommits.get(start);
        if (objects == null) {
            objects = new TreeMap<>();
        }
        DiffFormatter diffFmt = new DiffFormatter(NullOutputStream.INSTANCE);
        diffFmt.setRepository(getGitRepository());
        LogCommand log = new Git(getGitRepository()).log();
        if (!basePath.isEmpty()) {
            log.addPath(basePath);
        }
        for(RevCommit c: log.call()) {
            final RevTree a = c.getParentCount() > 0 ? c.getParent(0).getTree() : null;
            final RevTree b = c.getTree();

            for(DiffEntry diff: diffFmt.scan(a, b)) {
                objects.put(diff.getNewPath(), c.getId());
            }
        }
        lastModifiedCommits.put(start, objects);
    }

    private ObjectId getCachedLastModifiedCommit(String path, ObjectId start) throws IOException {
        Map<String, ObjectId> commits = lastModifiedCommits.get(start);

        if (commits == null) {
            return null;
        }

        return commits.get(path);
    }

    private void setCachedLastModifiedCommit(String path, ObjectId lastModified, ObjectId start) throws IOException {
        Map<String, ObjectId> commits = lastModifiedCommits.get(start);

        if (commits == null) {
            commits = new HashMap<>();
            lastModifiedCommits.put(start, commits);
        }

        commits.put(path, lastModified);
    }

    public long getCreatedRevision(String path, long revision) {
        path = DAVPathUtil.dropLeadingSlash(path);
        if (path.isEmpty()) {
            // FIXME: Is it always correct?
            return revision;
        }
        try {
            ObjectId start = SVNGitUtil.getCommitIdFromRevision(myGitRepository, revision);
            ObjectId lastModified = getCachedLastModifiedCommit(path, start);

            int sep = path.lastIndexOf('/');
            if (sep < 0) sep = 0;
            if (lastModified == null) {
                fillLastModifiedCommits(start, path.substring(0, sep));
                lastModified = getCachedLastModifiedCommit(path, start);
            }

            if (lastModified == null) {
                // slow way
                LogCommand logCommand = new Git(myGitRepository).log().add(start);
                if (path != null && !path.isEmpty()) {
                    logCommand.addPath(path);
                }
                Iterable<RevCommit> log = logCommand.call();
                // Get revision number from name of the ref which is targeted by refs/svn/id/:id
                // e.g. re        RevTree tree = new RevWalk(getGitRepository()).parseTree(start);fs/svn/id/3bf3247be67c6c918e9ee301ee23294b587452cd
                RevCommit commit = log.iterator().next();
                lastModified = commit.getId();
                setCachedLastModifiedCommit(path, lastModified, start);
            }

            return SVNGitUtil.getRevisionFromCommitId(myGitRepository, lastModified);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get the created revision", e);
        }
    }

    @Override
    public FSTransactionInfo openTxn(String txnName) throws SVNException {
        // TODO: put transaction first
        return transactions.get(getUUID() + ":" + txnName); // FIXME
    }

    public void storeActivity(FSTransactionInfo txnInfo) throws SVNException {
        transactions.put(getUUID() + ":" + txnInfo.getTxnId(), txnInfo);
    }

    @Override
    public FSTransactionRoot createTransactionRoot(FSTransactionInfo txn) throws SVNException {
        SVNProperties txnProps = getTransactionProperties(txn.getTxnId());
        int flags = 0;
        if (txnProps.getStringValue(SVNProperty.TXN_CHECK_OUT_OF_DATENESS) != null) {
            flags |= FSTransactionRoot.SVN_FS_TXN_CHECK_OUT_OF_DATENESS;
        }
        if (txnProps.getStringValue(SVNProperty.TXN_CHECK_LOCKS) != null) {
            flags |= FSTransactionRoot.SVN_FS_TXN_CHECK_LOCKS;
        }

        // FIXME
        return new FSTransactionRoot(this, txn.getTxnId(), txn.getBaseRevision(), flags) {
            public FSTransactionInfo getTxn() throws SVNException {
                return transactions.get(getUUID() + ":" + getTxnID());
            }
        };
    }
}
