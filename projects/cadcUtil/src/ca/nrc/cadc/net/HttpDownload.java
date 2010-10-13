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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import javax.net.ssl.HttpsURLConnection;

import org.apache.log4j.Logger;

import ca.nrc.cadc.net.event.TransferEvent;
import ca.nrc.cadc.util.CaseInsensitiveStringComparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Simple task to encapsulate a single download (GET). This class supports http and https
 * (SSL) using the specified SSLSocketFactory (or by creating one from the current Subject);
 * the SSLSocketFactory and/or Subject are only really needed if the server the client to have
 * an X509 certificate.
 *
 * @author pdowler
 */
public class HttpDownload extends HttpTransfer
{
    private static Logger log = Logger.getLogger(HttpDownload.class);

    private static int NONE = 0;
    private static int GZIP = 1;
    private static int ZIP = 2;

    private boolean decompress = false;
    private boolean overwrite = false;
    
    private File destDir = null;
    private File origFile;
    private File decompFile;
    private File removeFile;
    private int decompressor;
    
    private File destFile;
    private boolean skipped = false;
    private String contentType;
    private String contentEncoding;
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
        super();
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
            if (parent.exists() && parent.isDirectory())
            {
                this.destDir = parent;
                this.localFile = dest;
            }
            else
                throw new IllegalArgumentException("destination File parent must be a directory that exists");
        }

        this.remoteURL = src;
    }

    // unused
    private HttpDownload() { }

    @Override
    public String toString() 
    {
        if (localFile == null)
            return "HttpDownload[" + remoteURL + "]";
        return "HttpDownload[" + remoteURL + "," + localFile + "]";
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
        log.debug(this.toString());
        if (!go)
            return; // cancelled while queued, event notification handled in terminate()
        
        try
        {
            // store the thread so that other threads (typically the
            // Swing event thread) can terminate the Download
            this.thread = Thread.currentThread();
            
            if (remoteURL == null)
                throw new IllegalArgumentException("no URL");                
            
            fireEvent(TransferEvent.CONNECTING);
            
            doHead();

            fireEvent(TransferEvent.CONNECTED);
            
            boolean doDownload = doCheckDestination();
            if (skipped)
                go = false;
            if (!go)
                return; // jump to finally
            
            if (doDownload)
            {
                fireEvent(origFile, TransferEvent.TRANSFERING);
                File tmp = origFile;
                origFile = new File(origFile.getAbsolutePath() + ".part");
                doGet();
                log.debug("download completed, renaming " + origFile +" to " + tmp);
                origFile.renameTo(tmp);
                origFile = tmp;
            }
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
        catch (IOException ioex)
        {
            failure = ioex;
        }
        catch(Throwable t)
        {
            failure = t;
        }
        finally
        {
            //if (failure != null)
            //    failure.printStackTrace();
            
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
                log.debug("failed");
                fireEvent(failure);
            }
            else
            {
                log.debug("completed");
                fireEvent(destFile, TransferEvent.COMPLETED);
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
                    this.skipped = true;
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
                this.skipped = true;
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
    
    // perform http head, capture all header fields, and init output File vars
    private void doHead()
        throws IOException, InterruptedException
    {
        // check/clear interrupted flag and throw if necessary
        if ( Thread.interrupted() )
            throw new InterruptedException();

        HttpURLConnection conn = (HttpURLConnection) remoteURL.openConnection();
        log.debug("HttpURLConnection type: " + conn.getClass().getName() + " for HEAD " + remoteURL);

        if (conn instanceof HttpsURLConnection)
        {
            HttpsURLConnection sslConn = (HttpsURLConnection) conn;
            initHTTPS(sslConn);
        }

        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod("HEAD");
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("User-Agent", userAgent);
        
        int code = conn.getResponseCode();
        log.debug("HTTP HEAD status: " + code);
        log.debug("HTTP Location header: " + conn.getHeaderField("Location"));
        if (code != HttpURLConnection.HTTP_OK)
        {
            String msg = "(" + code + ") " + conn.getResponseMessage();
            switch(code)
            {
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                    throw new IOException("authentication failed " + msg);
                case HttpURLConnection.HTTP_FORBIDDEN:
                    throw new IOException("authorization failed " + msg);
                case HttpURLConnection.HTTP_NOT_FOUND:
                    throw new IOException("resource not found " + msg);
                default:
                    throw new IOException(msg);
                    
            }
        }

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

        // check if incoming data is compressed, determine decompressed filename
        this.contentEncoding = conn.getHeaderField(("Content-Encoding"));
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

        this.contentType = conn.getContentType();
        
        // get content-length
        this.contentLength = conn.getContentLength();
        // get uncompressed content length
        String s = conn.getHeaderField("X-Uncompressed-Length");
        if (s != null)
        {
            try { this.decompSize = Long.parseLong(s); }
            catch(NumberFormatException ignore) { }
        }
        
        this.lastModified = conn.getLastModified();

        log.debug("   original file: " + origFile);
        log.debug("     decomp file: " + decompFile);
        log.debug("  content length: " + contentLength);
        log.debug("    content type: " + contentType);
        log.debug("content encoding: " + contentEncoding);
        log.debug("     decomp size: " + decompSize);
        log.debug("    decompressor: " + decompressor);
        log.debug("    lastModified: " + lastModified);
    }
    
    private void doGet()
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
            this.size = contentLength;
            String pkey = null;
            String pvalue = null;
            boolean append = false;
            int startingPos = 0;
            if (origFile.exists() && origFile.length() < contentLength) // partial file from previous download
            {
                pkey = "Range";
                pvalue = "bytes=" + origFile.length() + "-"; // open ended
            }
            
            HttpURLConnection conn = (HttpURLConnection) remoteURL.openConnection();
            log.debug("HttpURLConnection type: " + conn.getClass().getName() + " for GET " + remoteURL);

            if (conn instanceof HttpsURLConnection)
            {
                HttpsURLConnection sslConn = (HttpsURLConnection) conn;
                initHTTPS(sslConn);
            }

            // TODO: maybe follow redirects manually so we can detect/handle a change from http to https?
            conn.setInstanceFollowRedirects(true);
            
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("User-Agent", userAgent);
            
            if (pkey != null)
            {
                log.debug("trying: " + pkey + " = " + pvalue);
                conn.setRequestProperty(pkey, pvalue);
            }
            
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            log.debug("HTTP GET status: " + code + " for " + remoteURL);

            if (pkey != null && code == 416) // server doesn't like range, retry without it
            {
                conn = (HttpURLConnection) remoteURL.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setRequestMethod("GET");
                code = conn.getResponseCode();
                log.debug("HTTP GET status: " + code + " for " + remoteURL);
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
                        startingPos = Integer.parseInt(parts[0]);
                        log.debug("found startingPos = " + startingPos);
                        String[] ss = cr.split("/");
                        this.size = Integer.parseInt(ss[1]);
                        log.debug("found real size = " + size);
                        append = true;
                    }
                }
            }
            else if (code == HttpURLConnection.HTTP_FORBIDDEN)
                throw new IOException("permission denied");
            // TODO: add custom handling for other failures here
            else if (code != HttpURLConnection.HTTP_OK)
                throw new IOException(conn.getResponseMessage());

            // check eventID hook
            findEventID(conn);

            boolean wrap = false;
            istream = conn.getInputStream();
            if ( !(istream instanceof BufferedInputStream) )
            {
                log.debug("using BufferedInputStream");
                istream = new BufferedInputStream(istream, bufferSize);
            }

            log.debug("output: " + origFile + " append: " + append);
            ostream = new FileOutputStream(origFile, append);
            log.debug("using BufferedOutputStream");
            ostream = new BufferedOutputStream(ostream, bufferSize);

            if (use_nio)
                nioLoop(istream, ostream, 2*bufferSize, startingPos);
            else
                ioLoop(istream, ostream, 2*bufferSize, startingPos);
            ostream.flush();
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
