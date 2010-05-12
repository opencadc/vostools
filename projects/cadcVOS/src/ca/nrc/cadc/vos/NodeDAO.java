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

import java.net.URISyntaxException;
import java.security.AccessControlException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import ca.nrc.cadc.vos.dao.NodeID;
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
    
    /**
     * Perform NodeDAO initialization.  This method must be called
     * before any DAO operations are used.
     */
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
     * Get the node from the parent.  This method assumes that the nodeID
     * of the parent object is set.  If the parent object is null, it is
     * assumed to be at the root level.
     */
    public Node getFromParent(Node node, ContainerNode parent) throws NodeNotFoundException
    {
        if (node == null)
        {
            throw new NodeNotFoundException("Node parameter is null.");
        }
        node.setParent(parent);
        synchronized (this)
        {
            Node returnNode = getSingleNodeFromSelect(getSelectNodeByNameAndParentSQL(node));
            if (returnNode == null)
            {
                throw new NodeNotFoundException(node.getPath());
            }
            
            try
            {
                returnNode.setUri(node.getUri());
            }
            catch (URISyntaxException e)
            {
                log.warn("Coudln't reset URI", e);
            }
            log.debug("Node retrieved from parent: " + returnNode);
            return returnNode;
        }
    }
    
    /**
     * Put the node in the provided container.  This method assumes that the
     * nodeID of the parent is set.  If the parent object is null, it is
     * assumed to be at the root level.
     */
    public Node putInContainer(Node node, ContainerNode parent) throws NodeNotFoundException, NodeAlreadyExistsException
    {
        
        if (node == null)
        {
            throw new NodeNotFoundException("Node parameter is null.");
        }
        
        synchronized (this)
        {
            try
            {
                // Start the transaction.
                startTransaction();
                
                // make sure the entire parent tree exists
                node.setParent(parent);
                
                // make sure this leaf does not already exist
                if (getSingleNodeFromSelect(getSelectNodeByNameAndParentSQL(node)) != null)
                {
                    throw new NodeAlreadyExistsException(node.getPath());
                }
                
                KeyHolder keyHolder = new GeneratedKeyHolder();
                
                final String insertSQL = getInsertNodeSQL(node);

                jdbc.update(new PreparedStatementCreator() {
                    public PreparedStatement createPreparedStatement(Connection connection)
                        throws SQLException
                    {
                        PreparedStatement ps = connection.prepareStatement(insertSQL,
                            Statement.RETURN_GENERATED_KEYS);
                        return ps;
                    }}, keyHolder);
                
                Long generatedID = new Long(keyHolder.getKey().longValue());
                node.appData = new NodeID(generatedID);

                // insert the node properties
                Iterator<NodeProperty> propertyIterator = node.getProperties().iterator();
                while (propertyIterator.hasNext())
                {
                    NodeProperty next = propertyIterator.next();
                    if (!NodePropertyMapper.isStandardHeaderProperty(next))
                    {
                        jdbc.update(getInsertNodePropertySQL(node, next));
                    }
                }

                // Commit the transaction.
                commitTransaction();
                
                log.debug("Inserted new node: " + node);
                
                return node;
                
            }
            catch (Throwable t)
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
     * Delete the node.  If deleteChildren is true, also recursively delete
     * any children of the node.
     */
    public void delete(Node node, boolean deleteChildren) throws AccessControlException, NodeNotFoundException
    {
        
        Node dbNode = null;
        
        synchronized (this)
        {
            try
            {
                
                // Get the node from the database.
                dbNode = this.getFromRoot(node);
                
                startTransaction();
                
                // delete the node properties
                jdbc.update(getDeleteNodePropertiesSQL(dbNode));
                
                // delete the node
                jdbc.update(getDeleteNodeSQL(dbNode));
                
                if (deleteChildren)
                {
                    // collect and delete children of the node
                    this.deleteChildrenOf(dbNode);
                }
                
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
        log.debug("Node deleted: " + dbNode);
    }
    
    /**
     * Update the properties associated with this node.  New properties are added,
     * changed property values are updated, and properties marked for deletion are
     * removed.
     */
    public Node updateProperties(Node node) throws AccessControlException, NodeNotFoundException
    {

        Node dbNode = null;
        
        synchronized (this)
        {
            try
            {
                
                // Get the node from the database.
                dbNode = getFromRoot(node);
                
                startTransaction();
                
                // Iterate through the user properties and the db properties,
                // potentially updading, deleting or adding new ones
                for (NodeProperty nextProperty : node.getProperties())
                {
                    
                    // Is this property saved already?
                    if (dbNode.getProperties().contains(nextProperty))
                    {
                        if (nextProperty.isMarkedForDeletion())
                        {
                            // delete the property
                            jdbc.update(getDeleteNodePropertySQL(dbNode, nextProperty));
                            dbNode.getProperties().remove(nextProperty);
                        }
                        else
                        {
                            // update the property value
                            jdbc.update(getUpdateNodePropertySQL(dbNode, nextProperty));
                            dbNode.getProperties().remove(new NodeProperty(nextProperty.getPropertyURI(), null));
                            dbNode.getProperties().add(nextProperty);
                        }
                    }
                    else
                    {
                        // insert the new property
                        jdbc.update(getInsertNodePropertySQL(dbNode, nextProperty));
                        dbNode.getProperties().add(nextProperty);
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
        
        log.debug("Node updated: " + dbNode);
        
        return dbNode;
    }
    
    /**
     * Move the node to the new path
     * @throws UnsupporedOperationException Until implementation is complete.
     */
    public void move(Node node, String newPath)
    {
        throw new UnsupportedOperationException("Move not implemented.");
    }
    

    /**
     * Copy the node to the new path
     * @throws UnsupporedOperationException Until implementation is complete.
     */
    public void copy(Node node, String copyToPath)
    {
        throw new UnsupportedOperationException("Copy not implemented.");
    }
    
    /**
     * Get the node all the way up to it's root.
     * @param node
     * @return
     * @throws NodeNotFoundException
     */
    protected Node getFromRoot(Node node) throws NodeNotFoundException
    {
        if (node == null)
        {
            throw new NodeNotFoundException("Node parameter is null.");
        }
        
        Stack<Node> nodeStack = new Stack<Node>();
        Node nextNode = node;
        while (nextNode != null)
        {
            nodeStack.push(nextNode);
            nextNode = nextNode.getParent();
        }
        
        Node dbNode = null;
        
        ContainerNode parent = null;
        while (!nodeStack.isEmpty())
        {
            nextNode = nodeStack.pop();
            nextNode.setParent(parent);
            dbNode = getSingleNodeFromSelect(getSelectNodeByNameAndParentSQL(nextNode));
            if (dbNode == null)
            {
                throw new NodeNotFoundException(node.getPath());
            }
            if (dbNode instanceof ContainerNode)
            {
                parent = (ContainerNode) dbNode;
            }
        }
        
        try
        {
            dbNode.setUri(node.getUri());
        }
        catch (URISyntaxException e)
        {
            log.warn("Coudln't reset URI", e);
        }
        return dbNode;
    }
    
    /**
     * Return the single node matching the select statement or null if none
     * were found.
     * 
     * @param sql The SQL to execute
     * @return The single node or null.
     */
    protected Node getSingleNodeFromSelect(String sql)
    {
        Node node = null;
        List<Node> nodeList = jdbc.query(sql, new NodeMapper());
        if (nodeList.size() > 1)
        {
            throw new IllegalStateException("More than one node returned for SQL: "
                    + sql);
        }
        if (nodeList.size() == 1)
        {
            Node returnNode = (Node) nodeList.get(0);
            
            // get the children if this is a container node
            if (returnNode instanceof ContainerNode)
            {
                List<Node> children = jdbc.query(getSelectNodesByParentSQL(returnNode), new NodeMapper());
                ((ContainerNode) returnNode).setNodes(children);
            }
            
            // get the properties for the node
            List<NodeProperty> returnNodeProperties = jdbc.query(getSelectNodePropertiesByID(returnNode), new NodePropertyMapper());
            returnNode.getProperties().addAll(returnNodeProperties);
            
            return returnNode;
        }
        return null;
    }
    
    /**
     * Delete the children of the provided node.
     * @param node
     */
    protected void deleteChildrenOf(Node node)
    {
        List<Node> children = jdbc.query(getSelectNodesByParentSQL(node), new NodeMapper());
        Iterator<Node> i = children.iterator();
        while (i.hasNext())
        {
            deleteChildrenOf(i.next());
        }
        jdbc.update(getDeleteNodePropertiesByParentSQL(node));
        jdbc.update(getDeleteNodesByParentSQL(node));
    }
    
    /**
     * The the nodeID of the provided node.
     * @param node
     * @return
     */
    protected Long getNodeID(Node node)
    {
        if (node == null || node.appData == null)
        {
            return null;
        }
        if (node.appData instanceof NodeID)
        {
            return ((NodeID) node.appData).getId();
        }
        return null;
    }
    
    /**
     * Return the value of the specified property.
     */
    protected String getPropertyValue(Node node, String propertyURI)
    {   
        final NodeProperty searchProperty = new NodeProperty(propertyURI, null);
        for (NodeProperty nodeProperty : node.getProperties())
        {
            if (nodeProperty.equals(searchProperty))
            {
                return nodeProperty.getPropertyValue();
            }
        }
        return null;
    }
    
    /**
     * @param node The node to query for.
     * @return The SQL string for finding the node in the database by
     * name and parentID.
     */
    protected String getSelectNodeByNameAndParentSQL(Node node)
    {
        String parentWhereClause = null;
        Long parentNodeID =  getNodeID(node.getParent());
        if (parentNodeID == null)
        {
            parentWhereClause = "(parentID is null or parentID = 0)";
        }
        else
        {
            parentWhereClause = "parentID = " + parentNodeID;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("select nodeID, parentID, name, type, owner, groupRead, groupWrite, "
            + "contentLength, contentType, contentEncoding, contentMD5 from Node where name = '"
            + node.getName()
            + "' and "
            + parentWhereClause);
        return sb.toString();
    }
    
    /**
     * @param parent The node to query for.
     * @return The SQL string for finding nodes given the parent. 
     */
    protected String getSelectNodesByParentSQL(Node parent)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("select nodeID, parentID, name, type, owner, groupRead, groupWrite, "
            + "contentLength, contentType, contentEncoding, contentMD5 from Node where parentID = "
            + getNodeID(parent));
        return sb.toString();
    }
    
    /**
     * @param parent The node for which properties are queried.
     * @return The SQL string for finding the node properties. 
     */
    protected String getSelectNodePropertiesByID(Node node)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("select nodePropertyID, propertyURI, propertyValue from ");
        sb.append(getNodePropertyTableName());
        sb.append(" where nodeID = ");
        sb.append(getNodeID(node));
        return sb.toString();
    }
    
    /**
     * @param node The node to insert
     * @return The SQL string for inserting the node in the database.
     */
    protected String getInsertNodeSQL(Node node)
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
        
        if (node.getOwner() == null)
        {
            throw new IllegalArgumentException("Node owner cannot be null.");
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
        sb.append(getNodeID(node.getParent()));
        sb.append(",'");
        sb.append(node.getName());
        sb.append("','");
        sb.append(NodeMapper.getDatabaseTypeRepresentation(node));
        sb.append("','");
        sb.append(node.getOwner());
        sb.append("',");
        sb.append((node.getGroupRead() == null) ? null : "'" + node.getGroupRead() + "'");
        sb.append(",");
        sb.append((node.getGroupWrite() == null) ? null : "'" + node.getGroupWrite() + "'");
        sb.append(",");
        sb.append(contentLength);
        sb.append(",");
        sb.append((contentType == null) ? null : "'" + contentType + "'");
        sb.append(",");
        sb.append((contentEncoding == null) ? null : "'" + contentEncoding + "'");
        sb.append(",");
        sb.append((contentMD5== null) ? null : "'" + contentMD5 + "'");
        sb.append(")");
        return sb.toString();
    }
    
    /**
     * @param node The node for the property
     * @param nodeProperty  The property of the node
     * @return The SQL string for inserting the node property in the database.
     */
    protected String getInsertNodePropertySQL(Node node,
            NodeProperty nodeProperty)
    {
        StringBuilder sb = new StringBuilder();
        String value = nodeProperty.getPropertyValue();
        if (nodeProperty.getPropertyURI().equals(NodePropertyMapper.PROPERTY_CONTENTENCODING_URI))
        {
            sb.append("update ");
            sb.append(getNodeTableName());
            sb.append(" set contentEncoding = ");
            sb.append((value == null) ? null : "'" + value + "'");
            sb.append(" where nodeID = ");
            sb.append(getNodeID(node));
        }
        else if (nodeProperty.getPropertyURI().equals(NodePropertyMapper.PROPERTY_CONTENTLENGTH_URI))
        {
            Long contentLength = new Long(value);
            sb.append("update ");
            sb.append(getNodeTableName());
            sb.append(" set contentLength = ");
            sb.append(contentLength);
            sb.append(" where nodeID = ");
            sb.append(getNodeID(node));
        }
        else if (nodeProperty.getPropertyURI().equals(NodePropertyMapper.PROPERTY_CONTENTMD5_URI))
        {
            sb.append("update ");
            sb.append(getNodeTableName());
            sb.append(" set contentMD5 = ");
            sb.append((value == null) ? null : "'" + value + "'");
            sb.append(" where nodeID = ");
            sb.append(getNodeID(node));
        }
        else if (nodeProperty.getPropertyURI().equals(NodePropertyMapper.PROPERTY_CONTENTTYPE_URI))
        {
            sb.append("update ");
            sb.append(getNodeTableName());
            sb.append(" set contentType = ");
            sb.append((value == null) ? null : "'" + value + "'");
            sb.append(" where nodeID = ");
            sb.append(getNodeID(node));
        }
        else
        {
            sb.append("insert into ");
            sb.append(getNodePropertyTableName());
            sb.append(" (");
            sb.append("nodeID,");
            sb.append("propertyURI,");
            sb.append("propertyValue");
            sb.append(") values (");
            sb.append(((NodeID) node.appData).getId());
            sb.append(",'");
            sb.append(nodeProperty.getPropertyURI());
            sb.append("','");
            sb.append(nodeProperty.getPropertyValue());
            sb.append("')");
        }
        return sb.toString();
    }
    
    /**
     * @param node The node to delete
     * @return The SQL string for deleting the node from the database.
     */
    protected String getDeleteNodeSQL(Node node)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("delete from ");
        sb.append(getNodeTableName());
        sb.append(" where nodeID = ");
        sb.append(getNodeID(node));
        return sb.toString();
    }
    
    /**
     * @param node Delete the properties of this node.
     * @return The SQL string for performing property deletion.
     */
    protected String getDeleteNodePropertiesSQL(Node node)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("delete from ");
        sb.append(getNodePropertyTableName());
        sb.append(" where nodeID = ");
        sb.append(getNodeID(node));
        return sb.toString();
    }
    
    /**
     * @param parent The parent who's children are to be deleted.
     * @return The SQL string to perform this deletion.
     */
    protected String getDeleteNodesByParentSQL(Node parent)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("delete from ");
        sb.append(getNodeTableName());
        sb.append(" where parentID = ");
        sb.append(getNodeID(parent));
        return sb.toString();
    }
    
    /**
     * @param parent Delete the properties of the children of this parent.
     * @return The SQL string for performing this deletion.
     */
    protected String getDeleteNodePropertiesByParentSQL(Node parent)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("delete from ");
        sb.append(getNodePropertyTableName());
        sb.append(" where nodeID in (select nodeID from ");
        sb.append(getNodeTableName());
        sb.append(" where parentID = ");
        sb.append(getNodeID(parent));
        sb.append(")");
        return sb.toString();
    }
    
    /**
     * @param node The node for the properties.
     * @param nodeProperty The property in question.
     * @return The SQL string for performing this deletion.
     */
    protected String getDeleteNodePropertySQL(Node node, NodeProperty nodeProperty)
    {
        StringBuilder sb = new StringBuilder();
        if (nodeProperty.getPropertyURI().equals(NodePropertyMapper.PROPERTY_CONTENTENCODING_URI))
        {
            sb.append("update ");
            sb.append(getNodeTableName());
            sb.append(" set contentEncoding = null where nodeID = ");
            sb.append(getNodeID(node));
        }
        else if (nodeProperty.getPropertyURI().equals(NodePropertyMapper.PROPERTY_CONTENTLENGTH_URI))
        {
            sb.append("update ");
            sb.append(getNodeTableName());
            sb.append(" set contentLength = null where nodeID = ");
            sb.append(getNodeID(node));
        }
        else if (nodeProperty.getPropertyURI().equals(NodePropertyMapper.PROPERTY_CONTENTMD5_URI))
        {
            sb.append("update ");
            sb.append(getNodeTableName());
            sb.append(" set contentMD5 = null where nodeID = ");
            sb.append(getNodeID(node));
        }
        else if (nodeProperty.getPropertyURI().equals(NodePropertyMapper.PROPERTY_CONTENTTYPE_URI))
        {
            sb.append("update ");
            sb.append(getNodeTableName());
            sb.append(" set contentType = null where nodeID = ");
            sb.append(getNodeID(node));
        }
        else
        {
            sb.append("delete from ");
            sb.append(getNodePropertyTableName());
            sb.append(" where nodeID = ");
            sb.append(getNodeID(node));
            sb.append(" and propertyURI = '");
            sb.append(nodeProperty.getPropertyURI());
            sb.append("'");
        }
        return sb.toString();
    }
    
    /**
     * @param node The node for which the properties are to be updated.
     * @param nodeProperty The node property in question.
     * @return The SQL string for performing this update.
     */
    protected String getUpdateNodePropertySQL(Node node, NodeProperty nodeProperty)
    {
        StringBuilder sb = new StringBuilder();
        String value = nodeProperty.getPropertyValue();
        if (nodeProperty.getPropertyURI().equals(NodePropertyMapper.PROPERTY_CONTENTENCODING_URI))
        {
            sb.append("update ");
            sb.append(getNodeTableName());
            sb.append(" set contentEncoding = ");
            sb.append((value == null) ? null : "'" + value + "'");
            sb.append(" where nodeID = ");
            sb.append(getNodeID(node));
        }
        else if (nodeProperty.getPropertyURI().equals(NodePropertyMapper.PROPERTY_CONTENTLENGTH_URI))
        {
            Long contentLength = new Long(value);
            sb.append("update ");
            sb.append(getNodeTableName());
            sb.append(" set contentLength = ");
            sb.append(contentLength);
            sb.append(" where nodeID = ");
            sb.append(getNodeID(node));
        }
        else if (nodeProperty.getPropertyURI().equals(NodePropertyMapper.PROPERTY_CONTENTMD5_URI))
        {
            sb.append("update ");
            sb.append(getNodeTableName());
            sb.append(" set contentMD5 = ");
            sb.append((value == null) ? null : "'" + value + "'");
            sb.append(" where nodeID = ");
            sb.append(getNodeID(node));
        }
        else if (nodeProperty.getPropertyURI().equals(NodePropertyMapper.PROPERTY_CONTENTTYPE_URI))
        {
            sb.append("update ");
            sb.append(getNodeTableName());
            sb.append(" set contentType = ");
            sb.append((value == null) ? null : "'" + value + "'");
            sb.append(" where nodeID = ");
            sb.append(getNodeID(node));
        }
        else
        {
            sb.append("update ");
            sb.append(getNodePropertyTableName());
            sb.append(" set propertyValue = '");
            sb.append(nodeProperty.getPropertyValue());
            sb.append("' where nodeID = ");
            sb.append(getNodeID(node));
            sb.append(" and propertyURI = '");
            sb.append(nodeProperty.getPropertyURI());
            sb.append("'");
        }
        return sb.toString();
    }
    
}
