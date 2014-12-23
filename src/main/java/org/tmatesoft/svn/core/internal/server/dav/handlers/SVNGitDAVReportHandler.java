package org.tmatesoft.svn.core.internal.server.dav.handlers;

/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.util.SVNLogType;
import org.xml.sax.Attributes;

import java.util.logging.Level;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNGitDAVReportHandler extends DAVReportHandler {
    private HttpServletRequest myRequest;
    private HttpServletResponse myResponse;
    private DAVRepositoryManager myRepositoryManager;
    private boolean myIsUnknownReport;
    private DAVReportHandler myReportHandler;

    public SVNGitDAVReportHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
        myRepositoryManager = connector;
        myRequest = request;
        myResponse = response;
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (parent == null) {
            initReportHandler(element);
        }
        getReportHandler().handleAttributes(parent, element, attrs);
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        getReportHandler().handleCData(parent, element, cdata);
    }

    public void execute() throws SVNException {
        long read = readInput(false);
        if (myIsUnknownReport) {
            throw new DAVException("The requested report is unknown.", null, HttpServletResponse.SC_NOT_IMPLEMENTED, null, SVNLogType.DEFAULT, Level.FINE,
                    null, DAVXMLUtil.SVN_DAV_ERROR_TAG, DAVElement.SVN_DAV_ERROR_NAMESPACE, SVNErrorCode.UNSUPPORTED_FEATURE.getCode(), null);
        }

        if (read == 0) {
            throw new DAVException("The request body must specify a report.", HttpServletResponse.SC_BAD_REQUEST, SVNLogType.NETWORK);
        }

        setDefaultResponseHeaders();
        setResponseContentType(DEFAULT_XML_CONTENT_TYPE);
        setResponseStatus(HttpServletResponse.SC_OK);

        getReportHandler().execute();
    }

    private void initReportHandler(DAVElement rootElement) {
        myIsUnknownReport = false;
        if (rootElement == DATED_REVISIONS_REPORT) {
            setReportHandler(new DAVDatedRevisionHandler(myRepositoryManager, myRequest, myResponse));
        } else if (rootElement == FILE_REVISIONS_REPORT) {
            setReportHandler(new DAVFileRevisionsHandler(myRepositoryManager, myRequest, myResponse, this));
        } else if (rootElement == GET_LOCATIONS) {
            setReportHandler(new DAVGetLocationsHandler(myRepositoryManager, myRequest, myResponse, this));
        } else if (rootElement == LOG_REPORT) {
            setReportHandler(new DAVLogHandler(myRepositoryManager, myRequest, myResponse, this));
        } else if (rootElement == MERGEINFO_REPORT) {
            setReportHandler(new DAVMergeInfoHandler(myRepositoryManager, myRequest, myResponse, this));
        } else if (rootElement == GET_LOCKS_REPORT) {
            setReportHandler(new DAVGetLocksHandler(myRepositoryManager, myRequest, myResponse));
        } else if (rootElement == REPLAY_REPORT) {
            setReportHandler(new DAVReplayHandler(myRepositoryManager, myRequest, myResponse, this));
        } else if (rootElement == UPDATE_REPORT) {
            setReportHandler(new SVNGitDAVUpdateHandler(myRepositoryManager, myRequest, myResponse, this));
        } else if (rootElement == GET_LOCATION_SEGMENTS) {
            setReportHandler(new DAVGetLocationSegmentsHandler(myRepositoryManager, myRequest, myResponse, this));
        } else if (rootElement == GET_DELETED_REVISION_REPORT) {
            setReportHandler(new DAVGetDeletedRevisionHandler(myRepositoryManager, myRequest, myResponse, this));
        } else {
            myIsUnknownReport = true;
            setReportHandler(new DumpReportHandler(myRepositoryManager, myRequest, myResponse));
        }
    }

    protected DAVRequest getDAVRequest() {
        return getReportHandler().getDAVRequest();
    }

    private DAVReportHandler getReportHandler() {
        return myReportHandler;
    }

    private void setReportHandler(DAVReportHandler reportHandler) {
        myReportHandler = reportHandler;
    }

    private static class DumpReportHandler extends DAVReportHandler {
        private DAVRequest myDAVRequest;

        protected DumpReportHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
            super(connector, request, response);
        }

        protected DAVRequest getDAVRequest() {
            if (myDAVRequest == null) {
                myDAVRequest = new DAVRequest() {
                    protected void init() throws SVNException {
                    }
                };
            }
            return myDAVRequest;
        }
    }
}
