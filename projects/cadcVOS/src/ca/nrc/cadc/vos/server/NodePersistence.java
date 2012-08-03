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
*                                       Node
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

package ca.nrc.cadc.vos.server;

import ca.nrc.cadc.util.FileMetadata;
import java.util.List;

import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodeNotSupportedException;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.VOS.NodeBusyState;

/**
 * An interface defining the methods available for working with VOSpace
 * nodes in the persistent layer.
 * 
 * @author majorb
 */
public interface NodePersistence
{

    /**
     * Find the node with the specified path. The returned node(s) will include
     * some properties (typically inherently single-valued properties like owner,
     * content-length, content-type, content-encoding, content-MD5) plus all
     * properties needed to make authorization checks (isPublic, group-read, and
     * group-write). Remaining properties and child nodes can be filled in as
     * needed with getProperties(Node) and getChildren(ContainerNode).
     *
     * @param vos a node identifier
     * @return the specified node
     * @throws NodeNotFoundException
     */
    Node get(VOSURI vos)
        throws NodeNotFoundException;

    /**
     * Load all the children of a container.
     *
     * @param node
     */
    void getChildren(ContainerNode node);

    /**
     * Load some of the children of a container. If <code>uri</code> is null, a
     * server-selected fitrst node is used. If <code>limit</code> is null or
     * exceeds an arbitrary internal value, the internal value is used.
     * 
     * @param parent
     * @param start
     * @param limit
     */
    void getChildren(ContainerNode parent, VOSURI start, Integer limit);

    /**
     * Load a single child of a container.
     * 
     * @param parent
     * @param name
     */
    void getChild(ContainerNode parent, String name);

    /**
     * Load all the properties of a node.
     * 
     * @param node
     */
    void getProperties(Node node);

    /**
     * Store the specified node.
     *
     * @param node
     * @return the persisted node
     * @throws NodeNotSupportedException
     */
    public Node put(Node node) throws NodeNotSupportedException;

    /**
     * Update the properties of the specified node.  The node must have
     * been retrieved from the persistent layer. Properties in the list are
     * merged with existing properties following the semantics specified in
     * the VOSpace 2.0 specification.
     * 
     * @param node
     * @param properties
     * @return the modified node
     */
    Node updateProperties(Node node, List<NodeProperty> properties);

    /**
     * Delete the specified node.
     * 
     * @param node
     */
    void delete(Node node);
   
    /**
     * Update the node metadata after a transfer (put) is complete.
     *
     * @param node
     * @param meta metadata from the successful put
     */
    void setFileMetadata(DataNode node, FileMetadata meta);

    /**
     * Set the busy state of the node from curState to newState.
     * 
     * @param node The node on which to alter the busy state.
     * @param state The new state for the node.
     * @return the new state or null if the transition failed
     * @throws NodeNotFoundException If the node could not be found.
     */
    void setBusyState(DataNode node, NodeBusyState curState, NodeBusyState newState);
    
    /**
     * Move the specified node to the new path.  The node must have been retrieved
     * from the persistent layer.
     * 
     * @param src The node to move.
     * @param destination The destination container.
     * @param name The name of the destination node.
     */
    void move(Node src, ContainerNode destination);
    
    /**
     * Copy the specified node to the specified path.  The node must been retrieved
     * from the persistent layer.
     * 
     * @param src The node to move.
     * @param destination The destination container.
     * @param name The name of the destination node.
     */
    void copy(Node src, ContainerNode destination);
    
}
