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

import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeFault;
import ca.nrc.cadc.vos.NodeParsingException;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.VOS;
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
    
    @Override
    public Node getClientNode()
            throws URISyntaxException, NodeParsingException, IOException 
    {
        NodeInputRepresentation nodeInputRepresentation =
            new NodeInputRepresentation(nodeXML, vosURI.getPath());
        return nodeInputRepresentation.getNode();
    }

    @Override
    public Node doAuthorizationCheck()
        throws AccessControlException, FileNotFoundException, TransientException
    {
        // locks don't apply to property updates
        voSpaceAuthorizer.setDisregardLocks(true);
        Node node = (Node) voSpaceAuthorizer.getWritePermission(vosURI.getURIObject());
        return node;
    }

    @Override
    public NodeActionResult performNodeAction(Node clientNode, Node serverNode)
        throws TransientException
    {
        // TODO: check if client and server node types match?

        // check for a busy node
        if (serverNode instanceof DataNode)
        {
            if (((DataNode) serverNode).isBusy())
            {
                log.debug("Node is busy: " + serverNode.getUri().getPath());
                NodeFault nodeFault = NodeFault.InternalFault;
                nodeFault.setMessage("Node is busy: " + serverNode.getUri().toString());
                return new NodeActionResult(nodeFault);
            }
        }

        // filter out any non-modifiable properties
        filterPropertiesForUpdate(clientNode);

        Node out = nodePersistence.updateProperties(serverNode, clientNode.getProperties());
        
        // return the node in xml format
        NodeWriter nodeWriter = new NodeWriter();
        return new NodeActionResult(new NodeOutputRepresentation(out, nodeWriter));
    }
    
    /**
     * Remove any properties from the Node that cannot be updated.
     * @param node
     */
    private void filterPropertiesForUpdate(Node node)
    {
        for (String propertyURI : VOS.READ_ONLY_PROPERTIES)
        {
            int propertyIndex = node.getProperties().indexOf(new NodeProperty(propertyURI, ""));
            if (propertyIndex != -1)
            {
                node.getProperties().remove(propertyIndex);
            }
        }

    }
}
