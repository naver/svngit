package org.tmatesoft.svn.core.internal.server.dav.handlers;

import com.naver.svngit.TreeBuilder;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPHeader;
import org.tmatesoft.svn.core.internal.io.fs.SVNGitRepositoryFactory;
import org.tmatesoft.svn.core.internal.server.dav.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;

public class SVNGitCopyMoveHandler extends DAVCopyMoveHandler {
    private final boolean myIsMove;

    protected SVNGitCopyMoveHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response, boolean isMove) {
        super(connector, request, response, isMove);
        myIsMove = isMove;
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
        resourceRepository.testConnection(); // to open the repository

        // get the source path to move or copy
        DAVResourceURI sourceURI =
                new DAVResourceURI(manager.getResourceContext(), manager.getResourcePathInfo(), null, false, version);
        String sourcePath = sourceURI.getPath();
        sourcePath = DAVPathUtil.dropLeadingSlash(sourcePath);

        String destination = getRequestHeader(HTTPHeader.DESTINATION_HEADER);
        if (destination == null) {
            String netScapeHost = getRequestHeader(HTTPHeader.HOST_HEADER);
            String netScapeNewURI = getRequestHeader(HTTPHeader.NEW_URI_HEADER);
            if (netScapeHost != null && netScapeNewURI != null) {
                String path = SVNPathUtil.append(netScapeHost, netScapeNewURI);
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                destination = "http://" + path;
            }
        }

        if (destination == null) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, "The request is missing a Destination header.");
            sendError(HttpServletResponse.SC_BAD_REQUEST, null);
            return;
        }

        // get the destination path to move or copy
        URI uri = DAVServletUtil.lookUpURI(destination, getRequest(), true);
        String resourceContext = manager.getResourceContext();
        String path = uri.getPath().substring(resourceContext.length());
        DAVResourceURI destinationURI =
                new DAVResourceURI(manager.getResourceContext(), path, null, false, version);
        String destinationPath = destinationURI.getPath();
        destinationPath = DAVPathUtil.dropLeadingSlash(destinationPath);

        TreeBuilder treeBuilder = SVNGitPropPatchHandler.treeBuilders.get(destinationURI.getActivityID());
        if (treeBuilder == null) {
            sendError(HttpServletResponse.SC_BAD_REQUEST, null);
            return;
        }

        if (myIsMove) {
            treeBuilder.move(sourcePath, destinationPath);
        } else {
            treeBuilder.copy(sourcePath, destinationPath);
        }

        handleDAVCreated(destination.toString(), "Destination", false); // FIXME: always false?
    }
}
