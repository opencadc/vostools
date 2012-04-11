/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.nrc.cadc.vos.server;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.server.JobPersistenceUtil;
import java.text.DateFormat;
import java.util.Date;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobInfo;
import ca.nrc.cadc.uws.web.InlineContentHandler;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Direction;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.Protocol;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.TransferReader;
import ca.nrc.cadc.vos.TransferWriter;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.View;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author jburke
 */
public class TransferInlineContentHandlerTest
{
    private static Logger log = Logger.getLogger(TransferInlineContentHandlerTest.class);
    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.vos", Level.INFO);
    }

    private static final String JOB_ID = "someJobID";
    private static final String RUN_ID = "someRunID";
    private static final String TEST_DATE = "2001-01-01T12:34:56.000";

    private static DateFormat dateFormat;
    private static Date baseDate;

    private static Transfer transfer;

    public TransferInlineContentHandlerTest() { }

    @BeforeClass
    public static void setUpClass()
        throws Exception
    {
        dateFormat = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
        baseDate = dateFormat.parse(TEST_DATE);

        VOSURI target = new VOSURI("vos://cadc.nrc.ca!vospace/mydata");
        View view = new View(new URI(VOS.VIEW_DEFAULT));

        List<Protocol> protocols = new ArrayList<Protocol>();
        protocols.add(new Protocol(VOS.PROTOCOL_HTTP_GET));
        transfer = new Transfer(target, Direction.pullFromVoSpace, protocols);
    }

    @Test
    public void testAcceptTransferDocument()
    {
        try
        {
            InlineContentHandler handler = new TransferInlineContentHandler();

            TransferWriter writer = new TransferWriter();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writer.write(transfer, out);
            ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

            Job job = new Job();
            job.setExecutionPhase(ExecutionPhase.PENDING);
            JobPersistenceUtil.assignID(job, JOB_ID);
            job.setRunID(RUN_ID);
            job.setQuote(new Date(baseDate.getTime() + 10000L));
            job.setExecutionDuration(123L);
            job.setDestructionTime(new Date(baseDate.getTime() + 300000L));

            try
            {
                handler.accept("filename", "text/plain", null);
                Assert.fail("Content-Type not set to text/xml should have thrown IllegalArgumentException");
            }
            catch (IllegalArgumentException ignore) {}
            
            try
            {
                handler.accept("filename", "text/xml", null);
                Assert.fail("Null InputStream should have thrown IOException");
            }
            catch (IOException ignore) {}

            handler.accept("filename", "text/xml", in);

            JobInfo jobInfo = handler.getJobInfo();
            Assert.assertNotNull(jobInfo.getContent());
            Assert.assertEquals("text/xml", jobInfo.getContentType());

            TransferReader reader = new TransferReader();
            Transfer newTransfer = reader.read(jobInfo.getContent());

            Assert.assertEquals("vos uri", transfer.getTarget(), newTransfer.getTarget());
            Assert.assertEquals("dirdction", transfer.getDirection(), newTransfer.getDirection());
            Assert.assertEquals("view", transfer.getView(), newTransfer.getView());
            Assert.assertEquals("protocol uri", transfer.getProtocols().get(0).getUri(), newTransfer.getProtocols().get(0).getUri());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

}
