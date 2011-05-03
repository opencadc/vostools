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

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.util.CaseInsensitiveStringComparator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
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

import ca.nrc.cadc.util.HexUtil;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOS.NodeBusyState;
import ca.nrc.cadc.vos.VOSURI;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Set;
import java.util.TreeSet;
import javax.security.auth.Subject;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

/**
 * Helper class for implementing NodePersistence with a
 * relational database back end for metadata. This class is
 * NOT thread-safe and ten caller must instantiate a new one
 * in each thread (e.g. app-server request thread).
 *
 * @author majorb
 */
public class NodeDAO
{
    private static Logger log = Logger.getLogger(NodeDAO.class);

    // temporarily needed by NodeMapper
    static final String NODE_TYPE_DATA = "D";
    static final String NODE_TYPE_CONTAINER = "C";
    
    private static final int NODE_NAME_COLUMN_SIZE = 256;
    private static final int MAX_TIMESTAMP_LENGTH = 30;

    // Database connection.
    protected DataSource dataSource;
    protected NodeSchema nodeSchema;
    protected String authority;
    protected IdentityManager identManager;

    protected JdbcTemplate jdbc;
    private DataSourceTransactionManager transactionManager;
    private DefaultTransactionDefinition defaultTransactionDef;
    private TransactionStatus transactionStatus;

    private DateFormat dateFormat;
    private Calendar cal;

    public static class NodeSchema
    {
        public String nodeTable;
        public String propertyTable;
        public NodeSchema(String nodeTable, String propertyTable)
        {
            this.nodeTable = nodeTable;
            this.propertyTable = propertyTable;
        }
    }
    
    /**
     * NodeDAO Constructor. This class was developed and tested using a
     * Sybase ASE RDBMS. Some SQL (update commands in particular) may be non-standard.
     *
     * @param dataSource
     * @param nodeSchema
     * @param authority
     * @param identManager 
     */
    public NodeDAO(DataSource dataSource, NodeSchema nodeSchema, String authority, IdentityManager identManager)
    {
        this.dataSource = dataSource;
        this.nodeSchema = nodeSchema;
        this.authority = authority;
        this.identManager = identManager;

        this.defaultTransactionDef = new DefaultTransactionDefinition();
        defaultTransactionDef.setIsolationLevel(DefaultTransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactionManager = new DataSourceTransactionManager(dataSource);

        this.dateFormat = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
        this.cal = Calendar.getInstance(DateUtil.UTC);
    }

    // convenience during refactor
    protected String getNodeTableName() { return nodeSchema.nodeTable; }

    // convenience during refactor
    protected String getNodePropertyTableName() { return nodeSchema.propertyTable; }
    
    /**
     * Start a transaction to the data source.
     */
    protected void startTransaction()
    {
        if (transactionStatus != null)
            throw new IllegalStateException("transaction already in progress");
        log.debug("startTransaction");
        this.transactionStatus = transactionManager.getTransaction(defaultTransactionDef);
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
     * Checks that the specified node has already been persisted. This will pass if this
     * node was obtained from the getPath method.
     * @param node
     */
    protected void expectPersistentNode(Node node)
    {
        if (node == null)
            throw new IllegalArgumentException("node cannot be null");
        if (node.appData == null)
            throw new IllegalArgumentException("node is not a persistent node: " + node.getUri().getPath());
    }

    /**
     * Get a complete path from the root container. For the container nodes in
     * the returned node, only child nodes on the path will be included in the
     * list of children; other children are not included. Nodes returned from
     * this method will have some but not all properties set. Specifically, any
     * properties that are inherently single-valued and stored in the Node table
     * are included, as are the access-control properties (isPublic, group-read, 
     * and group-write). The remaining properties for a node can be obtained by
     * calling getProperties(Node).
     *
     * @see getProperties(Node)
     * @param path
     * @return the last node in the path, with all parents or null if not found
     */
    public Node getPath(String path)
    {
        log.debug("getPath: " + path);
        if (path.charAt(0) == '/')
            path = path.substring(1);
        // generate single join query to extract path
        NodePathStatementCreator npsc = new NodePathStatementCreator(
                path.split("/"), getNodeTableName(), getNodePropertyTableName());

        // execute query with NodePathExtractor
        Node ret = (Node) jdbc.query(npsc, new NodePathExtractor());

        return ret;
    }

    /**
     * Load all the properties for the specified Node.
     * 
     * @param node
     */
    public void getProperties(Node node)
    {
        log.debug("getProperties: " + node.getUri().getPath() + ", " + node.getClass().getSimpleName());
        expectPersistentNode(node);

        log.debug("getProperties: " + node.getUri().getPath() + ", " + node.getClass().getSimpleName());
        String sql = getSelectNodePropertiesByID(node);
        log.debug("getProperties: " + sql);
        List<NodeProperty> props = jdbc.query(sql, new NodePropertyMapper());
        node.getProperties().addAll(props);
    }

    /**
     * Load a single child node of the specified container.
     * 
     * @param parent
     * @param name
     */
    public void getChild(ContainerNode parent, String name)
    {
        log.debug("getChild: " + parent.getUri().getPath() + ", " + name);
        expectPersistentNode(parent);

        String sql = getSelectChildNodeSQL(parent);
        log.debug("getChild: " + sql);
        List<Node> nodes = jdbc.query(sql, new Object[] { name }, 
                new NodeMapper(authority, parent.getUri().getPath(), identManager));
        if (nodes.size() > 1)
            throw new IllegalStateException("BUG - found " + nodes.size() + " child nodes named " + name
                    + " for container " + parent.getUri().getPath());
        addChildNodes(parent, nodes);
    }

    /**
     * Load all the child nodes of the specified container.
     *
     * @param parent
     */
    public void getChildren(ContainerNode parent)
    {
        log.debug("getChildren: " + parent.getUri().getPath() + ", " + parent.getClass().getSimpleName());
        expectPersistentNode(parent);

        // we must re-run the query in case server-side content changed since the argument node
        // was called, e.g. from delete(node) or markForDeletion(node)
        String sql = getSelectNodesByParentSQL(parent);
        log.debug("getChildren: " + sql);
        List<Node> nodes = jdbc.query(sql,
                new NodeMapper(authority, parent.getUri().getPath(), identManager));
        addChildNodes(parent, nodes);
    }

    private void addChildNodes(ContainerNode parent, List<Node> nodes)
    {
        for (Node n : nodes)
        {
            if (!parent.getNodes().contains(n))
            {
                log.debug("adding child to list: " + n.getUri().getPath());
                n.setParent(parent);
                parent.getNodes().add(n);
            }
            else
                log.debug("child already in list, not adding: " + n.getUri().getPath());
        }
    }

    /**
     * Store the specified node. The node must be attached to a parent container and
     * the parent container must have already been persisted.
     * 
     * @param node
     * @return the same node but with generated internal ID set in the appData field
     */
    public Node put(Node node, Subject owner)
    {
        log.debug("put: " + node.getUri().getPath() + ", " + node.getClass().getSimpleName());

        // if parent is null, this is just a new root-level node,
        if (node.getParent() != null && node.getParent().appData == null)
            throw new IllegalArgumentException("parent of node is not a persistent node: " + node.getUri().getPath());

        if (node.appData != null) // persistent node == update == not supported
            throw new UnsupportedOperationException("update of existing node not supported; try updateProperties");

        if (node.getName().length() > NODE_NAME_COLUMN_SIZE)
            throw new IllegalArgumentException("length of node name exceeds limit ("+NODE_NAME_COLUMN_SIZE+"): " + node.getName());

        try
        {
            

            startTransaction();
            NodePutStatementCreator npsc = new NodePutStatementCreator(nodeSchema, false);
            npsc.setValues(node, owner);
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(npsc, keyHolder);
            Long generatedID = new Long(keyHolder.getKey().longValue());

            node.appData = new NodeID(generatedID, owner);
            NodeID nodeID = (NodeID) node.appData;
            
            Iterator<NodeProperty> propertyIterator = node.getProperties().iterator();
            while (propertyIterator.hasNext())
            {
                NodeProperty prop = propertyIterator.next();
                if ( usePropertyTable(prop.getPropertyURI()) )
                {
                    PropertyStatementCreator ppsc = new PropertyStatementCreator(nodeSchema, nodeID, prop, false);
                    jdbc.update(ppsc);
                }
                // else: already persisted by the NodePutStatementCreator above
                // note: very important that the node owner (creator property) is excluded
                // by the above usePropertyTable returning false
            }

            commitTransaction();

            return node;
        }
        catch(Throwable t)
        {
            log.error("rollback for node: " + node.getUri().getPath(), t);
            try { rollbackTransaction(); }
            catch(Throwable oops) { log.error("failed to rollback transaction", oops); }
            throw new RuntimeException("failed to persist node: " + node.getUri(), t);
        }
        finally
        {
            if (transactionStatus != null)
                try
                {
                    log.warn("put: BUG - transaction still open in finally... calling rollback");
                    rollbackTransaction();
                }
                catch(Throwable oops) { log.error("failed to rollback transaction in finally", oops); }
        }
    }

    /**
     * Recursive delete of a node. This method irrevocably deletes a node and all
     * the child nodes below it.
     * 
     * @param node
     */
    public void delete(Node node)
    {
        log.debug("delete: " + node.getUri().getPath() + ", " + node.getClass().getSimpleName());
        expectPersistentNode(node);

        try
        {
            startTransaction();

            deleteNode(node, false);
            
            // TODO: call updateContentLength() on parent here??

            commitTransaction();
            log.debug("Node deleted: " + node.getUri().getPath());
        }
        catch (Throwable t)
        {
            log.error("Delete rollback for node: " + node.getUri().getPath(), t);
            if (transactionStatus != null)
                try { rollbackTransaction(); }
                catch(Throwable oops) { log.error("failed to rollback transaction", oops); }
            throw new RuntimeException("failed to delete " + node.getUri().getPath(), t);
        }
        finally
        {
            if (transactionStatus != null)
                try
                {
                    log.warn("delete - BUG - transaction still open in finally... calling rollback");
                    rollbackTransaction();
                }
                catch(Throwable oops) { log.error("failed to rollback transaction in finally", oops); }
        }
    }


    /**
     * Mark a node as deleted. A new node with the same name may be created without
     * colliding with the deleted node.
     *
     * @param node
     */
    public void markForDeletion(Node node)
    {
        log.debug("markForDeletion: " + node.getUri().getPath() + ", " + node.getClass().getSimpleName());
        expectPersistentNode(node);

        try
        {
            startTransaction();

            deleteNode(node, true);

            commitTransaction();
            log.debug("Node marked for deletion: " + node.getUri().getPath());
        }
        catch (Throwable t)
        {
            if (transactionStatus != null)
                try { rollbackTransaction(); }
                catch(Throwable oops) { log.error("failed to rollback transaction", oops); }
            throw new RuntimeException("failed to mark nodes as deleted", t);
        }
        finally
        {
            if (transactionStatus != null)
                try
                {
                    log.warn("markForDeletion - BUG - transaction still open in finally... calling rollback");
                    rollbackTransaction();
                }
                catch(Throwable oops) { log.error("failed to rollback transaction in finally", oops); }
        }
    }

    // no transaction implementation of node marking or deletion
    // the impl is depth-first so that child-parent constraints are
    // never violated
    private void deleteNode(Node node, boolean mark)
    {
        log.debug("deleteNode: " + node.getUri().getPath() + ", " + node.getClass().getSimpleName());
        // delete children
        if (node instanceof ContainerNode)
        {
            ContainerNode cn = (ContainerNode) node;
            deleteChildren(cn, mark);
        }

        // delete properties, same for real delete and mark
        String sql = getDeleteNodePropertiesSQL(node);
        log.debug(sql);
        jdbc.update(sql);

        // never delete a "home" node
        if (node.getParent() != null)
        {
            if (mark)
                sql = getMarkNodeForDeletionSQL(node);
            else
                sql = getDeleteNodeSQL(node);
            log.debug(sql);
            jdbc.update(sql);
        }
    }

    // no transaction implementation of recursively marking or deleting
    // child nodes
    private void deleteChildren(ContainerNode node, boolean mark)
    {
        log.debug("deleteChildren: " + node.getUri().getPath() + ", " + node.getClass().getSimpleName());
        getChildren(node);
        Iterator<Node> iter = node.getNodes().iterator();
        while ( iter.hasNext() )
        {
            Node n = iter.next();
            deleteNode(n, mark);
        }
    }

    // custom update methods for performance reasons since a full
    // put would be slow

    /**
     * Atomic update of the content length of the node by adding the
     * specified difference.
     *
     * @param node
     * @param delta amount to increment (+) or decrement (-)
     */
    public void updateContentLength(Node node, long delta)
    {
        log.debug("updateContentLength: " + node.getUri().getPath() + ", " + node.getClass().getSimpleName() + ", " + delta);
        expectPersistentNode(node);

        try
        {
            // use autocommit txn
            String sql = getUpdateContentLengthSQL(node, delta);
            log.debug(sql);
            int rc = jdbc.update(sql);
        }
        catch(Throwable t)
        {
            throw new RuntimeException("failed to delete " + node.getUri().getPath(), t);
        }
    }
    
    /**
     * Update the properties associated with this node.  New properties are added,
     * changed property values are updated, and properties marked for deletion are
     * removed. NOTE: support for multiple values not currently implemented.
     *
     * @param node the current persisted node
     * @param properties the new properties
     * @return the modified node
     */
    public Node updateProperties(Node node, List<NodeProperty> properties)
    {
        log.debug("updateProperties: " + node.getUri().getPath() + ", " + node.getClass().getSimpleName());
        expectPersistentNode(node);

        try
        {
            startTransaction();

            NodeID nodeID = (NodeID) node.appData;
            boolean doUpdateNode = false;         // check for props that are in the node table
            boolean doUpdateLastModified = false; // check for actual updates to prop table
            // Iterate through the user properties and the db properties,
            // potentially updating, deleting or adding new ones
            for (NodeProperty prop : properties)
            {
                boolean propTable = usePropertyTable(prop.getPropertyURI());
                NodeProperty cur = node.findProperty(prop.getPropertyURI());
                // Does this property exist already?
                log.debug("updateProperties: " + prop + " vs." + cur);
                if (cur != null)
                {
                    if (prop.isMarkedForDeletion())
                    {
                        if (propTable)
                        {
                            log.debug("doUpdateNode " + prop.getPropertyURI() + " to be deleted NodeProperty");
                            PropertyStatementCreator ppsc = new PropertyStatementCreator(nodeSchema, nodeID, prop);
                            jdbc.update(ppsc);
                            doUpdateLastModified = true;
                        }
                        else
                        {
                            log.debug("doUpdateNode " + prop.getPropertyURI() + " to be deleted in Node");
                            doUpdateNode = true;
                        }
                        node.getProperties().remove(prop);
                    }
                    else // update
                    {
                        String currentValue = cur.getPropertyValue();
                        log.debug("doUpdateNode " + prop.getPropertyURI() + ": " + currentValue + " != " + prop.getPropertyValue());
                        if (!currentValue.equals(prop.getPropertyValue()))
                        {
                            if (propTable)
                            {
                                log.debug("doUpdateNode " + prop.getPropertyURI() + " to be updated in NodeProperty");
                                PropertyStatementCreator ppsc = new PropertyStatementCreator(nodeSchema, nodeID, prop, true);
                                jdbc.update(ppsc);
                                doUpdateLastModified = true;
                            }
                            else
                            {
                                log.debug("doUpdateNode " + prop.getPropertyURI() + " to be updated in Node");
                                doUpdateNode = true;
                            }
                            cur.setValue(prop.getPropertyValue());
                        }
                        else
                        {
                            log.debug("Not updating node property: " + prop.getPropertyURI());
                        }
                    }
                }
                else
                {
                    if (propTable)
                    {
                        log.debug("doUpdateNode " + prop.getPropertyURI() + " to be inserted into NodeProperty");
                        PropertyStatementCreator ppsc = new PropertyStatementCreator(nodeSchema, nodeID, prop);
                        jdbc.update(ppsc);
                        doUpdateLastModified = true;
                    }
                    else
                    {
                        log.debug("doUpdateNode " + prop.getPropertyURI() + " to be inserted into Node");
                        doUpdateNode = true;
                    }
                    node.getProperties().add(prop);
                }
            }
            if (doUpdateNode || doUpdateLastModified)
            {
                log.debug("doUpdateNode: " + doUpdateNode + " doUpdateLastModified: " + doUpdateLastModified);
                NodePutStatementCreator npsc = new NodePutStatementCreator(nodeSchema, true);
                npsc.setValues(node,null);
                jdbc.update(npsc);
            }

            commitTransaction();
            log.debug("Node updated: " + node);
            return node;
        }
        catch (Throwable t)
        {
            log.error("Update rollback for node: " + node.getUri().getPath(), t);
            if (transactionStatus != null)
                try { rollbackTransaction(); }
                catch(Throwable oops) { log.error("failed to rollback transaction", oops); }
            throw new RuntimeException("failed to update properties:  " + node.getUri().getPath(), t);
        }
        finally
        {
            if (transactionStatus != null)
                try
                {
                    log.warn("updateProperties - BUG - transaction still open in finally... calling rollback");
                    rollbackTransaction();
                }
                catch(Throwable oops) { log.error("failed to rollback transaction in finally", oops); }
        }
    }

    /**
     * @param node
     * @param state
     */
    public void setBusyState(DataNode node, NodeBusyState state)
    {
        log.debug("setBusyState: " + node.getUri().getPath() + ", " + node.getClass().getSimpleName() + ", " + state);
        expectPersistentNode(node);
        
        try
        {
            startTransaction();
            String sql = getSetBusyStateSQL(node, state);
            log.debug(sql);
            jdbc.update(sql);
            commitTransaction();
            log.debug("Node busy state updated for: " + node);
        }
        catch (Throwable t)
        {
            log.error("Set busy state rollback for node: " + node, t);
            if (transactionStatus != null)
                try { rollbackTransaction(); }
                catch(Throwable oops) { log.error("failed to rollback transaction", oops); }
            throw new RuntimeException("failed to set busy state: " + node.getUri().getPath(), t);
        }
        finally
        {
            if (transactionStatus != null)
                try
                {
                    log.warn("setBusyState - BUG - transaction still open in finally... calling rollback");
                    rollbackTransaction();
                }
                catch(Throwable oops) { log.error("failed to rollback transaction in finally", oops); }
        }
    }


    /**
     * Move the node to the new path.
     *
     * @param node
     * @param destPath
     * @throws UnsupportedOperationException Until implementation is complete.
     */
    public void move(Node node, String destPath)
    {
        log.debug("move: " + node.getUri().getPath() + ", " + node.getClass().getSimpleName() + ", " + destPath);
        expectPersistentNode(node);
        throw new UnsupportedOperationException("Move not implemented.");
    }
    

    /**
     * Copy the node to the new path.
     *
     * @param node
     * @param destPath
     * @throws UnsupportedOperationException Until implementation is complete.
     */
    public void copy(Node node, String destPath)
    {
        log.debug("copy: " + node.getUri().getPath() + ", " + node.getClass().getSimpleName() + ", " + destPath);
        expectPersistentNode(node);
        throw new UnsupportedOperationException("Copy not implemented.");
    }
    
    /**
     * Extract the internal nodeID of the provided node from the appData field.
     * @param node
     * @return a nodeID or null for a non-persisted node
     */
    protected static Long getNodeID(Node node)
    {
        if (node == null || node.appData == null)
        {
            return null;
        }
        if (node.appData instanceof NodeID)
        {
            return ((NodeID) node.appData).getID();
        }
        return null;
    }
    
    /**
     * The resulting SQL must use a PreparedStatement with one argument
     * (child node name). The ResultSet can be processsed with a NodeMapper.
     *
     * @param parent
     * @return SQL prepared statement string
     */
    protected String getSelectChildNodeSQL(ContainerNode parent)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT nodeID, parentID, name, type, busyState, markedForDeletion, owner, isPublic, groupRead, groupWrite, ");
        sb.append("contentLength, contentType, contentEncoding, contentMD5, lastModified FROM ");
        sb.append(getNodeTableName());
        sb.append(" WHERE name = ?");
        sb.append(" AND parentID = ");
        sb.append(getNodeID(parent));
        sb.append(" AND markedForDeletion = 0");
        return sb.toString();
    }
    
    /**
     * The resulting SQL is a simple select statement. The ResultSet can be
     * processsed with a NodeMapper.
     *
     * @param parent The node to query for.
     * @return simple SQL statement select for use with NodeMapper
     */
    protected String getSelectNodesByParentSQL(Node parent)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT nodeID, parentID, name, type, busyState, markedForDeletion, owner, isPublic, groupRead, groupWrite, ");
        sb.append("contentLength, contentType, contentEncoding, contentMD5, lastModified FROM ");
        sb.append(getNodeTableName());
        sb.append(" WHERE parentID = ");
        sb.append(getNodeID(parent));
        sb.append(" AND markedForDeletion = 0");
        return sb.toString();
    }
    
    /**
     * The resulting SQL is a simple select statement. The ResultSet can be
     * processsed with a NodePropertyMapper.
     * 
     * @param node the node for which properties are queried
     * @return simple SQL string
     */
    protected String getSelectNodePropertiesByID(Node node)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT nodePropertyID, propertyURI, propertyValue FROM ");
        sb.append(getNodePropertyTableName());
        sb.append(" WHERE nodeID = ");
        sb.append(getNodeID(node));
        return sb.toString();
    }
    
    /**
     * @param node The node to delete
     * @return The SQL string for deleting the node from the database.
     */
    protected String getDeleteNodeSQL(Node node)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("DELETE FROM ");
        sb.append(getNodeTableName());
        sb.append(" WHERE nodeID = ");
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
        sb.append("DELETE FROM ");
        sb.append(getNodePropertyTableName());
        sb.append(" WHERE nodeID = ");
        sb.append(getNodeID(node));
        return sb.toString();
    }
    
    protected String getMarkNodeForDeletionSQL(Node node)
    {
        // need to rename as well so we don't get collisions later
        String newNodeName = node.getName() + "_" + Long.toString(new Date().getTime());

        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ");
        sb.append(getNodeTableName());
        sb.append(" SET");
        sb.append(" name='");
        sb.append(newNodeName);
        sb.append("',");
        sb.append(" markedForDeletion=1 WHERE nodeID = ");
        sb.append(getNodeID(node));
        return sb.toString();
    }
    
    protected String getSetBusyStateSQL(DataNode node, NodeBusyState state)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ");
        sb.append(getNodeTableName());
        sb.append(" SET busyState='");
        sb.append(state.getValue());
        sb.append("' WHERE nodeID = ");
        sb.append(getNodeID(node));
        return sb.toString();
    }
    
    protected String getUpdateContentLengthSQL(Node node, long difference)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ");
        sb.append(getNodeTableName());
        sb.append(" SET contentLength = (");
        sb.append("contentLength + " + difference);
        sb.append(") WHERE nodeID = ");
        sb.append(getNodeID(node));
        return sb.toString();
    }

    private static String[] NODE_SELECT_COLUMNS = new String[]
    {
        "nodeID",
        "name",
        "type",
        "busyState",
        "markedForDeletion",
        "isPublic",
        "owner",
        "contentLength",
        "contentType",
        "contentEncoding",
        "contentMD5",
        "lastModified",
        "groupRead",
        "groupWrite"
    };

    private static String[] NODE_PERSIST_COLUMNS = new String[]
    {
        "parentID",
        "name",
        "type",
        "busyState",
        "markedForDeletion",
        "isPublic",
        "owner",
        "contentLength",
        "contentType",
        "contentEncoding",
        "contentMD5",
        "lastModified",
        "groupRead",
        "groupWrite"
    };

    private static String getNodeType(Node node)
    {
        if (node instanceof DataNode)
            return NODE_TYPE_DATA;
        if (node instanceof ContainerNode)
            return NODE_TYPE_CONTAINER;
        throw new UnsupportedOperationException("unable to persist node type: " + node.getClass().getName());

    }
    private static String getBusyState(Node node)
    {
        if (node instanceof DataNode)
        {
            DataNode dn = (DataNode) node;
            if (dn.getBusy() != null)
                return dn.getBusy().getValue();
        }
        return VOS.NodeBusyState.notBusy.getValue();
    }
   
    private static void setPropertyValue(Node node, String uri, String value, boolean readOnly)
    {
        NodeProperty cur = node.findProperty(uri);
        if (cur == null)
        {
            cur = new NodeProperty(uri, value);
            node.getProperties().add(cur);
        }
        else
            cur.setValue(value);
        cur.setReadOnly(readOnly);
    }

    private static Set<String> coreProps;
    private boolean usePropertyTable(String uri)
    {
        if (coreProps == null)
        {
            // lazy init of the static set: thread-safe enough by
            // doing the assignment to the static last
            Set<String> core = new TreeSet<String>(new CaseInsensitiveStringComparator());

            core.add(VOS.PROPERTY_URI_ISPUBLIC);

            // note: very important that the node owner (creator property) is here
            core.add(VOS.PROPERTY_URI_CREATOR);

            core.add(VOS.PROPERTY_URI_CONTENTLENGTH);
            core.add(VOS.PROPERTY_URI_TYPE);
            core.add(VOS.PROPERTY_URI_CONTENTENCODING);
            core.add(VOS.PROPERTY_URI_CONTENTMD5);

            core.add(VOS.PROPERTY_URI_DATE);
            core.add(VOS.PROPERTY_URI_GROUPREAD);
            core.add(VOS.PROPERTY_URI_GROUPWRITE);
            coreProps = core;
        }
        return !coreProps.contains(uri);
    }

    private class PropertyStatementCreator implements PreparedStatementCreator
    {
        private NodeSchema ns;
        private boolean update;

        private NodeID nodeID;
        private NodeProperty prop;

        public PropertyStatementCreator(NodeSchema ns, NodeID nodeID, NodeProperty prop)
        {
            this(ns, nodeID, prop, false);
        }
        public PropertyStatementCreator(NodeSchema ns, NodeID nodeID, NodeProperty prop, boolean update)
        {
            this.ns = ns;
            this.nodeID = nodeID;
            this.prop = prop;
            this.update = update;
        }

        // if we care about caching the statement, we should look into prepared 
        // statement caching by the driver
        public PreparedStatement createPreparedStatement(Connection conn) throws SQLException
        {
            String sql;
            PreparedStatement prep;
            if (prop.isMarkedForDeletion())
                sql = getDeleteSQL();
            else if (update)
                sql = getUpdateSQL();
            else
                sql = getInsertSQL();
            log.debug(sql);
            prep = conn.prepareStatement(sql);
            setValues(prep);
            return prep;
        }

        void setValues(PreparedStatement ps)
            throws SQLException
        {
            int col = 1;
            if (prop.isMarkedForDeletion())
            {
                ps.setLong(col++, nodeID.getID());
                ps.setString(col++, prop.getPropertyURI());
                log.debug("setValues: " + nodeID.getID() + "," + prop.getPropertyURI());
            }
            else if (update)
            {
                ps.setString(col++, prop.getPropertyValue());
                ps.setLong(col++, nodeID.getID());
                ps.setString(col++, prop.getPropertyURI());
                log.debug("setValues: " + prop.getPropertyValue() + "," + nodeID.getID() + "," + prop.getPropertyURI());
            }
            else
            {
                ps.setLong(col++, nodeID.getID());
                ps.setString(col++, prop.getPropertyURI());
                ps.setString(col++, prop.getPropertyValue());
                log.debug("setValues: " + nodeID.getID() + "," + prop.getPropertyURI() + "," + prop.getPropertyValue());
            }
        }

        public String getSQL()
        {
            if (update)
                return getUpdateSQL();
            return getInsertSQL();
        }

        private String getInsertSQL()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("INSERT INTO ");
            sb.append(ns.propertyTable);
            sb.append(" (nodeID,propertyURI,propertyValue) VALUES (?, ?, ?)");
            return sb.toString();
        }
        private String getUpdateSQL()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("UPDATE ");
            sb.append(ns.propertyTable);
            sb.append(" SET propertyValue = ?");
            sb.append(" WHERE nodeID = ?");
            sb.append(" AND propertyURI = ?");
            return sb.toString();
        }
        private String getDeleteSQL()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("DELETE FROM ");
            sb.append(ns.propertyTable);
            sb.append(" WHERE nodeID = ?");
            sb.append(" AND propertyURI = ?");
            return sb.toString();
        }
    }

    private class NodePutStatementCreator implements PreparedStatementCreator
    {
        private NodeSchema ns;
        private boolean update;

        private Node node;
        private Subject owner;

        public NodePutStatementCreator(NodeSchema ns, boolean update)
        {
            this.ns = ns;
            this.update = update;
        }

        // if we care about caching the statement, we should look into prepared
        // statement caching by the driver
        public PreparedStatement createPreparedStatement(Connection conn) throws SQLException
        {
            PreparedStatement prep;
            if (owner == null)
            {
                String sql = getUpdateSQL();
                log.debug(sql);
                prep = conn.prepareStatement(sql);
            }
            else
            {
                String sql = getInsertSQL();
                log.debug(sql);
                prep = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            }
            setValues(prep);
            return prep;
        }

        public void setValues(Node node, Subject owner)
        {
            this.node = node;
            this.owner = owner;
        }
        
        void setValues(PreparedStatement ps)
            throws SQLException
        {
            StringBuilder sb = new StringBuilder();

            int col = 1;

            if (node.getParent() != null)
            {
                long v = getNodeID(node.getParent());
                ps.setLong(col, v);
                sb.append(v);
            }
            else
            {
                ps.setNull(col, Types.BIGINT);
                sb.append("null");
            }
            col++;
            sb.append(",");
            
            ps.setString(col++, node.getName());
            sb.append(node.getName());
            sb.append(",");

            ps.setString(col++, getNodeType(node));
            sb.append(getNodeType(node));
            sb.append(",");

            ps.setString(col++, getBusyState(node));
            sb.append(getBusyState(node));
            sb.append(",");

            ps.setBoolean(col++, node.isMarkedForDeletion());
            sb.append(node.isMarkedForDeletion());
            sb.append(",");

            ps.setBoolean(col++, node.isPublic());
            setPropertyValue(node, VOS.PROPERTY_URI_ISPUBLIC, Boolean.toString(node.isPublic()), false);
            sb.append(node.isPublic());
            sb.append(",");

            String pval;

            //String pval = node.getPropertyValue(VOS.PROPERTY_URI_CREATOR);
            //ps.setString(col++, pval);
            Subject theOwner = this.owner;
            if (theOwner == null && node.appData != null)
            {
                // get owner from NodeID
                NodeID nodeID = (NodeID) node.appData;
                theOwner = nodeID.getOwner();
            }
            if (theOwner == null)
                throw new IllegalStateException("cannot persist node without an owner");

            Object ownerObject  = identManager.toOwner(theOwner);
            int type = identManager.getOwnerType();
            ps.setObject(col++, ownerObject, type);
            sb.append(ownerObject);
            sb.append(",");

            pval = node.getPropertyValue(VOS.PROPERTY_URI_CONTENTLENGTH);
            if (pval != null)
            {
                ps.setLong(col, Long.valueOf(pval));
                sb.append(pval);
            }
            else
            {
                setPropertyValue(node, VOS.PROPERTY_URI_CONTENTLENGTH, "0", true);
                ps.setLong(col, 0);
                sb.append(0);
            }
            col++;
            sb.append(",");

            pval = node.getPropertyValue(VOS.PROPERTY_URI_TYPE);
            if (pval != null)
                ps.setString(col, pval);
            else
                ps.setNull(col, Types.VARCHAR);
            col++;
            sb.append(pval);
            sb.append(",");

            pval = node.getPropertyValue(VOS.PROPERTY_URI_CONTENTENCODING);
            if (pval != null)
                ps.setString(col, pval);
            else
                ps.setNull(col, Types.VARCHAR);
            col++;
            sb.append(pval);
            sb.append(",");

            pval = node.getPropertyValue(VOS.PROPERTY_URI_CONTENTMD5);
            if (pval != null)
                ps.setBytes(col, HexUtil.toBytes(pval));
            else
                ps.setNull(col, Types.VARBINARY);
            col++;
            sb.append(pval);
            sb.append(",");

            // always tweak the date
            Date now = new Date();
            setPropertyValue(node, VOS.PROPERTY_URI_DATE, dateFormat.format(now), true);
            //java.sql.Date dval = new java.sql.Date(now.getTime());
            Timestamp ts = new Timestamp(now.getTime());
            ps.setTimestamp(col, ts, cal);
            col++;
            sb.append(dateFormat.format(now));
            sb.append(",");

            pval = node.getPropertyValue(VOS.PROPERTY_URI_GROUPREAD);
            if (pval != null)
                ps.setString(col, pval);
            else
                ps.setNull(col, Types.VARCHAR);
            col++;
            sb.append(pval);
            sb.append(",");

            pval = node.getPropertyValue(VOS.PROPERTY_URI_GROUPWRITE);
            if (pval != null)
                ps.setString(col, pval);
            else
                ps.setNull(col, Types.VARCHAR);
            col++;
            sb.append(pval);
            
            if (update)
            {
                ps.setLong(col, getNodeID(node));
                sb.append(",");
                sb.append(getNodeID(node));
            }
            
            log.debug("setValues: " + sb);
        }

        String getSQL()
        {
            if (owner == null)
                return getUpdateSQL();
            return getInsertSQL();
        }

        private String getInsertSQL()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("INSERT INTO ");
            sb.append(ns.nodeTable);
            sb.append(" (");

            for (int c=0; c<NODE_PERSIST_COLUMNS.length; c++)
            {
                if (c > 0)
                    sb.append(",");
                sb.append(NODE_PERSIST_COLUMNS[c]);
            }
            sb.append(") VALUES (");
            for (int c=0; c<NODE_PERSIST_COLUMNS.length; c++)
            {
                if (c > 0)
                    sb.append(",");
                sb.append("?");
            }
            sb.append(")");
            return sb.toString();
        }
        private String getUpdateSQL()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("UPDATE ");
            sb.append(ns.nodeTable);

            // TODO: this is a very sybase=specific syntax and different from
            // postgresql, for example
            
            sb.append(" SET ");
            for (int c=0; c<NODE_PERSIST_COLUMNS.length; c++)
            {
                if (c > 0)
                    sb.append(",");
                sb.append(NODE_PERSIST_COLUMNS[c]);
                sb.append(" = ?");
            }
            sb.append(" WHERE nodeID = ?");
            return sb.toString();
        }
    }

    private class NodePathStatementCreator implements PreparedStatementCreator
    {
        private String[] path;
        private String nodeTablename;
        private String propTableName;

        public NodePathStatementCreator(String[] path, String nodeTablename, String propTableName)
        {
            this.path = path;
            this.nodeTablename = nodeTablename;
            this.propTableName = propTableName;
        }

        public PreparedStatement createPreparedStatement(Connection conn) throws SQLException
        {
            String sql = getSQL();
            log.debug("SQL: " + sql);

            PreparedStatement ret = conn.prepareStatement(sql);
            for (int i=0; i<path.length; i++)
                ret.setString(i+1, path[i]);
            return ret;
        }

        String getSQL()
        {
            StringBuffer sb = new StringBuffer();
            String acur = null;
            sb.append("SELECT ");
            for (int i=0; i<path.length; i++)
            {
                acur = "a"+i;
                if (i > 0)
                    sb.append(",");
                for (int c=0; c<NODE_SELECT_COLUMNS.length; c++)
                {
                    if (c > 0)
                        sb.append(",");
                    sb.append(acur);
                    sb.append(".");
                    sb.append(NODE_SELECT_COLUMNS[c]);
                }
                
            }

            // TODO: this part needs to be re-written when we move read props to
            // the NodeProperty table
            sb.append(" FROM ");
            String aprev;
            for (int i=0; i<path.length; i++)
            {
                aprev = acur;
                acur = "a"+i;
                if (i > 0)
                    sb.append(" JOIN ");
                sb.append(nodeTablename);
                sb.append(" AS ");
                sb.append(acur);
                if (i > 0)
                {
                    sb.append(" ON ");
                    sb.append(aprev);
                    sb.append(".nodeID=");
                    sb.append(acur);
                    sb.append(".parentID");
                }
            }

            sb.append(" WHERE ");
            for (int i=0; i<path.length; i++)
            {
                acur = "a"+i;
                if (i == 0) // root
                {
                    sb.append(acur);
                    sb.append(".parentID IS NULL");
                }
                sb.append(" AND ");
                sb.append(acur);
                sb.append(".name = ?");
                sb.append(" AND ");
                sb.append(acur);
                sb.append(".markedForDeletion = 0");
            }
            return sb.toString();
        }
    }

    private class NodePathExtractor implements ResultSetExtractor
    {
        
        private int columnsPerNode;

        public NodePathExtractor()
        {
            this.columnsPerNode = NODE_SELECT_COLUMNS.length;
        }

        public Object extractData(ResultSet rs)
            throws SQLException, DataAccessException
        {
            Node ret = null;
            ContainerNode root = null;
            String curPath = "";
            int numColumns = rs.getMetaData().getColumnCount();

            while ( rs.next() )
            {
                if (root == null) // reading first row completely
                {
                    log.debug("reading path from row 1");
                    int col = 1;
                    ContainerNode cur = null;
                    while (col < numColumns)
                    {
                        log.debug("readNode at " + col + ", path="+curPath);
                        Node n = readNode(rs, col, curPath);
                        log.debug("readNode: " + n.getUri());
                        curPath = n.getUri().getPath();
                        col += columnsPerNode;
                        ret = n; // always return the last node
                        if (root == null) // root container
                        {
                            cur = (ContainerNode) n;
                            root = cur;
                        }
                        else if (col < numColumns) // container in the path
                        {
                            ContainerNode cn = (ContainerNode) n;
                            cur.getNodes().add(cn);
                            cn.setParent(cur);
                            cur = cn;
                        }
                        else // last data node
                        {
                            cur.getNodes().add(n);
                            n.setParent(cur);
                        }
                    }
                }
                else
                    log.warn("found extra rows, expected only 0 or 1");
                /*
                else // reading extra group-read and group-write properties from join
                {
                    // NOTE: currently not needed since groupread and groupwrite are in main table
                    // and never multi-valued
                    int col = 1;
                    Node cur = root;
                    boolean done = false;
                    while (!done)
                    {
                        readProps(rs, col, cur);
                        col += columnsPerNode;
                        if (cur instanceof ContainerNode)
                        {
                            ContainerNode cn = (ContainerNode) cur;
                            cur = cn.getNodes().get(0);
                        }
                        else
                            done = true; // hit a non-container and col limit
                    }
                }
                */
            }
            return ret;
        }

        private Node readNode(ResultSet rs, int col, String basePath)
            throws SQLException
        {
            long nodeID = rs.getLong(col++);
            String name = rs.getString(col++);
            String type = rs.getString(col++);
            String busyString = getString(rs, col++);
            boolean markedForDeletion = rs.getBoolean(col++);
            boolean isPublic = rs.getBoolean(col++);

            //String owner = getString(rs, col++);
            Object ownerObject = rs.getObject(col++);
            Subject subject = identManager.toSubject(ownerObject);
            String owner = identManager.toOwnerString(subject);

            long contentLength = rs.getLong(col++);
            String contentType = getString(rs, col++);
            String contentEncoding = getString(rs, col++);
            Object contentMD5 = rs.getObject(col++);
            Date lastModified = rs.getTimestamp(col++, cal);
            String groupRead = getString(rs, col++);
            String groupWrite = getString(rs, col++);
            
            String path = basePath + "/" + name;
            VOSURI vos;
            try { vos = new VOSURI(new URI("vos", authority, path, null, null)); }
            catch(URISyntaxException bug)
            {
                throw new RuntimeException("BUG - failed to create vos URI", bug);
            }

            Node node;
            if (NODE_TYPE_CONTAINER.equals(type))
            {
                node = new ContainerNode(vos);
            }
            else if (NODE_TYPE_DATA.equals(type))
            {
                node = new DataNode(vos);
                ((DataNode) node).setBusy(NodeBusyState.getStateFromValue(busyString));
            }
            else
            {
                throw new IllegalStateException("Unknown node database type: " + type);
            }

            node.appData = new NodeID(nodeID, subject);

            node.setMarkedForDeletion(markedForDeletion);

            node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, Long.toString(contentLength)));

            if (contentType != null)
            {
                node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_TYPE, contentType));
            }
            if (contentEncoding != null)
            {
                node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTENCODING, contentEncoding));
            }
            if (contentMD5 != null && contentMD5 instanceof byte[])
            {
                String contentMD5String = HexUtil.toHex((byte[]) contentMD5);
                node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTMD5, contentMD5String));
            }
            if (lastModified != null)
            {
                node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_DATE, dateFormat.format(lastModified)));
            }
            if (groupRead != null)
            {
                node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_GROUPREAD, groupRead));
            }
            if (groupWrite != null)
            {
                node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_GROUPWRITE, groupWrite));
            }
            if (owner != null)
            {
                node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CREATOR, owner));
            }
            node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, Boolean.toString(isPublic)));

            // set the read-only flag on the properties
            for (String propertyURI : VOS.READ_ONLY_PROPERTIES)
            {
                int propertyIndex = node.getProperties().indexOf(new NodeProperty(propertyURI, null));
                if (propertyIndex != -1)
                {
                    node.getProperties().get(propertyIndex).setReadOnly(true);
                }
            }

            return node;
        }

        // pull out group-read and group-write props at the specified offset
        private void readProps(ResultSet rs, int col, Node n)
            throws SQLException
        {
            int c1 = col + NODE_SELECT_COLUMNS.length -3;
            int c2 = col + NODE_SELECT_COLUMNS.length -2;
            String gr = getString(rs, c1);
            String gw = getString(rs, c2);
            log.debug("column " + c1 + " group-read: " + gr);
            log.debug("column " + c2 + " group-write: " + gw);
            List<NodeProperty> props = n.getProperties();
            if (gr != null)
                props.add(new NodeProperty(VOS.PROPERTY_URI_GROUPREAD, gr));
            if (gw != null)
                props.add(new NodeProperty(VOS.PROPERTY_URI_GROUPWRITE, gw));
        }

        private String getString(ResultSet rs, int col)
            throws SQLException
        {
            String ret = rs.getString(col);
            if (ret != null)
            {
                ret = ret.trim();
                if (ret.length() == 0)
                ret = null;
            }
            return ret;
        }
    }
}
