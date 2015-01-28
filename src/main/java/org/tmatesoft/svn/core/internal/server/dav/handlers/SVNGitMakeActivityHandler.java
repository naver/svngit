package org.tmatesoft.svn.core.internal.server.dav.handlers;

import com.navercorp.svngit.GitFS;
import org.eclipse.jgit.lib.CommitBuilder;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.util.SVNLogType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

// Copy of DAVMakeActivityHandler
public class SVNGitMakeActivityHandler extends DAVMakeActivityHandler {
    private GitFS myGitFS;
    public static Map<String, CommitBuilder> commitBuilders = new HashMap<>(); // activity

    public SVNGitMakeActivityHandler(DAVRepositoryManager repositoryManager, HttpServletRequest request, HttpServletResponse response) {
        super(repositoryManager, request, response);
    }

    @Override
    public void execute() throws SVNException {
        DAVResource resource = getRequestedDAVResource(false, false);
        FSRepository repos = (FSRepository) resource.getRepository();
        myGitFS = (GitFS) repos.getFSFS();

        readInput(true);
        if (commitBuilders.containsKey(resource.getActivityID())) {
            throw new DAVException("<DAV:resource-must-be-null/>", HttpServletResponse.SC_CONFLICT, SVNLogType.NETWORK);
        }

        // FIXME: I don't know what it is
        if (!resource.canBeActivity()) {
            throw new DAVException("<DAV:activity-location-ok/>", HttpServletResponse.SC_FORBIDDEN, SVNLogType.NETWORK);
        }


        CommitBuilder builder = new CommitBuilder();
        commitBuilders.put(resource.getActivityID(), builder);

        setResponseHeader(CACHE_CONTROL_HEADER, CACHE_CONTROL_VALUE);
        handleDAVCreated(resource.getResourceURI().getURI(), "Activity", false);
    }
}
