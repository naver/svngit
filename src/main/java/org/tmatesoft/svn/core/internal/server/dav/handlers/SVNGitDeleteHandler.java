package org.tmatesoft.svn.core.internal.server.dav.handlers;

import com.navercorp.svngit.TreeBuilder;
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

// Copy of DAVDeleteHandler
public class SVNGitDeleteHandler extends DAVDeleteHandler {
    public SVNGitDeleteHandler(DAVRepositoryManager repositoryManager, HttpServletRequest request, HttpServletResponse response) {
        super(repositoryManager, request, response);
    }

    public void execute() throws SVNException {
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

        DAVResourceURI resourceURI =
                new DAVResourceURI(manager.getResourceContext(), manager.getResourcePathInfo(), null, false, version);
        TreeBuilder treeBuilder = SVNGitPropPatchHandler.treeBuilders.get(resourceURI.getActivityID());
        if (treeBuilder == null) {
            sendError(HttpServletResponse.SC_NOT_FOUND, null);
            return;
        }

        // Get the path to delete
        String path = resourceURI.getPath();

        if (path == null || path.isEmpty()) {
            // Delete the activity
            SVNGitPropPatchHandler.treeBuilders.remove(resourceURI.getActivityID());
            setResponseStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        path = DAVPathUtil.dropLeadingSlash(path);

        // TODO: Response 404 Not Found if not exists.

        // Delete the path
        treeBuilder.remove(path);

        setResponseStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
