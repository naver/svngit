package org.tmatesoft.svn.core.internal.server.dav.handlers;

import com.navercorp.svngit.GitFS;
import com.navercorp.svngit.SVNGitUtil;
import com.navercorp.svngit.TreeBuilder;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.fs.SVNGitRepository;
import org.tmatesoft.svn.core.internal.io.fs.SVNGitRepositoryFactory;
import org.tmatesoft.svn.core.internal.server.dav.*;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

// From DAVPropPatchHandler
public class SVNGitPropPatchHandler extends ServletDAVHandler {
    protected static Map<String, TreeBuilder> treeBuilders = new HashMap<>();
    private DAVPropPatchRequest myDAVRequest;

    public SVNGitPropPatchHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
    }

    public void execute() throws SVNException {
        long readLength = readInput(false);
        if (readLength <= 0) {
            getPropPatchRequest().invalidXMLRoot();
        }

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

        DAVResourceURI resourceURI = new DAVResourceURI(manager.getResourceContext(), manager.getResourcePathInfo(), null, false, version);
        CommitBuilder commitBuilder = SVNGitMakeActivityHandler.commitBuilders.get(resourceURI.getActivityID());
        if (commitBuilder == null) {
            sendError(HttpServletResponse.SC_BAD_REQUEST, null);
            return;
        }

        try {
            final Repository repo = ((GitFS)((SVNGitRepository) resourceRepository).getFSFS()).getGitRepository();
            RevCommit parentId = SVNGitUtil.getCommitFromRevision(repo, version);
            RevTree tree = new RevWalk(repo).parseTree(parentId);
            commitBuilder.setParentId(parentId);

            if (!SVNGitPropPatchHandler.treeBuilders.containsKey(resourceURI.getActivityID())) {
                SVNGitPropPatchHandler.treeBuilders.put(resourceURI.getActivityID(), new TreeBuilder(repo));
            }
            TreeBuilder treeBuilder = SVNGitPropPatchHandler.treeBuilders.get(resourceURI.getActivityID());
            treeBuilder.setBaseTreeId(tree.getId());
        } catch (IOException e) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Failed to ready commit"), e);
        }

        DAVPropPatchRequest requestXMLObject = getPropPatchRequest();
        DAVElementProperty rootElement = requestXMLObject.getRoot();
        List childrenElements = rootElement.getChildren();
        for (Iterator childrenIter = childrenElements.iterator(); childrenIter.hasNext();) {
            DAVElementProperty childElement = (DAVElementProperty) childrenIter.next();

            DAVElementProperty propChildrenElement = childElement.getChild(DAVElement.PROP);
            if (propChildrenElement == null) {
                SVNDebugLog.getDefaultLog().logError(SVNLogType.NETWORK, "A \"prop\" element is missing inside the propertyupdate command.");
                setResponseStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            for (Object child : propChildrenElement.getChildren()) {
                DAVElementProperty element = (DAVElementProperty) child;
                if (element.getName().equals(DAVElement.LOG)) {
                    commitBuilder.setMessage(element.getFirstValue(true));
                }
            }
        }

        // FIXME
        String propStatText =
                "<D:propstat><D:prop><ns3:log/></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>";

        DAVPropsResult propResult = new DAVPropsResult();
        propResult.addPropStatsText(propStatText);
        DAVResponse response = new DAVResponse(null, getRequest().getRequestURI(), null, propResult, 0);
        try {
            DAVXMLUtil.sendMultiStatus(response, getHttpServletResponse(), SC_MULTISTATUS, getNamespaces());
        } catch (IOException ioe) {
            throw new DAVException(ioe.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, SVNErrorCode.IO_ERROR.getCode());
        }
    }

    protected DAVRequest getDAVRequest() {
        return getPropPatchRequest();
    }

    private boolean isPropertyWritable(DAVElement property, LivePropertySpecification livePropSpec) {
        if (livePropSpec != null) {
            return livePropSpec.isWritable();
        }
        if (property == DAVElement.LOCK_DISCOVERY || property == DAVElement.SUPPORTED_LOCK) {
            return false;
        }
        return true;
    }

    private DAVPropPatchRequest getPropPatchRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVPropPatchRequest();
        }
        return myDAVRequest;
    }

    private static class PropertyChangeContext {
        private boolean myIsSet;
        private DAVElementProperty myProperty;
        private LivePropertySpecification myLivePropertySpec;
        private DAVException myError;
        private RollBackProperty myRollBackProperty;
    }

    private static class RollBackProperty {
        private DAVElement myPropertyName;
        private SVNPropertyValue myRollBackPropertyValue;

        public RollBackProperty(DAVElement propertyName, SVNPropertyValue rollBackPropertyValue) {
            myPropertyName = propertyName;
            myRollBackPropertyValue = rollBackPropertyValue;
        }
    }

    private static interface IDAVPropertyContextHandler {
        public void handleContext(PropertyChangeContext propContext);
    }

    private class DAVPropertyExecuteHandler implements IDAVPropertyContextHandler {
        private DAVPropertiesProvider myPropsProvider;

        public DAVPropertyExecuteHandler(DAVPropertiesProvider propsProvider) {
            myPropsProvider = propsProvider;
        }

        public void handleContext(PropertyChangeContext propContext) {
            if (propContext.myLivePropertySpec == null) {
                try {
                    SVNPropertyValue rollBackPropValue = myPropsProvider.getPropertyValue(propContext.myProperty.getName());
                    propContext.myRollBackProperty = new RollBackProperty(propContext.myProperty.getName(), rollBackPropValue);
                } catch (DAVException dave) {
                    handleError(dave, propContext);
                    return;
                }

                if (propContext.myIsSet) {
                    try {
                        myPropsProvider.storeProperty(propContext.myProperty);
                    } catch (DAVException dave) {
                        handleError(dave, propContext);
                        return;
                    }
                } else {
                    try {
                        myPropsProvider.removeProperty(propContext.myProperty.getName());
                    } catch (DAVException dave) {
                        //
                    }
                }
            }
        }

        private void handleError(DAVException dave, PropertyChangeContext propContext) {
            DAVException exc = new DAVException("Could not execute PROPPATCH.", HttpServletResponse.SC_INTERNAL_SERVER_ERROR, dave,
                    DAVErrorCode.PROP_EXEC);
            propContext.myError = exc;
        }
    }

    private class DAVPropertyRollBackHandler implements IDAVPropertyContextHandler {
        private DAVPropertiesProvider myPropsProvider;

        public DAVPropertyRollBackHandler(DAVPropertiesProvider propsProvider) {
            myPropsProvider = propsProvider;
        }

        public void handleContext(PropertyChangeContext propContext) {
            if (propContext.myRollBackProperty == null) {
                return;
            }

            if (propContext.myLivePropertySpec == null) {
                try {
                    myPropsProvider.applyRollBack(propContext.myRollBackProperty.myPropertyName,
                            propContext.myRollBackProperty.myRollBackPropertyValue);
                } catch (DAVException dave) {
                    if (propContext.myError == null) {
                        propContext.myError = dave;
                    } else {
                        DAVException err = dave;
                        while (err.getPreviousException() != null) {
                            err = err.getPreviousException();
                        }
                        err.setPreviousException(propContext.myError);
                        propContext.myError = dave;
                    }
                }
            }
        }
    }

}