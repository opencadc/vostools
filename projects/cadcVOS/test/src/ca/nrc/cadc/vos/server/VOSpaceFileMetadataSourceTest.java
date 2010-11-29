package ca.nrc.cadc.vos.server;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import ca.nrc.cadc.util.FileMetadata;
import ca.nrc.cadc.util.HexUtil;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;

public abstract class VOSpaceFileMetadataSourceTest extends AbstractPersistenceTest
{
    private VOSpaceFileMetadataSource vospaceFileMetadataSource;
    
    public VOSpaceFileMetadataSourceTest()
    {
        super();
    }
    
    @Before
    public void before() throws Exception
    {
        super.commonBefore();
        vospaceFileMetadataSource = new VOSpaceFileMetadataSource();
        vospaceFileMetadataSource.setNodePersistence(nodeDAO);
    }
    
    @Test
    public void testMetadata() throws Exception
    {
        
        ContainerNode rootContainer = (ContainerNode) nodeDAO.getFromParent(getRootContainerName(), null);
        VOSURI testURI = new VOSURI(getVOSURIPrefix() + "/" + getRootContainerName() + "/" + getNodeName("testNode"));
        DataNode testNode = new DataNode(testURI);
        testNode.setOwner(getNodeOwner());
        nodeDAO.putInContainer(testNode, rootContainer);
        
        
        String contentEncoding = "gzip";
        Long contentLength = 256L;
        String contentType = "text/xml";
        
        String contentMD5 = HexUtil.toHex(new byte[] {0, 1, 2, 3, 4, 5, 6, 7,
                                                      8, 9, 10, 11, 12, 13, 14,
                                                      15});
        
        FileMetadata metadata1 = new FileMetadata();
        metadata1.setContentEncoding(contentEncoding);
        metadata1.setContentLength(contentLength);
        metadata1.setContentType(contentType);
        metadata1.setMd5Sum(contentMD5);
        
        vospaceFileMetadataSource.set(testURI.getURIObject(), metadata1);
        FileMetadata metadata2 = vospaceFileMetadataSource.get(testURI.getURIObject());
        
        assertEquals(contentEncoding, metadata2.getContentEncoding());
        assertEquals(contentLength, metadata2.getContentLength());
        assertEquals(contentType, metadata2.getContentType());
        assertEquals(contentMD5, metadata2.getMd5Sum());
    }
    
    @Test
    public void testUpdateContentLength() throws Exception
    {
        
        // Setup a hierarchy of nodes:
        
        // TestRoot     +100  +500  -50  -210  +40 = +380
        //     |_A      +100        -50        +40 = +90
        //     | |_C    +100        -50        +40 = +90
        //     |   |_E  +100                   +40 = +140
        //     |_B            +500       -210      = +290
        //       |_D          +500       -210      = +290
        
        Node rootContainer = (ContainerNode) nodeDAO.getFromParent(getRootContainerName(), null);
        
        VOSURI testRootURI = new VOSURI(getVOSURIPrefix() + "/" + getRootContainerName() + "/" + getNodeName("testRoot"));
        Node testRoot = new ContainerNode(testRootURI);
        testRoot.setOwner(getNodeOwner());
        testRoot.setPublic(true);
        testRoot = nodeDAO.putInContainer(testRoot, (ContainerNode) rootContainer);
        
        VOSURI nodeAURI = new VOSURI(getVOSURIPrefix() + "/" + getRootContainerName() + "/" + getNodeName("testRoot") + "/" + getNodeName("A"));
        Node nodeA = new ContainerNode(nodeAURI);
        nodeA.setOwner(getNodeOwner());
        nodeA.setPublic(true);
        nodeA = nodeDAO.putInContainer(nodeA, (ContainerNode) testRoot);
        
        VOSURI nodeBURI = new VOSURI(getVOSURIPrefix() + "/" + getRootContainerName() + "/" + getNodeName("testRoot") + "/" + getNodeName("B"));
        Node nodeB = new ContainerNode(nodeBURI);
        nodeB.setOwner(getNodeOwner());
        nodeB.setPublic(true);
        nodeB = nodeDAO.putInContainer(nodeB, (ContainerNode) testRoot);
        
        VOSURI nodeCURI = new VOSURI(getVOSURIPrefix() + "/" + getRootContainerName() + "/" + getNodeName("testRoot") + "/" + getNodeName("A") + "/" + getNodeName("C"));
        Node nodeC = new ContainerNode(nodeCURI);
        nodeC.setOwner(getNodeOwner());
        nodeC.setPublic(true);
        nodeC = nodeDAO.putInContainer(nodeC, (ContainerNode) nodeA);
        
        VOSURI nodeDURI = new VOSURI(getVOSURIPrefix() + "/" + getRootContainerName() + "/" + getNodeName("testRoot") + "/" + getNodeName("B") + "/" + getNodeName("D"));
        Node nodeD = new ContainerNode(nodeDURI);
        nodeD.setOwner(getNodeOwner());
        nodeD.setPublic(true);
        nodeD = nodeDAO.putInContainer(nodeD, (ContainerNode) nodeB);
        
        VOSURI nodeEURI = new VOSURI(getVOSURIPrefix() + "/" + getRootContainerName() + "/" + getNodeName("testRoot") + "/" + getNodeName("A") + "/" + getNodeName("C") + "/" + getNodeName("E"));
        Node nodeE = new ContainerNode(nodeEURI);
        nodeE.setOwner(getNodeOwner());
        nodeE.setPublic(true);
        nodeE = nodeDAO.putInContainer(nodeE, (ContainerNode) nodeC);
        
        // Perform metadata operations, checking results at the end
        vospaceFileMetadataSource.updateContentLengths((ContainerNode) nodeE, +100);
        vospaceFileMetadataSource.updateContentLengths((ContainerNode) nodeD, +500);
        vospaceFileMetadataSource.updateContentLengths((ContainerNode) nodeC, -50);
        vospaceFileMetadataSource.updateContentLengths((ContainerNode) nodeD, -210);
        vospaceFileMetadataSource.updateContentLengths((ContainerNode) nodeE, +40);
        
        testRoot = nodeDAO.getFromParent(getNodeName("testRoot"), (ContainerNode) rootContainer);
        nodeA = nodeDAO.getFromParent(getNodeName("A"), (ContainerNode) testRoot);
        nodeB = nodeDAO.getFromParent(getNodeName("B"), (ContainerNode) testRoot);
        nodeC = nodeDAO.getFromParent(getNodeName("C"), (ContainerNode) nodeA);
        nodeD = nodeDAO.getFromParent(getNodeName("D"), (ContainerNode) nodeB);
        nodeE = nodeDAO.getFromParent(getNodeName("E"), (ContainerNode) nodeC);
        
        assertEquals(380, getContentLength(testRoot));
        assertEquals(90, getContentLength(nodeA));
        assertEquals(290, getContentLength(nodeB));
        assertEquals(90, getContentLength(nodeC));
        assertEquals(290, getContentLength(nodeD));
        assertEquals(140, getContentLength(nodeE));
        
    }
    
    private long getContentLength(Node node)
    {
        int index = node.getProperties().indexOf(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, null));
        return Long.parseLong(((NodeProperty) node.getProperties().get(index)).getPropertyValue()); 
    }
}
