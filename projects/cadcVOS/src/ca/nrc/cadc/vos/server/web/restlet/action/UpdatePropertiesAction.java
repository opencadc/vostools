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
import org.restlet.representation.Representation;

import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeFault;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodeParsingException;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.server.NodePersistence;
import ca.nrc.cadc.vos.server.auth.VOSpaceAuthorizer;
import ca.nrc.cadc.vos.server.web.representation.NodeInputRepresentation;
import ca.nrc.cadc.vos.server.web.representation.NodeOutputRepresentation;

/**
 * Class to perform the updating of a Node's properties.
 * 
 * @author majorb
 */
public class UpdatePropertiesAction extends NodeAction
{
    
    private static Logger log = Logger.getLogger(UpdatePropertiesAction.class);
    
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
        Node node = (Node) voSpaceAuthorizer.getWritePermission(clientNode);
        node.setProperties(clientNode.getProperties());
        return node;
    }

    /**
     * Perform the updating of the Node's properties.
     */
    @Override
    public NodeActionResult performNodeAction(Node node, NodePersistence nodePersistence, Request request) throws Exception
    {
        
        // check for a busy node
        if (node instanceof DataNode)
        {
            if (((DataNode) node).isBusy())
            {
                log.debug("Node is busy: " + node.getPath());
                NodeFault nodeFault = NodeFault.NodeBusy;
                nodeFault.setMessage(node.getUri().toString());
                return new NodeActionResult(nodeFault);
            }
        }
        
        try
        {
            // filter out any non-modifiable properties
            filterPropertiesForUpdate(node);
            
            Node updatedNode = nodePersistence.updateProperties(node);
            setNodeURI(updatedNode, node.getUri());
            
            // return the node in xml format
            NodeWriter nodeWriter = new NodeWriter();
            return new NodeActionResult(new NodeOutputRepresentation(updatedNode, nodeWriter));
        }
        catch (NodeNotFoundException e)
        {
            log.debug("Could not resolve part of path for node: " + node.getPath());
            NodeFault nodeFault = NodeFault.NodeNotFound;
            nodeFault.setMessage(node.getUri().toString());
            return new NodeActionResult(nodeFault);
        }
    }
    

    
    /**
     * Remove any properties from the Node that cannot be updated.
     * @param node
     */
    private void filterPropertiesForUpdate(Node node)
    {
        for (String propertyURI : VOS.READ_ONLY_PROPERTIES)
        {
            int propertyIndex = node.getProperties().indexOf(new NodeProperty(propertyURI, null));
            if (propertyIndex != -1)
            {
                node.getProperties().remove(propertyIndex);
            }
        }

    }

}
