package ca.nrc.cadc.vos;

import java.security.AccessControlException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;


public abstract class NodeDAO implements NodePersistence
{
    
    // Number of time to attempt to insert a Job.
    private static Logger log = Logger.getLogger(NodeDAO.class);

    // Database connection.
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactionManager;
    private DefaultTransactionDefinition defaultTransactionDef;
    private TransactionStatus transactionStatus;
    private NodeAuthorizer nodeAuthorizer;
    
    public NodeDAO(DataSource dataSource, NodeAuthorizer nodeAuthorizer)
    {
        this.defaultTransactionDef = new DefaultTransactionDefinition();
        defaultTransactionDef.setIsolationLevel(DefaultTransactionDefinition.ISOLATION_READ_COMMITTED);
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactionManager = new DataSourceTransactionManager(dataSource);
        this.nodeAuthorizer = nodeAuthorizer;
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

    public void copy(Node node, String copyToURI)
    {
        // TODO Auto-generated method stub
        
    }

    public void delete(Node node) throws AccessControlException, NodeNotFoundException
    {
        Node nodeInDb = this.get(node);
        synchronized(this)
        {
            jdbc.update(getDeleteNodeSQL(nodeInDb));
        }
        log.debug("Node deleted: " + node.getPath());
    }
    
    @Override
    public Node get(Node node) throws AccessControlException,
            NodeNotFoundException
    {
        return null;
    }

    public void move(Node node, String newURI)
    {
        // TODO Auto-generated method stub
        
    }

    public Node put(final Node node) throws AccessControlException, NodeNotFoundException, NodeAlreadyExistsException
    {

        synchronized(this)
        {
            try
            {

                // Start the transaction.
                startTransaction();
                
                // Check the node path and permissions
                if (node.getParent() == null)
                {
                    // see if the user can write to the root directory
                    nodeAuthorizer.checkWriteAccess(null);
                }
                else
                {
                    // build the line of parent nodes from the database
                    ContainerNode parentLineInDb = (ContainerNode) jdbc.query(getSelectNodeSQL(node.getParent()), new NodeMapper());
                    ContainerNode parent = parentLineInDb;
                    while (parent != null)
                    {
                        ContainerNode nextParent = (ContainerNode) jdbc.query(getSelectNodeSQL(parent.getParent()), new NodeMapper());
                        parent.setParent(nextParent);
                        parent = nextParent;
                    }
                    
                    // ensure the parent line in the database is the same as it
                    // is in the user's request
                    if (!parentLineInDb.heirarchyEquals(node.getParent()))
                    {
                        throw new NodeNotFoundException(node.getParent().getPath());
                    }
                    
                    // check write access to the parent line
                    nodeAuthorizer.checkWriteAccess(parentLineInDb);

                }
                
                // check to ensure a node by this name doesn't already exist
                Node existingNode = (Node) jdbc.query(getSelectNodeSQL(node), new NodeMapper());
                if (existingNode != null)
                {
                    throw new NodeAlreadyExistsException(node.getPath());
                }
                
                // insert the node
                jdbc.update(getInsertNodeSQL(node));
                
                // insert the node properties
                Iterator<NodeProperty> i = node.getProperties().iterator();
                while (i.hasNext())
                {
                    jdbc.update(getInsertNodePropertiesSQL(node, i.next()));
                }
                
                // Commit the transaction.
                commitTransaction();
                log.debug("Inserted new node: " + node.getPath());

                return node;
            }
            catch (Throwable t)
            {
                rollbackTransaction();
                log.error("Put rollback for node: " + node.getPath(), t);
                return null;
            }
        }
        
    }
    
    protected abstract String getNodeTableName();
    
    protected abstract String getNodePropertyTableName();
    
    protected String getSelectNodeSQL(Node node)
    {
        // TODO: Implement
        return null;
    }
    
    protected String getInsertNodeSQL(Node node)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("insert into ");
        sb.append(getNodeTableName());
        sb.append(" (");
        sb.append("parentId,");
        sb.append("name,");
        sb.append("type,");
        sb.append("owner,");
        sb.append("groupRead,");
        sb.append("groupWrite");
        sb.append(") values (");
        sb.append(node.getParent().getNodeId());
        sb.append(",'");
        sb.append(node.getName());
        sb.append("','");
        sb.append(node.getDatabaseTypeRepresentation());
        sb.append("','");
        //sb.append(node.getOwner());
        sb.append("','");
        //sb.append(node.getGroupRead());
        sb.append("','");
        //sb.append(node.getGroupWrite());
        sb.append(")");
        return sb.toString();
    }
    
    protected String getInsertNodePropertiesSQL(Node node, NodeProperty nodeProperty)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("insert into ");
        sb.append(getNodePropertyTableName());
        sb.append(" (");
        sb.append("nodePropertyId,");
        sb.append("nodeId,");
        sb.append("propertyURI,");
        sb.append("propertyValue");
        sb.append(") values (");
        sb.append(nodeProperty.getNodePropertyId());
        sb.append(",");
        sb.append(node.getNodeId());
        sb.append(",'");
        sb.append(nodeProperty.getUri());
        sb.append("','");
        sb.append(nodeProperty.getValue());
        sb.append("')");
        return sb.toString();
    }
    
    protected String getDeleteNodeSQL(Node node)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("delete from ");
        sb.append(getNodeTableName());
        sb.append(" where nodeID = ");
        sb.append(node.getNodeId());
        return sb.toString();
    }
    
    private class NodeMapper implements RowMapper
    {

        public Object mapRow(ResultSet rs, int rowNum) throws SQLException
        {

            long nodeID = rs.getLong("nodeID");
            String name = rs.getString("name");
            ContainerNode parent = new ContainerNode(rs.getLong("parentID"));
            
            byte type = rs.getByte("type");
            Node node = null;
            
            if (ContainerNode.DB_TYPE == type)
            {
                node = new ContainerNode(nodeID);
            }
            else if (DataNode.DB_TYPE == type)
            {
                node = new DataNode(nodeID);
            }
            else
            {
                throw new IllegalStateException("Unknown node database type: " + type);
            }
            
            node.setName(name);
            node.setParent(parent);

            return node;
        }
        
    }

}
