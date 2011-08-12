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

package ca.nrc.cadc.vos.server;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.Arrays;
import java.util.List;

import javax.security.auth.Subject;
import javax.sql.DataSource;

import org.apache.log4j.Logger;

import ca.nrc.cadc.auth.IdentityManager;
import ca.nrc.cadc.auth.X500IdentityManager;
import ca.nrc.cadc.util.StringUtil;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.VOS.NodeBusyState;

/**
 * Simple implementation of the NodePersistence interface that uses the NodeDAO
 * class to do the work. This class is thread-safe simply be creating a new
 * NodeDAO for each method call. Subclasses are responsible for specifying the
 * names of the node and property tables and a providing a DataSource to use.
 * We assume that this will be a connection pool found via JNDI in a web service
 * environment.
 *
 * @author pdowler
 */
public abstract class DatabaseNodePersistence implements NodePersistence
{
    private static Logger log = Logger.getLogger(DatabaseNodePersistence.class);
    
    private static List<NodeProperty> permissionProperties = Arrays.asList(
        new NodeProperty[] {
            new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, null),
            new NodeProperty(VOS.PROPERTY_URI_GROUPREAD, null),
            new NodeProperty(VOS.PROPERTY_URI_GROUPWRITE, null)});

    protected NodeDAO.NodeSchema nodeSchema;

    public DatabaseNodePersistence() { }

    /**
     * Subclass must implement this to return the fully-qualified table name for the
     * nodes table.
     *
     * @see NodeDAO
     * @return
     */
    protected abstract String getNodeTableName();

    /**
     * Subclass must implement this to return the fully-qualified table name for the
     * node properties table.
     *
     * @see NodeDAO
     * @return
     */
    protected abstract String getPropertyTableName();

    /**
     * Subclasses must implement this to find or create a usable DataSource. This is
     * typically a connection pool found via JNDI, but can be any valid JDBC DataSource.
     * @return
     */
    protected abstract DataSource getDataSource();

    /**
     * Get the IdentityManager implementation to use to store/retrieve node owner.
     * 
     * @return
     */
    protected IdentityManager getIdentityManager()
    {
        return new X500IdentityManager();
    }

    /**
     * Since the NodeDAO is not thread safe, this method returns a new NodeDAO
     * for every call.
     * 
     * @param authority
     * @return a new NodeDAO
     */
    protected NodeDAO getDAO(String authority)
    {
        if (nodeSchema == null) // lazy
            this.nodeSchema = new NodeDAO.NodeSchema(getNodeTableName(),getPropertyTableName());

        return new NodeDAO(getDataSource(), nodeSchema, authority, getIdentityManager());
    }

    public Node get(VOSURI vos)
        throws NodeNotFoundException
    {
        NodeDAO dao = getDAO( vos.getAuthority() );
        Node ret = dao.getPath(vos.getPath());
        if (ret == null)
            throw new NodeNotFoundException("not found: " + vos.getURIObject().toASCIIString());
        return ret;
    }

    public void getProperties(Node node)
    {
        NodeDAO dao = getDAO( node.getUri().getAuthority() );
        dao.getProperties(node);
    }

    public void getChildren(ContainerNode node)
    {
        NodeDAO dao = getDAO( node.getUri().getAuthority() );
        dao.getChildren(node);
    }
    
    public void getChild(ContainerNode node, String name)
    {
        NodeDAO dao = getDAO( node.getUri().getAuthority() );
        dao.getChild(node, name);
    }

    public Node put(Node node)
    {
        AccessControlContext acContext = AccessController.getContext();
        Subject creator = Subject.getSubject(acContext);
        NodeDAO dao = getDAO( node.getUri().getAuthority() );
        
        // inherit the permissions of the parent if a new node
        if (node.appData == null ||
           ((NodeID) node.appData).getID() == null)
        {
            inheritParentPermissions(node);
        }
        return dao.put(node, creator);
    }

    public Node updateProperties(Node node, List<NodeProperty> properties)
    {
        NodeDAO dao = getDAO( node.getUri().getAuthority() );
        return dao.updateProperties(node, properties);
    }
    
    /**
     * Delete the node. For DataNodes, this <b>does not</b> do anything to delete
     * the stored file from any byte-storage location; it only removes the metadata
     * from the database. Delete recursively calls updateContentLength for the 
     * parent, if necesasry.
     *
     * @see NodeDAO.delete(Node)
     * @see NodeDAO.markForDeletion(Node)
     * @param node the node to delete
     */
    public void delete(Node node)
    {
        log.debug("delete: " + node.getUri());
        NodeDAO dao = getDAO( node.getUri().getAuthority() );
        //dao.getProperties(node); // not needed to get content-length

        long contentLength = getContentLength(node);
        log.debug("delete: contentLength = " + contentLength);
        //dao.delete(node);
        dao.markForDeletion(node);
        if (contentLength > 0)
        {
            long delta = -1*contentLength;
            ContainerNode parent = node.getParent();
            while (parent != null)
            {
                log.debug("calling updateContentLength: " + parent.getUri() + "," + delta);
                dao.updateContentLength(parent, delta);
                parent = parent.getParent();
            }
        }
    }

    public void copy(Node node, String copyToPath)
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void move(Node node, String newPath)
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setBusyState(DataNode node, NodeBusyState state)
    {
        NodeDAO dao = getDAO( node.getUri().getAuthority() );
        dao.setBusyState(node, state);
    }

    public void updateContentLength(ContainerNode node, long delta)
    {
        NodeDAO dao = getDAO( node.getUri().getAuthority() );
        dao.updateContentLength(node, delta);
    }

    /**
     * Get the current contentLength. If the property is not set, 0 is returned.
     * 
     * @param node
     * @return content length, or 0 if not set
     */
    protected long getContentLength(Node node)
    {
        String str = node.getPropertyValue(VOS.PROPERTY_URI_CONTENTLENGTH);
        if (str != null)
            return Long.parseLong(str);
        return 0;
    }
    
    /**
     * Inherit the permissions of the parent if:
     * - the parent is not null
     * - none have been explicity set in the node
     * @param node
     */
    protected void inheritParentPermissions(Node node)
    {
        Node parent = node.getParent();
        if (parent == null)
        {
            return;
        }
        
        List<NodeProperty> nodeProperties = node.getProperties();
        List<NodeProperty> parentProperties = parent.getProperties();
        NodeProperty property = null;
        int nodePropertyIndex;
        int parentPropertyIndex;
        
        for (NodeProperty permissionProperty : permissionProperties)
        {
            nodePropertyIndex = nodeProperties.indexOf(permissionProperty);
            parentPropertyIndex = parentProperties.indexOf(permissionProperty);
            
            if (parentPropertyIndex >= 0)
            {
                if (nodePropertyIndex >= 0)
                {
                    property = nodeProperties.get(nodePropertyIndex);
                    if (propertyExplicitlySet(property))
                    {
                        log.debug("Keeping permission property: " + property);
                        continue;
                    }
                    nodeProperties.remove(nodePropertyIndex);
                }
                property = parentProperties.get(parentPropertyIndex);
                log.debug("Inheriting permission property: " + property);
                nodeProperties.add(property);
            }
        }
    }
    
    /**
     * Return true if this property has been set on the node.  This is
     * true if the property has a value or if it has been marked for
     * deletion.
     * @param property
     * @return
     */
    private boolean propertyExplicitlySet(NodeProperty property)
    {
        return StringUtil.hasText(property.getPropertyValue())
                || property.isMarkedForDeletion();
    }
}
