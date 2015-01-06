/**
 * Original From SVNKit (http://svnkit.com/index.html)
 *
 * Modified by Naver Corp. (Author: Yi EungJun <eungjun.yi@navercorp.com>)
 */
package com.naver.svngit;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVHandlerFactory;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVReportHandler;
import org.tmatesoft.svn.core.internal.server.dav.handlers.SVNGitDAVReportHandler;
import org.tmatesoft.svn.core.internal.server.dav.handlers.ServletDAVHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by nori on 14. 12. 28.
 */
public class SVNGitDAVHandlerFactory extends DAVHandlerFactory {
    public static ServletDAVHandler createHandler(DAVRepositoryManager manager, HttpServletRequest request, HttpServletResponse response) throws SVNException {
        String methodName = request.getMethod();

        if (METHOD_REPORT.equals(methodName)) {
            return new SVNGitDAVReportHandler(manager, request, response);
        } else {
            return DAVHandlerFactory.createHandler(manager, request, response);
        }
    }
}
