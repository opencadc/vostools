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
import java.security.PrivilegedAction;

import org.apache.log4j.Logger;
import org.restlet.Request;
import org.restlet.representation.Representation;

import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeFault;
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
 * Operations should be performed using Subject.doAs(subject, action) if a
 * subject is available.  This will invoke the entry point run() below.
 * 
 * @author majorb
 */
public abstract class NodeAction implements PrivilegedAction<Object>
{
    protected static Logger log = Logger.getLogger(NodeAction.class);
    
    private VOSpaceAuthorizer voSpaceAuthorizer;
    private NodePersistence nodePersistence;
    private VOSURI vosURI;
    private Representation nodeXML;
    private Request request;
    private String viewReference; 
    private String stylesheetReference;
    
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
     * Set the view reference sent in by the client.
     * @param viewReference
     */
    public void setViewReference(String viewReference)
    {
        this.viewReference = viewReference;
    }
    
    /**
     * Return the view reference sent in by the client.
     * @return
     */
    protected String getViewReference()
    {
        return viewReference;
    }
    
    /**
     * Set the stylesheet reference.
     */
    public void setStylesheetReference(String stylesheetReference)
    {
        this.stylesheetReference = stylesheetReference;
    }
    
    /**
     * Get the stylesheet reference.
     */
    protected String getStylesheetReference()
    {
        return stylesheetReference;
    }
    
    /**
     * Return the view requested by the client, or null if none specified.
     * @throws IllegalAccessException 
     * @throws InstantiationException 
     */
    protected AbstractView getView() throws InstantiationException, IllegalAccessException
    {
        if (viewReference == null || viewReference.trim().length() == 0)
        {
            return null;
        }
        
        // the default view is the same as no view
        if (viewReference.equalsIgnoreCase(VOS.VIEW_DEFAULT))
        {
            return null;
        }
        
        Views views = new Views();
        AbstractView view = views.getView(viewReference);
        if (view == null)
        {
            throw new UnsupportedOperationException("No view configured matching reference: "
                    + viewReference);
        }
        return view;
    }
    
    /**
     * Perform the action for which the subclass was designed.
     * 
     * The return object from this method (and from performNodeAction) must be an object
     * of type NodeActionResult.
     * 
     * @param node
     * @param nodePersistence
     * @param request
     * @return The NodeAction result
     * @throws Exception If a problem occurs.
     */
    abstract NodeActionResult performNodeAction(Node node, NodePersistence nodePersistence, Request request) throws Exception;
    
    /**
     * Given the node URI and XML, return the Node object specified
     * by the client.
     */
    abstract Node getClientNode(VOSURI vosURI, Representation nodeXML) throws URISyntaxException, NodeParsingException, IOException;
    
    /**
     * Perform an authorization check for the given node and return (if applicable)
     * the persistent version of the Node.
     */
    abstract Node doAuthorizationCheck(VOSpaceAuthorizer voSpaceAuthorizer, Node clientNode) throws AccessControlException, FileNotFoundException;
    
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
    @Override
    public Object run()
    {
        
        try
        {
            // Create the client version of the node to be used for the operation
            Node clientNode = getClientNode(vosURI, nodeXML);
            log.debug("Client node is: " + clientNode);
            
            // perform the authorization check
            Node node = doAuthorizationCheck(voSpaceAuthorizer, clientNode);
            setNodeURI(node, vosURI);
            log.debug("doAuthorizationCheck() retrived node: " + node);
            
            // perform the node action
            return performNodeAction(node, nodePersistence, request);
            
        }
        catch (FileNotFoundException e)
        {
            String faultMessage = vosURI.toString();
            log.debug("Could not find node with path: " + vosURI.getPath());
            return handleException(NodeFault.NodeNotFound, faultMessage);
        }
        catch (URISyntaxException e)
        {
            String faultMessage = "URI not well formed: " + vosURI;
            log.debug(faultMessage, e);
            return handleException(NodeFault.InvalidURI, faultMessage);
        }
        catch (AccessControlException e)
        {
            String faultMessage = "Access Denied: " + e.getMessage();
            log.debug(faultMessage, e);
            return handleException(NodeFault.PermissionDenied, faultMessage);
        }
        catch (NodeParsingException e)
        {
            String faultMessage = "Node XML not well formed: " + e.getMessage();
            log.debug(faultMessage, e);
            return handleException(NodeFault.InvalidToken, faultMessage);
        }
        catch (UnsupportedOperationException e)
        {
            String faultMessage = "Not supported: " + e.getMessage();
            log.debug(faultMessage, e);
            return handleException(NodeFault.NotSupported, faultMessage);
        }
        catch (IllegalArgumentException e)
        {
            String faultMessage = "Bad input: " + e.getMessage();
            log.debug(faultMessage, e);
            return handleException(NodeFault.BadRequest, faultMessage);
        }
        catch (Throwable t)
        {
            String faultMessage = "Internal Error:" + t.getMessage();
            log.debug(faultMessage, t);
            return handleException(NodeFault.InternalFault, faultMessage);
        }
    }
    
    /**
     * Create a NodeActionResult from the given fault and message.
     * @param nodeFault The fault.
     * @param message An optional message.
     * @return The new NodeActionResult.
     */
    private NodeActionResult handleException(NodeFault nodeFault, String message)
    {
        nodeFault.setMessage(message);
        return new NodeActionResult(nodeFault);
    }
    
    /**
     * Recursive method to set the URI of a node and its children.
     * @param node
     * @param uri
     * @throws URISyntaxException
     */
    protected void setNodeURI(Node node, VOSURI uri) throws URISyntaxException
    {
        node.setUri(uri);
        if (node instanceof ContainerNode)
        {
            ContainerNode containerNode = (ContainerNode) node;
            for (Node child : containerNode.getNodes())
            {
                VOSURI childURI = new VOSURI(uri.toString() + "/" + child.getName());
                setNodeURI(child, childURI);
            }
        }
    }

}
