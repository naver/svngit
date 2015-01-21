/**
 * Original From SVNKit (http://svnkit.com/index.html)
 *
 * Modified by Naver Corp. (Author: Yi EungJun <eungjun.yi@navercorp.com>)
 */
package org.tmatesoft.svn.core.internal.server.dav.handlers;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class SVNGitDAVHandlerFactory extends DAVHandlerFactory {
    public static ServletDAVHandler createHandler(DAVRepositoryManager manager, HttpServletRequest request, HttpServletResponse response) throws SVNException {
        String methodName = request.getMethod();

        if (METHOD_REPORT.equals(methodName)) {
            return new SVNGitDAVReportHandler(manager, request, response);
        } else if (METHOD_MKACTIVITY.equals(methodName)) {
            return new SVNGitMakeActivityHandler(manager, request, response);
        } else if (METHOD_DELETE.equals(methodName)) {
            return new SVNGitDeleteHandler(manager, request, response);
        } else if (METHOD_CHECKOUT.equals(methodName)) {
            return new SVNGitCheckoutHandler(manager, request, response);
        } else if (METHOD_PROPPATCH.equals(methodName)) {
            return new SVNGitPropPatchHandler(manager, request, response);
        } else if (METHOD_PUT.equals(methodName)) {
            return new SVNGitPutHandler(manager, request, response);
        } else if (METHOD_MERGE.equals(methodName)) {
            return new SVNGitMergeHandler(manager, request, response);
        } else if (METHOD_MKCOL.equals(methodName)) {
            return new SVNGitMakeCollectionHandler(manager, request, response);
        } else if (METHOD_COPY.equals(methodName)) {
            return new SVNGitCopyMoveHandler(manager, request, response, false);
        } else if (METHOD_MOVE.equals(methodName)) {
            return new SVNGitCopyMoveHandler(manager, request, response, true);
        } else {
            return DAVHandlerFactory.createHandler(manager, request, response);
        }
    }
}
