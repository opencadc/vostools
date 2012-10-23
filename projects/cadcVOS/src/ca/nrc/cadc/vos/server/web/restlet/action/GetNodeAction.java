/**
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2010.                            (c) 2010.
 *  Government of Canada                 Gouvernement du Canada
 *  National Research Council            Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 *  All rights reserved                  Tous droits réservés
 *
 *  NRC disclaims any warranties,        Le CNRC dénie toute garantie
 *  expressed, implied, or               énoncée, implicite ou légale,
 *  statutory, of any kind with          de quelque nature que ce
 *  respect to the software,             soit, concernant le logiciel,
 *  including without limitation         y compris sans restriction
 *  any warranty of merchantability      toute garantie de valeur
 *  or fitness for a particular          marchande ou de pertinence
 *  purpose. NRC shall not be            pour un usage particulier.
 *  liable in any event for any          Le CNRC ne pourra en aucun cas
 *  damages, whether direct or           être tenu responsable de tout
 *  indirect, special or general,        dommage, direct ou indirect,
 *  consequential or incidental,         particulier ou général,
 *  arising from the use of the          accessoire ou fortuit, résultant
 *  software.  Neither the name          de l'utilisation du logiciel. Ni
 *  of the National Research             le nom du Conseil National de
 *  Council of Canada nor the            Recherches du Canada ni les noms
 *  names of its contributors may        de ses  participants ne peuvent
 *  be used to endorse or promote        être utilisés pour approuver ou
 *  products derived from this           promouvoir les produits dérivés
 *  software without specific prior      de ce logiciel sans autorisation
 *  written permission.                  préalable et particulière
 *                                       par écrit.
 *
 *  This file is part of the             Ce fichier fait partie du projet
 *  OpenCADC project.                    OpenCADC.
 *
 *  OpenCADC is free software:           OpenCADC est un logiciel libre ;
 *  you can redistribute it and/or       vous pouvez le redistribuer ou le
 *  modify it under the terms of         modifier suivant les termes de
 *  the GNU Affero General Public        la “GNU Affero General Public
 *  License as published by the          License” telle que publiée
 *  Free Software Foundation,            par la Free Software Foundation
 *  either version 3 of the              : soit la version 3 de cette
 *  License, or (at your option)         licence, soit (à votre gré)
 *  any later version.                   toute version ultérieure.
 *
 *  OpenCADC is distributed in the       OpenCADC est distribué
 *  hope that it will be useful,         dans l’espoir qu’il vous
 *  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
 *  without even the implied             GARANTIE : sans même la garantie
 *  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
 *  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
 *  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
 *  General Public License for           Générale Publique GNU Affero
 *  more details.                        pour plus de détails.
 *
 *  You should have received             Vous devriez avoir reçu une
 *  a copy of the GNU Affero             copie de la Licence Générale
 *  General Public License along         Publique GNU Affero avec
 *  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
 *  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
 *                                       <http://www.gnu.org/licenses/>.
 *
 ************************************************************************
 */

package ca.nrc.cadc.vos.server.web.restlet.action;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessControlException;
import java.util.ListIterator;

import org.apache.log4j.Logger;
import org.restlet.data.Reference;

import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.util.StringUtil;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.LinkingException;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodeParsingException;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.server.AbstractView;
import ca.nrc.cadc.vos.server.PathResolver;
import ca.nrc.cadc.vos.server.web.representation.NodeOutputRepresentation;
import ca.nrc.cadc.vos.server.web.representation.ViewRepresentation;

/**
 * Class to perform the retrieval of a Node.
 * 
 * @author majorb
 */
public class GetNodeAction extends NodeAction
{
    
    protected static Logger log = Logger.getLogger(GetNodeAction.class);

    /**
     * Basic empty constructor.
     */
    public GetNodeAction()
    {
    }
    
    @Override
    protected Node getClientNode() throws URISyntaxException,
            NodeParsingException, IOException
    {
        // No client node in a GET
        return null;
    }

    @Override
    public Node doAuthorizationCheck()
        throws AccessControlException, FileNotFoundException, LinkingException, TransientException
    {
        // resolve any container links
        PathResolver pathResolver = new PathResolver(nodePersistence);
        try
        {
            return pathResolver.resolveWithReadPermissionCheck(vosURI, 
                    partialPathVOSpaceAuthorizer, false);
        }
        catch (NodeNotFoundException e)
        {
            throw new FileNotFoundException(e.getMessage());
        }
    }
    
    @Override
    public NodeActionResult performNodeAction(Node clientNode, Node serverNode)
        throws URISyntaxException, FileNotFoundException, TransientException
    {        
        long start;
        long end;
        if (serverNode instanceof ContainerNode)
        {
            // Paging parameters
            String startURI = queryForm.getFirstValue(QUERY_PARAM_URI);
            String pageLimitString = queryForm.getFirstValue(QUERY_PARAM_LIMIT);
            
            ContainerNode cn = (ContainerNode) serverNode;
            boolean paginate = false;
            VOSURI startURIObject = null;
            
            // parse the pageLimit
            Integer pageLimit = null;
            if (pageLimitString != null)
            {
                try
                {
                    pageLimit = new Integer(pageLimitString);
                    paginate = true;
                }
                catch (NumberFormatException e)
                {
                    throw new IllegalArgumentException("value for limit must be an integer.");
                }
            }
            
            // validate startURI
            if (StringUtil.hasText(startURI))
            {
                startURIObject = new VOSURI(startURI);
                if (!vosURI.equals(startURIObject.getParentURI()))
                {
                    throw new IllegalArgumentException("uri parameter not a child of target uri.");
                }
                paginate = true;
            }
            
            // get the children as requested
            start = System.currentTimeMillis();
            if (cn.getUri().isRoot())
            {
                // if this is the root node, ignore user requested limit
                // for the time being (see method doFilterChildren below)
                nodePersistence.getChildren(cn, startURIObject, null);
                log.debug(String.format(
                    "Get children on root returned [%s] nodes with startURI=[%s], pageLimit=[%s].",
                        cn.getNodes().size(), startURI, null));
            }
            else if (paginate)
            {
                if (pageLimit != null && pageLimit < 1)
                {
                    log.debug("Get children not called becauase pageLimit < 1");
                }
                else
                {
                    // request for a subset of children
                    nodePersistence.getChildren(cn, startURIObject, pageLimit);
                    log.debug(String.format(
                        "Get children returned [%s] nodes with startURI=[%s], pageLimit=[%s].",
                            cn.getNodes().size(), startURI, pageLimit));
                }
            }
            else
            {
                // get as many children as allowed
                nodePersistence.getChildren(cn);
                log.debug(String.format(
                    "Get children returned [%s] nodes.", cn.getNodes().size()));
            }

            end = System.currentTimeMillis();
            log.debug("nodePersistence.getChildren() elapsed time: " + (end - start) + "ms");
            doFilterChildren(cn, pageLimit);
        }
        
        // Detail level parameter
        String detailLevel = queryForm.getFirstValue(QUERY_PARAM_DETAIL);
        
        start = System.currentTimeMillis();
        
        // get the properties if no detail level is specified (null) or if the
        // detail level is something other than 'min'.
        if (!VOS.Detail.min.getValue().equals(detailLevel))
            nodePersistence.getProperties(serverNode);
        
        end = System.currentTimeMillis();
        log.debug("nodePersistence.getProperties() elapsed time: " + (end - start) + "ms");

        AbstractView view;
        String viewReference = queryForm.getFirstValue(QUERY_PARAM_VIEW);
        try
        {
            view = getView();
        }
        catch (Exception ex)
        {
            log.error("failed to load view: " + viewReference, ex);
            // this should generate an InternalFault in NodeAction
            throw new RuntimeException("view was configured but failed to load: " + viewReference);
        }

        if (view == null)
        {
            // no view specified or found--return the xml representation
            final NodeWriter nodeWriter = new NodeWriter();
            nodeWriter.setStylesheetURL(getStylesheetURL());
            
            // clear the properties from server node if the detail
            // level is set to 'min'
            if (VOS.Detail.min.getValue().equals(detailLevel))
                serverNode.getProperties().clear();
            
            return new NodeActionResult(new NodeOutputRepresentation(serverNode, nodeWriter));
        }
        else
        {
            Reference ref = request.getOriginalRef();
            URL url = ref.toUrl();
            view.setNode(serverNode, viewReference, url);
            URL redirectURL = view.getRedirectURL();
            if (redirectURL != null)
            {
                return new NodeActionResult(redirectURL);
            }
            else
            {
                // return a representation for the view
                return new NodeActionResult(new ViewRepresentation(view));
            }
        }
    }
    
    /**
     * Look for the stylesheet URL in the request context.
     * @return      The String URL of the stylesheet for this action.
     *              Null if no reference is provided.
     */
    public String getStylesheetURL()
    {
        log.debug("Stylesheet Reference is: " + stylesheetReference);
        if (stylesheetReference != null)
        {
            String scheme = request.getHostRef().getScheme();
            String server = request.getHostRef().getHostDomain();
            StringBuilder url = new StringBuilder();
            url.append(scheme);
            url.append("://");
            url.append(server);
            if (!stylesheetReference.startsWith("/"))
                url.append("/");
            url.append(stylesheetReference);
            return url.toString();
        }
        return null;
    }

    /**
     * If this is the root node, we apply a privacy policy and filter out
     * child nodes the caller is not allowed to read
     * 
     * TODO: The approach to manually trimming to the pageLimit size for
     * the root container will cease to work when the number of root
     * container nodes exceeds the upper limit of children returned as
     * defined in the node persistence.  Instead, a loop should be implemented,
     * as it is in the client, to manually retrieve more children if
     * necessary if some have been filtered out due to lack of read permission. 
     */
    private void doFilterChildren(ContainerNode node, Integer pageLimit)
    {
        if ( !node.getUri().isRoot() )
            return;

        ListIterator<Node> iter = node.getNodes().listIterator();
        int nodeCount = 0;
        boolean metLimit = false;
        while ( iter.hasNext() && !metLimit)
        {
            Node n = iter.next();
            try
            {
                voSpaceAuthorizer.getReadPermission(n);
                nodeCount++;
                
                // stop iterating if we've met a specified limit
                if (pageLimit != null && nodeCount >= pageLimit)
                    metLimit = true;
            }
            catch(AccessControlException ex)
            {
                log.debug("doFilterChildren: remove " + n);
                iter.remove();
            }
        }
        
        // since a limit isn't supplied to node persistence when getting
        // the children of the root node, apply the limit value now.
        if (pageLimit != null && node.getNodes().size() > pageLimit)
        {
            log.debug("Reducing child list size from " + node.getNodes().size() +
                    " to " + pageLimit + " to meet limit request on root node.");
            node.setNodes(node.getNodes().subList(0, pageLimit));
        }
    }

}
