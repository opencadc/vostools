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
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’esties(serverNode);

            // return the node in xml format
            NodeWriter nodeWriter = new NodeWriter();
            return new NodeActionResult(new N
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 4 $
*
************************************************************************
*/

package ca.nrc.cadc.vos;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * Abstract class defining an object within VOSpace.
 *  
 * @see ca.nrc.cadc.vos.DataNode
 * @see ca.nrc.cadc.vos.ContainerNode
 * 
 * @author majorb
 *
 */
public abstract class Node implements Comparable<Object>
{
    private static Logger log = Logger.getLogger(Node.class);

    // The node uri
    protected VOSURI uri;

    // The name of the node
    protected String name;

    // The parent of the node
    protected ContainerNode parent;

    // The list of node properties
    protected List<NodeProperty> properties;

    // Flag indicating if the node is public
    protected boolean isPublic;
    
    // Flag indicating if the node is locked
    protected boolean isLocked;

    // True if marked for deletion
    //protected boolean markedForDeletion;

    // To be used by controlling applications as they wish
    public transient Object appData;
    
    // List of views which this node accepts
    protected List<URI> accepts;
    
    // List of views which this node provides
    protected List<URI> provides;
    

    private Node() { }

    /**
     * @param uri
     */
    public Node(VOSURI uri)
    {
        this(uri, new ArrayList<NodeProperty>());
    }

    /**
     * @param uri The uri of the node
     * @param properties The node's properties
     */
    public Node(VOSURI uri, List<NodeProperty> properties)
    {
        this.uri = uri;
        this.properties = properties;

        this.name = uri.getName();
        log.debug("uri: " + uri + " -> name: " + name);
        accepts = new ArrayList<URI>();
        provides = new ArrayList<URI>();
    }

    @Override
    public String toString()
    {
        String parentStr = null;
        if (parent != null)
        {
            parentStr = parent.uri.toString() + " " + parent.appData;
        }

        return this.getClass().getSimpleName() 
                + " [appData=" + appData
                //+ ", markedForDeletion=" + markedForDeletion
                + ", uri=" + uri.toString()
                + ", parent=" + parentStr
                + ", properties=" + properties + "]";
    }
    
    /**
     * Return an integer denoting the display order for two nodes.
     */
    public int compareTo(Object o1)
    {
        if (o1 == null)
        {
            return -1;
        }
        if (! (o1 instanceof Node))
            throw new ClassCastException("compareTo requires a Node, got: " + o1.getClass().getName());
        Node rhs = (Node) o1;
        String s1 = this.getUri().getURIObject().toASCIIString();
        String s2 = rhs.getUri().getURIObject().toASCIIString();

        return s1.compareTo(s2);
    }

    /**
     * Nodes are considered equal if their URIs are equal.
     */
    @Override
    public boolean equals(Object o)
    {
        return this.compareTo(o) == 0;
    }

    /**
     * @return true if the VOSpace understands the format of the data.
     */
    public abstract boolean isStructured();

    /**
     * @return A list of view uris which the node can use for importing.
     */
    public List<URI> accepts()
    {
        return accepts;
    }

    /**
     * @return A list of view uris which the node can use for exporting.
     */
    public List<URI> provides()
    {
        return provides;
    }
    
    /**
     * Set the accepts list.
     * @param accepts
     */
    public void setAccepts(List<URI> accepts)
    {
        this.accepts = accepts;
    }
    
    /**
     * Set the provides list.
     * @param provides
     */
    public void setProvides(List<URI> provides)
    {
        this.provides = provides;
    }

    public VOSURI getUri()
    {
        return uri;
    }

    /**
     * Convenience method to get the relative name of this node.
     * @return
     */
    public String getName()
    {
        return name;
    }
    
    public void setName(String name)
    {
        this.name = name;
    }

    public ContainerNode getParent()
    {
        return parent;
    }

    public void setParent(ContainerNode parent)
    {
        this.parent = parent;
        // TODO: should verify that this.uri is a single path component
        // extension of parent.uri
    }

    public List<NodeProperty> getProperties()
    {
        return properties;
    }

    public void setProperties(List<NodeProperty> properties)
    {
        this.properties = properties;
    }

    /*
    public boolean isMarkedForDeletion()
    {
        return markedForDeletion;
    }

    public void setMarkedForDeletion(boolean markedForDeletion)
    {
        this.markedForDeletion = markedForDeletion;
    }
    */
    
    /**
     * Convenience method. This just calls
     * <pre>
     * Node.getPropertyValue(VOS.PROPERTY_URI_ISPUBLIC)
     * </pre>
     *
     * @return true if the property is set to true, otherwise false
     */
    public boolean isPublic()
    {
        String val = getPropertyValue(VOS.PROPERTY_URI_ISPUBLIC);
        return "true".equals(val);
    }
    
    /**
     * Convenience method. This just calls
     * <pre>
     * Node.getPropertyValue(VOS.PROPERTY_URI_ISLOCKED)
     * </pre>
     *
     * @return true if the property is set to true, otherwise false
     */
    public boolean isLocked()
    {
        String val = getPropertyValue(VOS.PROPERTY_URI_ISLOCKED);
        return "true".equals(val);
    }

    /**
     * Find a property by its key (propertyUri).
     * TODO: this should return a List<NodeProperty>
     * 
     * @param propertyURI
     * @return the node property object or null if not found
     */
    public NodeProperty findProperty(String propertyURI)
    {
        for (NodeProperty prop : this.properties)
        {
            if (prop.getPropertyURI().equalsIgnoreCase(propertyURI))
                return prop;
        }
        return null;
    }
    /**
     * Return the value of the specified property.
     *
     * @param propertyURI
     * @return the value or null if not found
     */
    public String getPropertyValue(String propertyURI)
    {
        NodeProperty prop = findProperty(propertyURI);
        if (prop != null)
            return prop.getPropertyValue();
        return null;
    }

    /**
     * Get a linked list of nodes from leaf to root.
     *
     * @return list of nodes, with leaf first and root last
     */
    public static LinkedList<Node> getNodeList(Node leaf)
    {
        LinkedList<Node> nodes = new LinkedList<Node>();
        Node cur = leaf;
        while (cur != null)
        {
            nodes.add(cur);
            cur = cur.getParent();
        }
        return nodes;
    }
}
