package ca.nrc.cadc.vos;

import static org.junit.Assert.assertEquals;

import java.nio.charset.Charset;
import java.security.MessageDigest;

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
        
        String md5SumSource = "valuetohashis a longer string now.";
        
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.reset();
        md.update(md5SumSource.getBytes("iso-8859-1"), 0, md5SumSource.length());
        byte[] md5hash = md.digest();
        String contentMD5 = new String(md5hash, Charset.forName("iso-8859-1"));
        
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

}
