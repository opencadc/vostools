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
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 4 $
*
************************************************************************
*/

package ca.nrc.cadc.vos.client;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.AccessController;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.Subject;

import org.apache.log4j.Logger;
import org.jdom.JDOMException;

import ca.nrc.cadc.auth.SSLUtil;
import ca.nrc.cadc.net.HttpDownload;
import ca.nrc.cadc.net.HttpRequestProperty;
import ca.nrc.cadc.net.HttpUpload;
import ca.nrc.cadc.net.NetUtil;
import ca.nrc.cadc.net.OutputStreamWrapper;
import ca.nrc.cadc.net.event.TransferListener;
import ca.nrc.cadc.util.StringUtil;
import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobReader;
import ca.nrc.cadc.vos.Direction;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.xml.XmlUtil;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;

/**
 * A client-side wrapper for a transfer to make it runnable.
 */
public class ClientTransfer implements Runnable
{
    private static Logger log = Logger.getLogger(ClientTransfer.class);
    private static final long POLL_INTERVAL = 100L;
    
    private SSLSocketFactory sslSocketFactory;
    
    private URL jobURL;
    private Transfer transfer;
    private boolean monitorAsync;
    private boolean schemaValidation;
    
    private File localFile;
    private OutputStreamWrapper wrapper;
    private List<HttpRequestProperty> httpRequestProperties;
    private int maxRetries;
    private TransferListener transListener;

    private Throwable throwable;
    private ExecutionPhase phase;
    private ErrorSummary error;
    
    private ClientTransfer() { }

    /**
     * @param jobURL UWS job URL for the transfer job
     * @param transfer a negotiated transfer
     * @param monitor monitor the job until complete (true) or just start
     *  it and return (false)
     */
    ClientTransfer(URL jobURL, Transfer transfer, boolean schemaValidation)
    {
        this.httpRequestProperties = new ArrayList<HttpRequestProperty>();
        this.jobURL = jobURL;
        this.transfer = transfer;
        this.monitorAsync = false;
        this.schemaValidation = schemaValidation;
    }

    /**
     * Get the URL to the Job.
     * 
     * @return URL tot the Job.
     */
    public URL getJobURL()
    {
        return jobURL;
    }

    /**
     * Get the negotiated transfer details.
     * 
     * @return the negotiated transfer
     */
    public Transfer getTransfer() { return transfer; }

    /**
     * Get any client-side error that was caught during the run method.
     * @return
     */
    public Throwable getThrowable()
    {
        return throwable;
    }

    /**
     * Get the UWS execution phase of the job.
     *
     * @return the current phase
     */
    public ExecutionPhase getPhase()
        throws IOException
    {
        if (phase != null)
            return phase;

        // TODO: read just the phase in text/plain from the phaseURL
        //URL phaseURL = new URL(jobURL.toExternalForm() + "/phase");

        Job job = getJob();
        ExecutionPhase ep = job.getExecutionPhase();
        if (ExecutionPhase.ABORTED.equals(ep)
                || ExecutionPhase.COMPLETED.equals(ep)
                || ExecutionPhase.ERROR.equals(ep) )
            this.phase = ep; // only set when final phase
        return ep;
                
    }

    public ErrorSummary getServerError()
        throws IOException
    {
        if (error != null)
            return error;

        Job job = getJob();
        this.error = job.getErrorSummary();
        return error;
    }

    private Job getJob()
        throws IOException
    {
        try
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpDownload get = new HttpDownload(jobURL, out);
            get.run();

            VOSClientUtil.checkFailureClean(get.getThrowable());
            
            // add the extra xsd information for vospace if we
            // are using schema validation
            JobReader jobReader = null;
            if (schemaValidation)
            {
                Map<String, String> extreaSchemas = new HashMap<String, String>();
                String xsdFile = XmlUtil.getResourceUrlString(
                        VOS.XSD_FILE_NAME, ClientRecursiveSetNode.class);
                extreaSchemas.put(VOS.XSD_KEY, xsdFile);
                jobReader = new JobReader(extreaSchemas);
            }
            else
            {
                jobReader = new JobReader(false);
            }
            
            return jobReader.read(new StringReader(new String(out.toByteArray(), "UTF-8")));
        }
        catch(ParseException ex)
        {
            throw new RuntimeException("failed to parse job from "+jobURL, ex);
        }
        catch(JDOMException ex)
        {
            throw new RuntimeException("failed to parse job from "+jobURL, ex);
        }
        catch(MalformedURLException bug)
        {
            throw new RuntimeException("BUG: failed to create error url", bug);
        }
    }
    public void setFile(File file)
    {
        if (Direction.pullFromVoSpace.equals(transfer.getDirection())
                || Direction.pushToVoSpace.equals(transfer.getDirection()))
        {
            this.localFile = file;
            return;
        }
        throw new IllegalStateException("cannot specify a local File for transfer direction " + transfer.getDirection());
    }

    /**
     * After a download, this will be the actual file downloaded.
     * 
     * @return
     */
    public File getLocalFile()
    {
        return localFile;
    }
    
    public void setOutputStreamWrapper(OutputStreamWrapper wrapper)
    {
        if (Direction.pushToVoSpace.equals(transfer.getDirection()))
        {
            this.wrapper = wrapper;
            return;
        }
        throw new IllegalStateException("cannot specify an OutputStreamWrapper for transfer direction " + transfer.getDirection());
    }

    /**
     * Set an optional listener to get events from the underying HttpTransfer.
     * 
     * @param transListener
     */
    public void setTransferListener(TransferListener transListener)
    {
        this.transListener = transListener;
    }

    public void setSSLSocketFactory(SSLSocketFactory sslSocketFactory)
    {
        this.sslSocketFactory = sslSocketFactory;
    }


    /**
     * Set the maximum number of retries when the server is busy. This value is
     * passed to the underlying HttpTransfer.
     * </p><p>
     * Set this to Integer.MAX_VALUE to retry indefinitely.
     * 
     * @param maxRetries
     */
    public void setMaxRetries(int maxRetries)
    {
        this.maxRetries = maxRetries;
    }

    /**
     * Set additional request headers.
     *
     * @param header
     * @param value
     */
    public void setRequestProperty(String header, String value)
    {
        httpRequestProperties.add(new HttpRequestProperty(header,value));
    }

    /**
     * Enable or disable monitoring an async job until it is finished. If enabled, 
     * the run() method will not return until the job reaches a terminal state. If 
     * disabled, the run() method will simply start the async job and return immediately.
     * 
     * @param enabled
     */
    public void setMonitor(boolean enabled) { this.monitorAsync = enabled; }

    /**
     * Run the transfer and catch any throwables. The cakller must check the
     * getPhase(), getServerError(), and getThrowable() methods to see if the
     * transfer failed.
     */
    public void run()
    {
        log.debug("start: " + transfer);
        try
        {
            runTransfer();
        }
        catch(Throwable t)
        {
            this.throwable = t;
        }
        log.debug("done: " + transfer);
    }

    /**
     * Run the transfer. Use this method if you want to have client-side
     * exceptiosn thrown.
     * @throws IOException
     * @throws InterruptedException if a server transfer is interrupted
     * @throws RuntimeException for server response errors
     */
    public void runTransfer()
        throws IOException, InterruptedException, RuntimeException
    {
        try
        {
            if ( Direction.pullFromVoSpace.equals(transfer.getDirection()))
            {
                checkProtocols();
                doDownload();
            }
            else if ( Direction.pushToVoSpace.equals(transfer.getDirection()))
            {
                checkProtocols();
                doUpload();
            }
            else
                doServerTransfer();
        }
        catch(JDOMException ex)
        {
            throw new RuntimeException("failed to parse transfer document", ex);
        }
        catch(ParseException ex)
        {
            throw new RuntimeException("failed to parse transfer document", ex);
        }
    }

    
    private void checkProtocols()
        throws IOException, JDOMException, ParseException
    {
        // Handle errors by parsing url, getting job and looking at phase/error summary.
        // Zero protocols in resulting transfer indicates that an error was encountered.
        if (transfer.getProtocols() == null || transfer.getProtocols().size() == 0)
        {
            log.debug("Found zero protocols in returned transfer, checking "
                    + "job for error details.");
            Job job = getJob();
            if (job.getExecutionPhase().equals(ExecutionPhase.ERROR) &&
                    job.getErrorSummary() != null)
            {
                throw new RuntimeException("Transfer Failure: " +
                        job.getErrorSummary().getSummaryMessage());
            }
            else
            {
                throw new IllegalStateException("Job with no protocol endpoints received for job "
                        + job.getID());
            }
        }
    }
   
    // pick one of the endpoints
    private URL findGetEndpoint()
        throws MalformedURLException
    {
        String ret = transfer.getEndpoint(VOS.PROTOCOL_HTTP_GET);
        if (ret == null)
            ret = transfer.getEndpoint(VOS.PROTOCOL_HTTPS_GET);
        if (ret == null)
            throw new RuntimeException("failed to find a usable endpoint URL");
        return new URL(ret);
    }
    // pick one of the endpoints
    private URL findPutEndpoint()
        throws MalformedURLException
    {
        String ret = transfer.getEndpoint(VOS.PROTOCOL_HTTP_PUT);
        if (ret == null)
            ret = transfer.getEndpoint(VOS.PROTOCOL_HTTPS_PUT);
        if (ret == null)
            throw new RuntimeException("failed to find a usable endpoint URL");
        return new URL(ret);
    }

    private void doUpload()
        throws IOException
    {
        URL url = findPutEndpoint();
        log.debug(url);

        if (localFile == null && wrapper == null)
            throw new IllegalStateException("cannot perform upload without a File or OutputStreamWrapper");

        HttpUpload upload = null;
        if (localFile != null)
            upload = new HttpUpload(localFile, url);
        else
            upload = new HttpUpload(wrapper, url);
        
        log.debug("calling HttpUpload.setRequestProperties with " + httpRequestProperties.size() + " props");
        upload.setRequestProperties(httpRequestProperties);
        upload.setMaxRetries(maxRetries);
        if (transListener != null)
            upload.setTransferListener(transListener);
        if (sslSocketFactory != null)
            upload.setSSLSocketFactory(sslSocketFactory);
        upload.run();
        if (upload.getThrowable() != null)
        {
            // allow illegal arugment exceptions through
            if (upload.getThrowable() instanceof IllegalArgumentException)
            {
                throw (IllegalArgumentException) upload.getThrowable();
            }
            else
            {
                try
                {
                    throw new IOException("failed to upload file", upload.getThrowable());
                }
                catch (NoSuchMethodError e)
                {
                    // Java5 does not have the above constructor.
                    throw new IOException("failed to upload file: " + upload.getThrowable().getMessage());
                }
            }

        }
    }

    private void doDownload()
        throws IOException, MalformedURLException
    {
        URL url = findGetEndpoint();
        log.debug(url);
        if (localFile == null)
            throw new IllegalStateException("cannot perform download without a local File");
        
        HttpDownload download = new HttpDownload(url, localFile);
        download.setOverwrite(true);
        download.setRequestProperties(httpRequestProperties);
        download.setMaxRetries(maxRetries);
        if (transListener != null)
            download.setTransferListener(transListener);
        if (sslSocketFactory != null)
            download.setSSLSocketFactory(sslSocketFactory);
        download.run();
        if (download.getThrowable() != null)
        {
            throw new IOException("failed to download file", download.getThrowable());
        }
        // the actual resulting file
        this.localFile = download.getFile();
    }

    // run and monitor an async server side transfer
    private void doServerTransfer()
        throws IOException, InterruptedException
    {
        try
        {
            URL phaseURL = new URL(jobURL.toExternalForm() + "/phase");
            String parameters = "PHASE=RUN";
            
            HttpURLConnection connection = (HttpURLConnection) phaseURL.openConnection();
            if (connection instanceof HttpsURLConnection)
                initHTTPS((HttpsURLConnection) connection);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Length", "" + Integer.toString(parameters.getBytes().length));
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setDoInput(true);

            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(parameters.getBytes("UTF-8"));
            outputStream.close();

            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();
            String errorBody = NetUtil.getErrorBody(connection);
            if (StringUtil.hasText(errorBody))
                responseMessage += ": " + errorBody;
            
            switch (responseCode)
            {
                case 200: // successful
                case 303: // redirect to jobURL
                    break;
                case 500:
                    throw new RuntimeException(responseMessage);
                case 401:
                    throw new AccessControlException(responseMessage);
                case 404:
                    throw new IllegalArgumentException(responseMessage);
                default:
                    throw new RuntimeException("unexpected failure mode: " + responseMessage + "(" + responseCode + ")");
            }

            if (monitorAsync)
            {
                while (phase == null)
                {
                    Thread.sleep(POLL_INTERVAL);
                    getPhase();
                }
            }
        }
        catch(MalformedURLException bug)
        {
            throw new RuntimeException("BUG: failed to create phase url", bug);
        }
        finally
        {

        }
    }

    private void initHTTPS(HttpsURLConnection sslConn)
    {
        if (sslSocketFactory == null) // lazy init
        {
            log.debug("initHTTPS: lazy init");
            AccessControlContext ac = AccessController.getContext();
            Subject s = Subject.getSubject(ac);
            this.sslSocketFactory = SSLUtil.getSocketFactory(s);
        }
        if (sslSocketFactory != null && sslConn != null)
        {
            log.debug("setting SSLSocketFactory on " + sslConn.getClass().getName());
            sslConn.setSSLSocketFactory(sslSocketFactory);
        }
    }
}
