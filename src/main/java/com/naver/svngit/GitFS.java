package com.naver.svngit;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by nori on 14. 12. 23.
 */
public class GitFS extends FSFS {
    private Repository myGitRepository;

    public GitFS(File repositoryRoot) {
        super(repositoryRoot);
    }

    // TODO: Git 저장소를 찾는 기능으로 개조하기
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
        // TODO: 구현하기
        return true;
    }

    public long getYoungestRevision() throws SVNException {
        try {
            // TODO refs/svn/lastest가 자동으로 생성되도록 해야함
            String prefix = "refs/svn/";
            Ref ref = myGitRepository.getRef(prefix + "latest");
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "repo " + myGitRepository.getDirectory().getAbsolutePath());
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "ref " + ref);
            Ref target = ref.getTarget();
            long youngestRevision = Long.valueOf(target.getName().replaceFirst(prefix, ""));
            setYoungestRevisionCache(youngestRevision);
            return youngestRevision;
        } catch (IOException e) {
            // FIXME: error handling
            return 0;
        }
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
        FSRepresentation txtRep = revNode.getTextRepresentation();
        /*
        if (txtRep != null && txtRep.isTxn()) {
            FSFile childrenFile = getTransactionRevisionNodeChildrenFile(revNode.getId());
            Map entries = null;
            try {
                SVNProperties rawEntries = childrenFile.readProperties(false, false);
                rawEntries.putAll(childrenFile.readProperties(true, false));

                rawEntries.removeNullValues();

                entries = parsePlainRepresentation(rawEntries, true);
            } finally {
                childrenFile.close();
            }
            return entries;
        } else if (txtRep != null) {
        */
        if (txtRep != null) {
            String path = revNode.getCreatedPath();
            SVNHashMap map = new SVNHashMap();

            try {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "getDirContents - path: " + path);
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "getDirContents - rev: " + revNode.getCreatedRevision());
                ObjectId commitId = SVNGitUtil.getCommitIdFromRevision(myGitRepository, revNode.getCreatedRevision());
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "getDirContents - commitId: " + commitId);
                TreeWalk treeWalk;
                // FIXME: It may be slow.
                if (path.isEmpty()) {
                    treeWalk = new TreeWalk(myGitRepository);
                    treeWalk.addTree(new RevWalk(myGitRepository).parseCommit(commitId).getTree());
                } else {
                    treeWalk = TreeWalk.forPath(myGitRepository, path, commitId);
                }
                while(treeWalk.next()) {
                    String name = treeWalk.getNameString();
                    FSEntry entry = new FSEntry();
                    entry.setName(name);
                    FSID id = FSID.createRevId(treeWalk.getObjectId(0).getName(), null, revNode.getCreatedRevision(), -1); // FIXME
                    entry.setId(id);
                    map.put(name, entry); // 여기서 entry에 정보가 부족하면 나중에 NPE를 만나게 된다.
                }
            } catch (IOException e) {
                SVNDebugLog.getDefaultLog().logError(SVNLogType.DEFAULT, e.getMessage());
            }

            // TODO: and so on...
            return map;
            /*
            FSFile revisionFile = null;
            try {
                revisionFile = openAndSeekRepresentation(txtRep);
                String repHeader = revisionFile.readLine(160);

                if (!"PLAIN".equals(repHeader)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed representation header");
                    SVNErrorManager.error(err, SVNLogType.FSFS);
                }

                revisionFile.resetDigest();
                SVNProperties rawEntries = revisionFile.readProperties(false, false);
                String checksum = revisionFile.digest();

                if (!checksum.equals(txtRep.getMD5HexDigest())) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT,
                            "Checksum mismatch while reading representation:\n   expected:  {0}\n     actual:  {1}",
                            new Object[]{checksum, txtRep.getMD5HexDigest()});
                    SVNErrorManager.error(err, SVNLogType.FSFS);
                }

                return parsePlainRepresentation(rawEntries, false);
            } finally {
                if (revisionFile != null) {
                    revisionFile.close();
                }
            }
            */
        }
        /*
        }
        */
        return new SVNHashMap();// returns an empty map, must not be null!!
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
        /*
        try{
            return readRevisionProperties(revision);
        } catch(SVNException e ) {
            if(e.getErrorMessage().getErrorCode()==SVNErrorCode.FS_NO_SUCH_REVISION && myDBFormat >= MIN_PACKED_REVPROP_FORMAT ) {
                updateMinUnpackedRevProp();
                return readRevisionProperties(revision);
            }
            throw e;
        }
        */
    }

    @Override
    public String getUUID() throws SVNException {
        // uuid를 정하기가 매우매우매우매우 어렵다. 그냥 랜덤으로 생성해서 저장해둘까
        // FIXME

        return "fake-uuid";
    }
}
