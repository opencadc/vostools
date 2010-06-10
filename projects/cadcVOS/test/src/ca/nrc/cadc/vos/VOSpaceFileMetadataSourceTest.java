package ca.nrc.cadc.vos;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Test;

public abstract class VOSpaceFileMetadataSourceTest extends PersistenceTest
{
    private VOSpaceFileMetadataSource vospaceFileMetadataSource;
    private DataNode testNode;
    
    @Before
    public void before() throws Exception
    {
        super.commonBefore();
        vospaceFileMetadataSource = new VOSpaceFileMetadataSource();
        vospaceFileMetadataSource.setNodePersistence(nodeDAO);
        
        ContainerNode rootContainer = (ContainerNode) nodeDAO.getFromParent(getRootContainerName(), null);
        testNode = new DataNode(getVOSURIPrefix() + getRootContainerName() + "/" + getNodeName("testNode"));
        nodeDAO.putInContainer(testNode, rootContainer);
    }
    
    public abstract DataSource getDataSource();
    
    public abstract NodeDAO getNodeDAO(DataSource dataSource);
    
    public abstract String getVOSURIPrefix();
    
    public abstract String getRootContainerName();
    
    public abstract String getNodeOwner();
    
    @Test
    public void testMetadata() throws Exception
    {
        
    }

}
