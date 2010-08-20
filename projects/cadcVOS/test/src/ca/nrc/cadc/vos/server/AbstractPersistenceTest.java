package ca.nrc.cadc.vos.server;

import java.sql.Connection;
import java.sql.PreparedStatement;

import javax.sql.DataSource;

import org.junit.After;

public abstract class AbstractPersistenceTest implements TestPersistence
{

    protected DataSource dataSource;
    protected NodeDAO nodeDAO;
    protected String runId;
    protected Connection connection;
    
    private TestPersistence testPersistence;
    
    public AbstractPersistenceTest()
    {
        this.testPersistence = getTestPersistence();
    }
    
    public abstract TestPersistence getTestPersistence();
    
    public DataSource getDataSource()
    {
        return testPersistence.getDataSource();
    }
    
    public NodeDAO getNodeDAO(DataSource dataSource)
    {
        return testPersistence.getNodeDAO(dataSource);
    }
    
    public String getVOSURIPrefix()
    {
        return testPersistence.getVOSURIPrefix();
    }
    
    public String getRootContainerName()
    {
        return testPersistence.getRootContainerName();
    }
    
    public String getNodeOwner()
    {
        return testPersistence.getNodeOwner();
    }
    
    protected String getNodeName(String identifier)
    {
        return runId + identifier;
    }
    
    public void commonBefore() throws Exception
    {
        dataSource = getDataSource();
        nodeDAO = getNodeDAO(dataSource);
        connection = dataSource.getConnection();
        runId = this.getClass().getSimpleName() + System.currentTimeMillis();
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
        //prepStmt.executeUpdate();
        
        prepStmt.close();
        
        connection.close();
    }
    
}
