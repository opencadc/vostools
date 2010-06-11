package ca.nrc.cadc.vos;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import ca.nrc.cadc.util.FileMetadata;

public abstract class VOSpaceFileMetadataSourceTest extends AbstractPersistenceTest
{
    private VOSpaceFileMetadataSource vospaceFileMetadataSource;
    private VOSURI testURI;
    private DataNode testNode;
    
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
        
        ContainerNode rootContainer = (ContainerNode) nodeDAO.getFromParent(getRootContainerName(), null);
        testURI = new VOSURI(getVOSURIPrefix() + "/" + getRootContainerName() + "/" + getNodeName("testNode"));
        testNode = new DataNode(testURI);
        testNode.setOwner(getNodeOwner());
        nodeDAO.putInContainer(testNode, rootContainer);
    }
    
    @Test
    public void testMetadata() throws Exception
    {
        String contentEncoding = "gzip";
        Long contentLength = 256L;
        String contentType = "text/xml";
        String md5Sum = new String(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        
        FileMetadata metadata1 = new FileMetadata();
        metadata1.setContentEncoding(contentEncoding);
        metadata1.setContentLength(contentLength);
        metadata1.setContentType(contentType);
        metadata1.setMd5Sum(md5Sum);
        
        vospaceFileMetadataSource.set(testURI.getURIObject(), metadata1);
        FileMetadata metadata2 = vospaceFileMetadataSource.get(testURI.getURIObject());
        
        assertEquals(contentEncoding, metadata2.getContentEncoding());
        assertEquals(contentLength, metadata2.getContentLength());
        assertEquals(contentType, metadata2.getContentType());
        assertEquals(md5Sum, metadata2.getMd5Sum());

    }

}
