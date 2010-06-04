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

package ca.nrc.cadc.vos;

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
     * Find the node with the specified name and parent.  The parent must have been
     * retrieved from the persistent layer.  A parent of 'null' indicates that
     * the node to be returned is a root node.
     * 
     * @param name The name of the node
     * @param parent The persistent parent object, or null if a root node.
     * @return The persistent object specified by name.
     * @throws NodeNotFoundException If the node could not be found.
     */
    Node getFromParent(String name, ContainerNode parent) throws NodeNotFoundException;
    
    /**
     * Persist the node in the given container.  The container must have been retrieved
     * from the persistent layer.
     * 
     * @param node The node to persist
     * @param parent The persistent parent node of 'node'
     * @return The persistent version of the node.
     * @throws NodeNotFoundException If the parent node could not be found.
     * @throws NodeAlreadyExistsException If a node with the same name already exists
     * in the parent container.
     */
    Node putInContainer(Node node, ContainerNode parent) throws NodeNotFoundException, NodeAlreadyExistsException;
    
    /**
     * Delete the node.  This node must have been retrieved previously from the
     * persistent layer.
     * 
     * @param node The node to delete.
     * @param deleteChildren If true, delete any children of this node.
     * @throws NodeNotFoundException If the node could not be found.
     */
    void delete(Node node, boolean deleteChildren) throws NodeNotFoundException;
    
    /**
     * Mark the node for deletion.  This node must have been retrieved previously
     * from the persistent layer.
     * 
     * @param node The node to mark as deleted.
     * @param markChildren If true, mark any children as deleted as well.
     * @throws NodeNotFoundException If the node could not be found.
     */
    void markForDeletion(Node node, boolean markChildren) throws NodeNotFoundException;
    
    /**
     * Update the properties of the specified node.  The node must have been retrieved
     * from the persistent layer.
     * 
     * @param node The node containing the properties to update.
     * @return The persistent version of the updated node and properties.
     * @throws NodeNotFoundException If the node could not be found.
     */
    Node updateProperties(Node node) throws NodeNotFoundException;
    
    /**
     * Set the busy state of the node.
     * 
     * @param node The node on which to alter the busy state.
     * @param state The new state for the node.
     * @throws NodeNotFoundException If the node could not be found.
     */
    void setBusyState(Node node, NodeBusyState state) throws NodeNotFoundException;
    
    /**
     * Move the specified node to the new path.  The node must have been retrieved
     * from the persistent layer.
     * 
     * @param node
     * @param newPath
     */
    void move(Node node, String newPath);
    
    /**
     * Copy the specified node to the specified path.  The node must been retrieved
     * from the persistent layer.
     * 
     * @param node
     * @param copyToPath
     */
    void copy(Node node, String copyToPath);

}
