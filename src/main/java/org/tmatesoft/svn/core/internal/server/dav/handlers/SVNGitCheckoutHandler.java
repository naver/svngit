package org.tmatesoft.svn.core.internal.server.dav.handlers;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.*;
import org.tmatesoft.svn.util.SVNLogType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

// Copy of DAVCheckoutHandler
public class SVNGitCheckoutHandler extends ServletDAVHandler {
    private DAVCheckOutRequest myDAVRequest;

    public SVNGitCheckoutHandler(DAVRepositoryManager repositoryManager, HttpServletRequest request, HttpServletResponse response) {
        super(repositoryManager, request, response);
    }

    public void execute() throws SVNException {
        long readLength = readInput(false);

        boolean applyToVSN = false;
        boolean isUnreserved = false;
        boolean createActivity = false;

        List activities = null;
        if (readLength > 0) {
            DAVCheckOutRequest davRequest = getCheckOutRequest();
            if (davRequest.isApplyToVersion()) {
                if (getRequestHeader(LABEL_HEADER) != null) {
                    response("DAV:apply-to-version cannot be used in conjunction with a Label header.",
                            DAVServlet.getStatusLine(HttpServletResponse.SC_CONFLICT), HttpServletResponse.SC_CONFLICT);
                }
                applyToVSN = true;
            }

            isUnreserved = davRequest.isUnreserved();
            DAVElementProperty rootElement = davRequest.getRoot();
            DAVElementProperty activitySetElement = rootElement.getChild(DAVCheckOutRequest.ACTIVITY_SET);
            if (activitySetElement != null) {
                if (activitySetElement.hasChild(DAVCheckOutRequest.NEW)) {
                    createActivity = true;
                } else {
                    activities = new LinkedList();
                    List activitySetChildren = activitySetElement.getChildren();
                    for (Iterator activitySetIter = activitySetChildren.iterator(); activitySetIter.hasNext();) {
                        DAVElementProperty activitySetChild = (DAVElementProperty) activitySetIter.next();
                        if (activitySetChild.getName() == DAVElement.HREF) {
                            activities.add(activitySetChild.getFirstValue(true));
                        }
                    }

                    if (activities.isEmpty()) {
                        throw new DAVException("Within the DAV:activity-set element, the DAV:new element must be used, or at least one DAV:href must be specified.",
                                null, HttpServletResponse.SC_BAD_REQUEST, null, SVNLogType.NETWORK, Level.FINE, null, null, null, 0, null);
                    }
                }
            }
        }

        DAVResource resource = getRequestedDAVResource(true, applyToVSN);
        if (!resource.exists()) {
            throw new DAVException(DAVServlet.getStatusLine(HttpServletResponse.SC_NOT_FOUND), null, HttpServletResponse.SC_NOT_FOUND, null,
                    SVNLogType.NETWORK, Level.FINE, null, null, null, 0, null);
        }

        if (resource.getResourceURI().getType() != DAVResourceType.REGULAR &&
                resource.getResourceURI().getType() != DAVResourceType.VERSION) {
            response("Cannot checkout this type of resource.", DAVServlet.getStatusLine(HttpServletResponse.SC_CONFLICT),
                    HttpServletResponse.SC_CONFLICT);
        }

        if (!resource.isVersioned()) {
            response("Cannot checkout unversioned resource.", DAVServlet.getStatusLine(HttpServletResponse.SC_CONFLICT),
                    HttpServletResponse.SC_CONFLICT);
        }

        if (resource.isWorking()) {
            response("The resource is already checked out to the workspace.", DAVServlet.getStatusLine(HttpServletResponse.SC_CONFLICT),
                    HttpServletResponse.SC_CONFLICT);
        }

        // Do Nothing? (FIXME)

        DAVURIInfo parse = DAVPathUtil.simpleParseURI((String) activities.get(0), resource);

        DAVResource workingResource =
                DAVWorkingResourceHelper.createWorkingResource(resource, parse.getActivityID(), "checked-out resource", false);

        setResponseHeader(CACHE_CONTROL_HEADER, CACHE_CONTROL_VALUE);

        handleDAVCreated(workingResource.getResourceURI().getRequestURI(), "Checked-out resource", false);
    }

    protected DAVRequest getDAVRequest() {
        return getCheckOutRequest();
    }

    private DAVCheckOutRequest getCheckOutRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVCheckOutRequest();
        }
        return myDAVRequest;
    }

}
