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

import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.security.AccessControlException;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;
import org.restlet.Request;
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
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.auth.PrivilegedReadAuthorizationExceptionAction;
import ca.nrc.cadc.vos.auth.PrivilegedWriteAuthorizationExceptionAction;
import ca.nrc.cadc.vos.auth.VOSpaceAuthorizer;
import ca.nrc.cadc.vos.dao.SearchNode;
import ca.nrc.cadc.vos.web.representation.NodeErrorRepresentation;
import ca.nrc.cadc.vos.web.representation.NodeInputRepresentation;
import ca.nrc.cadc.vos.web.representation.NodeOutputRepresentation;

/**
 * Handles HTTP requests for Node resources.
 * 
 * @author majorbNodeNotFoundException
 *
 */
public class NodeResource extends BaseResource
{
    private static Logger log = Logger.getLogger(NodeResource.class);
    
    private static final String CERTIFICATE_REQUEST_ATTRIBUTE_NAME = "org.restlet.https.clientCertificates";
    
    private String path;
    private Node node;
    private NodeError nodeError = null;
    private VOSURI vosURI = null;
    
    /**
     * Called after object instantiation.
     */
    public void doInit()
    {
        log.debug("Enter NodeResource.doInit(): " + getMethod());

        // Create a subject for authentication
        Set<Principal> principals = getPrincipals(getRequest());
        Subject subject = null;
        if (principals != null && principals.size() > 0) {
            Set<Object> emptyCredentials = new HashSet<Object>();
            subject = new Subject(true, principals, emptyCredentials, emptyCredentials);
        }
        
        log.debug(principals.size() + " principals found in request.");
        
        try
        {
            Set<Method> allowedMethods = new CopyOnWriteArraySet<Method>();
            allowedMethods.add(Method.GET);
            allowedMethods.add(Method.PUT);
            allowedMethods.add(Method.DELETE);
            allowedMethods.add(Method.POST);
            setAllowedMethods(allowedMethods);

            path = (String) getRequest().getAttributes().get("nodePath");  
            log.debug("path = " + path);
            
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
            
            // perform the authorization check
            VOSpaceAuthorizer voSpaceAuthorizer = new VOSpaceAuthorizer();
            voSpaceAuthorizer.setNodePersistence(getNodePersistence());
            try
            {
                if (getMethod().equals(Method.GET))
                {
                    PrivilegedReadAuthorizationExceptionAction readAuthorization =
                        new PrivilegedReadAuthorizationExceptionAction(voSpaceAuthorizer, clientNode);
                    node = (Node) Subject.doAs(subject, readAuthorization);
                }
                else if (getMethod().equals(Method.PUT))
                {
                    PrivilegedWriteAuthorizationExceptionAction writeAuthorization =
                        new PrivilegedWriteAuthorizationExceptionAction(voSpaceAuthorizer, clientNode.getParent());
                    ContainerNode parent = (ContainerNode) Subject.doAs(subject, writeAuthorization);
                    node = clientNode;
                    node.setParent(parent);
                }
                else 
                {
                    // (DELETE or POST)
                    PrivilegedWriteAuthorizationExceptionAction writeAuthorization =
                        new PrivilegedWriteAuthorizationExceptionAction(voSpaceAuthorizer, clientNode);
                    node = (Node) Subject.doAs(subject, writeAuthorization);
                }
            }
            catch (PrivilegedActionException e)
            {
                log.info("Authentication failed: " + e.getCause().getMessage());
                throw e.getCause();
            }
            
            node.setUri(vosURI);
            
            // If this is an HTTP PUT, set the owner on the node to be
            // the distinguished name contained in the client certificate(s)
            if (getMethod().equals(Method.PUT))
            {
                clientNode.setOwner(getOwner(principals));
            }
            
            log.debug("doInit() retrived node: " + node);
        }
        catch (FileNotFoundException e)
        {
            log.debug("Could not find node with path: " + path, e);
            nodeError = new NodeError(NodeFault.NodeNotFound, vosURI.toString());
        }
        catch (URISyntaxException e)
        {
            String message = "URI not well formed: " + vosURI;
            log.debug(message, e);
            nodeError = new NodeError(NodeFault.InvalidURI, message);
        }
        catch (AccessControlException e)
        {
            final String message = "Access Denied: " + e.getMessage();
            log.debug(message, e);
            nodeError = new NodeError(NodeFault.PermissionDenied, message);
        }
        catch (NodeParsingException e)
        {
            final String message = "Node XML not well formed: " + e.getMessage();
            log.debug(message, e);
            nodeError = new NodeError(NodeFault.InvalidToken, message);
        }
        catch (UnsupportedOperationException e)
        {
            final String message = "Not supported: " + e.getMessage();
            log.debug(message, e);
            nodeError = new NodeError(NodeFault.NotSupported, message);
        }
        catch (IllegalArgumentException e)
        {
            final String message = "Bad input: " + e.getMessage();
            log.debug(message, e);
            nodeError = new NodeError(NodeFault.BadRequest, message);
        }
        catch (Throwable t)
        {
            final String message = "Internal Error:" + t.getMessage();
            log.debug(message, t);
            nodeError = new NodeError(NodeFault.InternalFault, message);
        }
    }
    
    /**
     * HTTP GET
     */
    @Get("xml")
    public Representation represent()
    {
        log.debug("Enter NodeResource.represent()");
        if (nodeError != null)
        {
            return createNodeFaultRepresentation(nodeError);
        }
        
        try
        {
            NodeWriter nodeWriter = new NodeWriter();
            return new NodeOutputRepresentation(node, nodeWriter);
        }
        catch (Throwable t)
        {
            log.debug(t);
            return createNodeFaultRepresentation(new NodeError(NodeFault.InternalFault, t.getMessage()));
        }
    }
    
    /**
     * HTTP PUT
     */
    @Put("xml")
    public Representation store()
    {   
        log.debug("Enter NodeResource.store()");
        if (nodeError != null)
        {
            return createNodeFaultRepresentation(nodeError);
        }
        
        try
        {
            Node storedNode = getNodePersistence().putInContainer(node, node.getParent());
            
            // return the node in xml format
            NodeWriter nodeWriter = new NodeWriter();
            NodeOutputRepresentation nodeOutputRepresentation =
                new NodeOutputRepresentation(storedNode, nodeWriter);
            setStatus(Status.SUCCESS_CREATED);
            return nodeOutputRepresentation;
        }
        catch (NodeAlreadyExistsException e)
        {
            log.debug("Node already exists: " + path, e);
            return createNodeFaultRepresentation(new NodeError(NodeFault.DuplicateNode, vosURI.toString()));
        }
        catch (NodeNotFoundException e)
        {
            log.debug("Could not resolve part of path for node: " + path, e);
            return createNodeFaultRepresentation(new NodeError(NodeFault.ContainerNotFound));
        }
        catch (Throwable t)
        {
            log.debug(t);
            return createNodeFaultRepresentation(new NodeError(NodeFault.InternalFault, t.getMessage()));
        }
    }
    
    /**
     * HTTP POST
     */
    @Post("xml")
    public Representation accept()
    {
        log.debug("Enter NodeResource.accept()");
        if (nodeError != null)
        {
            return createNodeFaultRepresentation(nodeError);
        }
        
        try
        {
            // filter out any non-modifiable properties
            filterPropertiesForUpdate(node);
            
            Node updatedNode = getNodePersistence().updateProperties(node);
            
            // return the node in xml format
            NodeWriter nodeWriter = new NodeWriter();
            return new NodeOutputRepresentation(updatedNode, nodeWriter);
        }
        catch (NodeNotFoundException e)
        {
            log.debug("Could not resolve part of path for node: " + path);
            return createNodeFaultRepresentation(new NodeError(NodeFault.NodeNotFound, vosURI.toString()));
        }
        catch (Throwable t)
        {
            log.debug(t);
            return createNodeFaultRepresentation(new NodeError(NodeFault.InternalFault, t.getMessage()));
        }
    }
    
    /**
     * HTTP DELETE
     */
    @Delete
    public Representation remove()
    {
        log.debug("Enter NodeResource.remove()");
        if (nodeError != null)
        {
            return createNodeFaultRepresentation(nodeError);
        }
        
        try
        {
            getNodePersistence().delete(node, true);
            return null;
        }
        catch (NodeNotFoundException e)
        {
            log.debug("Could not find node with path: " + path, e);
            return createNodeFaultRepresentation(new NodeError(NodeFault.NodeNotFound, vosURI.toString()));
        }
        catch (Throwable t)
        {
            log.debug(t);
            return createNodeFaultRepresentation(new NodeError(NodeFault.InternalFault, t.getMessage()));
        }
    }
    
    /**
     * Using the restlet request object, get all the basic authentication and
     * certification authentication principals.
     * @param request The restlet request object.
     * @return A set of principals found in the restlet request.
     */
    @SuppressWarnings("unchecked")
    private Set<Principal> getPrincipals(Request request) {
        
        Set<Principal> principals = new HashSet<Principal>();
        
        // BM: Removed: Basic authentication credentials no longer
        // collected
        //
        // look for basic authentication
        // if (request.getChallengeResponse() != null &&
        //    StringUtil.hasLength(request.getChallengeResponse().getIdentifier()))
        // {
        //     principals.add(new HttpPrincipal(request.getChallengeResponse().getIdentifier()));
        // }
        
        // look for X509 certificates
        Map<String, Object> requestAttributes = request.getAttributes();
        if (requestAttributes.containsKey(CERTIFICATE_REQUEST_ATTRIBUTE_NAME))
        {
            final Collection<X509Certificate> clientCertificates =
                (Collection<X509Certificate>)requestAttributes.get(CERTIFICATE_REQUEST_ATTRIBUTE_NAME);
            
            if ((clientCertificates != null) && (!clientCertificates.isEmpty()))
            {
                for (final X509Certificate cert : clientCertificates)
                {
                    principals.add(cert.getSubjectX500Principal());
                }
            }
        }
        
        return principals;
        
    }
    
    /**
     * Get the first distinguished name found from the set of principals.
     * @param principals
     * @return
     */
    private String getOwner(Set<Principal> principals)
    {
        for (Principal principal : principals)
        {
            return principal.getName();
        }
        return "";
    }
    
    /**
     * Set the status according to the fault and create an output representation
     * of the fault.
     * @param nodeError
     * @return
     */
    private NodeErrorRepresentation createNodeFaultRepresentation(NodeError nodeError)
    {
        setStatus(nodeError.getNodeFault().getStatus());
        return new NodeErrorRepresentation(nodeError.getNodeFault(), nodeError.getMessage());
    }
    
    /**
     * Remove any properties from the Node that cannot be updated.
     * @param node
     */
    private void filterPropertiesForUpdate(Node node)
    {
        if (node.getProperties().contains(VOS.PROPERTY_URI_DATE))
        {
            node.getProperties().remove(new NodeProperty(VOS.PROPERTY_URI_DATE, null));
        }
    }
    
}
