package com.naver.svngit;

import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.internal.io.fs.FSTranslateReporter;
import org.tmatesoft.svn.core.internal.io.fs.FSUpdateContext;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.*;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;

/**
 * Created by nori on 14. 12. 23.
 */
// TODO: make it works
public class SVNGitRepository extends FSRepository {

    private FSUpdateContext myReporterContext; // TODO: init this
    private File myReposRootDir; // TODO: init this
    private FSFS myGitFS; // TODO: init this

    protected SVNGitRepository(SVNURL location, ISVNSession options) {
        super(location, options);
    }

    public long getLatestRevision() throws SVNException {
        try {
            openRepository();
            return myGitFS.getYoungestRevision();
        } finally {
            closeRepository();
        }
    }

    private void openRepository() throws SVNException {
        try {
            openRepositoryRoot();
        } catch (SVNException svne) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_LOCAL_REPOS_OPEN_FAILED, "Unable to open repository ''{0}''", getLocation().toString());
            err.setChildErrorMessage(svne.getErrorMessage());
            SVNErrorManager.error(err.wrap("Unable to open an ra_local session to URL"), SVNLogType.FSFS);
        }
    }

    private void openRepositoryRoot() throws SVNException {
        // FIXME: Do we need to lock?
        // lock();

        String hostName = getLocation().getHost();
        boolean hasCustomHostName = !"".equals(hostName) &&
                                    !"localhost".equalsIgnoreCase(hostName);

        if (!SVNFileUtil.isWindows && hasCustomHostName) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "Local URL ''{0}'' contains unsupported hostname", getLocation().toString());
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }

        String startPath = SVNEncodingUtil.uriDecode(getLocation().getURIEncodedPath());
        // TODO: findRepositoryRoot가 호출하는 isRepositoryRoot가 Git 저장소를 저장소라고 판단하도록 고친다.
        String rootPath = GitFS.findRepositoryRoot(hasCustomHostName ? hostName : null, startPath) + ".git";
        if (rootPath == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_LOCAL_REPOS_OPEN_FAILED, "Unable to open repository ''{0}''", getLocation().toString());
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }

        String dirPath = rootPath.replaceFirst("\\|", "\\:");

        myReposRootDir = hasCustomHostName ? new File("\\\\" + hostName, dirPath).getAbsoluteFile() :
                                             new File(dirPath).getAbsoluteFile();
        myGitFS = new GitFS(myReposRootDir);
        myGitFS.open();
        // 필요없어보여서 주석처리. TODO: 정말 필요없는지 확인할 것
        /*
        myFSFS.setHooksEnabled(isHooksEnabled());
        myFSFS.open();
        setRepositoryCredentials(myGitFS.getUUID(), getLocation().setPath(rootPath, false));
        */
        // setRepositoryCredentials(myGitFS.getUUID(), getLocation().setPath(rootPath, false)); 에서 myRepositoryRoot를 설정하는데 그건 필요한 것 같다.
        myRepositoryRoot = getLocation().setPath(rootPath, false); // TODO: .git을 더하는 문제를 해결해야 한다.
    }

    @Override
    public FSFS getFSFS() {
        return myGitFS;
    }

    void closeRepository() throws SVNException {
        myGitFS.close();
        // TODO: 필요없어보여서 주석처리
        // Git은 저장소 전체를 lock 하거나 하지 않는다.
        /*
        if (myFSFS != null) {
            myFSFS.close();
        }
        unlock();
        */
    }

    public void testConnection() throws SVNException {
        // try to open and close a repository
        try {
            openRepository();
        } finally {
            closeRepository();
        }
    }

    public FSTranslateReporter beginReport(long revision, SVNURL url, String target, boolean ignoreAncestry,
            boolean sendTextDeltas, boolean sendCopyFromArgs, SVNDepth depth, ISVNEditor editor) throws SVNException {
        openRepository();
        makeReporterContext(revision, target, url, depth, ignoreAncestry, sendTextDeltas, sendCopyFromArgs, editor);
        return new FSTranslateReporter(this);
    }

    private void makeReporterContext(long targetRevision, String target, SVNURL switchURL,
            SVNDepth depth, boolean ignoreAncestry, boolean textDeltas, boolean sendCopyFromArgs,
            ISVNEditor editor) throws SVNException {
        if (depth == SVNDepth.EXCLUDE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS, "Request depth 'exclude' not supported");
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }

        target = target == null ? "" : target;

        if (!isValidRevision(targetRevision)) {
            targetRevision = getFSFS().getYoungestRevision();
        }

        String switchPath = null;

        if (switchURL != null) {
            SVNURL reposRootURL = getRepositoryRoot(false);

            if (switchURL.toDecodedString().indexOf(reposRootURL.toDecodedString()) == -1) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "''{0}''\nis not the same repository as\n''{1}''", new Object[] {
                        switchURL, getRepositoryRoot(false)
                });
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }

            switchPath = switchURL.toDecodedString().substring(reposRootURL.toDecodedString().length());

            if ("".equals(switchPath)) {
                switchPath = "/";
            }
        }

        String anchor = getRepositoryPath("");
        String fullTargetPath = switchPath != null ? switchPath : SVNPathUtil.getAbsolutePath(SVNPathUtil.append(anchor, target));

        if (myReporterContext == null) {
            myReporterContext = new FSUpdateContext(this, getFSFS(), targetRevision,
                                                    SVNFileUtil.createTempFile("report", ".tmp"),
                                                    target, fullTargetPath,
                                                    switchURL == null ? false : true,
                                                    depth, ignoreAncestry, textDeltas,
                                                    sendCopyFromArgs, editor);
        } else {
            myReporterContext.reset(this, getFSFS(), targetRevision, SVNFileUtil.createTempFile("report", ".tmp"),
                                    target, fullTargetPath, switchURL == null ? false : true, depth,
                                    ignoreAncestry, textDeltas, sendCopyFromArgs, editor);
        }
    }

    /**
     * Returns a path relative to the repository root directory given
     * a path relative to the location to which this driver object is set.
     *
     * @param  relativePath a path relative to the location to which
     *                      this <b>SVNRepository</b> is set
     * @return              a path relative to the repository root
     * @throws SVNException in case the repository could not be connected
     * @throws org.tmatesoft.svn.core.SVNAuthenticationException in case of authentication problems
     */
    public String getRepositoryPath(String relativePath) throws SVNException {
        if (relativePath == null) {
            return "/";
        }
        if (relativePath.length() > 0 && relativePath.charAt(0) == '/') {
            return relativePath;
        }
        String fullPath = SVNPathUtil.append(getLocation().getPath(), relativePath) + ".git";
        String repositoryPath = fullPath.substring(getRepositoryRoot(true).getPath().length());
        if ("".equals(repositoryPath)) {
            return "/";
        }

        //if url does not contain a repos root path component, then it results here in
        //a path that lacks leading slash, fix that
        if (!repositoryPath.startsWith("/")) {
            repositoryPath = "/" + repositoryPath;
        }
        return repositoryPath;
    }

    /** start: related with myReporterContext **/

    public void finishReport() throws SVNException {
        try {
            myReporterContext.drive();
        } finally {
            myReporterContext.dispose();
        }
    }

    public void abortReport() throws SVNException {
        if(myReporterContext != null){
            myReporterContext.dispose();
        }
    }

    public void deletePath(String path) throws SVNException {
        myReporterContext.writePathInfoToReportFile(path, null, null, SVNRepository.INVALID_REVISION, false, SVNDepth.INFINITY);
    }

    public void linkPath(SVNURL url, String path, String lockToken, long revision, SVNDepth depth, boolean startEmpty) throws SVNException {
        assertValidRevision(revision);
        if (depth == SVNDepth.EXCLUDE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS, "Depth 'exclude' not supported for link");
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }

        SVNURL reposRootURL = getRepositoryRoot(false);
        if (url.toDecodedString().indexOf(reposRootURL.toDecodedString()) == -1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "''{0}''\nis not the same repository as\n''{1}''", new Object[] {
                    url, reposRootURL
            });
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        String reposLinkPath = url.toDecodedString().substring(reposRootURL.toDecodedString().length());
        if ("".equals(reposLinkPath)) {
            reposLinkPath = "/";
        }
        myReporterContext.writePathInfoToReportFile(path, reposLinkPath, lockToken, revision, startEmpty, depth);
    }

    public void setPath(String path, String lockToken, long revision, SVNDepth depth, boolean startEmpty) throws SVNException {
        assertValidRevision(revision);
        myReporterContext.writePathInfoToReportFile(path, null, lockToken, revision, startEmpty, depth);
    }

    /** end: related with myReporterContext **/

}
