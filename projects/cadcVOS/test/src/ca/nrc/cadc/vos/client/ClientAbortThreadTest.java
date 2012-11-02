/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.nrc.cadc.vos.client;

import ca.nrc.cadc.net.HttpDownload;
import ca.nrc.cadc.net.HttpPost;
import ca.nrc.cadc.util.Log4jInit;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jburke
 */
public class ClientAbortThreadTest
{
    private static Logger log = Logger.getLogger(ClientAbortThreadTest.class);

    protected static String serviceUrl;

    public ClientAbortThreadTest()
    {
        // Base URL of the service to be tested.
        serviceUrl = System.getProperty("service.url");
        if (serviceUrl == null)
            throw new RuntimeException("service.url System property not set");
        log.debug("serviceUrl: " + serviceUrl);
    }

    @BeforeClass
    public static void setUpClass()
    {
        Log4jInit.setLevel("ca.nrc.cadc.vos.client", Level.INFO);
    }

    /**
     * Test of run method, of class ClientAbortThread.
     */
    @Test
    public void testRun()
    {
        try
        {
            URL uwsService = new URL(serviceUrl);

            // Create a UWS Job.
            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("runId", new String[] {"INTTEST"});

            HttpPost post = new HttpPost(uwsService, parameters, false);
            post.run();
            assertNull("POST to UWS service should not cause an exception:" , post.getThrowable());

            URL jobURL = post.getRedirectURL();
            log.debug("jobURL: " + jobURL);

            // Check that the phase is PENDING.
            URL phaseEndpoint = new URL(jobURL.toExternalForm() + "/phase");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpDownload get = new HttpDownload(phaseEndpoint, out);
            get.run();
            
            assertNull("GET of Job should not cause an exception", get.getThrowable());
            String phase = out.toString("UTF-8");
            log.debug("GET phase: " + out.toString("UTF-8"));
            assertTrue("phase should be PENDING", phase.equalsIgnoreCase("PENDING"));

            // Create and run thread to abort Job.
            ClientAbortThread abortThread = new ClientAbortThread(jobURL);
            abortThread.run();

            // Check the phase, should be ABORTED.
            out = new ByteArrayOutputStream();
            get = new HttpDownload(phaseEndpoint, out);
            get.run();

            assertNull("GET of Job should not cause an exception", get.getThrowable());
            phase = out.toString("UTF-8");
            log.debug("GET phase: " + out.toString("UTF-8"));
            assertTrue("phase should be ABORTED", phase.equalsIgnoreCase("ABORTED"));
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
}
