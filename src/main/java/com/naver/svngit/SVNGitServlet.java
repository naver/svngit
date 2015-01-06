/**
 * Original From SVNKit (http://svnkit.com/index.html)
 *
 * Modified by Naver Corp. (Author: Yi EungJun <eungjun.yi@navercorp.com>)
 */
package com.naver.svngit;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVHandlerFactory;
import org.tmatesoft.svn.core.internal.server.dav.handlers.ServletDAVHandler;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by nori on 14. 12. 16.
 */
public class SVNGitServlet extends DAVServlet {
    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ServletDAVHandler handler = null;
        logRequest(request);//TODO: remove later
        try {
            DAVRepositoryManager repositoryManager = new SVNGitRepositoryManager(getDAVConfig(), request);
            handler = SVNGitDAVHandlerFactory.createHandler(repositoryManager, request, response);
            handler.execute();
        } catch (DAVException de) {
            response.setContentType(XML_CONTENT_TYPE);
            handleError(de, response);
        } catch (SVNException svne) {
            StringWriter sw = new StringWriter();
            svne.printStackTrace(new PrintWriter(sw));
            /**
             * truncate status line if it is to long
             */
            String msg = sw.getBuffer().toString();
            if ( msg.length() > 128 ){
                msg = msg.substring(0, 128);
            }
            SVNErrorCode errorCode = svne.getErrorMessage().getErrorCode();
            if (errorCode == SVNErrorCode.FS_NOT_DIRECTORY ||
                    errorCode == SVNErrorCode.FS_NOT_FOUND ||
                    errorCode == SVNErrorCode.RA_DAV_PATH_NOT_FOUND) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, msg);
            } else if (errorCode == SVNErrorCode.NO_AUTH_FILE_PATH) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, msg);
            } else if (errorCode == SVNErrorCode.RA_NOT_AUTHORIZED) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, msg);
            } else {
                String errorBody = generateStandardizedErrorBody(errorCode.getCode(), null, null, svne.getMessage());
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType(XML_CONTENT_TYPE);
                response.getWriter().print(errorBody);
            }
        } catch (Throwable th) {
            StringWriter sw = new StringWriter();
            th.printStackTrace(new PrintWriter(sw));
            String msg = sw.getBuffer().toString();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg);
        } finally {
            response.flushBuffer();
        }
    }

    private String generateStandardizedErrorBody(int errorID, String namespace, String tagName, String description) {
        StringBuffer xmlBuffer = new StringBuffer();
        SVNXMLUtil.addXMLHeader(xmlBuffer);
        Collection namespaces = new ArrayList();
        namespaces.add(DAVElement.DAV_NAMESPACE);
        namespaces.add(DAVElement.SVN_APACHE_PROPERTY_NAMESPACE);
        if (namespace != null) {
            namespaces.add(namespace);
        }
        SVNXMLUtil.openNamespaceDeclarationTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVXMLUtil.SVN_DAV_ERROR_TAG, namespaces,
                SVNXMLUtil.PREFIX_MAP, xmlBuffer);
        String prefix = (String) SVNXMLUtil.PREFIX_MAP.get(namespace);
        if (prefix != null) {
            prefix = SVNXMLUtil.DAV_NAMESPACE_PREFIX;
        }
        if (tagName != null && tagName.length() > 0) {
            SVNXMLUtil.openXMLTag(prefix, tagName, SVNXMLUtil.XML_STYLE_SELF_CLOSING, null, xmlBuffer);
        }

        SVNXMLUtil.openXMLTag(SVNXMLUtil.SVN_APACHE_PROPERTY_PREFIX, "human-readable", SVNXMLUtil.XML_STYLE_NORMAL, "errcode",
                String.valueOf(errorID), xmlBuffer);
        xmlBuffer.append(SVNEncodingUtil.xmlEncodeCDATA(description));
        SVNXMLUtil.closeXMLTag(SVNXMLUtil.SVN_APACHE_PROPERTY_PREFIX, "human-readable", xmlBuffer);
        SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVXMLUtil.SVN_DAV_ERROR_TAG, xmlBuffer);
        return xmlBuffer.toString();
    }

    private void logRequest(HttpServletRequest request) {
        StringBuffer logBuffer = new StringBuffer();
        logBuffer.append('\n');
        logBuffer.append("request.getAuthType(): " + request.getAuthType());
        logBuffer.append('\n');
        logBuffer.append("request.getCharacterEncoding(): " + request.getCharacterEncoding());
        logBuffer.append('\n');
        logBuffer.append("request.getContentType(): " + request.getContentType());
        logBuffer.append('\n');
        logBuffer.append("request.getContextPath(): " + request.getContextPath());
        logBuffer.append('\n');
        logBuffer.append("request.getContentLength(): " + request.getContentLength());
        logBuffer.append('\n');
        logBuffer.append("request.getMethod(): " + request.getMethod());
        logBuffer.append('\n');
        logBuffer.append("request.getPathInfo(): " + request.getPathInfo());
        logBuffer.append('\n');
        logBuffer.append("request.getPathTranslated(): " + request.getPathTranslated());
        logBuffer.append('\n');
        logBuffer.append("request.getQueryString(): " + request.getQueryString());
        logBuffer.append('\n');
        logBuffer.append("request.getRemoteAddr(): " + request.getRemoteAddr());
        logBuffer.append('\n');
        logBuffer.append("request.getRemoteHost(): " + request.getRemoteHost());
        logBuffer.append('\n');
        logBuffer.append("request.getRemoteUser(): " + request.getRemoteUser());
        logBuffer.append('\n');
        logBuffer.append("request.getRequestURI(): " + request.getRequestURI());
        logBuffer.append('\n');
        logBuffer.append("request.getServerName(): " + request.getServerName());
        logBuffer.append('\n');
        logBuffer.append("request.getServerPort(): " + request.getServerPort());
        logBuffer.append('\n');
        logBuffer.append("request.getServletPath(): " + request.getServletPath());
        logBuffer.append('\n');
        logBuffer.append("request.getRequestURL(): " + request.getRequestURL());
        SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, logBuffer.toString());
    }
}
