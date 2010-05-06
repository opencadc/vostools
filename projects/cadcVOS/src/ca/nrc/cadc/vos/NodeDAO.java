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

import java.security.AccessControlException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.List;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import ca.nrc.cadc.vos.dao.DAOContainerNode;
import ca.nrc.cadc.vos.dao.DAONode;
import ca.nrc.cadc.vos.dao.NodeMapper;
import ca.nrc.cadc.vos.dao.NodePropertyMapper;

/**
 * Default implementation of the NodePersistence interface.
 * 
 * @author majorb
 *
 */
public abstract class NodeDAO implements NodePersistence
{
    
    private static Logger log = Logger.getLogger(NodeDAO.class);

    // Database connection.
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactionManager;
    private DefaultTransactionDefinition defaultTransactionDef;
    private TransactionStatus transactionStatus;

    /**
     * NodeDAO Constructor.
     */
    public NodeDAO()
    {
    }
    
    public void init()
    {
        this.defaultTransactionDef = new DefaultTransactionDefinition();
        defaultTransactionDef
                .setIsolationLevel(DefaultTransactionDefinition.ISOLATION_READ_COMMITTED);
        DataSource dataSource = getDataSource();
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactionManager = new DataSourceTransactionManager(dataSource);
    }

    /**
     * Start a transaction to the data source.
     */
    protected void startTransaction()
    {
        if (transactionStatus != null)
            throw new IllegalStateException("transaction already in progress");
        log.debug("startTransaction");
        this.transactionStatus = transactionManager
                .getTransaction(defaultTransactionDef);
        log.debug("startTransaction: OK");
    }

    /**
     * Commit the transaction to the data source.
     */
    protected void commitTransaction()
    {
        if (transactionStatus == null)
            throw new IllegalStateException("no transaction in progress");
        log.debug("commitTransaction");
        transactionManager.commit(transactionStatus);
        this.transactionStatus = null;
        log.debug("commit: OK");
    }

    /**
     * Rollback the transaction to the data source.
     */
    protected void rollbackTransaction()
    {
        if (transactionStatus == null)
            throw new IllegalStateException("no transaction in progress");
        log.debug("rollbackTransaction");
        transactionManager.rollback(transactionStatus);
        this.transactionStatus = null;
        log.debug("rollback: OK");
    }

    /**
     * Get the node specified by argument 'node' from the database.
     */
    public Node get(Node node) throws AccessControlException,
            NodeNotFoundException
    {
        if (node == null)
        {
            throw new IllegalArgumentException("Missing argument 'node'");
        }
        return getDAONode(node).getNode();
    }

    /**
     * Put the given node into the database.
     */
    public Node put(final Node node) throws AccessControlException,
            NodeNotFoundException, NodeAlreadyExistsException
    {

        synchronized (this)
        {
            try
            {

                // Start the transaction.
                startTransaction();
                
                DAONode dbNode = getNodesAbove(new NodeMapper().mapDomainNode(node));
                
                // check the write permissions
                getNodeAuthorizer().checkWriteAccess(dbNode.getParent().getNode());
                
                // make sure the leaf node doesn't already exist
                if (getSingleNodeFromSelect(getSelectNodeByNameAndParentSQL(dbNode)) != null)
                {
                    throw new NodeAlreadyExistsException(node.getPath());
                }
                
                KeyHolder keyHolder = new GeneratedKeyHolder();
                
                final String insertSQL = getInsertNodeSQL(dbNode);

                jdbc.update(new PreparedStatementCreator() {
                    public PreparedStatement createPreparedStatement(Connection connection)
                        throws SQLException
                    {
                        PreparedStatement ps = connection.prepareStatement(insertSQL,
                            Statement.RETURN_GENERATED_KEYS);
                        return ps;
                    }}, keyHolder);
                
                Long generatedId = new Long(keyHolder.getKey().longValue());
                dbNode.setNodeID(generatedId);

                // insert the node properties
                Iterator<NodeProperty> propertyIterator = node.getProperties().iterator();
                while (propertyIterator.hasNext())
                {
                    NodeProperty next = propertyIterator.next();
                    if (!NodePropertyMapper.isStandardHeaderProperty(next))
                    {
                        jdbc.update(getInsertNodePropertySQL(dbNode, next));
                    }
                }

                // Commit the transaction.
                commitTransaction();
                
                log.debug("Inserted new node: " + node);
                
                return dbNode.getNode();
                
            } catch (Throwable t)
            {
                rollbackTransaction();
                log.error("Put rollback for node: " + node, t);
                if (t instanceof NodeNotFoundException)
                {
                    throw (NodeNotFoundException) t;
                }
                else if (t instanceof NodeAlreadyExistsException)
                {
                    throw (NodeAlreadyExistsException) t;
                }
                else if (t instanceof AccessControlException)
                {
                    throw (AccessControlException) t;
                }
                else
                {
                    throw new IllegalStateException(t);
                }
            }
        }

    }
    
    /**
     * Delete the given node from the database.
     */
    public void delete(Node node) throws AccessControlException,
        NodeNotFoundException
    {
        // Get the node from the database.
        DAONode nodeInDb = this.getDAONode(node);
        
        // check delete permissions
        getNodeAuthorizer().checkDeleteAccess(nodeInDb.getNode());
        
        synchronized (this)
        {
            try
            {
                startTransaction();
                
                // delete the node properties
                jdbc.update(getDeleteNodePropertiesSQL(nodeInDb));
                
                // delete the node
                jdbc.update(getDeleteNodeSQL(nodeInDb));
                
                // collect and delete children of the node
                this.deleteChildrenOf(nodeInDb);
                
                commitTransaction();
                
            } catch (Throwable t)
            {
                rollbackTransaction();
                log.error("Delete rollback for node: " + node, t);
                if (t instanceof NodeNotFoundException)
                {
                    throw (NodeNotFoundException) t;
                }
                else if (t instanceof AccessControlException)
                {
                    throw (AccessControlException) t;
                }
                else
                {
                    throw new IllegalStateException(t);
                }
            }
            
        }
        log.debug("Node deleted: " + nodeInDb);
    }
    
    /**
     * Update existing node properties, delete existing properties marked
     * for deletion, and add any new properties.
     */
    public Node updateProperties(Node node) throws AccessControlException,
        NodeNotFoundException
    {
        // Get the node from the database.
        DAONode nodeInDb = this.getDAONode(node);
        
        // check update permissions
        getNodeAuthorizer().checkUpdateAccess(nodeInDb.getNode());
        
        synchronized (this)
        {
            try
            {
                startTransaction();
                
                // Iterate through the user properties and the db properties,
                // potentially updading, deleting or adding new ones
                for (NodeProperty nextProperty : node.getProperties())
                {
                    // Is this property saved already?
                    if (nodeInDb.getProperties().contains(nextProperty))
                    {
                        if (nextProperty.isMarkedForDeletion())
                        {
                            // delete the property
                            jdbc.update(getDeleteNodePropertySQL(nodeInDb, nextProperty));
                        }
                        else
                        {
                            // update the property value
                            jdbc.update(getUpdateNodePropertySQL(nodeInDb, nextProperty));
                        }
                    }
                    else
                    {
                        // insert the new property
                        jdbc.update(getInsertNodePropertySQL(nodeInDb, nextProperty));
                    }
                }
                
                commitTransaction();
                
            } catch (Throwable t)
            {
                rollbackTransaction();
                log.error("Update rollback for node: " + node, t);
                if (t instanceof NodeNotFoundException)
                {
                    throw (NodeNotFoundException) t;
                }
                else if (t instanceof AccessControlException)
                {
                    throw (AccessControlException) t;
                }
                else
                {
                    throw new IllegalStateException(t);
                }
            }
            
        }
        log.debug("Node updated: " + nodeInDb);
        
        // return a fresh instance of the node
        return get(node);
    }
    
    /**
     * Copy the node to the specified path.
     */
    public void copy(Node node, String copyToPath)
    {
        throw new UnsupportedOperationException("Copy not yet implemented.");
    }
    
    /**
     * Move the node to the specified path.
     */
    public void move(Node node, String newPath)
    {
        throw new UnsupportedOperationException("Move not yet implemented.");
    }
    
    protected DAONode getDAONode(Node node) throws AccessControlException,
            NodeNotFoundException
    {
        
        DAONode returnNode;
        synchronized (this)
        {
            // get the nodes above in the hierarchy
            DAONode dbNode = getNodesAbove(new NodeMapper().mapDomainNode(node));
            
            // get the node in question
            returnNode = getSingleNodeFromSelect(getSelectNodeByNameAndParentSQL(dbNode));
            
            if (returnNode == null)
            {
                throw new NodeNotFoundException(node.toString());
            }
            
            // get the children if this is a container node
            if (returnNode instanceof DAOContainerNode)
            {
                List children = jdbc.query(this.getSelectNodesByParentSQL(returnNode), new NodeMapper());
                ((DAOContainerNode) returnNode).setNodes(children);
            }
            
            List returnNodeProperties = jdbc.query(getSelectNodePropertiesByID(returnNode), new NodePropertyMapper());
            returnNode.getProperties().addAll(returnNodeProperties);
            returnNode.setParent(dbNode.getParent());
            returnNode.setUri(node.getUri());
        }
        
        if (returnNode == null)
        {
            throw new NodeNotFoundException(node.getPath());
        }
        
        // check the permissions
        getNodeAuthorizer().checkReadAccess(returnNode.getNode());
        
        log.debug("get node success: nodeID: " + returnNode.getNodeID() + " for path: " + node.getPath());
        
        return returnNode;
    }

    /**
     * Return the single node matching the select statement or null if none
     * were found.
     * 
     * @param sql The SQL to execute
     * @return The single node or null.
     */
    protected DAONode getSingleNodeFromSelect(String sql)
    {
        DAONode node = null;
        List nodeList = jdbc.query(sql, new NodeMapper());
        if (nodeList.size() > 1)
        {
            throw new IllegalStateException("More than one node with nodeID: "
                    + node.getNodeID());
        }
        if (nodeList.size() == 1)
        {
            return (DAONode) nodeList.get(0);
        }
        return null;
    }
    
    protected DAONode getNodesAbove(DAONode node) throws NodeNotFoundException
    {
        List<DAONode> hierarchyFromRoot = node.getHierarchy();
        Iterator<DAONode> hierarchyIterator = hierarchyFromRoot.iterator();
        DAONode next = null;
        next = hierarchyIterator.next();
        
        while (!next.getNode().equals(node.getNode()))
        {
            log.debug("NodeDAO:getNodesAbove() next: " + next.getNode().getName());
            
            // select id from node where parent is next.getParent() and name = next.getName()
            DAONode nodeInDb = getSingleNodeFromSelect(getSelectNodeByNameAndParentSQL(next));
            
            // check for existence
            if (nodeInDb == null)
            {
                throw new NodeNotFoundException(node.getPath());
            }
            
            // check that it is a container node
            if (! (next instanceof DAOContainerNode))
            {
                throw new IllegalStateException("Non-container node found mid-hierarchy.");
            }
            
            next.setNodeID(nodeInDb.getNodeID());
            
            // get the properties for the node
            List nodeProperties = jdbc.query(getSelectNodePropertiesByID(next), new NodePropertyMapper());
            next.getProperties().addAll(nodeProperties);
            
            if (hierarchyIterator.hasNext())
            {
                DAONode nextInHierarchy = hierarchyIterator.next();
                nextInHierarchy.setParent((DAOContainerNode) next);
                next = nextInHierarchy;
            }
            else
            {
                throw new IllegalStateException("Target node missed.");
            }
        }
        return next;
    }
    
    protected void deleteChildrenOf(DAONode node)
    {
        List children = jdbc.query(getSelectNodesByParentSQL(node), new NodeMapper());
        Iterator i = children.iterator();
        while (i.hasNext())
        {
            deleteChildrenOf((DAONode) i.next());
        }
        jdbc.update(getDeleteNodePropertiesByParentSQL(node));
        jdbc.update(getDeleteNodesByParentSQL(node));
    }

    /**
     * @return The name of the table for persisting nodes.
     */
    public abstract String getNodeTableName();

    /**
     * @return The name of the table for storing node properties.
     */
    public abstract String getNodePropertyTableName();
    
    /**
     * @return The node data source to use.
     */
    public abstract DataSource getDataSource();
    
    /**
     * @return The node authorizer to use.
     */
    public abstract NodeAuthorizer getNodeAuthorizer();

    /**
     * @param node The node to query for.
     * @return The SQL string for finding the node in the database by
     * name and parentID.
     */
    protected String getSelectNodeByNameAndParentSQL(DAONode node)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("select nodeID, parentID, name, type, owner, groupRead, groupWrite, "
            + "contentLength, contentType, contentEncoding, contentMD5 from Node where name = '"
            + node.getName()
            + "' and "
            + ((node.getParent() == null || (node.getParent().getNodeID() == 0)) ?
                "(parentID is null or parentID = 0)" :
                "parentID = " + node.getParent().getNodeID()));
        return sb.toString();
    }
    
    protected String getSelectNodesByParentSQL(DAONode parent)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("select nodeID, parentID, name, type, owner, groupRead, groupWrite, "
            + "contentLength, contentType, contentEncoding, contentMD5 from Node where parentID = "
            + parent.getNodeID());
        return sb.toString();
    }

    /**
     * @param node The node to query for.
     * @return The SQL string for finding the node in the database by nodeID.
     */
    protected String getSelectNodeByIdSQL(DAONode node)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("select nodeID, parentID, name, type, owner, groupRead, groupWrite, "
           +  "contentLength, contentType, contentEncoding, contentMD5 from Node where nodeID = "
            + node.getNodeID());
        return sb.toString();
    }
    
    protected String getSelectNodePropertiesByID(DAONode node)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("select nodePropertyID, propertyURI, propertyValue from ");
        sb.append(getNodePropertyTableName());
        sb.append(" where nodeID = ");
        sb.append(node.getNodeID());
        return sb.toString();
    }

    /**
     * @param node The node to insert
     * @return The SQL string for inserting the node in the database.
     */
    protected String getInsertNodeSQL(DAONode node)
    {
        
        long contentLength = 0;
        String contentType = null;
        String contentEncoding = null;
        byte[] contentMD5 = null;
        
        String contentLengthString = getPropertyValue(node, NodePropertyMapper.PROPERTY_CONTENTLENGTH_URI);
        contentType = getPropertyValue(node, NodePropertyMapper.PROPERTY_CONTENTTYPE_URI);
        contentEncoding = getPropertyValue(node, NodePropertyMapper.PROPERTY_CONTENTENCODING_URI);
        String contentMD5String = getPropertyValue(node, NodePropertyMapper.PROPERTY_CONTENTMD5_URI);
        
        if (contentLengthString != null)
        {
            try
            {
                contentLength = new Long(contentLengthString);
            } catch (NumberFormatException e)
            {
                log.warn("Content length is not a number, continuing.");
            }
        }
        
        if (contentMD5String != null)
        {
            contentMD5 = contentMD5String.getBytes();
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("insert into ");
        sb.append(getNodeTableName());
        sb.append(" (");
        sb.append("parentID,");
        sb.append("name,");
        sb.append("type,");
        sb.append("owner,");
        sb.append("groupRead,");
        sb.append("groupWrite,");
        sb.append("contentLength,");
        sb.append("contentType,");
        sb.append("contentEncoding,");
        sb.append("contentMD5");
        sb.append(") values (");
        sb.append((node.getParent() == null || node.getParent().getNodeID() == 0) ? null : node.getParent()
                .getNodeID());
        sb.append(",'");
        sb.append(node.getName());
        sb.append("','");
        sb.append(node.getDatabaseTypeRepresentation());
        sb.append("','");
        sb.append(node.getOwner());
        sb.append("','");
        sb.append(node.getGroupRead());
        sb.append("','");
        sb.append(node.getGroupWrite());
        sb.append("',");
        sb.append(contentLength);
        sb.append(",'");
        sb.append(contentType);
        sb.append("','");
        sb.append(contentEncoding);
        sb.append("','");
        sb.append(contentMD5);
        sb.append("')");
        return sb.toString();
    }
    
    private String getPropertyValue(DAONode node, String propertyURI)
    {
        int propertyIndex;
        String value = null;
        if ((propertyIndex = node.getProperties().indexOf(new NodeProperty(propertyURI, ""))) != -1)
        {
            value = ((NodeProperty) node.getProperties().get(propertyIndex)).getPropertyValue();
        }        
        return value;
    }

    /**
     * @param node The node for the property
     * @param nodeProperty  The property of the node
     * @return The SQL string for inserting the node property in the database.
     */
    protected String getInsertNodePropertySQL(DAONode node,
            NodeProperty nodeProperty)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("insert into ");
        sb.append(getNodePropertyTableName());
        sb.append(" (");
        sb.append("nodeID,");
        sb.append("propertyURI,");
        sb.append("propertyValue");
        sb.append(") values (");
        sb.append(node.getNodeID());
        sb.append(",'");
        sb.append(nodeProperty.getPropertyURI());
        sb.append("','");
        sb.append(nodeProperty.getPropertyValue());
        sb.append("')");
        return sb.toString();
    }

    /**
     * @param node The node to delete
     * @return The SQL string for deleting the node from the database.
     */
    protected String getDeleteNodeSQL(DAONode node)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("delete from ");
        sb.append(getNodeTableName());
        sb.append(" where nodeID = ");
        sb.append(node.getNodeID());
        return sb.toString();
    }
    
    protected String getDeleteNodePropertiesSQL(DAONode node)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("delete from ");
        sb.append(getNodePropertyTableName());
        sb.append(" where nodeID = ");
        sb.append(node.getNodeID());
        return sb.toString();
    }
    
    protected String getDeleteNodePropertySQL(DAONode node, NodeProperty nodeProperty)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("delete from ");
        sb.append(getNodePropertyTableName());
        sb.append(" where nodeID = ");
        sb.append(node.getNodeID());
        sb.append(" and propertyURI = '");
        sb.append(nodeProperty.getPropertyURI());
        sb.append("'");
        return sb.toString();
    }
    
    protected String getUpdateNodePropertySQL(DAONode node, NodeProperty nodeProperty)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("update ");
        sb.append(getNodePropertyTableName());
        sb.append(" set propertyValue = '");
        sb.append(nodeProperty.getPropertyValue());
        sb.append("' where nodeID = ");
        sb.append(node.getNodeID());
        sb.append(" and propertyURI = '");
        sb.append(nodeProperty.getPropertyURI());
        sb.append("'");
        return sb.toString();
    }
    
    protected String getDeleteNodesByParentSQL(DAONode parent)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("delete from ");
        sb.append(getNodeTableName());
        sb.append(" where parentID = ");
        sb.append(parent.getNodeID());
        return sb.toString();
    }
    
    protected String getDeleteNodePropertiesByParentSQL(DAONode parent)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("delete from ");
        sb.append(getNodePropertyTableName());
        sb.append(" where nodeID in (select nodeID from ");
        sb.append(getNodeTableName());
        sb.append(" where parentID = ");
        sb.append(parent.getNodeID());
        sb.append(")");
        return sb.toString();
    }

}
