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

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.AccessControlException;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

import org.apache.log4j.Logger;

import ca.nrc.cadc.util.StringUtil;

/**
 * Perform an HTTP Post.
 * 
 * Post data can be supplied as either parameters in a map or as a single
 * string.  For Posts that may result in large response data, the constructor
 * with an output stream should be used.
 * 
 * @author majorb, pdowler
 *
 */
public class HttpPost extends HttpTransfer
{
    private static Logger log = Logger.getLogger(HttpPost.class);

    // request information
    private Map<String,Object> map;
    private String content;
    private String contentType;
    private OutputStream outputStream;

    // result information
    private String responseContentType;
    private String responseContentEncoding;
    private String responseBody;
    
    /**
     * HttpPost contructor.  Redirects will be followed.
     * Ideal for large expected responses.
     * 
     * @param url The POST destination.
     * @param map A map of the data to be posted.
     * @param outputStream An output stream to capture the response data.
     */
    public HttpPost(URL url, Map<String, Object> map, OutputStream outputStream)
    {
        super(true);
        this.remoteURL = url;
        this.map = map;
        this.outputStream = outputStream;
        this.followRedirects = true;
        if (url == null)
            throw new IllegalArgumentException("dest cannot be null.");
        if (map == null || map.size() == 0)
            throw new IllegalArgumentException("parameters cannot be empty.");
        
    }
    
    /**
     * HttpPost contructor.
     * 
     * @param url The POST destination.
     * @param map A map of the data to be posted.
     * @param followRedirects Whether or not to follow server redirects.
     */
    public HttpPost(URL url, Map<String, Object> map, boolean followRedirects)
    {
        super(followRedirects);
        this.remoteURL = url;
        this.map = map;
        if (url == null)
            throw new IllegalArgumentException("dest cannot be null.");
        if (map == null || map.isEmpty())
            throw new IllegalArgumentException("parameters cannot be empty.");
    }
    
    /**
     * HttpPost constructor.
     * 
     * @param url The POST destination
     * @param content The content to post.
     * @param contentType The type of the content.
     * @param followRedirects Whether or not to follow server redirects.
     */
    public HttpPost(URL url, String content, String contentType, boolean followRedirects)
    {
        super(followRedirects);
        this.remoteURL = url;
        this.content = content;
        this.contentType = contentType;
        if (url == null)
            throw new IllegalArgumentException("dest cannot be null.");
        if (!StringUtil.hasText(content))
            throw new IllegalArgumentException("cannot have empty content.");
    }

    @Override
    public String toString() { return "HttpPost[" + remoteURL + "]"; }
    
    public String getResponseContentEncoding()
    {
        return responseContentEncoding;
    }

    public String getResponseContentType()
    {
        return responseContentType;
    }

    /**
     * If an OutputStream wasn't supplied in the HttpPost constructor,
     * the response can be retrieved here.
     * @return
     */
    public String getResponseBody()
    {
        return responseBody;
    }

    /**
     * Retry on TransientExceptions
     */
    public void run()
    {
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
                    log.debug("retry " + numRetries + " sleeping  for " + dt);
                    Thread.sleep(dt);
                }
                catch(InterruptedException iex)
                {
                    log.debug("retry interrupted");
                    done = true;
                }
            }
        }
    }

    private void runX()
        throws TransientException
    {
        log.debug(this.toString());

        try
        {
            this.thread = Thread.currentThread();
            HttpURLConnection conn = (HttpURLConnection) this.remoteURL.openConnection();

            if (conn instanceof HttpsURLConnection)
            {
                HttpsURLConnection sslConn = (HttpsURLConnection) conn;
                initHTTPS(sslConn);
            }
            
            if (map == null)
                doPost(conn, content, contentType);
            else
                doPost(conn, map);
            
        }
        catch(TransientException tex)
        {
            log.debug("caught: " + tex);
            throw tex;
        }
        catch(Throwable t)
        {
            log.debug("caught: " + t, t);
            failure = t;
        }
        finally
        {
            if (outputStream != null)
            {
                log.debug("closing OutputStream");
                try { outputStream.close(); }
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
            
            if (failure != null)
            {
                log.debug("failed: " + failure);
            }
        }
    }
    
    private void doPost(HttpURLConnection conn, Map<String, Object> parameters)
        throws IOException, InterruptedException, TransientException
    {
        StringBuilder content = new StringBuilder();
        Set<String> keys = parameters.keySet();
        Object value = null;
        for (String key : keys)
        {
            value = parameters.get(key);
            content.append(key);
            content.append("=");
            content.append( URLEncoder.encode(value.toString(), "UTF-8") );
            content.append("&");
        }
        // trim off the last ampersand
        content.setLength(content.length() - 1);
        doPost(conn, content.toString(), "application/x-www-form-urlencoded");
    }
    
    private void doPost(HttpURLConnection conn, String content, String contentType)
        throws IOException, InterruptedException, TransientException
    {
        setRequestSSOCookie(conn);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Length", ""
                + Integer.toString(content.getBytes("UTF-8").length));
        
        if (contentType != null)
            conn.setRequestProperty("Content-Type", contentType);
        else
            conn.setRequestProperty("Content-Type", "text/plain");
        
        conn.setInstanceFollowRedirects(followRedirects);
        conn.setUseCaches(false);
        conn.setDoOutput(true);
        conn.setDoInput(true);

        log.debug("POST - writing content: " + content);
        OutputStream ostream = conn.getOutputStream();
        ostream.write(content.getBytes("UTF-8"));
        ostream.flush();
        ostream.close();
        log.debug("POST - done: " + remoteURL.toString());
        
        int statusCode = checkStatusCode(conn);
        this.responseCode = statusCode;
        
        // check for a redirect
        String location = conn.getHeaderField("Location");
        if ((statusCode == HttpURLConnection.HTTP_SEE_OTHER
            || statusCode == HttpURLConnection.HTTP_MOVED_TEMP) 
            && location != null)
        {
            this.redirectURL = new URL(location);
        }
        else
        {
            this.responseContentType = conn.getContentType();
            this.responseContentEncoding = conn.getContentEncoding();
            // otherwise get the response content
            InputStream istream = conn.getInputStream();
            if (outputStream != null)
            {
                if (use_nio)
                    nioLoop(istream, outputStream, 2*bufferSize, 0);
                else
                    ioLoop(istream, outputStream, 2*bufferSize, 0);
                outputStream.flush();
            }
            else
            {
                int smallBufferSize = 512;
                ByteArrayOutputStream byteArrayOstream = new ByteArrayOutputStream();
                try
                {
                    if (use_nio)
                        nioLoop(istream, byteArrayOstream, smallBufferSize, 0);
                    else
                        ioLoop(istream, byteArrayOstream, smallBufferSize, 0);
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
    
    private int checkStatusCode(HttpURLConnection conn)
    throws IOException, TransientException
    {
        int code = conn.getResponseCode();
        log.debug("HTTP POST status: " + code + " for " + remoteURL);
        this.responseCode = code;
        
        if (code != HttpURLConnection.HTTP_OK &&
            code != HttpURLConnection.HTTP_MOVED_TEMP &&
            code != HttpURLConnection.HTTP_SEE_OTHER)
        {
            String msg = "(" + code + ") " + conn.getResponseMessage();
            String body = NetUtil.getErrorBody(conn);
            if (StringUtil.hasText(body))
            {
                msg = msg + ": " + body;
            }
            checkTransient(code, msg, conn);
            switch(code)
            {
                case HttpURLConnection.HTTP_NO_CONTENT:
                    break;
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                    throw new AccessControlException("permission denied: " + msg);
                case HttpURLConnection.HTTP_FORBIDDEN:
                    throw new AccessControlException("permission denied: " + msg);
                case HttpURLConnection.HTTP_NOT_FOUND:
                    throw new FileNotFoundException("resource not found " + msg);
                default:
                    throw new IOException(msg);
            }
        }
        return code;
    }

}
