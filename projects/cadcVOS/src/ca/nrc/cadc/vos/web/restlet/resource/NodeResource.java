/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2009.                            (c) 2009.
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
*  $Revision: 4 $
*
************************************************************************
*/

package ca.nrc.cadc.vos.web.restlet.resource;

import java.net.URISyntaxException;
import java.security.AccessControlException;
import java.util.HashSet;
import java.util.Stack;

import org.apache.log4j.Logger;
import org.restlet.data.Method;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;

import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeAlreadyExistsException;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodeParsingException;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.dao.SearchNode;
import ca.nrc.cadc.vos.web.representation.NodeInputRepresentation;
import ca.nrc.cadc.vos.web.representation.NodeOutputRepresentation;

/**
 * Handles HTTP requests for Node resources.
 * 
 * @author majorb
 *
 */
public class NodeResource extends BaseResource
{
    private static Logger log = Logger.getLogger(NodeResource.class);
    
    private String path;
    private Node node;
    
    /**
     * Called after object instantiation.
     */
    public void doInit()
    {
        log.debug("Enter NodeResource.doInit()");

        // TODO: Put the authentication principals in a subject
        VOSURI vosURI = null;
        try
        {
            HashSet<Method> allowedMethods = new HashSet<Method>();
            allowedMethods.add(Method.GET);
            allowedMethods.add(Method.PUT);
            allowedMethods.add(Method.DELETE);
            allowedMethods.add(Method.POST);
            setAllowedMethods(allowedMethods);

            path = (String) getRequest().getAttributes().get("nodePath");  
            log.debug("doInit() path = " + path);
            
            if (path == null || path.trim().length() == 0)
            {
                throw new IllegalArgumentException("No node path information provided.");
            }
            
            vosURI = new VOSURI(getVosUri() + "/" + path);
            
            // Get the node for the operation, either from the persistent layer,
            // or from the client supplied xml
            Node clientNode = null;
            
            if (getMethod().equals(Method.GET) || getMethod().equals(Method.DELETE))
            {
                clientNode = new SearchNode(vosURI);
            }
            else if (getMethod().equals(Method.PUT) || getMethod().equals(Method.POST))
            {
                Representation xml = getRequestEntity();
                NodeInputRepresentation nodeInputRepresentation =
                    new NodeInputRepresentation(xml, path);
                clientNode = nodeInputRepresentation.getNode();
            }
            else
            {
                throw new UnsupportedOperationException("Method not supported: " + getMethod());
            }
            
            log.debug("Client node is: " + clientNode);
           
            // find and stack the path of the node to
            // the root
            Stack<Node> nodeStack = stackToRoot(clientNode);
            
            Node persistentNode = null;
            Node nextNode = null;
            ContainerNode parent = null;
            
            while (!nodeStack.isEmpty())
            {
                nextNode = nodeStack.pop();
                nextNode.setParent(parent);
                log.debug("Retrieving node with path: " + nextNode.getPath());
                
                // get the node from the persistence layer
                persistentNode = getNodePersistence().getFromParent(nextNode, parent);
                
                // check authorization
                // TODO: Check authorization using Subject.doAs()
                authorize(persistentNode, !nodeStack.isEmpty());

                // get the parent 
                if (persistentNode instanceof ContainerNode)
                {
                    parent = (ContainerNode) persistentNode;
                }
                else if (!nodeStack.isEmpty())
                {
                    final String message = "Non-container node found mid-tree";
                    log.warn(message);
                    throw new NodeNotFoundException(message);
                }
                
            }
            
            node = persistentNode;
            log.debug("doInit() retrived node: " + node);
        }
        catch (NodeNotFoundException e)
        {
            final String message = "Could not find node with path: " + path;
            log.debug(message, e);
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, message);
        }
        catch (URISyntaxException e)
        {
            final String message = "URI not well formed: " + vosURI;
            log.debug(message, e);
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, message);
        }
        catch (AccessControlException e)
        {
            final String message = "Access Denied: " + e.getMessage();
            log.debug(message, e);
            setStatus(Status.CLIENT_ERROR_UNAUTHORIZED, message);
        }
        catch (NodeParsingException e)
        {
            final String message = "Node XML not well formed: " + e.getMessage();
            log.debug(message, e);
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, message);
        }
        catch (UnsupportedOperationException e)
        {
            final String message = "Not supported: " + e.getMessage();
            log.debug(message, e);
            setStatus(Status.SERVER_ERROR_NOT_IMPLEMENTED, message);
        }
        catch (IllegalArgumentException e)
        {
            final String message = "Bad input: " + e.getMessage();
            log.debug(message, e);
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, message);
        }
        catch (IllegalStateException e)
        {
            final String message = "Internal Error:" + e.getMessage();
            log.debug(message, e);
            setStatus(Status.SERVER_ERROR_INTERNAL, message);
        }
        catch (Throwable t)
        {
            final String message = "Internal Error:" + t.getMessage();
            log.debug(message, t);
            setStatus(Status.SERVER_ERROR_INTERNAL, message);
        }
    }
    
    /**
     * HTTP GET
     */
    @Get("xml")
    public Representation represent()
    {
        log.debug("Enter NodeResource.represent()");
        NodeWriter nodeWriter = new NodeWriter();
        return new NodeOutputRepresentation(node, nodeWriter);
    }
    
    /**
     * HTTP PUT
     */
    @Put("xml")
    //public Representation store(Representation xmlValue)
    public Representation store()
    {   
        log.debug("Enter NodeResource.store()");
        try
        {
            Node storedNode = getNodePersistence().putInContainer(node, node.getParent());
            
            // return the node in xml format
            NodeWriter nodeWriter = new NodeWriter();
            return new NodeOutputRepresentation(storedNode, nodeWriter);
        }
        catch (NodeAlreadyExistsException e)
        {
            final String message = "Node already exists: " + path;
            log.debug(message, e);
            setStatus(Status.CLIENT_ERROR_CONFLICT, message);
        }
        catch (NodeNotFoundException e)
        {
            final String message = "Could not resolve part of path for node: " + path;
            log.debug(message, e);
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, message);
        }
        return null;
    }
    
    /**
     * HTTP POST
     */
    @Post("xml")
    //public Representation accept(Representation xmlValue)
    public Representation accept()
    {
        log.debug("Enter NodeResource.accept()");
        try
        {
            Node updatedNode = getNodePersistence().updateProperties(node);
            
            // return the node in xml format
            NodeWriter nodeWriter = new NodeWriter();
            return new NodeOutputRepresentation(updatedNode, nodeWriter);
        }
        catch (NodeNotFoundException e)
        {
            final String message = "Could not resolve part of path for node: " + path;
            log.debug(message, e);
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, message);
        }
        return null;
    }
    
    /**
     * HTTP DELETE
     */
    @Delete
    public void remove()
    {
        log.debug("Enter NodeResource.remove()");
        try
        {
            getNodePersistence().delete(node, true);
        }
        catch (NodeNotFoundException e)
        {
            final String message = "Could not find node with path: " + path;
            log.debug(message, e);
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, message);
        }
    }
    
    private Stack<Node> stackToRoot(Node node)
    {
        Stack<Node> nodeStack = new Stack<Node>();
        Node nextNode = node;
        while (nextNode != null)
        {
            nodeStack.push(nextNode);
            nextNode = nextNode.getParent();
        }
        return nodeStack;
    }
    
    private void authorize(Node node, boolean isParentNode)
    throws AccessControlException
    {
        log.debug("Checking authorization for HTTP " + getMethod());
        Object authorizationObject = null;
        if (getMethod().equals(Method.GET))
        {
            authorizationObject = getNodeAuthorizer().getReadPermission(
                    node.getUri().getURIObject());
        }
        else
        {
            if (isParentNode)
            {
                authorizationObject = getNodeAuthorizer().getReadPermission(
                        node.getUri().getURIObject());
            }
            else
            {
                authorizationObject = getNodeAuthorizer().getWritePermission(
                        node.getUri().getURIObject());
            }
        }
        log.info("Authorization for node " + node.getUri() + ": " + authorizationObject);
    }

}
