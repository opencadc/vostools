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


package ca.nrc.cadc.dlm.client;

import ca.nrc.cadc.dlm.client.event.ProgressListener;
import ca.nrc.cadc.dlm.client.event.DownloadListener;
import ca.nrc.cadc.dlm.client.event.DownloadEvent;
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
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

/**
 * Simple task to encapsulate a single download.
 *
 * @author pdowler
 */
public class Download implements Runnable
{
    private static int NONE = 0;
    private static int GZIP = 1;
    private static int ZIP = 2;

    private boolean debug = true;
    
    // these have to be configured by the caller
    public URL url;
    public String label;
    public boolean decompress = false;  
    public boolean overwrite = false;
    public File destDir = null;
    public String suggestedFilename = null;

    // state that gets set as the Download is setup/run/completed
    private File origFile;
    private File decompFile;
    private File removeFile;
    private int decompressor;
    
    // state that observer(s) might be interested in
    public File destFile;
    public boolean skipped = false;
    public int contentLength = -1;
    public int decompSize = -1;
    public int size = -1;
    public long lastModified = -1;
    public String eventID = null;
    public Throwable failure;

    private FileOverwriteDecider fod;
    private ProgressListener progressListener;
    private DownloadListener downloadListener;
    private boolean fireEvents = false;
    
    private boolean go;
    private Thread thread;
    
    private static String userAgent;
    private static boolean use_nio = false; // throughput not great, needs work before use
    
    static
    {
        String jv = "Java " + System.getProperty("java.version") + ";" + System.getProperty("java.vendor");
        String os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        userAgent = "CADC DownloadManager/" + jv + "/" + os;
        
        //String unp = System.getProperty(Download.class.getName() + ".use_nio");
        //if (unp != null && unp.equals("true"))
        //    use_nio = true;
    }
        
    public Download() { this.go = true; }
    
    public String toString() { return "Download[" + label + "]"; }
    
    public void setDebug(boolean debug) { this.debug = debug; }
    
    public void setFileOverwriteDecider(FileOverwriteDecider fod) { this.fod = fod; }
    
    public void setProgressListener(ProgressListener listener) 
    {
        this.progressListener = listener;
        fireEvents = (progressListener != null || downloadListener != null);
    }
    
    public void setDownloadListener(DownloadListener listener) 
    {
        this.downloadListener = listener;
        fireEvents = (progressListener != null || downloadListener != null);
    }
    
    public void terminate()
    {
        boolean fire = fireEvents;
        this.fireEvents = false; // prevent run() and future calls to terminate from firing the CANCELLED event
        this.go = false;
        synchronized(this) // other synchronized block in in the finally part of run()
        {
            if (thread != null)
            {
                // give it a poke just in case it is blocked/slow
                msg("Download.terminate(): interrupting " + thread.getName());
                try 
                {
                    thread.interrupt(); 
                }
                catch(Throwable ignore) { }
            }
        }
        // this just fires the cancel event now so we don't have to wait for it to run()
        if (fire)
        {
            DownloadEvent de = new DownloadEvent(this, eventID, url, destFile, DownloadEvent.CANCELLED);
            if (downloadListener != null)
                downloadListener.downloadEvent(de);
            if (progressListener != null)
                progressListener.downloadEvent(de);
        }
    }
    
    public int getSize() { return size; }
    
    public File getFilename() { return destFile; }
        
    public void run()
    {
        if (!go)
            return; // cancelled while queued, event notification handled in terminate()
        
        try
        {
            // store the thread so that other threads (typically the
            // Swing event thread) can terminate the Download
            this.thread = Thread.currentThread();
            
            if (url == null)
                throw new IllegalArgumentException("no URL");                
            
            if (fireEvents)
            {
                DownloadEvent de = new DownloadEvent(this,null,url,null,DownloadEvent.CONNECTING);
                if (downloadListener != null)
                    downloadListener.downloadEvent(de);
                if (progressListener != null)
                    progressListener.downloadEvent(de);
            }
            
            doHead();

            if (fireEvents)
            {
                DownloadEvent de = new DownloadEvent(this,null,url,null,DownloadEvent.CONNECTED);
                if (downloadListener != null)
                        downloadListener.downloadEvent(de);
                if (progressListener != null)
                    progressListener.downloadEvent(de);
            }
            
            boolean doDownload = doCheckDestination();
            if (skipped)
                go = false;
            if (!go)
                return; // jump to finally
            
            if (doDownload)
            {
                File tmp = origFile;
                origFile = new File(origFile.getAbsolutePath() + ".part");
                doGet();
                msg("download completed, renaming " + origFile +" to " + tmp);
                origFile.renameTo(tmp);
                origFile = tmp;
            }
            if (decompress && decompressor != NONE)
            {
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
            if (failure != null)
                failure.printStackTrace();
            
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
                msg("removing: " + removeFile);
                removeFile.delete();
            }
            
            if (fireEvents)
            {
                DownloadEvent de = null;
                if (!go)
                    de = new DownloadEvent(this, eventID, url, destFile, DownloadEvent.CANCELLED);
                else if (failure != null)
                    de = new DownloadEvent(this, eventID, url, destFile, failure);
                else
                    de = new DownloadEvent(this, eventID, url, destFile); // completed
                if (downloadListener != null)
                    downloadListener.downloadEvent(de);
                if (progressListener != null)
                    progressListener.downloadEvent(de);
            }
            
        }
    }
        
    private boolean askOverwrite(File f, int length, long lastMod)
    {
        return overwrite || 
            (
                fod != null &&
                fod.overwriteFile(f.getAbsolutePath(), f.length(), f.lastModified(), length, lastMod)
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
            msg(origFile + " exists");
            if ( askOverwrite(origFile, contentLength, lastModified) )
            {
                msg("overwrite: YES -- " + origFile);
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
                msg("overwrite: NO -- " + origFile);
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
            msg(decompFile + " exists");
            if ( askOverwrite(decompFile, decompSize, lastModified) )
            {
                msg("overwrite: YES -- " + decompFile);
                decompFile.delete(); // origFile does not exist
                if (decompress && decompressor != NONE)
                    this.destFile = decompFile;    // download and decompress
                else
                    this.destFile = origFile;      // download
            }
            else
            {
                msg("overwrite: NO -- " + decompFile);
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
        msg("destination file: " + destFile);
        
        return doDownload;
    }
    
    // perform http head, capture all header fields, and init output File vars
    private void doHead()
        throws IOException, InterruptedException
    {
        // check/clear interrupted flag and throw if necessary
        if ( Thread.interrupted() )
            throw new InterruptedException();

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        msg("HttpURLConnection type: " + conn.getClass().getName() + " for HEAD " + url);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod("HEAD");
        conn.setRequestProperty("Accept", "*/*");
        
        conn.setRequestProperty("User-Agent", userAgent);
        
        int code = conn.getResponseCode();
        msg("HTTP HEAD status: " + code);
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
            
        // determine filename
        String origFilename = null;

	// first option: use what the caller suggested
	if (origFilename == null)
		origFilename = suggestedFilename;
        
	// second option: use supplied filename if present in http header
        String cdisp = conn.getHeaderField("Content-Disposition");
        msg("HTTP HEAD: Content-Disposition = " + cdisp);
        if ( cdisp != null )
            origFilename = parseContentDisposition(cdisp);

        // last resort: pull something from the end of the URL
        if (origFilename == null)
        {
            String s = url.getPath();
            String query = url.getQuery();
            int i = s.lastIndexOf('/');
            if (i != -1 && i < s.length() - 1)
                origFilename = s.substring(i + 1, s.length());
            if (query != null)
                origFilename += "?" + query;
        }
        if (origFilename == null)
        {
            origFilename = url.getHost(); // no path at all
        }
        this.origFile = new File(destDir, origFilename);

        // check if incoming data is compressed, determine decompressed filename
        String encoding = conn.getHeaderField(("Content-Encoding"));
        int decompressor = NONE;
        if ("gzip".equals(encoding) || origFilename.endsWith(".gz"))
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
        else if ("zip".equals(encoding) || origFilename.endsWith(".zip"))
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
        
        // get content-length
        this.contentLength = conn.getContentLength();
        // get uncompressed content length
        String s = conn.getHeaderField("X-Uncompressed-Length");
        if (s != null)
        {
            try { this.decompSize = Integer.parseInt(s); }
            catch(NumberFormatException ignore) { }
        }
        
        this.lastModified = conn.getLastModified();

        msg("   original file: " + origFile);
        msg("     decomp file: " + decompFile);
        msg("  content length: " + contentLength);
        msg("     decomp size: " + decompSize);
        msg("    decompressor: " + decompressor);
        msg("    lastModified: " + lastModified);
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
            
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            msg("HttpURLConnection type: " + conn.getClass().getName() + " for GET " + url);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("User-Agent", userAgent);
            
            if (pkey != null)
            {
                msg("trying: " + pkey + " = " + pvalue);
                conn.setRequestProperty(pkey, pvalue);
            }
            
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            msg("HTTP GET status: " + code + " for " + url);

            if (pkey != null && code == 416) // server doesn't like range, retry without it
            {
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setRequestMethod("GET");
                code = conn.getResponseCode();
                msg("HTTP GET status: " + code + " for " + url);
            }
            
            if (pkey != null && code == HttpURLConnection.HTTP_PARTIAL)
            {
                String cr = conn.getHeaderField("Content-Range");
                msg("Content-Range = " + cr);
                if (cr != null)
                {
                    cr = cr.trim();
                    if (cr.startsWith("bytes"))
                    {
                        cr = cr.substring(6);
                        String[] parts = cr.split("-");
                        startingPos = Integer.parseInt(parts[0]);
                        msg("found startingPos = " + startingPos);
                        String[] ss = cr.split("/");
                        this.size = Integer.parseInt(ss[1]);
                        msg("found real size = " + size);
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
            String eventHeader = null;
            if (downloadListener != null)
                eventHeader = downloadListener.getEventHeader();
            if (eventHeader != null)
                this.eventID = conn.getHeaderField(eventHeader);
            int sz = 8192;

            boolean wrap = false;
            istream = conn.getInputStream();
            if ( !wrap || istream instanceof BufferedInputStream )
                msg("input: HttpURLConnection.getInputStream()");
            else
            {
                msg("input: BufferedInputStream(HttpURLConnection.getInputStream())");
                istream = new BufferedInputStream(istream, sz);
            }

            if (fireEvents)
            {
                DownloadEvent de = new DownloadEvent(this, eventID, url, origFile, DownloadEvent.DOWNLOADING);
                de.setStartingPosition(startingPos);
                if (downloadListener != null)
                        downloadListener.downloadEvent(de);
                if (progressListener != null)
                    progressListener.downloadEvent(de);
            }

            msg("output: " + origFile + " append: " + append);
            FileOutputStream fstream = new FileOutputStream(origFile, append);
            ostream = new BufferedOutputStream(fstream, sz);
            //ostream = new RandomAccessFile(origFile, "rwd");

            // read from network and write to file
            if (use_nio)
                nioLoop(istream, ostream, 2*sz, startingPos);
            else
                ioLoop(istream, ostream, 2*sz, startingPos);
            ostream.flush();
        }
        finally
        {
            if (istream != null)
            {
                msg("[Download] closing InputStream");
                try { istream.close(); }
                catch(Exception ignore) { }
            }
            if (ostream != null)
            {
                msg("[Download] closing OutputStream");
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
            if (fireEvents)
            {
                DownloadEvent de = new DownloadEvent(this, eventID, url, origFile, DownloadEvent.DECOMPRESSING);
                if (downloadListener != null)
                        downloadListener.downloadEvent(de);
                if (progressListener != null)
                    progressListener.downloadEvent(de);
            }
            
            int sz = 8192;
            if (decompressor == GZIP)
            {
                msg("input: GZIPInputStream(BufferedInputStream(FileInputStream)");
                istream = new GZIPInputStream(new FileInputStream(origFile), sz);
            }
            else if (decompressor == ZIP)
            {
                msg("input: ZIPInputStream(BufferedInputStream(FileInputStream)");
                istream = new ZipInputStream(new BufferedInputStream(new FileInputStream(origFile)));
            }
            msg("output: " + decompFile);
            ostream = new BufferedOutputStream(new FileOutputStream(decompFile), sz);
            //ostream = new RandomAccessFile(decompFile, "rwd");

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
    
    private void ioLoop(InputStream istream, OutputStream ostream, int sz, int startingPos)
        throws IOException, InterruptedException
    {
        msg("[Download] ioLoop: using java.io with byte[] buffer size " + sz);
        byte[] buf = new byte[sz];
        
        int nb = 0;
        int nb2 = 0;
        int tot = startingPos; // non-zero for resumed download
        int n = 0;
        
        if (progressListener != null)
            progressListener.update(0, tot);
        
        while (nb != -1)
        {
            // check/clear interrupted flag and throw if necessary
            if ( Thread.interrupted() )
                throw new InterruptedException();
        
            nb = istream.read(buf, 0, sz);
            if (nb != -1)
            {
                if (nb < sz/2)
                {
                    // try to get more data: merges a small chunk with a 
                    // subsequent one to minimise write calls
                    nb2 = istream.read(buf, nb, sz-nb);
                    if (nb2 > 0)
                        nb += nb2;
                }
                
                ostream.write(buf, 0, nb);
                tot += nb;
                if (progressListener != null)
                    progressListener.update(nb, tot);
            }
        }
    }
    
    private void nioLoop(InputStream istream, OutputStream ostream, int sz, int startingPos)
        throws IOException, InterruptedException
    {
        msg("[Download] nioLoop: using java.nio with ByteBuffer size " + sz);
        // check/clear interrupted flag and throw if necessary
        if ( Thread.interrupted() )
            throw new InterruptedException();
        
        ReadableByteChannel rbc = Channels.newChannel(istream);
        WritableByteChannel wbc = Channels.newChannel(ostream);
        
        int tot = startingPos; // non-zero for resumed download
        int count = 0;
        
        ByteBuffer buffer = ByteBuffer.allocate(sz);
        
        if (progressListener != null)
            progressListener.update(count, tot);

        while(count != -1)
        {
            // check/clear interrupted flag and throw if necessary
            if ( Thread.interrupted() )
                throw new InterruptedException();
            
            count = rbc.read(buffer);
            if (count != -1)
            {
                wbc.write((ByteBuffer)buffer.flip());
                buffer.flip();
                tot += count;
                if (progressListener != null)
                    progressListener.update(count, tot);
            }
        }
    }

   
    private static char SINGLE_QUOTE = "'".charAt(0);
    private static char DOUBLE_QUOTE = "\"".charAt(0);

    public static boolean isFilenameDisposition(String cdisp)
    {
        cdisp = cdisp.toLowerCase(); // just for checking
        // HACK: HTTP/1.1 allows attachment or extension token, but some sites use inline anyway
        return ( cdisp.startsWith("attachment") || cdisp.startsWith("inline") );
    }
    
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
     
     private void msg(String s)
     {
         if (debug)
             System.out.println("[Download] " + s);
     }
}
