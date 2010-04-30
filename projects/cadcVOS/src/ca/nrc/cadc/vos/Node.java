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

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract class defining a data object within a VOSpace.
 *  
 * @see ca.nrc.cadc.vos.DataNode
 * @see ca.nrc.cadc.vos.ContainerNode
 * 
 * @author majorb
 *
 */
public abstract class Node
{
    
    // The path (including the name) of the node
    protected String path;
    
    // The name of the node
    protected String name;
    
    // The parent of the node
    protected ContainerNode parent;
    
    // The list of node properties
    protected List<NodeProperty> properties;
    
    // The group allowed to read
    protected String groupRead;
    
    // The group allowed to write
    protected String groupWrite;
    
    // The node owner
    protected String owner;
    
    public Node()
    {
        this.path = "";
        properties = new ArrayList<NodeProperty>();
    }
    
    /**
     * Node constructor.
     * 
     * @param path The path of the node;
     */
    public Node(String path)
    {
        this.path = path;
        buildParent(path);
        properties = new ArrayList<NodeProperty>();
    }
    
    /**
     * Node constructor.
     * 
     * @param path The path of the node
     * @param properties The node's properties
     */
    public Node(String path, List<NodeProperty> properties)
    {
        this.path = path;
        buildParent(path);
        this.properties = properties;
    }
    
    /**
     * Given the path, build the parent if one exists.
     * Set the name of the node.
     * @param path
     */
    private void buildParent(String path)
    {
        if (path == null || path.trim().length() == 0)
        {
            throw new IllegalArgumentException("Node path not provided");
        }
        
        String refinedPath = path;
        if (refinedPath.startsWith("/"))
        {
            refinedPath = refinedPath.substring(1);
        }
        if (refinedPath.endsWith("/"))
        {
            refinedPath = refinedPath.substring(0, refinedPath.length() - 1);
        }
        String[] segments = refinedPath.split("/");
        
        if (segments == null || segments.length == 0)
        {
            throw new IllegalArgumentException("Node path invalid.");
        }
        
        this.name = segments[segments.length - 1];
        
        if (segments.length == 1)
        {
            parent = null;
        }
        else
        {
            parent = new ContainerNode(refinedPath.substring(0, refinedPath.lastIndexOf("/")));
        }
    }
    
    public String toString()
    {
        return " Node Path: " + path;
    }
    
    public boolean equals(Object o)
    {
        if (o instanceof Node)
        {
            Node n = (Node) o;
            if (this.name.equals(n.name))
            {
                if (this.parent == null)
                {
                    return (n.parent == null);
                }
                else
                {
                    if (n.parent == null)
                    {
                        return false;
                    }
                    else
                    {
                        return this.parent.equals(n.parent);
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * @return true if the VOSpace understands the format of the data.
     */
    public abstract boolean isStructured();
    
    /**
     * @return A list of views which the node can use for importing.
     */
    public abstract List<View> accepts();
    
    /**
     * @return A list of views which the node can use for exporting.
     */
    public abstract List<View> provides();
    
    public String getPath()
    {
        return path;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        if (name == null || !name.equals(this.name))
        {
            // reset the path
            this.path = "";
        }
        this.name = name;
    }

    public ContainerNode getParent()
    {
        return parent;
    }

    public void setParent(ContainerNode parent)
    {
        this.parent = parent;
    }

    public List<NodeProperty> getProperties()
    {
        return properties;
    }

    public void setProperties(List<NodeProperty> properties)
    {
        this.properties = properties;
    }

    public String getGroupRead()
    {
        return groupRead;
    }

    public void setGroupRead(String groupRead)
    {
        this.groupRead = groupRead;
    }

    public String getGroupWrite()
    {
        return groupWrite;
    }

    public void setGroupWrite(String groupWrite)
    {
        this.groupWrite = groupWrite;
    }

    public String getOwner()
    {
        return owner;
    }

    public void setOwner(String owner)
    {
        this.owner = owner;
    }

}
