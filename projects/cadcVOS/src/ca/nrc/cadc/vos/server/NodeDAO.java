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
*  with OpenCADC.  If not, sesrc/jsp/index.jspe          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 4 $
*
************************************************************************
*/

package ca.nrc.cadc.vos.server;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.security.auth.Subject;
import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import ca.nrc.cadc.auth.IdentityManager;
import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.db.DBUtil;
import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.util.CaseInsensitiveStringComparator;
import ca.nrc.cadc.util.FileMetadata;
import ca.nrc.cadc.util.HexUtil;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.LinkNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOS.NodeBusyState;
import ca.nrc.cadc.vos.VOSURI;

/**
 * Helper class for implementing NodePersistence with a
 * relational database back end for metadata. This class is
 * NOT thread-safe and the caller must instantiate a new one
 * in each thread (e.g. app-server request thread).
 *
 * @author majorb
 */
public class NodeDAO
{
    private static Logger log = Logger.getLogger(NodeDAO.class);
    private static final int CHILD_BATCH_SIZE = 1000;

    // temporarily needed by NodeMapper
    static final String NODE_TYPE_DATA = "D";
    static final String NODE_TYPE_CONTAINER = "C";
    static final String NODE_TYPE_LINK = "L";
    
    private static final int NODE_NAME_COLUMN_SIZE = 256;
    private static final int NODE_PROPERTY_COLUMN_SIZE = 700;

    // Database connection.
    protected DataSource dataSource;
    protected NodeSchema nodeSchema;
    protected String authority;
    protected IdentityManager identManager;
    protected String deletedNodePath;

    protected JdbcTemplate jdbc;
    private DataSourceTransactionManager transactionManager;
    private DefaultTransactionDefinition defaultTransactionDef;
    private DefaultTransactionDefinition dirtyReadTransactionDef;
    private TransactionStatus transactionStatus;

    // reusable object for recursive admin methods
    private NodePutStatementCreator adminStatementCreator;
    
    // instrument for unit tests of admin methods
    int numTxnStarted = 0;
    int numTxnCommitted = 0;

    private DateFormat dateFormat;
    private Calendar cal;

    private Map<Object,Subject> identityCache = new HashMap<Object,Subject>();

    public static class NodeSchema
    {
        public String nodeTable;
        public String propertyTable;
        boolean limitWithTop;
        boolean fileMetadataWritable;

        public String deltaIndexName;

        /**
         * Constructor for specifying the table where Node(s) and NodeProperty(s) are
         * stored.
         * @param nodeTable fully qualified name of node table
         * @param propertyTable fully qualified name of property table
         * @param limitWithTop - true if the RDBMS uses TOP, false for LIMIT
         * @param fileMetadataWritable true if the contentLength and contentMD5 properties
         * are writable, false if they are read-only in the DB
         */
        public NodeSchema(String nodeTable, String propertyTable,
                boolean limitWithTop,
                boolean fileMetadataWritable)
        {
            this.nodeTable = nodeTable;
            this.propertyTable = propertyTable;
            this.limitWithTop = limitWithTop;
            this.fileMetadataWritable = fileMetadataWritable;
        }
        
    }
    private static String[] NODE_COLUMNS = new String[]
    {
        "parentID", // FK, for join to parent
        "name",
        "type",
        "busyState",
        "isPublic",
        "ownerID",
        "creatorID",
        "groupRead",
        "groupWrite",
        "lastModified",
        // semantic file metadata
        "contentType",
        "contentEncoding",
        // LinkNode uri
        "link",
        // physical file metadata
        "nodeSize",
        "contentLength",
        "contentMD5"
    };

    /**
     * NodeDAO Constructor. This class was developed and tested using a
     * Sybase ASE RDBMS. Some SQL (update commands in particular) may be non-standard.
     *
     * @param dataSource
     * @param nodeSchema
     * @param authority
     * @param identManager 
     */
    public NodeDAO(DataSource dataSource, NodeSchema nodeSchema, 
            String authority, IdentityManager identManager, String deletedNodePath)
    {
        this.dataSource = dataSource;
        this.nodeSchema = nodeSchema;
        this.authority = authority;
        this.identManager = identManager;
        this.deletedNodePath = deletedNodePath;

        this.defaultTransactionDef = new DefaultTransactionDefinition();
        defaultTransactionDef.setIsolationLevel(DefaultTransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.dirtyReadTransactionDef = new DefaultTransactionDefinition();
        dirtyReadTransactionDef.setIsolationLevel(DefaultTransactionDefinition.ISOLATION_READ_UNCOMMITTED);
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactionManager = new DataSourceTransactionManager(dataSource);

        this.dateFormat = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
        this.cal = Calendar.getInstance(DateUtil.UTC);
    }

    // convenience during refactor
    protected String getNodeTableName()
    {
        return nodeSchema.nodeTable;
    }

    // convenience during refactor
    protected String getNodePropertyTableName()
    {
        return nodeSchema.propertyTable;
    }
    
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
        numTxnStarted++;
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
        numTxnCommitted++;
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
    public Node getPath(String path) throws TransientException
    {
    	return this.getPath(path, false);
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
     * @param allowPartialPath
     * @return the last node in the path, with all parents or null if not found
     */
    public Node getPath(String path, boolean allowPartialPath) throws TransientException
    {
        log.debug("getPath: " + path);
        if (path.length() > 0 && path.charAt(0) == '/')
            path = path.substring(1);
        // generate single join query to extract path
        NodePathStatementCreator npsc = new NodePathStatementCreator(
                path.split("/"), getNodeTableName(), getNodePropertyTableName(), allowPartialPath);

        TransactionStatus dirtyRead = null;
        try
        {
            dirtyRead = transactionManager.getTransaction(dirtyReadTransactionDef);
            // execute query with NodePathExtractor
            Node ret = (Node) jdbc.query(npsc, new NodePathExtractor());
            transactionManager.commit(dirtyRead);
            dirtyRead = null;
            
            // for non-LinkNode, 
            if ((ret != null) && !(ret.getUri().getPath().equals("/" + path)))
            {
            	if (!(ret instanceof LinkNode))
            		ret = null;
            }
            
            loadSubjects(ret);
            return ret;
        }
        catch(Throwable t)
        {
            log.error("rollback dirtyRead for node: " + path, t);
            try 
            {
                transactionManager.rollback(dirtyRead);
                dirtyRead = null;
            }
            catch(Throwable oops) { log.error("failed to dirtyRead rollback transaction", oops); }
            
            if (DBUtil.isTransientDBException(t))
                throw new TransientException("failed to get node " + path, t);
            else
                throw new RuntimeException("failed to get node: " + path, t);
        }
        finally
        {
            if (dirtyRead != null)
                try
                {
                    log.warn("put: BUG - dirtyRead transaction still open in finally... calling rollback");
                    transactionManager.rollback(dirtyRead);
                }
                catch(Throwable oops) { log.error("failed to rollback dirtyRead transaction in finally", oops); }
        }
    }

    /**
     * Load all the properties for the specified Node.
     * 
     * @param node
     */
    public void getProperties(Node node) throws TransientException
    {
        log.debug("getProperties: " + node.getUri().getPath() + ", " + node.getClass().getSimpleName());
        expectPersistentNode(node);

        log.debug("getProperties: " + node.getUri().getPath() + ", " + node.getClass().getSimpleName());
        String sql = getSelectNodePropertiesByID(node);
        log.debug("getProperties: " + sql);

        TransactionStatus dirtyRead = null;
        try
        {
            dirtyRead = transactionManager.getTransaction(dirtyReadTransactionDef);
            List<NodeProperty> props = jdbc.query(sql, new NodePropertyMapper());
            node.getProperties().addAll(props);
            transactionManager.commit(dirtyRead);
            dirtyRead = null;
        }
        catch(Throwable t)
        {
            log.error("rollback dirtyRead for node: " + node.getUri().getPath(), t);
            try
            {
                transactionManager.rollback(dirtyRead);
                dirtyRead = null;
            }
            catch(Throwable oops) { log.error("failed to dirtyRead rollback transaction", oops); }
            
            if (DBUtil.isTransientDBException(t))
                throw new TransientException("failed to get node: " + node.getUri().getPath(), t);
            else
                throw new RuntimeException("failed to get node: " + node.getUri().getPath(), t);
        }
        finally
        {
            if (dirtyRead != null)
                try
                {
                    log.warn("put: BUG - dirtyRead transaction still open in finally... calling rollback");
                    transactionManager.rollback(dirtyRead);
                }
                catch(Throwable oops) { log.error("failed to rollback dirtyRead transaction in finally", oops); }
        }
    }

    /**
     * Load a single child node of the specified container.
     * 
     * @param parent
     * @param name
     */
    public void getChild(ContainerNode parent, String name) throws TransientException
    {
        log.debug("getChild: " + parent.getUri().getPath() + ", " + name);
        expectPersistentNode(parent);

        String sql = getSelectChildNodeSQL(parent);
        log.debug("getChild: " + sql);

        TransactionStatus dirtyRead = null;
        try
        {
            dirtyRead = transactionManager.getTransaction(dirtyReadTransactionDef);
            List<Node> nodes = jdbc.query(sql, new Object[] { name },
                new NodeMapper(authority, parent.getUri().getPath()));
            
            transactionManager.commit(dirtyRead);
            dirtyRead = null;

            if (nodes.size() > 1)
                throw new IllegalStateException("BUG - found " + nodes.size() + " child nodes named " + name
                    + " for container " + parent.getUri().getPath());
            
            loadSubjects(nodes);
            addChildNodes(parent, nodes);
        }
        catch(Throwable t)
        {
            log.error("rollback dirtyRead for node: " + parent.getUri().getPath(), t);
            try
            {
                transactionManager.rollback(dirtyRead);
                dirtyRead = null;
            }
            catch(Throwable oops) { log.error("failed to dirtyRead rollback transaction", oops); }
            
            if (DBUtil.isTransientDBException(t))
                throw new TransientException("failed to get node: " + parent.getUri().getPath(), t);
            else
                throw new RuntimeException("failed to get node: " + parent.getUri().getPath(), t);
        }
        finally
        {
            if (dirtyRead != null)
                try
                {
                    log.warn("put: BUG - dirtyRead transaction still open in finally... calling rollback");
                    transactionManager.rollback(dirtyRead);
                }
                catch(Throwable oops) { log.error("failed to rollback dirtyRead transaction in finally", oops); }
        }
    }

    /**
     * Load all the child nodes of the specified container.
     *
     * @param parent
     */
    public void getChildren(ContainerNode parent) throws TransientException
    {
        log.debug("getChildren: " + parent.getUri().getPath() + ", " + parent.getClass().getSimpleName());
        getChildren(parent, null, null);
    }
    
    /**
     * Loads some of the child nodes of the specified container.
     * @param parent
     * @param start
     * @param limit
     */
    public void getChildren(ContainerNode parent, VOSURI start, Integer limit) throws TransientException
    {
        log.debug("getChildren: " + parent.getUri().getPath() + ", " + parent.getClass().getSimpleName());
        expectPersistentNode(parent);

        Object[] args = null;
        if (start != null)
            args = new Object[] { start.getName() };
        else
            args = new Object[0];
        
        // we must re-run the query in case server-side content changed since the argument node
        // was called, e.g. from delete(node) or markForDeletion(node)
        String sql = getSelectNodesByParentSQL(parent, limit, (start!=null));
        log.debug("getChildren: " + sql);

        TransactionStatus dirtyRead = null;
        try
        {
            dirtyRead = transactionManager.getTransaction(dirtyReadTransactionDef);
            List<Node> nodes = jdbc.query(sql,  args,
                new NodeMapper(authority, parent.getUri().getPath()));
            transactionManager.commit(dirtyRead);
            dirtyRead = null;

            loadSubjects(nodes);
            addChildNodes(parent, nodes);
        }
        catch(Throwable t)
        {
            log.error("rollback dirtyRead for node: " + parent.getUri().getPath(), t);
            try
            {
                transactionManager.rollback(dirtyRead);
                dirtyRead = null;
            }
            catch(Throwable oops) { log.error("failed to dirtyRead rollback transaction", oops); }
            
            if (DBUtil.isTransientDBException(t))
                throw new TransientException("failed to get node: " + parent.getUri().getPath(), t);
            else
                throw new RuntimeException("failed to get node: " + parent.getUri().getPath(), t);
        }
        finally
        {
            if (dirtyRead != null)
                try
                {
                    log.warn("put: BUG - dirtyRead transaction still open in finally... calling rollback");
                    transactionManager.rollback(dirtyRead);
                }
                catch(Throwable oops) { log.error("failed to rollback dirtyRead transaction in finally", oops); }
        }
    }

    /**
     * Add the provided children to the parent.
     */
    private void addChildNodes(ContainerNode parent, List<Node> nodes)
    {
        if (parent.getNodes().isEmpty())
        {
            for (Node n : nodes)
            {
                log.debug("adding child to list: " + n.getUri().getPath());
                parent.getNodes().add(n);
                n.setParent(parent);
            }
        }
        else
        {
            // 'nodes' will not have duplicates, but 'parent.getNodes()' may
            // already contain some of 'nodes'.
            List<Node> existingChildren = new ArrayList<Node>(parent.getNodes().size());
            existingChildren.addAll(parent.getNodes());
            for (Node n : nodes)
            {
                if (!existingChildren.contains(n))
                {
                    log.debug("adding child to list: " + n.getUri().getPath());
                    n.setParent(parent);
                    parent.getNodes().add(n);
                }
                else
                    log.debug("child already in list, not adding: " + n.getUri().getPath());
            }
        }
    }
    
    private void loadSubjects(List<Node> nodes)
    {
        for (Node n : nodes)
            loadSubjects(n);
    }
    
    private void loadSubjects(Node node)
    {
        if (node == null || node.appData == null)
            return;

        NodeID nid = (NodeID) node.appData;
        if (nid.owner != null)
            return; // already loaded (parent loop below)

        Subject s = identityCache.get(nid.ownerObject);
        if (s == null)
        {
            log.debug("lookup subject for owner=" + nid.ownerObject);
            s = identManager.toSubject(nid.ownerObject);
            identityCache.put(nid.ownerObject, s);
        }
        else
            log.debug("found cached subject for owner=" + nid.ownerObject);
        nid.owner = s;
        String owner = identManager.toOwnerString(nid.owner);
        if (owner != null)
            node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CREATOR, owner));
        Node parent = node.getParent();
        while (parent != null)
        {
            loadSubjects(parent);
            parent = parent.getParent();
        }
    }

    /**
     * Store the specified node. The node must be attached to a parent container and
     * the parent container must have already been persisted.
     * 
     * @param node
     * @param creator
     * @return the same node but with generated internal ID set in the appData field
     */
    public Node put(Node node, Subject creator) throws TransientException
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
            // call IdentityManager outside resource lock to avoid deadlock
            NodeID nodeID = new NodeID();
            nodeID.owner = creator;
            nodeID.ownerObject = identManager.toOwner(creator);
            node.appData = nodeID;

            startTransaction();
            NodePutStatementCreator npsc = new NodePutStatementCreator(nodeSchema, false);
            npsc.setValues(node, null);
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(npsc, keyHolder);
            nodeID.id = new Long(keyHolder.getKey().longValue());
            
            Iterator<NodeProperty> propertyIterator = node.getProperties().iterator();
            while (propertyIterator.hasNext())
            {
                NodeProperty prop = propertyIterator.next();
                
                if (prop.getPropertyValue() != null && prop.getPropertyValue().length() > NODE_PROPERTY_COLUMN_SIZE)
                    throw new IllegalArgumentException("length of node property value exceeds limit ("+NODE_PROPERTY_COLUMN_SIZE+"): " + prop.getPropertyURI());
                
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

            node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CREATOR, identManager.toOwnerString(creator)));
            if (node instanceof ContainerNode)
                node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, Long.toString(0)));
            return node;
        }
        catch(Throwable t)
        {
            log.error("rollback for node: " + node.getUri().getPath(), t);
            try { rollbackTransaction(); }
            catch(Throwable oops) { log.error("failed to rollback transaction", oops); }
            
            if (DBUtil.isTransientDBException(t))
                throw new TransientException("failed to persist node: " + node.getUri(), t);
            else
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
    public void delete(Node node) throws TransientException
    {
        log.debug("delete: " + node.getUri().getPath() + ", " + node.getClass().getSimpleName());
        expectPersistentNode(node);

        try
        {
            if (node instanceof ContainerNode)
            {
                ContainerNode dest = (ContainerNode) getPath(deletedNodePath);
                // need a unique name under /deletedPath
                String idName = getNodeID(node) + "-" + node.getName();
                node.setName(idName);
                // move handles the transaction, container size changes, rename, and reparent
                move(node, dest);
            }
            else
            {
                startTransaction();

                // lock the child
                String sql = getUpdateLockSQL(node);
                jdbc.update(sql);
                
                if (node instanceof DataNode)
                {
                    // get the nodeSize value
                    sql = getSelectNodeSizeSQL(node);
                    
                    // Note: if node size is null, the jdbc template
                    // will return zero.
                    Long nodeSize = jdbc.queryForLong(sql);
	                    
                    // delete the node only if it is not busy
                    deleteNode(node, true); 
	
                    // apply the negative nodeSize to the parent
                    sql = this.getApplyNodeSizeSQL(node.getParent(), nodeSize, false);
                    log.debug(sql);
                    jdbc.update(sql);
                }
                else if (node instanceof LinkNode)
                {
                    // delete the node
                    deleteNode(node, false);
                }
                else
                	throw new RuntimeException("BUG - unsupported node type: " + node.getClass());

                commitTransaction();
            }
            log.debug("Node deleted: " + node.getUri().getPath());
        }
        catch (Throwable t)
        {
            log.error("delete rollback for node: " + node.getUri().getPath(), t);
            if (transactionStatus != null)
                try { rollbackTransaction(); }
                catch(Throwable oops) { log.error("failed to rollback transaction", oops); }
            
            if (t instanceof IllegalStateException)
                throw (IllegalStateException) t;
            else if (DBUtil.isTransientDBException(t))
                throw new TransientException("failed to delete " + node.getUri().getPath(), t);
            else
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

    private void deleteNode(Node node, boolean notBusyOnly)
    {
        // delete properties: FK constraint -> node
        String sql = getDeleteNodePropertiesSQL(node);
        log.debug(sql);
        jdbc.update(sql);
        
        // delete the node
        sql = getDeleteNodeSQL(node, notBusyOnly); // only delete if non-busy
        log.debug(sql);
        int count = jdbc.update(sql);
        if (count == 0)
            throw new IllegalStateException("node busy or path changed during delete: "+node.getUri());
    }

    /**
     * Change the busy stateof a node from a known state to another.
     * 
     * @param node
     * @param state
     */
    public void setBusyState(DataNode node, NodeBusyState curState, NodeBusyState newState) throws TransientException
    {
        log.debug("setBusyState: " + node.getUri().getPath() + ", " + curState + " -> " + newState);
        expectPersistentNode(node);

        try
        {
            startTransaction();
            String sql = getSetBusyStateSQL(node, curState, newState);
            log.debug(sql);
            int num = jdbc.update(sql);
            if (num != 1)
                throw new IllegalStateException("setBusyState " + curState + " -> " + newState + " failed: " + node.getUri());
            commitTransaction();
        }
        catch (Throwable t)
        {
            log.error("Delete rollback for node: " + node.getUri().getPath(), t);
            if (transactionStatus != null)
                try { rollbackTransaction(); }
                catch(Throwable oops) { log.error("failed to rollback transaction", oops); }
            
            if (t instanceof IllegalStateException)
                throw (IllegalStateException) t;
            else if (DBUtil.isTransientDBException(t))
                throw new TransientException("failed to updateNodeMetadata " + node.getUri().getPath(), t);
            else
                throw new RuntimeException("failed to updateNodeMetadata " + node.getUri().getPath(), t);
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
     * Update the size of all nodes in the path a specified increment or
     * decrement, plus set BusyState of the DataNode to  not-busy.
     *
     * @param node
     * @param delta amount to increment (+) or decrement (-)
     */
    public void updateNodeMetadata(DataNode node, FileMetadata meta) throws TransientException
    {
        log.debug("updateNodeMetadata: " + node.getUri().getPath());

        expectPersistentNode(node);
        
        // old size is in the nodeSize column
        // new size is in meta.getContentLength() (NodeSchema.fileMetaWritable)
        // or already in contentLength column (!NodeSchema.fileMetaWritable)

        try
        {
            startTransaction();
            
            // update nodeSize, maybe contentLength and md5
            DataNodeUpdateStatementCreator dnup = new DataNodeUpdateStatementCreator(
                    getNodeID(node), meta.getContentLength(), meta.getMd5Sum());
            jdbc.update(dnup);

            // last, update the busy state of the target node
            String trans = getSetBusyStateSQL(node, NodeBusyState.busyWithWrite, NodeBusyState.notBusy);
            log.debug(trans);
            int num = jdbc.update(trans);
            if (num != 1)
                throw new IllegalStateException("updateFileMetadata requires a node with busyState=W: "+node.getUri());

            // now safe to update properties of the target node
            List<NodeProperty> props = new ArrayList<NodeProperty>();
            NodeProperty np;

            np = findOrCreate(node, VOS.PROPERTY_URI_CONTENTENCODING, meta.getContentEncoding());
            if (np != null)
                props.add(np);
            np = findOrCreate(node, VOS.PROPERTY_URI_TYPE, meta.getContentType());
            if (np != null)
                props.add(np);

            doUpdateProperties(node, props);
           
            commitTransaction();
        }
        catch (Throwable t)
        {
            log.error("updateNodeMetadata rollback for node: " + node.getUri().getPath(), t);
            if (transactionStatus != null)
                try { rollbackTransaction(); }
                catch(Throwable oops) { log.error("failed to rollback transaction", oops); }
            
            if (t instanceof IllegalStateException)
                throw (IllegalStateException) t;
            else if (DBUtil.isTransientDBException(t))
                throw new TransientException("failed to updateNodeMetadata " + node.getUri().getPath(), t);
            else
                throw new RuntimeException("failed to updateNodeMetadata " + node.getUri().getPath(), t);
            
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

    // find existing prop, mark for delete if value is null or set value
    // create new prop with value
    private NodeProperty findOrCreate(Node node, String uri, String value)
    {
        NodeProperty np = node.findProperty(uri);
        if (np == null && value == null)
            return null;

        if (value == null)
            np.setMarkedForDeletion(true);
        else if (np == null)
            np = new NodeProperty(uri, value);
        else
            np.setValue(value);

        return np;
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
    public Node updateProperties(Node node, List<NodeProperty> properties) throws TransientException
    {
        log.debug("updateProperties: " + node.getUri().getPath() + ", " + node.getClass().getSimpleName());
        expectPersistentNode(node);

        try
        {
            startTransaction();

            Node ret = doUpdateProperties(node, properties);

            commitTransaction();

            return ret;
        }
        catch (Throwable t)
        {
            log.error("Update rollback for node: " + node.getUri().getPath(), t);
            if (transactionStatus != null)
                try { rollbackTransaction(); }
                catch(Throwable oops) { log.error("failed to rollback transaction", oops); }
            
            if (DBUtil.isTransientDBException(t))
                throw new TransientException("failed to update properties:  " + node.getUri().getPath(), t);
            else
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

    private Node doUpdateProperties(Node node, List<NodeProperty> properties)
    {
        NodeID nodeID = (NodeID) node.appData;

        // Iterate through the user properties and the db properties,
        // potentially updating, deleting or adding new ones
        List<PropertyStatementCreator> updates = new ArrayList<PropertyStatementCreator>();
        for (NodeProperty prop : properties)
        {
            boolean propTable = usePropertyTable(prop.getPropertyURI());
            NodeProperty cur = node.findProperty(prop.getPropertyURI());
            // Does this property exist already?
            log.debug("updateProperties: " + prop + " vs. " + cur);

            if (cur != null)
            {
                if (prop.isMarkedForDeletion())
                {
                    if (propTable)
                    {
                        log.debug("doUpdateNode " + prop.getPropertyURI() + " to be deleted from NodeProperty");
                        PropertyStatementCreator ppsc = new PropertyStatementCreator(nodeSchema, nodeID, prop);
                        updates.add(ppsc);
                    }
                    else
                    {
                        log.debug("doUpdateNode " + prop.getPropertyURI() + " to be set to null in Node");
                    }
                    boolean rm = node.getProperties().remove(prop);
                    log.debug("removed " + prop.getPropertyURI() + " from node: " + rm);
                }
                else // update
                {
                    String currentValue = cur.getPropertyValue();
                    if (!currentValue.equals(prop.getPropertyValue()))
                    {
                        
                        if (prop.getPropertyValue() != null && prop.getPropertyValue().length() > NODE_PROPERTY_COLUMN_SIZE)
                            throw new IllegalArgumentException("length of node property value exceeds limit ("+NODE_PROPERTY_COLUMN_SIZE+"): " + prop.getPropertyURI());
                        
                        log.debug("doUpdateNode " + prop.getPropertyURI() + ": "
                                + currentValue + " != " + prop.getPropertyValue());
                        if (propTable)
                        {
                            log.debug("doUpdateNode " + prop.getPropertyURI() + " to be updated in NodeProperty");
                            PropertyStatementCreator ppsc = new PropertyStatementCreator(nodeSchema, nodeID, prop, true);
                            updates.add(ppsc);
                        }
                        else
                        {
                            log.debug("doUpdateNode " + prop.getPropertyURI() + " to be updated in Node");
                        }
                        cur.setValue(prop.getPropertyValue());
                    }
                    else
                    {
                        log.debug("Value unchanged, not updating node property: " + prop.getPropertyURI());
                    }
                }
            }
            else
            {
                if (prop.getPropertyValue() != null && prop.getPropertyValue().length() > NODE_PROPERTY_COLUMN_SIZE)
                    throw new IllegalArgumentException("length of node property value exceeds limit ("+NODE_PROPERTY_COLUMN_SIZE+"): " + prop.getPropertyURI());
                
                if (propTable)
                {
                    log.debug("doUpdateNode " + prop.getPropertyURI() + " to be inserted into NodeProperty");
                    PropertyStatementCreator ppsc = new PropertyStatementCreator(nodeSchema, nodeID, prop);
                    updates.add(ppsc);
                }
                else
                {
                    log.debug("doUpdateNode " + prop.getPropertyURI() + " to be inserted into Node");
                }
                node.getProperties().add(prop);
            }
        }
        // OK: update Node, then NodeProperty(s)
        NodePutStatementCreator npsc = new NodePutStatementCreator(nodeSchema, true);
        npsc.setValues(node, null);
        jdbc.update(npsc);

        for (PropertyStatementCreator psc : updates)
            jdbc.update(psc);

        return node;
    }

    /**
     * Move the node to inside the destination container.
     *
     * @param src The node to move
     * @param dest The container in which to move the node.
     * @param subject The new owner for the moved node.
     */
    public void move(Node src, ContainerNode dest) throws TransientException
    {
        log.debug("move: " + src.getUri() + " to " + dest.getUri() + " as " + src.getName());
        expectPersistentNode(src);
        expectPersistentNode(dest);
        
        // move rule checking
        if (src instanceof ContainerNode)
        {
            // check that we are not moving root or a root container
            if (src.getParent() == null || src.getParent().getUri().isRoot())
                throw new IllegalArgumentException("Cannot move a root container.");
            
            // check that 'src' is not in the path of 'dest' so that
            // circular paths are not created
            Node target = dest;
            Long srcNodeID = getNodeID(src);
            Long targetNodeID = null;
            while (target != null && !target.getUri().isRoot())
            {
                targetNodeID = getNodeID(target);
                if (targetNodeID.equals(srcNodeID))
                    throw new IllegalArgumentException("Cannot move to a contained sub-node.");
                target = target.getParent();
            }
        }
        
        try
        {    
            startTransaction();

            // get the lock
            String sql = this.getUpdateLockSQL(src);
            jdbc.update(sql);
            
            Long nodeSize = new Long(0);
            if (!(src instanceof LinkNode))
            {
                // get the nodeSize
                sql = this.getSelectNodeSizeSQL(src);
                
                // Note: if nodeSize is null, jdbc template will return zero.
                nodeSize = jdbc.queryForLong(sql);
            }
            
            // re-parent the node
            ContainerNode srcParent = src.getParent();
            src.setParent(dest);

            // update the node with the new parent and potentially new name
            NodePutStatementCreator putStatementCreator = new NodePutStatementCreator(nodeSchema, true);
            putStatementCreator.setValues(src, null);
            int count = jdbc.update(putStatementCreator);
            if (count == 0)
            {
                // tried to move a busy data node
                throw new IllegalStateException("src node busy: "+src.getUri());
            }
            
            if (!(src instanceof LinkNode))
            {
                // apply the nodeSize
                String sql1 = getApplyNodeSizeSQL(srcParent, nodeSize, false);
                String sql2 = getApplyNodeSizeSQL(dest, nodeSize, true);
	            
                // these operations should happen in nodeID order for consistency
                // to avoid deadlocks
                if (getNodeID(src) > getNodeID(dest))
                {
                    String swap = sql1;
                    sql1 = sql2;
                    sql2 = swap;
                }
	            
                log.debug(sql1);
                jdbc.update(sql1);
                log.debug(sql2);
                jdbc.update(sql2);
            }
            
            // recursive chown removed since it is costly and nominally incorrect
            
            commitTransaction();
        }
        catch (Throwable t)
        {
            log.error("move rollback for node: " + src.getUri().getPath(), t);
            if (transactionStatus != null)
                try { rollbackTransaction(); }
                catch(Throwable oops) { log.error("failed to rollback transaction", oops); }
            
            if (t instanceof IllegalStateException)
                throw (IllegalStateException) t;
            else if (DBUtil.isTransientDBException(t))
                throw new TransientException("failed to move:  " + src.getUri().getPath(), t);
            else
                throw new RuntimeException("failed to move:  " + src.getUri().getPath(), t);
        }
        finally
        {
            if (transactionStatus != null)
            {
                try
                {
                    log.warn("move - BUG - transaction still open in finally... calling rollback");
                    rollbackTransaction();
                }
                catch(Throwable oops) { log.error("failed to rollback transaction in finally", oops); }
            }
        }
    }
    

    /**
     * Copy the node to the new path.
     *
     * @param node
     * @param destPath
     * @throws UnsupportedOperationException Until implementation is complete.
     */
    public void copy(Node src, ContainerNode dest) throws TransientException
    {
        log.debug("copy: " + src.getUri() + " to " + dest.getUri() + " as " + src.getName());
        throw new UnsupportedOperationException("Copy not implemented.");
    }
    
    // admin functions

    private int commitBatch(String name, int batchSize, int count, boolean dryrun)
    {
        if (!dryrun && count >= batchSize)
        {
            commitTransaction();
            log.info(name + " batch committed: " + count);
            count = 0;
            startTransaction();
        }
        return count;
    }

    /**
     * Recursively delete a container in one or more batchSize-d transactions. The
     * actual number of rows deleted per transaction is not exact since deletion of
     * node properties is done via a single delete statement.
     * </p><p>
     * Note: this method <b>does not</b> update parent container node sizes while
     * deleting nodes and should not be used on actual user content. It can also
     * fail at some point and have committed some deletions due to the batching;
     * the caller should resolve the issue and then call it again to continue
     * (delete is bottom up so there are never any orphans).
     * 
     * @param node
     * @param batchSize
     */
    void delete(Node node, int batchSize, boolean dryrun) throws TransientException
    {
        log.debug("delete: " + node.getUri().getPath() + "," + batchSize);
        expectPersistentNode(node);
        if (batchSize < 1)
            throw new IllegalArgumentException("batchSize must be positive");
        try
        {
            this.adminStatementCreator = new NodePutStatementCreator(nodeSchema, true);
            if (!dryrun)
                startTransaction();
            int count = deleteNode(node, batchSize, 0, dryrun);
            if (!dryrun)
            {
                commitTransaction();
                log.info("delete batch committed: " + count);
            }
        }
        catch (Throwable t)
        {
            log.error("chown rollback for node: " + node.getUri().getPath(), t);
            if (transactionStatus != null)
                try { rollbackTransaction(); }
                catch(Throwable oops) { log.error("failed to rollback transaction", oops); }
            
            if (DBUtil.isTransientDBException(t))
                throw new TransientException("failed to delete:  " + node.getUri().getPath(), t);
            else
                throw new RuntimeException("failed to delete:  " + node.getUri().getPath(), t);
        }
        finally
        {
            if (transactionStatus != null)
            {
                try
                {
                    log.warn("chown - BUG - transaction still open in finally... calling rollback");
                    rollbackTransaction();
                }
                catch(Throwable oops) { log.error("failed to rollback transaction in finally", oops); }
            }
        }
    }

    private int deleteNode(Node node, int batchSize, int count, boolean dryrun)
    {
        log.info("deleteNode: " + node.getClass().getSimpleName() + " " + node.getUri().getPath());
        // delete children: depth first so we don't leave orphans
        if (node instanceof ContainerNode)
        {
            ContainerNode cn = (ContainerNode) node;
            count = deleteChildren(cn, batchSize, count, dryrun);
        }

        // delete properties: so we don't violate FK constraint -> node
        String sql = getDeleteNodePropertiesSQL(node);
        log.debug(sql);
        if (!dryrun)
            count += jdbc.update(sql);

        // delete the node itself
        sql = getDeleteNodeSQL(node, false); // admin mode: ignore busy state
        log.debug(sql);
        if (!dryrun)
        {
            int num = jdbc.update(sql);
            if (num == 0)
                throw new IllegalStateException("node busy or path changed during delete: "+node.getUri());
            count += num;
        }
        count = commitBatch("delete", batchSize, count, dryrun);
        return count;
    }
    
    private int deleteChildren(ContainerNode container, int batchSize, int count, boolean dryrun)
    {
        String sql = getSelectNodesByParentSQL(container, CHILD_BATCH_SIZE, false);
        NodeMapper mapper = new NodeMapper(authority, container.getUri().getPath());
        List<Node> children = jdbc.query(sql, new Object[0], mapper);
        Object[] args = new Object[1];
        while (children.size() > 0)
        {
            Node cur = null;
            for (Node child : children)
            {
                cur = child;
                child.setParent(container);
                count = deleteNode(child, batchSize, count, dryrun);
                count = commitBatch("delete", batchSize, count, dryrun);
            }
            sql = getSelectNodesByParentSQL(container, CHILD_BATCH_SIZE, true);
            args[0] = cur.getName();
            children = jdbc.query(sql, args, mapper);
            children.remove(cur); // the query is name >= cur and we already processed cur
        }
        return count;
    }

    /**
     * Change ownership of a Node (optionally recursive) in one or more
     * batchSize-d transactions.
     * 
     * @param node
     * @param newOwner
     * @param recursive
     * @param batchSize
     */
    void chown(Node node, Subject newOwner, boolean recursive, int batchSize, boolean dryrun)
        throws TransientException
    {
        log.debug("chown: " + node.getUri().getPath() + ", " + newOwner + ", " + recursive + "," + batchSize);
        expectPersistentNode(node);
        if (batchSize < 1)
            throw new IllegalArgumentException("batchSize must be positive");
        try
        {
            Object newOwnerObj = identManager.toOwner(newOwner);
            this.adminStatementCreator = new NodePutStatementCreator(nodeSchema, true);
            if (!dryrun)
                startTransaction();
            int count = chownNode(node, newOwnerObj, recursive, batchSize, 0, dryrun);
            if (!dryrun)
                commitTransaction();
            log.debug("chown batch committed: " + count);
        }
        catch (Throwable t)
        {
            log.error("chown rollback for node: " + node.getUri().getPath(), t);
            if (transactionStatus != null)
                try { rollbackTransaction(); }
                catch(Throwable oops) { log.error("failed to rollback transaction", oops); }
            
            if (DBUtil.isTransientDBException(t))
                throw new TransientException("failed to chown:  " + node.getUri().getPath(), t);
            else
                throw new RuntimeException("failed to chown:  " + node.getUri().getPath(), t);
        }
        finally
        {
            if (transactionStatus != null)
            {
                try
                {
                    log.warn("chown - BUG - transaction still open in finally... calling rollback");
                    rollbackTransaction();
                }
                catch(Throwable oops) { log.error("failed to rollback transaction in finally", oops); }
            }
        }
    }

    
    private int chownNode(Node node, Object newOwnerObject, boolean recursive, int batchSize, int count, boolean dryrun)
    {
        // update the node with the specfied owner.
        adminStatementCreator.setValues(node, newOwnerObject);
        if (!dryrun)
            count += jdbc.update(adminStatementCreator);
        count = commitBatch("chown", batchSize, count, dryrun);
        if (recursive && (node instanceof ContainerNode))
            count = chownChildren((ContainerNode) node, newOwnerObject, batchSize, count, dryrun);
        return count;
    }

    private int chownChildren(ContainerNode container, Object newOwnerObj, int batchSize, int count, boolean dryrun)
    {
        String sql = null;
        sql = getSelectNodesByParentSQL(container, CHILD_BATCH_SIZE, false);
        NodeMapper mapper = new NodeMapper(authority, container.getUri().getPath());
        List<Node> children = jdbc.query(sql, new Object[0], mapper);
        Object[] args = new Object[1];
        while (children.size() > 0)
        {
            Node cur = null;
            for (Node child : children)
            {
                cur = child;
                child.setParent(container);
                count = chownNode(child, newOwnerObj, true, batchSize, count, dryrun);
            }
            sql = getSelectNodesByParentSQL(container,CHILD_BATCH_SIZE, true);
            args[0] = cur.getName();
            children = jdbc.query(sql, args, mapper);
            children.remove(cur); // the query is name >= cur and we already processed cur
        }
        return count;
    }
    
    /**
     * Admin function.
     * @param limit The maximum to return
     * @return A list of outstanding node size propagations
     */
    List<NodeSizePropagation> getOutstandingPropagations(int limit)
    {
        try
        {
            String sql = this.getFindOutstandingPropagationsSQL(limit);
            log.debug("getOutstandingPropagations (limit " + limit + "): " + sql);
            NodeSizePropagationExtractor propagationExtractor = new NodeSizePropagationExtractor();
            List<NodeSizePropagation> propagations = (List<NodeSizePropagation>) jdbc.query(sql, propagationExtractor);
            return propagations;
        }
        catch (Throwable t)
        {
            String message = "getOutstandingPropagations failed: " + t.getMessage();
            log.error(message, t);
            throw new RuntimeException(message, t);
        }
    }
    
    /**
     * Admin function.
     * @param propagation
     */
    void applyPropagation(NodeSizePropagation propagation) throws TransientException
    {
        log.debug("applyPropagation: " + propagation);
        try
        {
            startTransaction();
            
            // apply progagation updates
            String[] propagationSQL = getApplyDeltaSQL(propagation);
            for (String sql : propagationSQL)
            {
                log.debug(sql);
                jdbc.update(sql);
            }

            commitTransaction();
            log.debug("applyPropagation committed.");
        }
        catch (Throwable t)
        {
            log.error("applyPropagation rollback", t);
            if (transactionStatus != null)
                try { rollbackTransaction(); }
                catch(Throwable oops) { log.error("failed to rollback transaction", oops); }
            
            if (DBUtil.isTransientDBException(t))
                throw new TransientException("failed to apply propagation.", t);
            else
                throw new RuntimeException("failed to apply propagation.", t);
        }
        finally
        {
            if (transactionStatus != null)
            {
                try
                {
                    log.warn("applyPropagation - BUG - transaction still open in finally... calling rollback");
                    rollbackTransaction();
                }
                catch(Throwable oops) { log.error("failed to rollback transaction in finally", oops); }
            }
        }
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
        sb.append("SELECT nodeID");
        for (String col : NODE_COLUMNS)
        {
            sb.append(",");
            sb.append(col);
        }
        sb.append(" FROM ");
        sb.append(getNodeTableName());
        Long nid = getNodeID(parent);
        if (nid != null)
        {
            sb.append(" WHERE parentID = ");
            sb.append(getNodeID(parent));
        }
        else
            sb.append(" WHERE parentID IS NULL");
        sb.append(" AND name = ?");
        return sb.toString();
    }
    
    /**
     * The resulting SQL is a simple select statement. The ResultSet can be
     * processsed with a NodeMapper.
     *
     * @param parent The node to query for.
     * @param limit
     * @param withStart
     * @return simple SQL statement select for use with NodeMapper
     */
    protected String getSelectNodesByParentSQL(ContainerNode parent, Integer limit, boolean withStart)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT nodeID");
        for (String col : NODE_COLUMNS)
        {
            sb.append(",");
            sb.append(col);
        }
        sb.append(" FROM ");
        sb.append(getNodeTableName());
        Long nid = getNodeID(parent);
        if (nid != null)
        {
            sb.append(" WHERE parentID = ");
            sb.append(getNodeID(parent));
        }
        else
            sb.append(" WHERE parentID IS NULL");
        if (withStart)
            sb.append(" AND name >= ?");

        if (withStart || limit != null)
            sb.append(" ORDER BY name");

        if (limit != null)
        {

            if (nodeSchema.limitWithTop) // TOP, eg sybase
                sb.replace(0, 6, "SELECT TOP " + limit);
            else // LIMIT, eg postgresql
            {
                sb.append(" LIMIT ");
                sb.append(limit);
            }
        }
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
    protected String getDeleteNodeSQL(Node node, boolean notBusyOnly)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("DELETE FROM ");
        sb.append(getNodeTableName());
        sb.append(" WHERE nodeID = ");
        sb.append(getNodeID(node));
        sb.append(" AND parentID = ");
        sb.append(getNodeID(node.getParent()));
        if (notBusyOnly)
        {
            sb.append(" AND busyState = '");
            sb.append(VOS.NodeBusyState.notBusy.getValue());
            sb.append("'");
        }
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

    protected String getSetBusyStateSQL(DataNode node, NodeBusyState curState, NodeBusyState newState)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ");
        sb.append(getNodeTableName());
        sb.append(" SET busyState='");
        sb.append(newState.getValue());
        sb.append("', lastModified='");
        // always tweak the date
        Date now = new Date();
        setPropertyValue(node, VOS.PROPERTY_URI_DATE, dateFormat.format(now), true);
        Timestamp ts = new Timestamp(now.getTime());
        sb.append(dateFormat.format(now));
        sb.append("'");
        sb.append(" WHERE nodeID = ");
        sb.append(getNodeID(node));
        sb.append(" AND busyState='");
        sb.append(curState.getValue());
        sb.append("'");
        return sb.toString();
    }
    
    /**
     * @param node The node to delete
     * @return The SQL string for applying a negative or positive
     * delta to the parent of the target node.
     */
    protected String getApplyNodeSizeSQL(ContainerNode dest, long nodeSize, boolean increment)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ");
        sb.append(getNodeTableName());
        sb.append(" SET delta = coalesce(delta, 0) ");
        if (increment)
            sb.append("+ ");
        else
            sb.append("- ");
        sb.append(nodeSize);
        sb.append(" WHERE nodeID = ");
        sb.append(getNodeID(dest));
        return sb.toString();
    }
    
    protected String[] getApplyDeltaSQL(NodeSizePropagation propagation)
    {
        List<String> sql = new ArrayList<String>();
        Date now = new Date();
        
        // update 1 adjusts the child node size and resets
        // the delta to zero
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ");
        sb.append(getNodeTableName());
        sb.append(" SET nodeSize = coalesce(nodeSize, 0) + coalesce(delta, 0)");
        if (NODE_TYPE_CONTAINER.equals(propagation.getChildType()))
        {
            // tweak the date if a container node
            sb.append(", lastModified = '");
            sb.append(dateFormat.format(now));
            sb.append("'");
        }
        sb.append(" WHERE nodeID = ");
        sb.append(propagation.getChildID());
        sql.add(sb.toString());
        
        // update 2 adjusts the parent delta
        if (propagation.getParentID() != null)
        {
            sb = new StringBuilder();
            sb.append("UPDATE ");
            sb.append(getNodeTableName());
            sb.append(" SET delta = coalesce(delta, 0) + ");
            sb.append("(SELECT coalesce(delta, 0) FROM ");
            sb.append(getNodeTableName());
            sb.append(" WHERE nodeID = ");
            sb.append(propagation.getChildID());
            sb.append("), lastModified = '");
            sb.append(dateFormat.format(now));
            sb.append("'");
            sb.append(" WHERE nodeID = ");
            sb.append(propagation.getParentID());
            sql.add(sb.toString());
        }
        
        // update 3 resets the child delta
        sb = new StringBuilder();
        sb.append("UPDATE ");
        sb.append(getNodeTableName());
        sb.append(" SET delta = 0");
        sb.append(" WHERE nodeID = ");
        sb.append(propagation.getChildID());
        sql.add(sb.toString());
        
        return sql.toArray(new String[0]);
    }

    protected String[] getRootUpdateLockSQL(Node n1, Node n2)
    {
        Node root1 = n1;
        Node root2 = null;
        while (root1.getParent() != null)
            root1 = root1.getParent();
        if (n2 != null)
        {
            root2 = n2;
            while (root2.getParent() != null)
                root2 = root2.getParent();
        }
        
        return getUpdateLockSQL(root1, root2);
    }
    
    protected String[] getUpdateLockSQL(Node n1, Node n2)
    {
        Node[] nodes = null;
        Long id1 = getNodeID(n1);
        Long id2 = null;
        if (n2 != null)
            id2 = getNodeID(n2);
        
        if ( n2 == null || id1.compareTo(id2) == 0 ) // same node
            nodes = new Node[] { n1 };
        else if (id1.compareTo(id2) < 0)
            nodes = new Node[] { n1, n2 };
        else
            nodes = new Node[] { n2, n1 };
        
        String[] ret = new String[nodes.length];
        for (int i=0; i<nodes.length; i++)
        {
            ret[i] = getUpdateLockSQL(nodes[i]);
        }
        return ret;
    }
    
    protected String getUpdateLockSQL(Node node)
    {
        Long id = getNodeID(node);
        String type = getNodeType(node);

        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ");
        sb.append(getNodeTableName());
        sb.append(" SET type='");
        sb.append(type);
        sb.append("' WHERE nodeID = ");
        sb.append(id);
        return sb.toString();
    }
    
    // apply delta to Node.nodeSize (set if NULL)
//    protected String getUpdateNodeSizeSQL(Node node, long delta)
//    {
//        // update Node set nodeSize=(case when nodeSize is null then diff else nodeSize+diff end) where nodeID=?
//        StringBuilder sb = new StringBuilder();
//        sb.append("UPDATE ");
//        sb.append(getNodeTableName());
//        sb.append(" SET nodeSize = ");
//        sb.append("(CASE WHEN nodeSize IS NULL THEN ");
//        sb.append(Long.toString(delta));
//        sb.append(" ELSE ");
//        sb.append("nodeSize + ");
//        sb.append(Long.toString(delta));
//        sb.append(" END) WHERE nodeID = ");
//        sb.append(getNodeID(node));
//        // force check of parent to detect path changes
//        if (node.getParent() != null)
//        {
//            sb.append(" AND parentID = ");
//            sb.append(getNodeID(node.getParent()));
//        }
//        else
//            sb.append(" AND parentID IS NULL");
//
//        return sb.toString();
//    }

    protected String getMoveNodeSQL(Node src, ContainerNode dest, String name)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ");
        sb.append(getNodeTableName());
        sb.append(" SET parentID = ");
        sb.append(getNodeID(dest));
        sb.append(", name = '");
        sb.append(name);
        sb.append("' WHERE nodeID = ");
        sb.append(getNodeID(src));
        return sb.toString();
    }
    
    protected String getFindOutstandingPropagationsSQL(int limit)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT");
        sb.append(" nodeID, type, parentID FROM ");
        sb.append(getNodeTableName());
        if (nodeSchema.deltaIndexName != null)
        {
            sb.append(" (INDEX ");
            sb.append(nodeSchema.deltaIndexName);
            sb.append(")");
        }
        sb.append(" WHERE delta != 0");
        sb.append(" AND type IN ('");
        sb.append(NODE_TYPE_DATA);
        sb.append("', '");
        sb.append(NODE_TYPE_CONTAINER);
        sb.append("')");
        
        if (nodeSchema.limitWithTop) // TOP, eg sybase
            sb.replace(0, 6, "SELECT TOP " + limit);
        else // LIMIT, eg postgresql
        {
            sb.append(" LIMIT ");
            sb.append(limit);
        }
        
        return sb.toString();
    }
    
    protected String getSelectNodeSizeSQL(Node node)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT nodeSize FROM ");
        sb.append(getNodeTableName());
        sb.append(" WHERE nodeID = ");
        sb.append(getNodeID(node));
        return sb.toString();
    }

    private static String getNodeType(Node node)
    {
        if (node instanceof DataNode)
            return NODE_TYPE_DATA;
        if (node instanceof ContainerNode)
            return NODE_TYPE_CONTAINER;
        if (node instanceof LinkNode)
            return NODE_TYPE_LINK;
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

    private class DataNodeUpdateStatementCreator implements PreparedStatementCreator
    {
        private Long len;
        private String md5;
        private Long nodeID;

        public DataNodeUpdateStatementCreator(Long nodeID, Long len, String md5)
        {
            this.nodeID = nodeID;
            this.len = len;
            this.md5 = md5;
        }
        public PreparedStatement createPreparedStatement(Connection conn)
            throws SQLException
        {
            StringBuilder sb = new StringBuilder();
            sb.append("UPDATE ");
            sb.append(getNodeTableName());
            sb.append(" SET ");
            
            sb.append("lastModified = ?, ");
            
            if (nodeSchema.fileMetadataWritable)
            {
                sb.append("delta = (? - coalesce(nodeSize, 0)), contentLength = ?, contentMD5 = ?");
            }
            else
            {
                // contentLength and md5 are virtual columns
                sb.append(" delta = (coalesce(contentLength, 0) - coalesce(nodeSize, 0))");
            }
            sb.append(" WHERE nodeID = ?");
            String sql = sb.toString();
            log.debug(sql);

            sb = new StringBuilder("values: ");
            PreparedStatement prep = conn.prepareStatement(sql);

            int col = 1;
            
            Date now = new Date();
            Timestamp ts = new Timestamp(now.getTime());
            prep.setTimestamp(col++, ts, cal);
            
            if (nodeSchema.fileMetadataWritable)
            {
                
                if (len == null)
                {
                    prep.setLong(col++, 0);
                    prep.setNull(col++, Types.BIGINT);
                }
                else
                {
                    prep.setLong(col++, len);
                    prep.setLong(col++, len);
                }
                sb.append(len);
                sb.append(",");
                sb.append(len);
                sb.append(",");
                if (md5 == null)
                    prep.setNull(col++, Types.VARBINARY);
                else
                    prep.setBytes(col++, HexUtil.toBytes(md5));
                sb.append(md5);
                sb.append(",");
            }
            prep.setLong(col++, nodeID);
            sb.append(nodeID);
            log.debug(sb.toString());
            return prep;
        }
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
        @Override
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
                ps.setLong(col++, nodeID.getID());
                ps.setString(col++, prop.getPropertyURI());
                log.debug("setValues: " + nodeID.getID() + "," 
                        + prop.getPropertyURI() + "," + prop.getPropertyValue() + ","
                        + nodeID.getID() + ","
                        + prop.getPropertyURI());
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
            sb.append(" (nodeID,propertyURI,propertyValue) SELECT ?, ?, ?");
            sb.append(" WHERE NOT EXISTS (SELECT * FROM ");
            sb.append(ns.propertyTable);
            sb.append(" WHERE nodeID=? and propertyURI=?)");
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

        private Node node = null;
        private Object differentOwner = null;

        public NodePutStatementCreator(NodeSchema ns, boolean update)
        {
            this.ns = ns;
            this.update = update;
        }

        // if we care about caching the statement, we should look into prepared
        // statement caching by the driver
        @Override
        public PreparedStatement createPreparedStatement(Connection conn) throws SQLException
        {
            PreparedStatement prep;
            if (update)
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

        public void setValues(Node node, Object differentOwner)
        {
            this.node = node;
            this.differentOwner = differentOwner;
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
            
            String name = node.getName();
            ps.setString(col++, name);
            sb.append(name);
            sb.append(",");

            ps.setString(col++, getNodeType(node));
            sb.append(getNodeType(node));
            sb.append(",");

            ps.setString(col++, NodeBusyState.notBusy.getValue());
            sb.append(getBusyState(node));
            sb.append(",");

            ps.setBoolean(col++, node.isPublic());
            setPropertyValue(node, VOS.PROPERTY_URI_ISPUBLIC, Boolean.toString(node.isPublic()), false);
            sb.append(node.isPublic());
            sb.append(",");

            String pval;
            
            // ownerID and creatorID data type
            int ownerDataType = identManager.getOwnerType();

            Object ownerObject = null;
            NodeID nodeID = (NodeID) node.appData;
            ownerObject = nodeID.ownerObject;
            if (differentOwner != null)
                ownerObject = differentOwner;
            if (ownerObject == null)
                throw new IllegalStateException("cannot update a node without an owner.");
            
            // ownerID
            ps.setObject(col++, ownerObject, ownerDataType);
            sb.append(ownerObject);
            sb.append(",");
            
            // always use the value in nodeID.ownerObject
            ps.setObject(col++, nodeID.ownerObject, ownerDataType);
            sb.append(nodeID.ownerObject);
            sb.append(",");

            //log.debug("setValues: " + sb);

            pval = node.getPropertyValue(VOS.PROPERTY_URI_GROUPREAD);
            if (pval != null)
                ps.setString(col, pval);
            else
                ps.setNull(col, Types.VARCHAR);
            col++;
            sb.append(pval);
            sb.append(",");

            //log.debug("setValues: " + sb);

            pval = node.getPropertyValue(VOS.PROPERTY_URI_GROUPWRITE);
            if (pval != null)
                ps.setString(col, pval);
            else
                ps.setNull(col, Types.VARCHAR);
            col++;
            sb.append(pval);
            sb.append(",");
            
            //log.debug("setValues: " + sb);

            // always tweak the date
            Date now = new Date();
            setPropertyValue(node, VOS.PROPERTY_URI_DATE, dateFormat.format(now), true);
            //java.sql.Date dval = new java.sql.Date(now.getTime());
            Timestamp ts = new Timestamp(now.getTime());
            ps.setTimestamp(col, ts, cal);
            col++;
            sb.append(dateFormat.format(now));
            sb.append(",");

            //log.debug("setValues: " + sb);

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
	    
            pval = null;
            if (node instanceof LinkNode)
            {
                pval = ((LinkNode)node).getTarget().toString();
                ps.setString(col, pval);
            }
            else
                ps.setNull(col, Types.LONGVARCHAR);
            col++;
            sb.append(pval);
            sb.append(",");

            if (update)
            {
                ps.setLong(col, getNodeID(node));
                sb.append(",");
                sb.append(getNodeID(node));
            }
            
            log.debug("setValues: " + sb);
        }

        private String getInsertSQL()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("INSERT INTO ");
            sb.append(ns.nodeTable);
            sb.append(" (");

            // we never insert or update physical file (DataNode) metadata here
            int numCols = NODE_COLUMNS.length - 3;
            
            for (int c=0; c<numCols; c++)
            {
                if (c > 0)
                    sb.append(",");
                sb.append(NODE_COLUMNS[c]);
            }
            sb.append(") VALUES (");
            for (int c=0; c<numCols; c++)
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

            // we never insert or update physical file (DataNode) metadata here
            int numCols = NODE_COLUMNS.length - 3;

            sb.append(" SET ");
            for (int c=0; c<numCols; c++)
            {
                if (c > 0)
                    sb.append(",");
                sb.append(NODE_COLUMNS[c]);
                sb.append(" = ?");
            }
            sb.append(" WHERE nodeID = ? AND busyState = '");
            sb.append(NodeBusyState.notBusy.getValue());
            sb.append("'");
            
            return sb.toString();
        }
    }

    private class NodePathStatementCreator implements PreparedStatementCreator
    {
        private String[] path;
        private String nodeTablename;
        private String propTableName;
        private boolean allowPartialPath;

        public NodePathStatementCreator(String[] path, String nodeTablename, String propTableName, boolean allowPartialPath)
        {
            this.path = path;
            this.nodeTablename = nodeTablename;
            this.propTableName = propTableName;
            this.allowPartialPath = allowPartialPath;
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
            StringBuilder sb = new StringBuilder();
            String acur = null;
            sb.append("SELECT ");
            for (int i=0; i<path.length; i++)
            {
                acur = "a"+i;
                if (i > 0)
                    sb.append(",");
                for (int c=0; c<NODE_COLUMNS.length; c++)
                {
                    if (c > 0)
                        sb.append(",");
                    sb.append(acur);
                    sb.append(".");
                    sb.append(NODE_COLUMNS[c]);
                }
                sb.append(",");
                sb.append(acur);
                sb.append(".nodeID");
            }

            sb.append(" FROM ");
            String aprev;
            for (int i=0; i<path.length; i++)
            {
                aprev = acur;
                acur = "a"+i;
                if (i > 0)
                {
                    if (this.allowPartialPath)
                        sb.append(" LEFT");
                    sb.append(" JOIN ");
                }
                sb.append(nodeTablename);
                sb.append(" AS ");
                sb.append(acur);
                if (i == 0)
                {
                    sb.append(" JOIN ");
                    sb.append(nodeTablename);
                    sb.append(" AS ");
                    sb.append(acur+0);
                    sb.append(" ON (");
                    sb.append(acur);
                    sb.append(".parentID IS NULL");
                    sb.append(" AND ");
                    sb.append(acur);
                    sb.append(".nodeID=");
                    sb.append(acur+0);
                    sb.append(".nodeID");
                    sb.append(" AND ");
                    sb.append(acur);
                    sb.append(".name = ? )");
                }
                else
                {
                    sb.append(" ON (");
                    sb.append(aprev);
                    sb.append(".nodeID=");
                    sb.append(acur);
                    sb.append(".parentID");
                    sb.append(" AND ");
                    sb.append(acur);
                    sb.append(".name = ? )");
                }
            }

            return sb.toString();
        }
    }

    private class NodePathExtractor implements ResultSetExtractor
    {
        private int columnsPerNode;

        public NodePathExtractor()
        {
            this.columnsPerNode = NODE_COLUMNS.length + 1;
        }

        public Object extractData(ResultSet rs)
            throws SQLException, DataAccessException
        {
            boolean done = false;
            Node ret = null;
            Node root = null;
            String curPath = "";
            int numColumns = rs.getMetaData().getColumnCount();

            while ( !done && rs.next() )
            {
                if (root == null) // reading first row completely
                {
                    log.debug("reading path from row 1");
                    int col = 1;
                    Node cur = null;
                    while (!done && (col < numColumns))
                    {
                        log.debug("readNode at " + col + ", path="+curPath);
                        Node n = readNode(rs, col, curPath);
                        ret = n; // always return the last node
                        if (n == null)
                        {
                        	done = true;
                        }
                        else
                        {
	                        log.debug("readNode: " + n.getUri());
	                        curPath = n.getUri().getPath();
	                        col += columnsPerNode;
	                        if ((n instanceof LinkNode) || (n instanceof DataNode))
	                            done = true; // exit while loop
	                        
	                        if (root == null) // root container
	                        {
	                            cur = n;
	                            root = cur;
	                        }
	                        else 
	                        {
	                            ((ContainerNode) cur).getNodes().add(n);
	                            n.setParent((ContainerNode) cur);
	                            cur = n;
	                        }
                        }
                    }
                }
                else
                    log.warn("found extra rows, expected only 0 or 1");
            }
            return ret;
        }

        private Node readNode(ResultSet rs, int col, String basePath)
            throws SQLException
        {
            Long parentID = null;
            Object o = rs.getObject(col++);
            if (o != null)
            {
                Number n = (Number) o;
                parentID = new Long(n.longValue());
            }
            String name = rs.getString(col++);
            String type = rs.getString(col++);
            String busyString = getString(rs, col++);
            boolean isPublic = rs.getBoolean(col++);

            Object ownerObject = rs.getObject(col++);
            String owner = null;

            Object creatorObject = rs.getObject(col++); // unused
            
            String groupRead = getString(rs, col++);
            String groupWrite = getString(rs, col++);

            Date lastModified = rs.getTimestamp(col++, cal);

            String contentType = getString(rs, col++);
            String contentEncoding = getString(rs, col++);
            String linkStr = getString(rs, col++);

            Long nodeSize = null;
            o = rs.getObject(col++);
            if (o != null)
            {
                Number n = (Number) o;
                nodeSize = new Long(n.longValue());
            }
            Long contentLength = null;
            o = rs.getObject(col++);
            if (o != null)
            {
                Number n = (Number) o;
                contentLength = new Long(n.longValue());
            }
            log.debug("readNode: nodeSize = " + nodeSize + ", contentLength = " + contentLength);

            Object contentMD5 = rs.getObject(col++);

            Long nodeID = null;
            o = rs.getObject(col++);
            if (o != null)
            {
                Number n = (Number) o;
                nodeID = new Long(n.longValue());
            }
            
            String path = basePath + "/" + name;
            VOSURI vos;
            try { vos = new VOSURI(new URI("vos", authority, path, null, null)); }
            catch(URISyntaxException bug)
            {
                throw new RuntimeException("BUG - failed to create vos URI", bug);
            }

            Node node = null;            
            // Since we support partial paths, a node not in the Node table is 
            // returned with all columns having null values. Instead of 
            // checking all columns, if we do not have the following condition,
            // return a null node.
            if (!((parentID == null) && (nodeID == null) && (name == null) && (type == null)))
            {
	            if (NODE_TYPE_CONTAINER.equals(type))
	            {
	                node = new ContainerNode(vos);
	            }
	            else if (NODE_TYPE_DATA.equals(type))
	            {
	                node = new DataNode(vos);
	                ((DataNode) node).setBusy(NodeBusyState.getStateFromValue(busyString));
	            }
	            else if (NODE_TYPE_LINK.equals(type))
	            {
	                URI link;
	               
	                try { link = new URI(linkStr); }
	                catch(URISyntaxException bug)
	                {
	                    throw new RuntimeException("BUG - failed to create link URI", bug);
	                }
	                 
	                node = new LinkNode(vos, link);
	            }
	            else
	            {
	                throw new IllegalStateException("Unknown node database type: " + type);
	            }
	
	            NodeID nid = new NodeID();
	            nid.id = nodeID;
	            nid.ownerObject = ownerObject;
	            node.appData = nid;
	
	            if (contentType != null)
	            {
	                node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_TYPE, contentType));
	            }
	
	            if (contentEncoding != null)
	            {
	                node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTENCODING, contentEncoding));
	            }
	            
	            if (node instanceof DataNode)
	            {
	                if (contentLength != null)
	                    node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, contentLength.toString()));
	                else
	                    node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, "0"));
	            }
	            else if (node instanceof ContainerNode)
	            {
	                if (nodeSize != null)
	                    node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, nodeSize.toString()));
	                else
	                    node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, "0"));
	            }
	
	            if (contentMD5 != null && contentMD5 instanceof byte[])
	            {
	                byte[] md5 = (byte[]) contentMD5;
	                if (md5.length < 16)
	                {
	                    byte[] tmp = md5;
	                    md5 = new byte[16];
	                    System.arraycopy(tmp, 0, md5, 0, tmp.length);
	                    // extra space is init with 0
	                }
	                String contentMD5String = HexUtil.toHex(md5);
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
            }

            return node;
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
    
    private class NodeSizePropagationExtractor implements ResultSetExtractor
    {
        @Override
        public Object extractData(ResultSet rs) throws SQLException,
                DataAccessException
        {
            List<NodeSizePropagation> propagations = new ArrayList<NodeSizePropagation>(rs.getFetchSize());
            long childID;
            String childType;
            Long parentID;
            NodeSizePropagation propagation = null;
            int col;
            while (rs.next())
            {
                col = 1;
                childID = rs.getLong(col++);
                childType = rs.getString(col++);
                parentID = rs.getLong(col++);
                propagation = new NodeSizePropagation(childID, childType, parentID);
                propagations.add(propagation);
            }
            return propagations;
        }
        
    }
}
