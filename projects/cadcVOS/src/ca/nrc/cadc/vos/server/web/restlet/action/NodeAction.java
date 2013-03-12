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
import java.security.AccessControlException;

import org.apache.log4j.Logger;
import org.restlet.Request;
import org.restlet.data.Form;
import org.restlet.representation.Representation;

import ca.nrc.cadc.io.ByteLimitExceededException;
import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.util.StringUtil;
import ca.nrc.cadc.vos.LinkingException;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeFault;
import ca.nrc.cadc.vos.NodeLockedException;
import ca.nrc.cadc.vos.NodeParsingException;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.server.AbstractView;
import ca.nrc.cadc.vos.server.NodePersistence;
import ca.nrc.cadc.vos.server.Views;
import ca.nrc.cadc.vos.server.auth.VOSpaceAuthorizer;

/**
 * Abstract class encapsulating the behaviour of an action on a Node.  Clients
 * must ensure that setVosURI(), setNodeXML(), setVOSpaceAuthorizer(), and
 * setNodePersistence() are called before using any concrete implementations of
 * this class.
 * 
 * @author majorb
 */
public abstract class NodeAction
{
    protected static Logger log = Logger.getLogger(NodeAction.class);
    
    // query form parameter names
    protected static final String QUERY_PARAM_VIEW = "view";
    protected static final String QUERY_PARAM_DETAIL = "detail";
    protected static final String QUERY_PARAM_URI = "uri";
    protected static final String QUERY_PARAM_LIMIT = "limit";

    // some subclasses may nede to determine hostname, request path, etc
    protected Request request;
    protected Form queryForm;
    protected VOSpaceAuthorizer voSpaceAuthorizer;
    protected VOSpaceAuthorizer partialPathVOSpaceAuthorizer;
    protected NodePersistence nodePersistence;
    protected VOSURI vosURI;
    protected Representation nodeXML;
    protected String stylesheetReference;
    
    /**
     * Set the URI for this action.
     * @param vosURI
     */
    public void setVosURI(VOSURI vosURI)
    {
        this.vosURI = vosURI;
    }
    
    /**
     * Set the node XML from the client for this action.
     * @param nodeXML
     */
    public void setNodeXML(Representation nodeXML)
    {
        this.nodeXML = nodeXML;
    }
    
    /**
     * Set the authorizer to be used by this action.
     * @param voSpaceAuthorizer
     */
    public void setVOSpaceAuthorizer(VOSpaceAuthorizer voSpaceAuthorizer)
    {
        this.voSpaceAuthorizer = voSpaceAuthorizer;
    }
    
    /**
     * Set the authorizer that allows partial paths.
     * @param partialPathVOSpaceAuthorizer
     */
    public void setPartialPathVOSpaceAuthorizer(
            VOSpaceAuthorizer partialPathVOSpaceAuthorizer)
    {
        this.partialPathVOSpaceAuthorizer = partialPathVOSpaceAuthorizer;
    }
    
    /**
     * Set the persistence to be used by this action.
     * @param nodePersistence
     */
    public void setNodePersistence(NodePersistence nodePersistence)
    {
        this.nodePersistence = nodePersistence;
    }
    
    /**
     * Set the request object.
     * @param request
     */
    public void setRequest(Request request)
    {
        this.request = request;
    }
    
    /**
     * Set the form object.
     * @param form
     */
    public void setQueryForm(Form form)
    {
        this.queryForm = form;
    }
    
    /**
     * Set the stylesheet reference.
     * @param stylesheetReference  The URI reference string to the stylesheet
     *                             location.
     */
    public void setStylesheetReference(String stylesheetReference)
    {
        this.stylesheetReference = stylesheetReference;
    }
 
    /**
     * Return the view requested by the client, or null if none specified.
     *
     * @return  Instance of an AbstractView.
     *
     * @throws Exception If the object could not be constructed.
     */
    protected AbstractView getView() throws Exception
    {
        String viewReference = queryForm.getFirstValue(QUERY_PARAM_VIEW);
        
        if (!StringUtil.hasText(viewReference))
        {
            return null;
        }
        
        // the default view is the same as no view
        if (viewReference.equalsIgnoreCase(VOS.VIEW_DEFAULT))
        {
            return null;
        }
        
        final Views views = new Views();
        AbstractView view = views.getView(viewReference);

        if (view == null)
        {
            throw new UnsupportedOperationException(
                    "No view configured matching reference: " + viewReference);
        }
        view.setNodePersistence(nodePersistence);
        view.setVOSpaceAuthorizer(voSpaceAuthorizer);
        
        return view;
    }
    
    /**
     * Perform the action for which the subclass was designed.
     * 
     * The return object from this method (and from performNodeAction) must be an object
     * of type NodeActionResult.
     * 
     * @param clientNode teh node supplied by the client (may be null)
     * @param serverNode the persistent node returned from doAuthorizationCheck
     * @return the appropriate NodeActionResult from which the response is constructed
     */
    protected abstract NodeActionResult performNodeAction(Node clientNode, Node serverNode)
        throws URISyntaxException, NodeParsingException, FileNotFoundException, TransientException;
    
    /**
     * Given the node URI and XML, return the Node object specified
     * by the client.
     *
     * @return the deault implementation returns null
     * @throws URISyntaxException
     * @throws NodeParsingException
     * @throws IOException
     */
    protected abstract Node getClientNode()
        throws URISyntaxException, NodeParsingException, IOException;
    
    /**
     * Perform an authorization check for the given node and return (if applicable)
     * the persistent version of the Node.
     *
     * @return the applicable persistent (server) Node
     * @throws AccessControlException if permission is denied
     * @throws FileNotFoundException if the target node does not exist
     * @throws LinkingException if a container link in the path could not be resolved
     */
    protected abstract Node doAuthorizationCheck()
        throws AccessControlException, FileNotFoundException, LinkingException, TransientException;
    
    /**
     * Entry point in performing the steps of a Node Action.  This includes:
     * 
     * Calling abstract method getClientNode()
     * Calling abstract method doAuthorizationCheck()
     * Calling abstract method performNodeAction()
     * 
     * The return object from this method (and from performNodeAction) must be an object
     * of type NodeActionResult.
     */
    public NodeActionResult run() throws TransientException
    {
        
        try
        {
            // Create the client version of the node to be used for the operation
            Node clientNode = getClientNode();
            if (clientNode != null)
                log.debug("client node: " + clientNode.getUri());
            else
                log.debug("no client node");

            // perform the authorization check
            long start = System.currentTimeMillis();
            Node serverNode = doAuthorizationCheck();
            long end = System.currentTimeMillis();
            log.debug("doAuthorizationCheck() elapsed time: " + (end - start) + "ms");
            log.debug("doAuthorizationCheck() returned server node: " + serverNode.getUri());
            
            // perform the node action
            start = System.currentTimeMillis();
            NodeActionResult result = performNodeAction(clientNode, serverNode);
            end = System.currentTimeMillis();
            log.debug("performNodeAction() elapsed time: " + (end - start) + "ms");
            return result;
        }
        catch (ByteLimitExceededException e)
        {
            String faultMessage = "XML document exceeds " + e.getLimit() + " bytes";
            log.debug(faultMessage);
            return handleException(NodeFault.RequestEntityTooLarge, faultMessage, false);
        }
        catch (FileNotFoundException e)
        {
            log.debug("Could not find node with path: " + vosURI.getPath());
            NodeFault nodeFault = handleException(e);
            String faultMessage = vosURI.toString();
            return handleException(nodeFault, faultMessage, false);
        }
        catch (URISyntaxException e)
        {
            String faultMessage = "URI not well formed: " + vosURI;
            log.debug(faultMessage);
            return handleException(NodeFault.InvalidURI, faultMessage, false);
        }
        catch (NodeLockedException e)
        {
            String faultMessage = "Node Locked: " + e.getMessage();
            log.debug(faultMessage);
            return handleException(NodeFault.NodeLocked, faultMessage, false);
        }
        catch (AccessControlException e)
        {
            String faultMessage = e.getMessage();
            if (!StringUtil.hasText(e.getMessage()))
            {
                faultMessage = "Access Denied";
            }
            log.debug(faultMessage);
            return handleException(NodeFault.PermissionDenied, faultMessage, false);
        }
        catch (NodeParsingException e)
        {
            String faultMessage = "Node XML not well formed: " + e.getMessage();
            log.debug(faultMessage);
            return handleException(NodeFault.TypeNotSupported, faultMessage, false);
        }
        catch (UnsupportedOperationException e)
        {
            String faultMessage = "Not supported: " + e.getMessage();
            log.debug(faultMessage);
            return handleException(NodeFault.InvalidArgument, faultMessage, false);
        }
        catch (IllegalArgumentException e)
        {
            String faultMessage = "Bad input: " + e.getMessage();
            log.debug(faultMessage);
            return handleException(NodeFault.InvalidArgument, faultMessage, false);
        }
        catch (LinkingException e)
        {
            String faultMessage = "Linking exception: " + e.getMessage();
            log.debug(faultMessage);
            return handleException(NodeFault.UnreadableLinkTarget, faultMessage, false);
        }
        catch(IllegalStateException e)
        {
            String faultMessage = e.getMessage();
            log.debug(faultMessage);
            return handleException(NodeFault.InternalFault, faultMessage, true);
        }
        catch (TransientException e)
        {
            // pass transient exceptions up to the resource layer
            throw e;
        }
        catch (Throwable t)
        {
            String faultMessage = "Internal Error:" + t.getMessage();
            log.debug("BUG: " + faultMessage, t);
            return handleException(NodeFault.InternalFault, faultMessage, true);
        }
    }

    protected NodeFault handleException(FileNotFoundException ex)
        throws TransientException
    {
        if (this instanceof CreateNodeAction)
            return NodeFault.ContainerNotFound;

        return NodeFault.NodeNotFound;
    }
    
    /**
     * Create a NodeActionResult from the given fault and message.
     * @param nodeFault The fault.
     * @param message An optional message.
     * @return The new NodeActionResult.
     */
    private NodeActionResult handleException(NodeFault nodeFault, String message, boolean serviceFailure)
    {
        nodeFault.setMessage(message);
        nodeFault.setServiceFailure(serviceFailure);
        return new NodeActionResult(nodeFault);
    }
}
