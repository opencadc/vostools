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

package ca.nrc.cadc.net;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.AccessControlException;

import javax.net.ssl.HttpsURLConnection;

import org.apache.log4j.Logger;

import ca.nrc.cadc.net.event.TransferEvent;

/**
 * Perform an upload (PUT). 
 * 
 * Note: Redirects are never followed.
 * 
 * @author zhangsa, pdowler
 *
 */
public class HttpUpload extends HttpTransfer
{
    private static Logger log = Logger.getLogger(HttpUpload.class);

    private String contentType;
    private String contentEncoding;
    private String contentMD5;
    private String responseBody;

    private InputStream istream;
    private OutputStreamWrapper wrapper;

    public HttpUpload(File src, URL dest)
    {
        super(false);
        this.localFile = src;
        this.remoteURL = dest;
        if (remoteURL == null)
            throw new IllegalArgumentException("destination URL cannot be null");
        if (localFile == null) 
            throw new IllegalArgumentException("source File cannot be null");
    }

    public HttpUpload(InputStream src, URL dest)
    {
        super(false);
        this.istream = src;
        this.remoteURL = dest;
        if (remoteURL == null)
            throw new IllegalArgumentException("destination URL cannot be null");
        if (istream == null)
            throw new IllegalArgumentException("source InputStream cannot be null");
    }

    public HttpUpload(OutputStreamWrapper src, URL dest)
    {
        super(false);
        this.wrapper = src;
        this.remoteURL = dest;
        if (remoteURL == null)
            throw new IllegalArgumentException("destination URL cannot be null");
        if (wrapper == null)
            throw new IllegalArgumentException("source OutputStreamWrapper cannot be null");
    }
    
    // unused
    private HttpUpload() { super(false); }

    @Override
    public void setFollowRedirects(boolean followRedirects)
    {
        if (followRedirects)
            throw new IllegalArgumentException("followRedirects=true not allowed for upload");
    }

    
    public void setContentEncoding(String contentEncoding)
    {
        this.contentEncoding = contentEncoding;
    }

    public void setContentMD5(String contentMD5)
    {
        this.contentMD5 = contentMD5;
    }

    public void setContentType(String contentType)
    {
        this.contentType = contentType;
    }

    public String getResponseBody()
    {
        return responseBody;
    }

    @Override
    public String toString() { return "HttpUpload[" + remoteURL + "," + localFile + "]"; }

    public void run()
    {
        // currently not feasible since we would have to be able to rewind the
        // stream (istream) or the wrapper impl would have to be idempotent
        // and invokable multiple times... but with some servers (tomcat 5) that
        // will be bad because it accepts the entire stream before we can check
        // the retry response code
        if (istream != null || wrapper != null)
        {
            log.debug("input comes from a stream, disabling retries");
            this.maxRetries = 0;
        }

        boolean done = false;
        while (!done)
        {
            try
            {
                runX();
                done = true;
            }
            catch(TransientException ex)
            {
                try
                {
                    long dt = 1000L * ex.getRetryDelay();
                    log.debug("retry "+numRetries+" sleeping  for " + dt);
                    fireEvent(TransferEvent.RETRYING);
                    Thread.sleep(dt);
                }
                catch(InterruptedException iex)
                {
                    log.debug("retry interrupted");
                    this.go = false;
                    done = true;
                }
            }
        }
    }

    private void runX()
        throws TransientException
    {
        log.debug(this.toString());
        if (!go)
            return; // cancelled while queued, event notification handled in terminate()

        boolean throwTE = false;
        try
        {
            this.thread = Thread.currentThread();

            fireEvent(TransferEvent.CONNECTING);

            HttpURLConnection conn = (HttpURLConnection) this.remoteURL.openConnection();

            if (conn instanceof HttpsURLConnection)
            {
                HttpsURLConnection sslConn = (HttpsURLConnection) conn;
                initHTTPS(sslConn);
            }

            doPut(conn);
        }
        catch(InterruptedException iex)
        {
            // need to catch this or it looks like a failure instead of a cancel
            this.go = false;
        }
        catch(TransientException tex)
        {
            log.debug("caught: " + tex);
            throwTE = true;
            throw tex;
        }
        catch(Throwable t)
        {
            failure = t;
        }
        finally
        {
            if (istream != null)
            {
                log.debug("closing InputStream");
                try { istream.close(); }
                catch(Exception ignore) { }
            }

            synchronized(this) // vs sync block in terminate()
            {
                if (thread != null)
                {
                    // clear interrupt status
                    if ( Thread.interrupted() )
                        go = false;
                    this.thread = null;
                }
            }

            if (!go)
            {
                log.debug("cancelled");
                fireEvent(TransferEvent.CANCELLED);
            }
            else if (failure != null)
            {
                log.debug("failed: " + failure);
                fireEvent(failure);
            }
            else if (!throwTE)
            {
                log.debug("completed");
                fireEvent(TransferEvent.COMPLETED);
            }
        }
    }

    private void doPut(HttpURLConnection conn)
        throws IOException, InterruptedException, TransientException
    {
        OutputStream ostream = null;

        if (localFile != null)
        {
            try
            {
                // Try using the setFixedLengthStreamingMode method that takes a long as a parameter.
                // (Only available in Java 7 and up)
                Method longContentLengthMethod = conn.getClass().getMethod("setFixedLengthStreamingMode", long.class);
                longContentLengthMethod.invoke(conn, new Long(localFile.length()));
                log.debug("invoked setFixedLengthStreamingMode(long)");
            }
            catch (Exception noCanDo)
            {
                // Check if the file size is greater than Integer.MAX_VALUE
                if (localFile.length() > Integer.MAX_VALUE)
                {
                    // Cannot set the header length in the standard fashion, so
                    // set it to chunked streaming mode and set a custom header
                    // for use by servers that recognize this attribute
                    conn.setChunkedStreamingMode(bufferSize);
                    conn.setRequestProperty(CADC_CONTENT_LENGTH_HEADER, Long.toString(localFile.length()));
                    log.debug("invoked setChunkedStreamingMode");
                }
                else
                {
                    // Set the file size with integer representation
                    conn.setFixedLengthStreamingMode((int) localFile.length());
                    log.debug("invoked setFixedLengthStreamingMode(int)");
                }
            }
        }
        else
        {
            // note: settig the bufferSize to be larger than 8k without a content-length
            // seems to cause troble with apache+ajp+tomcat6 in cases where the actual
            // payload is bigger than 8k but smaller than the buffer: avoid it for now (pdd)
            conn.setChunkedStreamingMode(8192);
            log.debug("invoked setChunkedStreamingMode");
        }

        setRequestSSOCookie(conn);
        conn.setRequestMethod("PUT");
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);

        // this seems to fail, maybe not allowed with PUT
        //conn.setRequestProperty("User-Agent", userAgent);

        // Note: conn.setRequestProperty("Content-Length", value) has no effect.  Content
        // length header is set by calling setFixedLengthStreamingMode() (done above)
        if (localFile != null)
            conn.setRequestProperty("Content-Length", Long.toString(localFile.length()));
        if (contentType != null)
        {
            log.debug("set Content-Type="+contentType);
            conn.setRequestProperty("Content-Type", contentType);
        }
        if (contentEncoding != null)
            conn.setRequestProperty("Content-Encoding", contentEncoding);
        if (contentMD5 != null)
            conn.setRequestProperty("Content-MD5", contentMD5);

        log.debug("custom request properties: " + requestProperties.size());
        for (HttpRequestProperty rp : requestProperties)
        {
            String p = rp.getProperty();
            String v = rp.getValue();
            log.debug("set request property: "+p+"="+v);
            conn.setRequestProperty(p, v);
        }

        int bSize = bufferSize;
        if (localFile != null && localFile.length() < bSize)
            bSize = (int) localFile.length();

        IOException ioex = null;
        FileInputStream fin = null;
        InputStream in = null;
        try
        {
            ostream = conn.getOutputStream();

            if (localFile != null)
            {
                fin = new FileInputStream(localFile);
                in = fin;
            }
            else if (istream != null)
                in = istream;
            
            if ( !(ostream instanceof BufferedOutputStream) )
            {
                log.debug("using BufferedOutputStream");
                ostream = new BufferedOutputStream(ostream, bufferSize);
            }

            if (in != null)
            {
                log.debug("using BufferedInputStream");
                in = new BufferedInputStream(in, bufferSize);
            }

            fireEvent(TransferEvent.TRANSFERING);

            if (in != null)
                ioLoop(in, ostream, 2*this.bufferSize, 0);
            else
                 wrapper.write(ostream);

            log.debug("OutputStream.flush");
            ostream.flush();
            log.debug("OutputStream.flush OK");
        }
        catch(IOException ex)
        {
            ioex = ex;
            // dealt with below
        }
        finally
        {
            try
            {
                if (ostream != null)
                {
                    log.debug("OutputStream.close");
                    ostream.close();
                    log.debug("OutputStream.close OK");
                }
            }
            catch(IOException ignore) 
            {
                log.debug("OutputStream.close FAIL", ignore);
            }
            
            try
            {
                if (fin != null)
                    fin.close();
            }
            catch(IOException ignore) { }
        }

        int code = conn.getResponseCode();
        log.debug("code: " + code);
        this.responseCode = code;
        if (code != HttpURLConnection.HTTP_OK)
        {
            String msg = "(" + code + ") " + conn.getResponseMessage();
            checkTransient(code, msg, conn);
            switch(code)
            {
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                    throw new AccessControlException("authentication failed " + msg);
                case HttpURLConnection.HTTP_FORBIDDEN:
                    throw new AccessControlException("authorization failed " + msg);
                case HttpURLConnection.HTTP_NOT_FOUND:
                    throw new FileNotFoundException("resource not found " + msg);
                case HttpURLConnection.HTTP_ENTITY_TOO_LARGE:
                    throw new IOException("No space left - " + msg);
                default:
                    throw new IOException(msg);
            }
        }
        if (ioex != null) // an error writing that was not detected via response code
            throw ioex;

        // Write reponse body for retrieval.
        InputStream inputStream = conn.getInputStream();
        if (inputStream != null)
        {
            int smallBufferSize = 512;
            ByteArrayOutputStream byteArrayOstream = new ByteArrayOutputStream();
            try
            {
                if (use_nio)
                    nioLoop(inputStream, byteArrayOstream, smallBufferSize, 0);
                else
                    ioLoop(inputStream, byteArrayOstream, smallBufferSize, 0);
                byteArrayOstream.flush();
                responseBody = new String(byteArrayOstream.toByteArray(), "UTF-8");
            }
            finally
            {
                if (byteArrayOstream != null)
                {
                    try { byteArrayOstream.close(); }
                    catch(Exception ignore) { }
                }
            }
        }
    }

}
