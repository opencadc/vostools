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

package ca.nrc.cadc.vos.dao;

import java.util.ArrayList;
import java.util.List;

import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;

/**
 * Abstact DAO wrapper class of a Node object.
 * 
 * @author majorb
 *
 */
public abstract class DAONode
{
    
    // The id of the node in the database
    protected long nodeID;
    
    // The id of the parent
    protected long parentId;
    
    /**
     * Node Constructor
     * 
     * @param nodeID The ID of the node in the database.
     */
    public DAONode(long nodeID)
    {
        this.nodeID = nodeID;
    }
    
    ///////////////////
    // Abstract methods
    ///////////////////
    
    /**
     * @return The database representation of this node type
     */
    public abstract char getDatabaseTypeRepresentation();
    
    /**
     * @return The node object
     */
    public abstract Node getNode();
    
    //////////////////
    // Utility methods
    //////////////////
    
    public String toString()
    {
        return "Node Type: " + getDatabaseTypeRepresentation() + " Node Path: " + getNode().getPath() + " Node ID: "+ nodeID;
    }
    
    /**
     * A DAONode is equal if the ids are equal.
     */
    public boolean equals(Object o)
    {
        if (o instanceof DAONode)
        {
            DAONode n = (DAONode) o;
            if (this.nodeID == 0 || n.nodeID == 0)
            {
                return false;
            }
            return this.nodeID == n.nodeID;
        }
        return false;
    }
    
    public List<DAONode> getHierarchy()
    {
        List<DAONode> list = new ArrayList<DAONode>();
        if (getNode().getParent() != null)
        {
            list.addAll(new NodeMapper().mapDomainNode(getNode().getParent()).getHierarchy());
        }
        list.add(this);
        return list;
    }
    
    ///////////////////////////////////////////////////
    // Methods that redirect to the real domain objects
    ///////////////////////////////////////////////////
    
    public void setName(String name)
    {
        getNode().setName(name);
    }
    
    public String getName()
    {
        return getNode().getName();
    }
    
    public void setParent(DAOContainerNode parent)
    {
        if (parent != null)
        {
            this.parentId = parent.getNodeID();
            getNode().setParent((ContainerNode) parent.getNode());
        }
        else
        {
            this.parentId = 0;
            getNode().setParent(null);
        }
    }
    
    public DAOContainerNode getParent()
    {
        return new DAOContainerNode(getNode().getParent(), parentId);
    }
    
    public void setProperties(List<NodeProperty> properties)
    {
        getNode().setProperties(properties);
    }
    
    public List<NodeProperty> getProperties()
    {
        return getNode().getProperties();
    }
    
    public String getPath()
    {
        return getNode().getPath();
    }
    
    public String getGroupRead()
    {
        return getNode().getGroupRead();
    }

    public void setGroupRead(String groupRead)
    {
        getNode().setGroupRead(groupRead);
    }

    public String getGroupWrite()
    {
        return getNode().getGroupWrite();
    }

    public void setGroupWrite(String groupWrite)
    {
        getNode().setGroupWrite(groupWrite);
    }
    
    public String getOwner()
    {
        return getNode().getOwner();
    }

    public void setOwner(String owner)
    {
        getNode().setOwner(owner);
    }
    
    public String getUri()
    {
        return getNode().getUri();
    }
     
    public void setUri(String uri)
    {
        getNode().setUri(uri);
    }
    
    //////////////////////
    // Getters and Setters
    //////////////////////
    
    public long getNodeID()
    {
        return nodeID;
    }

    public void setNodeID(long nodeId)
    {
        this.nodeID = nodeId;
    }

    long getParentId()
    {
        return parentId;
    }

    void setParentId(long parentId)
    {
        this.parentId = parentId;
    }

}
