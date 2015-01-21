package org.tmatesoft.svn.core.internal.server.dav.handlers;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.SVNGitRepositoryFactory;
import org.tmatesoft.svn.core.internal.server.dav.DAVPathUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceURI;
import org.tmatesoft.svn.core.io.SVNRepository;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SVNGitMakeCollectionHandler extends DAVMakeCollectionHandler {
    public SVNGitMakeCollectionHandler(DAVRepositoryManager manager, HttpServletRequest request, HttpServletResponse response) {
        super(manager, request, response);
    }

    protected static Map<String, Set<String>> emptyDirss = new HashMap<>();

    public void execute() throws SVNException {
        // TODO: Check whether the directory exists already
        String versionName = getRequestHeader(SVN_VERSION_NAME_HEADER);
        long version = DAVResource.INVALID_REVISION;
        try {
            version = Long.parseLong(versionName);
        } catch (NumberFormatException e) {
        }

        DAVRepositoryManager manager = getRepositoryManager();

        // use latest version, if no version was specified
        SVNRepository resourceRepository = SVNGitRepositoryFactory.create(SVNURL.parseURIEncoded(manager.getResourceRepositoryRoot()));
        if ( version == -1 )
        {
          version = resourceRepository.getLatestRevision();
        }
        resourceRepository.testConnection(); // to open the repository

        DAVResourceURI resourceURI =
                new DAVResourceURI(manager.getResourceContext(), manager.getResourcePathInfo(), null, false, version);
        String activityId = resourceURI.getActivityID();
        if (!emptyDirss.containsKey(activityId)) {
            emptyDirss.put(activityId, new HashSet<String>());
        }
        Set<String> emptyDirs = emptyDirss.get(activityId);

        String path = resourceURI.getPath();
        path = DAVPathUtil.dropLeadingSlash(path);
        emptyDirs.add(path);

        // 1. 이미 존재하면 에러 - 에러 안내면 안되나. 그냥 안낼래
        // 2. 빈 디렉토리라면 에러

        handleDAVCreated(null, "Collection", false);
    }
}