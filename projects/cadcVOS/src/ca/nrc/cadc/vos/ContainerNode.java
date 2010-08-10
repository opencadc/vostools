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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * A VOSpace node that describes the a data item that contains other data
 * items.  A ContainerNode is similar to a directory.
 * 
 * @author majorb
 *
 */
public class ContainerNode extends Node
{
    
    private static Logger log = Logger.getLogger(ContainerNode.class);
    
    public static final char DB_TYPE = 'C';
    
    // The list of contained nodes.
    private List<Node> nodes;
    
    /**
     * ContainerNode constructor.
     */
    public ContainerNode()
    {
        super();
        nodes = new ArrayList<Node>();
    }
    
    /**
     * ContainerNode constructor.
     * @param name
     */
    public ContainerNode(String name)
    {
        super(name);
        nodes = new ArrayList<Node>();
    }
    
    /**
     * ContainerNode constructor.
     * @throws URISyntaxException 
     */
    public ContainerNode(VOSURI uri) throws URISyntaxException
    {
        super(uri);
        nodes = new ArrayList<Node>();
    }
    
    /**
     * ContainerNode constructor.
     * @throws URISyntaxException 
     */
    public ContainerNode(VOSURI uri, List<NodeProperty> properties) throws URISyntaxException
    {
        super(uri, properties);
        nodes = new ArrayList<Node>();
    }
    
    /**
     * A container node is structured if all its
     * nodes within are structured.
     * 
     * @return True if the node is considered structured.
     */
    public boolean isStructured()
    {
        boolean structured = true;
        Iterator<Node> i = nodes.iterator();
        while (i.hasNext() && structured == true)
        {
            if (!i.next().isStructured())
            {
                structured = false;
            }
        }
        return structured;
    }
    
    public boolean heirarchyEquals(ContainerNode containerNode)
    {
        if (containerNode == null)
        {
            return false;
        }
        if (this.name.equals(containerNode.getName()))
        {
            if (this.parent == null)
            {
                if (containerNode.parent == null)
                {
                    return true;
                }
                else
                {
                    return false;
                }
            }
            else
            {
                if (containerNode.parent == null)
                {
                    return false;
                }
                else
                {
                    return this.parent.heirarchyEquals(containerNode.parent);
                }
            }
        }
        return false;
    }

    /**
     * @return A list of nodes contained by this node.
     */
    public List<Node> getNodes()
    {
        return nodes;
    }

    /**
     * Sets the list of contained nodes.
     * 
     * @param nodes The nodes to contain.
     */
    public void setNodes(List<Node> nodes)
    {
        this.nodes = nodes;
    }
    
    /**
     * @return A list of views which the node can use for importing.
     */
    public List<URI> accepts()
    {
        List<URI> accepts = new ArrayList<URI>(1);
        try
        {
            accepts.add(new URI(VOS.VIEW_DEFAULT));
        } catch (URISyntaxException e)
        {
            log.error(e);
        }
        return accepts;
    }
    
    /**
     * @return A list of views which the node can use for exporting.
     */
    public List<URI> provides()
    {
        List<URI> provides = new ArrayList<URI>(2);
        try
        {
            provides.add(new URI(VOS.VIEW_DEFAULT));
            provides.add(new URI("ivo://cadc.nrc.ca/vospace/core#rssview"));
        } catch (URISyntaxException e)
        {
            log.error(e);
        }
        return provides;
    }
    
    /**
     * @return The database respresentation of this node type
     */
    public char getDatabaseTypeRepresentation()
    {
        return DB_TYPE;
    }

    @Override
    public String toString()
    {
        return "ContainerNode [nodes=" + nodes + ", appData=" + appData + ", markedForDeletion=" + markedForDeletion + ", name="
                + name + ", owner=" + owner + ", parent=" + parent + ", path=" + path + ", properties=" + properties + ", uri="
                + uri + "]";
    }

}
