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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.AccessControlException;
import java.util.Date;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import javax.net.ssl.HttpsURLConnection;

import org.apache.log4j.Logger;

import ca.nrc.cadc.net.event.TransferEvent;
import ca.nrc.cadc.util.FileMetadata;
import ca.nrc.cadc.util.StringUtil;

/**
 * Simple task to encapsulate a single download (GET). This class supports http and https
 * (SSL) using the specified SSLSocketFactory (or by creating one from the current Subject);
 * the SSLSocketFactory and/or Subject are only really needed if the server the client to have
 * an X509 certificate. This class also supports retrying downloads if the server responds
 * with a 503 and a valid Retry-After header, where valid means an integer (number of seconds)
 * that is between 0 and HttpTransfer.MAX_RETRY_DELAY.
 *
 * Note: Redirects are followed by default.
 * 
 * @author pdowler
 */
public class HttpDownload extends HttpTransfer
{
    private static Logger log = Logger.getLogger(HttpDownload.class);

    private static int NONE = 0;
    private static int GZIP = 1;
    private static int ZIP = 2;

    private boolean headOnly = false;
    private String logAction = "HTTP GET";
    private boolean decompress = false;
    private boolean overwrite = false;
    
    private File destDir = null;
    private File origFile;
    private File decompFile;
    private File removeFile;
    private int decompressor;

    private OutputStream destStream;
    
    private File destFile;
    private InputStreamWrapper wrapper;
    
    //private boolean skipped = false;
    private String contentType;
    private String contentEncoding;
    private String contentMD5;
    private long contentLength = -1;
    private long decompSize = -1;
    private long size = -1;
    private long lastModified = -1;

    /**
     * Constructor with default user-agent string.
     * 
     * @see HttpDownload(String,URL,File)
     * @param src URL to read
     * @param dest file or directory to write to
     */
    public HttpDownload(URL src, File dest)
    {
        this(null, src, dest);
    }

    /**
     * Constructor with default user-agent string.
     * @see HttpDownload(String,URL,OutputStream)
     * @param src URL to read
     * @param dest output stream to write to
     */
    public HttpDownload(URL src, OutputStream dest)
    {
        this(null,src,dest);
    }

    public HttpDownload(URL src, InputStreamWrapper dest)
    {
        this(null, src, dest);
    }

    /**
     * Constructor. If the user agent string is not supplied, a default value will be generated.
     * </p><p>
     * The src URL cannot be null. If the protocol is https, this class will get the current Subject from
     * the AccessControlContext and use the Certificate(s) and PrivateKey(s) found there to set up an
     * SSLSocketFactory. This is required if ther server requests that the client authenticate itself.
     * </p><p>
     * The dest File cannot be null. If dest is a directory, the downloaded
     * file will be saved in that directory and the filename will be determined from the HTTP headers or
     * URL. If dest is an existing file or it does not exist but it's parent is a directory, dest will
     * be used directly.
     *
     * @param userAgent user-agent string to report in HTTP headers
     * @param src URL to read
     * @param dest file or directory to write to
     */
    public HttpDownload(String userAgent, URL src, File dest)
    {
        super(true);
        setUserAgent(userAgent);
        
        if (src == null)
            throw new IllegalArgumentException("source URL cannot be null");
        if (dest == null)
            throw new IllegalArgumentException("destination File cannot be null");

        if (dest.exists() && dest.isDirectory())
        {
            this.destDir = dest;
        }
        else
        {
            File parent = dest.getParentFile();
            if (parent == null) // relative path
                throw new IllegalArgumentException("destination File cannot be relative");
            //if (parent.exists() && parent.isDirectory())
            //{
                this.destDir = parent;
                this.localFile = dest;
            //}
            //else
            //    throw new IllegalArgumentException("destination File parent must be a directory that exists");
        }

        // dest does not exist == dest is the file to write
        // dest exists and is a file = dest is the file to (over)write
        // dest exists and it a directory == dest is the parent, we determine filename
        // all other path components are directories, if the do not exist we create them

        this.remoteURL = src;
    }

    /**
     * Constructor. If the user agent string is not supplied, a default value will be generated.
     * </p><p>
     * The src URL cannot be null. If the protocol is https, this class will get the current Subject from
     * the AccessControlContext and use the Certificate(s) and PrivateKey(s) found there to set up an
     * SSLSocketFactory. This is required if ther server requests that the client authenticate itself.
     * </p><p>
     * The dest output stream cannot be null.
     *
     * @param userAgent user-agent string to report in HTTP headers
     * @param src URL to read
     * @param dest output stream to write to
     */
    public  HttpDownload(String userAgent, URL src, OutputStream dest)
    {
        super(true);
        setUserAgent(userAgent);
        if (src == null)
            throw new IllegalArgumentException("source URL cannot be null");
        if (dest == null)
            throw new IllegalArgumentException("destination stream cannot be null");
        this.remoteURL = src;
        this.destStream = dest;
    }

    public HttpDownload(String userAgent, URL src, InputStreamWrapper dest)
    {
        super(true);
        setUserAgent(userAgent);
        if (src == null)
            throw new IllegalArgumentException("source URL cannot be null");
        if (dest == null)
            throw new IllegalArgumentException("destination wrapper cannot be null");
        this.remoteURL = src;
        this.wrapper = dest;
    }

    // unused
    private HttpDownload() { super(true); }

    @Override
    public String toString() 
    {
        if (localFile == null)
            return "HttpDownload[" + remoteURL + "]";
        return "HttpDownload[" + remoteURL + "," + localFile + "]";
    }

    /** 
     * Set mode so only an HTTP HEAD will be performed. After the download is run(),
     * the http header parameters from the response can be checked via various
     * get methods.
     * 
     * @param headOnly
     */
    public void setHeadOnly(boolean headOnly)
    {
        this.headOnly = headOnly;
        if (headOnly)
            this.logAction = "HTTP HEAD";
    }


    /**
     * Enable optional decompression of the data after download. GZIP and ZIP are supported.
     * @param decompress
     */
    public void setDecompress(boolean decompress)
    {
        this.decompress = decompress;
    }

    /**
     * Enable forced overwrite of existing destiantion file.
     * 
     * @param overwrite
     */
    public void setOverwrite(boolean overwrite)
    {
        this.overwrite = overwrite;
    }
    
    /**
     * Get the size of the result file. This may be smaller than the content-length if the
     * file is being decompressed.
     * 
     * @return the size in bytes, or -1 of unknown
     */
    public long getSize() { return size; }

    /**
     * Get the content-type returned by the server.
     * @return
     */
    public String getContentType()
    {
        return contentType;
    }

    /**
     * Get the content-encoding returned by the server.
     * @return
     */
    public String getContentEncoding()
    {
        return contentEncoding;
    }

    /**
     * Get the size of the download (the Content-Length).
     *
     * @return the content-length or -1 of unknown
     */
    public long getContentLength() { return contentLength; }
    
    /**
     * Get the md5 sum of the download (the Content-MD5).
     * 
     * @return the content-md5 or null if unknown
     */
    public String getContentMD5() { return contentMD5; }

    /**
     * Get a reference to the result file. In some cases this is null until the
     * download is complete.
     * 
     * @return reference to the output file or null if download failed
     */
    public File getFile() { return destFile; }

    /**
     * Run the download. This method is intended to be run via a Thread (or pool)
     * but can be called directly. The safe way to stop the download is to call the
     * terminate() method (@see HttpTransfer#terminate()).
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
                    long dt = 1000L * ex.getRetryDelay(); // to milliseconds
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
            // store the thread so that other threads (typically the
            // Swing event thread) can terminate the Download
            this.thread = Thread.currentThread();
            
            fireEvent(TransferEvent.CONNECTING);

            doGet();
            
            if (decompress && decompressor != NONE)
            {
                fireEvent(decompFile, TransferEvent.DECOMPRESSING);
                doDecompress();
            }
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
        catch(AccessControlException ex)
        {
            failure = ex;
        }
        catch(Throwable t)
        {
            failure = t;
            if (log.isDebugEnabled())
                log.debug("unexpected transfer failure", t);
        }
        finally
        {
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
                        
            if (failure == null && removeFile != null) // only remove if download was successful
            {
                log.debug("removing: " + removeFile);
                fireEvent(removeFile, TransferEvent.DELETED);
                removeFile.delete();
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
                FileMetadata meta = new FileMetadata();
                meta.setContentType(contentType);
                meta.setContentEncoding(contentEncoding);
                meta.setContentLength(contentLength);
                meta.setMd5Sum(contentMD5);
                meta.setLastModified(new Date(lastModified));
                fireEvent(destFile, TransferEvent.COMPLETED, meta);
            }
        }
    }

    protected boolean askOverwrite(File f, Long length, Long lastMod)
    {
        return overwrite ||
            (
                overwriteChooser != null &&
                overwriteChooser.overwriteFile(f.getAbsolutePath(), f.length(), f.lastModified(), length, lastMod)
            );
    }

    // determine which file to read and write, enable optional decompression
    private boolean doCheckDestination()
        throws InterruptedException
    {
        // check/clear interrupted flag and throw if necessary
        if ( Thread.interrupted() )
            throw new InterruptedException();
            
        boolean doDownload = true;
        if (origFile.exists())
        {
            log.debug(origFile + " exists");
            if ( askOverwrite(origFile, contentLength, lastModified) )
            {
                log.debug("overwrite: YES -- " + origFile);
                origFile.delete();
                if (decompFile != null && decompFile.exists())
                    decompFile.delete();
                if (decompress && decompressor != NONE)
                    this.destFile = decompFile; // download and decompress
                else
                    this.destFile = origFile; // download
            }
            else
            {
                log.debug("overwrite: NO -- " + origFile);
                if (decompress && decompressor != NONE)
                {
                    decompFile.delete();
                    doDownload = false;
                    this.destFile = decompFile; // decomp only
                    this.removeFile = origFile; // remove after decompress
                }
                else
                {
                    doDownload = false;
                    //this.skipped = true;
                }
            }
        }
        else if (decompFile != null && decompFile.exists())
        {
            log.debug(decompFile + " exists");
            if ( askOverwrite(decompFile, decompSize, lastModified) )
            {
                log.debug("overwrite: YES -- " + decompFile);
                //decompFile.delete(); // origFile does not exist
                this.removeFile = decompFile;
                if (decompress && decompressor != NONE)
                    this.destFile = decompFile;    // download and decompress
                else
                    this.destFile = origFile;      // download
            }
            else
            {
                log.debug("overwrite: NO -- " + decompFile);
                this.destFile = decompFile;
                this.removeFile = null;
                doDownload = false;
                //this.skipped = true;
            }
        }
        else
        {
            // found no local files that match
            if (decompress && decompressor != NONE && decompFile != null)
                this.destFile = decompFile;
            else
                this.destFile = origFile;
        }
        log.debug("destination file: " + destFile);
        this.localFile = destFile;
        return doDownload;
    }

    // called from doHead and doGet to capture HTTP standard header values
    private void processHeader(HttpURLConnection conn)
        throws IOException, InterruptedException
    {
        this.contentEncoding = conn.getHeaderField(("Content-Encoding"));
        this.contentType = conn.getContentType();
        this.contentMD5 = conn.getHeaderField("Content-MD5");

        // parse this ourselves to get 64-bit sizes: conn.getContentLength returns int
        String cl = conn.getHeaderField("Content-Length");
        if (cl != null)
        {
            try { this.contentLength = Long.parseLong(cl); }
            catch(NumberFormatException ignore) { }
        }

        // custom CADC header
        String ucl = conn.getHeaderField("X-Uncompressed-Length");
        if (ucl != null)
        {
            try { this.decompSize = Long.parseLong(ucl); }
            catch(NumberFormatException ignore) { }
        }

        this.lastModified = conn.getLastModified();
        
        if (destStream == null && wrapper == null) // download to file: extra metadata
        {
            // determine filename and use destDir
            String origFilename = null;

            // first option: use what the caller suggested
            if (localFile != destDir)
                this.origFile = localFile;

            if (origFile == null)
            {
                // second option: use supplied filename if present in http header
                String cdisp = conn.getHeaderField("Content-Disposition");
                log.debug("HTTP HEAD: Content-Disposition = " + cdisp);
                if ( cdisp != null )
                    origFilename = parseContentDisposition(cdisp);

                // last resort: pull something from the end of the URL
                if (origFilename == null)
                {
                    String s = remoteURL.getPath();
                    String query = remoteURL.getQuery();
                    int i = s.lastIndexOf('/');
                    if (i != -1 && i < s.length() - 1)
                        origFilename = s.substring(i + 1, s.length());
                    if (query != null)
                        origFilename += "?" + query;
                }
                // very last resort for no path: use hostname
                if (origFilename == null)
                {
                    origFilename = remoteURL.getHost();
                }
                this.origFile = new File(destDir, origFilename);
            }
            origFilename = origFile.getName();

            // encoding mucks with filename
            if ("gzip".equals(contentEncoding) || origFilename.endsWith(".gz"))
            {
                if (origFilename.endsWith(".gz"))
                    this.decompFile = new File(destDir, origFilename.substring(0, origFilename.length() - 3));
                else
                {
                    this.decompFile = origFile;
                    this.origFile = new File(destDir, origFilename + ".gz");
                }
                this.decompressor = GZIP;
            }
            else if ("zip".equals(contentEncoding) || origFilename.endsWith(".zip"))
            {
                if (origFilename.endsWith(".zip"))
                    this.decompFile = new File(destDir, origFilename.substring(0, origFilename.length() - 4));
                else
                {
                    this.decompFile = origFile;
                    this.origFile = new File(destDir, origFilename + ".zip");
                }
                this.decompressor = ZIP;
            }
        }

        log.debug("   original file: " + origFile);
        log.debug("     decomp file: " + decompFile);
        log.debug("  content length: " + contentLength);
        log.debug("     content md5: " + contentMD5);
        log.debug("    content type: " + contentType);
        log.debug("content encoding: " + contentEncoding);
        log.debug("     decomp size: " + decompSize);
        log.debug("    decompressor: " + decompressor);
        log.debug("    lastModified: " + lastModified);
    }

    private int checkStatusCode(HttpURLConnection conn)
        throws IOException, TransientException
    {
        int code = conn.getResponseCode();
        this.responseCode = code;
        log.debug(logAction+" status: " + code + " for " + remoteURL);

        String location = conn.getHeaderField("Location");
        if ((code == HttpURLConnection.HTTP_SEE_OTHER
            || code == HttpURLConnection.HTTP_MOVED_TEMP) 
            && location != null)
        {
            this.redirectURL = new URL(location);
        }
        else if (code != HttpURLConnection.HTTP_OK)
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
                    throw new AccessControlException("authentication failed " + msg);
                case HttpURLConnection.HTTP_FORBIDDEN:
                    throw new AccessControlException("authorization failed " + msg);
                case HttpURLConnection.HTTP_NOT_FOUND:
                    throw new FileNotFoundException("resource not found " + msg);
                default:
                    throw new IOException(msg);
            }
        }
        return code;
    }

    private void doGet()
        throws IOException, InterruptedException, TransientException
    {
        // check/clear interrupted flag and throw if necessary
        if ( Thread.interrupted() )
            throw new InterruptedException();
        
        InputStream istream = null;
        OutputStream ostream = null;
        try
        {
            // open connection
            HttpURLConnection conn = (HttpURLConnection) remoteURL.openConnection();
            log.debug("HttpURLConnection type: " + conn.getClass().getName() + " for GET " + remoteURL);
            if (conn instanceof HttpsURLConnection)
            {
                HttpsURLConnection sslConn = (HttpsURLConnection) conn;
                initHTTPS(sslConn);
            }

            setRequestSSOCookie(conn);
            conn.setInstanceFollowRedirects(followRedirects);
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("User-Agent", userAgent);
            for (HttpRequestProperty rp : requestProperties)
            {
                conn.setRequestProperty(rp.getProperty(), rp.getValue());
            }

            if (headOnly)
                conn.setRequestMethod("HEAD");
            else
                conn.setRequestMethod("GET");

            int code = checkStatusCode(conn);
            processHeader(conn);

            if (headOnly || (!followRedirects && redirectURL != null))
                return;

            // evaulate overwrite of complete file
            boolean doDownload = true;
            if (destStream == null && wrapper == null)
                doDownload = doCheckDestination();

            // go=false means cancelled, doDownload==false means skipped
            go = go && doDownload;
            if (!go)
                return;

            // evaluate possible resume?
            File tmp = origFile;
            
            this.size = contentLength;
            String pkey = null;
            String pvalue = null;
            boolean append = false;
            long startingPos = 0;
            if (destStream == null && wrapper == null) // downloading to file
            {
                // temporary destination
                origFile = new File(origFile.getAbsolutePath() + ".part");
                if (origFile.exists() && origFile.length() < contentLength) // partial file from previous download
                {
                    pkey = "Range";
                    pvalue = "bytes=" + origFile.length() + "-"; // open ended
                }
            }

            if (pkey != null) // open 2nd connection with a range request
            {
                HttpURLConnection rconn = (HttpURLConnection) remoteURL.openConnection();
                log.debug("HttpURLConnection type: " + conn.getClass().getName() + " for GET " + remoteURL);
                if (rconn instanceof HttpsURLConnection)
                {
                    HttpsURLConnection sslConn = (HttpsURLConnection) rconn;
                    initHTTPS(sslConn);
                }

                setRequestSSOCookie(rconn);
                rconn.setInstanceFollowRedirects(true);
                rconn.setRequestProperty("Accept", "*/*");
                rconn.setRequestProperty("User-Agent", userAgent);

                for (HttpRequestProperty rp : requestProperties)
                    rconn.setRequestProperty(rp.getProperty(), rp.getValue());
                log.debug("trying: " + pkey + " = " + pvalue);
                rconn.setRequestProperty(pkey, pvalue);
                rconn.setRequestMethod("GET");
                int rcode = rconn.getResponseCode();
                log.debug(logAction + " status: " + rcode + " for range request to " + remoteURL);
                if (pkey != null && code == 416) // server doesn't like range
                {
                    try 
                    {
                        log.debug("cannot resume: closing second connection");
                        rconn.disconnect();
                    }
                    catch(Exception ignore) { }
                    // proceed with original connection
                }
                else
                {
                    try 
                    {
                        log.debug("can resume: closing first connection");
                        conn.disconnect();
                    }
                    catch(Exception ignore) { }
                    conn = rconn; // use the second connection with partial
                    code = rcode;
                }
            }
            
            if (pkey != null && code == HttpURLConnection.HTTP_PARTIAL)
            {
                String cr = conn.getHeaderField("Content-Range");
                log.debug("Content-Range = " + cr);
                if (cr != null)
                {
                    cr = cr.trim();
                    if (cr.startsWith("bytes"))
                    {
                        cr = cr.substring(6);
                        String[] parts = cr.split("-");
                        startingPos = Long.parseLong(parts[0]);
                        log.debug("found startingPos = " + startingPos);
                        String[] ss = cr.split("/");
                        this.size = Long.parseLong(ss[1]);
                        log.debug("found real size = " + size);
                        append = true;
                    }
                }
            }
            else
            {
                checkStatusCode(conn);
            }
            
            fireEvent(TransferEvent.CONNECTED);

            // check eventID hook
            findEventID(conn);

            fireEvent(origFile, TransferEvent.TRANSFERING);

            istream = conn.getInputStream();
            if ( !(istream instanceof BufferedInputStream) )
            {
                log.debug("using BufferedInputStream");
                istream = new BufferedInputStream(istream, bufferSize);
            }

            if (this.destStream != null)
            {
                log.debug("output: supplied OutputStream");
                ostream = destStream;
                log.debug("using BufferedOutputStream");
                ostream = new BufferedOutputStream(ostream, bufferSize);
            }
            else if (wrapper == null)
            {
                // prepare to write to origFile
                File parent = origFile.getParentFile();
                parent.mkdirs();
                if ( !parent.exists() )
                    throw new IOException("failed to create one or more parent dir(s):" + parent);

                log.debug("output: " + origFile + " append: " + append);
                ostream = new FileOutputStream(origFile, append);
                log.debug("using BufferedOutputStream");
                ostream = new BufferedOutputStream(ostream, bufferSize);
            }

            if (wrapper != null)
                wrapper.read(istream);
            else
            {
                if (use_nio)
                    nioLoop(istream, ostream, 2*bufferSize, startingPos);
                else
                    ioLoop(istream, ostream, 2*bufferSize, startingPos);
            }

            if (ostream != null)
                ostream.flush();

            log.debug("download completed");
            if (destStream == null && wrapper == null) // downloading to file
            {
                log.debug("renaming " + origFile +" to " + tmp);
                origFile.renameTo(tmp);
                origFile = tmp;
                destFile = tmp;
            }
        }
        finally
        {
            if (istream != null)
            {
                log.debug("closing InputStream");
                try { istream.close(); }
                catch(Exception ignore) { }
            }
            if (ostream != null)
            {
                log.debug("closing OutputStream");
                try { ostream.close(); }
                catch(Exception ignore) { }
            }
        }
    }

    private void doDecompress()
        throws IOException, InterruptedException
    {
        // check/clear interrupted flag and throw if necessary
        if ( Thread.interrupted() )
            throw new InterruptedException();
        
        InputStream istream = null;
        OutputStream ostream = null;
        //RandomAccessFile ostream = null;
        try
        {
            this.size = decompSize;
            int sz = bufferSize;
            if (decompressor == GZIP)
            {
                log.debug("input: GZIPInputStream(BufferedInputStream(FileInputStream)");
                istream = new GZIPInputStream(new FileInputStream(origFile), sz);
            }
            else if (decompressor == ZIP)
            {
                log.debug("input: ZIPInputStream(BufferedInputStream(FileInputStream)");
                istream = new ZipInputStream(new BufferedInputStream(new FileInputStream(origFile)));
            }
            log.debug("output: " + decompFile);
            ostream = new BufferedOutputStream(new FileOutputStream(decompFile), sz);

            this.removeFile = origFile;

            if (use_nio)
                nioLoop(istream, ostream, sz, 0);
            else
                ioLoop(istream, ostream, sz, 0);
            ostream.flush();

            this.destFile = decompFile; // ?? 
        }
        finally
        {
            if (istream != null)
                try { istream.close(); }
                catch(Exception ignore) { }
            if (ostream != null)
                try { ostream.close(); }
                catch(Exception ignore) { }
        }
    }
    
    private static char SINGLE_QUOTE = "'".charAt(0);
    private static char DOUBLE_QUOTE = "\"".charAt(0);

    private static boolean isFilenameDisposition(String cdisp)
    {
        if (cdisp == null)
            return false;
        cdisp = cdisp.toLowerCase(); // just for checking
        // HACK: HTTP/1.1 allows attachment or extension token, but some sites use inline anyway
        return ( cdisp.startsWith("attachment") || cdisp.startsWith("inline") );
    }

    /**
     * Parse a Content-Disposition header value and extract the filename.
     *
     * @param cdisp value of the Content-Disposition header
     * @return a filename, or null
     */
    public static String parseContentDisposition(String cdisp)
    {
        if ( !isFilenameDisposition(cdisp) )
            return null;
        
        // TODO: should split on ; and check each part for filename=something
        // extra filename from cdisp value
        String[] parts = cdisp.split(";");
        for (int p=0; p<parts.length; p++)
        {
            String part = parts[p].trim();
            // check/remove double quotes
            if (part.charAt(0) == '"')
                part = part.substring(1,part.length());
            if (part.charAt(part.length()-1) == '"')
                part = part.substring(0, part.length() - 1);
            if (part.startsWith("filename"))
            {
                int i = part.indexOf('=');
                String filename = part.substring(i + 1, part.length());

                // strip off optional quotes
                char c1 = filename.charAt(0);
                char c2 = filename.charAt(filename.length()-1);
                boolean rs = (c1 == SINGLE_QUOTE || c1 == DOUBLE_QUOTE);
                boolean re = (c2 == SINGLE_QUOTE || c2 == DOUBLE_QUOTE);
                if (rs && re)
                    filename = filename.substring(1, filename.length() - 1);
                else if (rs)
                    filename = filename.substring(1, filename.length());
                else if (re)
                    filename = filename.substring(0, filename.length() - 1);

                // strip off optional path information
                i = filename.lastIndexOf('/'); // unix
                if (i >= 0)
                    filename = filename.substring(i+1);
                i = filename.lastIndexOf('\\'); // windows
                if (i >= 0)
                    filename = filename.substring(i+1);

                // TODO: check/sanitize for security issues  

                return filename;
            }
        }
        return null;
     }

}
