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
import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Principal;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;

import org.restlet.Request;
import org.restlet.data.Status;
import org.restlet.representation.Representation;

import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeAlreadyExistsException;
import ca.nrc.cadc.vos.NodeFault;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodeParsingException;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.server.NodePersistence;
import ca.nrc.cadc.vos.server.auth.VOSpaceAuthorizer;
import ca.nrc.cadc.vos.server.web.representation.NodeInputRepresentation;
import ca.nrc.cadc.vos.server.web.representation.NodeOutputRepresentation;

/**
 * Class to perform the creation of a Node.
 * 
 * @author majorb
 */
public class CreateNodeAction extends NodeAction
{
    
    /**
     * Given the node URI and XML, return the Node object specified
     * by the client.
     */
    @Override
    public Node getClientNode(VOSURI vosURI, Representation nodeXML)
            throws URISyntaxException, NodeParsingException, IOException 
    {
        NodeInputRepresentation nodeInputRepresentation =
            new NodeInputRepresentation(nodeXML, vosURI.getPath());
        return nodeInputRepresentation.getNode();
    }
    
    /**
     * Perform an authorization check for the given node and return (if applicable)
     * the persistent version of the Node.
     */
    @Override
    public Node doAuthorizationCheck(VOSpaceAuthorizer voSpaceAuthorizer, Node clientNode)
            throws AccessControlException, FileNotFoundException
    {
        ContainerNode parent = (ContainerNode) voSpaceAuthorizer.getWritePermission(clientNode.getParent());
        clientNode.setParent(parent);
        clientNode.setOwner(getOwnerDistinguishedName());
        return clientNode;
    }

    /**
     * Perform the Node creation.
     */
    @Override
    public NodeActionResult performNodeAction(Node node, NodePersistence nodePersistence, Request request) throws Exception
    {
        try
        {
            Node storedNode = nodePersistence.putInContainer(
                    node, node.getParent());
            storedNode.setUri(node.getUri());
            
            // return the node in xml format
            NodeWriter nodeWriter = new NodeWriter();
            NodeOutputRepresentation nodeOutputRepresentation =
                new NodeOutputRepresentation(storedNode, nodeWriter);
            return new NodeActionResult(nodeOutputRepresentation, Status.SUCCESS_CREATED);
        }
        catch (NodeAlreadyExistsException e)
        {
            log.debug("Node already exists: " + node.getPath(), e);
            NodeFault nodeFault = NodeFault.DuplicateNode;
            nodeFault.setMessage(node.getUri().toString());
            return new NodeActionResult(nodeFault);
        }
        catch (NodeNotFoundException e)
        {
            log.debug("Could not resolve part of path for node: " + node.getPath(), e);
            return new NodeActionResult(NodeFault.ContainerNotFound);
        }
    }
    
    /**
     * @return The distinguished name of the owner for the new node.
     */
    private String getOwnerDistinguishedName()
    {
        AccessControlContext acContext = AccessController.getContext();
        Subject subject = Subject.getSubject(acContext);
        
        if (subject != null) {
            Set<Principal> principals = subject.getPrincipals();
            for (Principal principal : principals)
            {
                if (principal instanceof X500Principal)
                {
                    return AuthenticationUtil.canonizeDistinguishedName(
                            principal.getName());
                }
            }
        }
        return "";
    }

}
