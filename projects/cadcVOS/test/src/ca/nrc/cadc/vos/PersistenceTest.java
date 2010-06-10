package ca.nrc.cadc.vos;

import java.sql.Connection;
import java.sql.PreparedStatement;

import javax.sql.DataSource;

import org.junit.After;

public abstract class PersistenceTest
{
    
    protected NodeDAO nodeDAO;
    protected String runId;
    protected Connection connection;
    
    public abstract DataSource getDataSource();
    
    public abstract NodeDAO getNodeDAO(DataSource dataSource);
    
    public abstract String getVOSURIPrefix();
    
    public abstract String getRootContainerName();
    
    public abstract String getNodeOwner();
    
    protected String getNodeName(String identifier)
    {
        return runId + identifier;
    }
    
    public void commonBefore() throws Exception
    {
        DataSource dataSource = getDataSource();
        nodeDAO = getNodeDAO(dataSource);
        connection = dataSource.getConnection();
        runId = NodeDAOTest.class.getName() + System.currentTimeMillis();
    }
    
    @After
    public void after() throws Exception
    {
        PreparedStatement prepStmt = connection.prepareStatement(
                "delete from " + nodeDAO.getNodePropertyTableName()
                + " where nodeID in (select nodeID from "
                +  nodeDAO.getNodeTableName()
                + " where name like ?)");
        prepStmt.setString(1, runId + "%");
        prepStmt.executeUpdate();
        
        prepStmt = connection.prepareStatement(
            "delete from " + nodeDAO.getNodeTableName() + " where name like ?");
        prepStmt.setString(1, runId + "%");
        prepStmt.executeUpdate();
        
        prepStmt.close();
        
        connection.close();
        
    }

}
