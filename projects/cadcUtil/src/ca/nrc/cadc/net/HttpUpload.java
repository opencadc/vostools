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

import ca.nrc.cadc.net.event.TransferEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

import org.apache.log4j.Logger;

/**
 * Perform an upload (PUT).
 * 
 * Following the structure of Download.java
 * 
 * @see /cadcDownloadManager/src/ca/nrc/cadc/dlm/client/Download.java
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

    public HttpUpload(File src, URL dest)
    {
        this.localFile = src;
        this.remoteURL = dest;
        if (remoteURL == null)
            throw new IllegalArgumentException("destination URL cannot be null");
        if (localFile == null) 
            throw new IllegalArgumentException("source File cannot be null");

    }
    
    // unused
    private HttpUpload() { }

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


    @Override
    public String toString() { return "HttpUpload[" + remoteURL + "," + localFile + "]"; }

    @Override
    public void run()
    {
        log.debug(this.toString());
        if (!go)
            return; // cancelled while queued, event notification handled in terminate()
        
        InputStream istream = null;
        OutputStream ostream = null;
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

            conn.setRequestMethod("PUT");
            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);

            // this seesm to fail, maybe not allowed with PUT
            //conn.setRequestProperty("User-Agent", userAgent);

            conn.setRequestProperty("Content-Length", Long.toString(localFile.length()));
            if (contentType != null)
                conn.setRequestProperty("Content-Type", contentType);
            if (contentEncoding != null)
                conn.setRequestProperty("Content-Encoding", contentEncoding);
            if (contentMD5 != null)
                conn.setRequestProperty("Content-MD5", contentMD5);
            
            int bSize = bufferSize;
            if (localFile.length() < bSize)
                bSize = (int) localFile.length();

            ostream = conn.getOutputStream();
            istream = new FileInputStream(localFile);
            if (bSize < localFile.length())
            {
                if ( !(ostream instanceof BufferedOutputStream) )
                {
                    log.debug("using BufferedOutputStream");
                    ostream = new BufferedOutputStream(ostream, bufferSize);
                }
                log.debug("using BufferedInputStream");
                istream = new BufferedInputStream(istream, bufferSize);
            }

            fireEvent(TransferEvent.TRANSFERING);

            ioLoop(istream, ostream, 2*this.bufferSize, 0);

            
            ostream.flush();
            log.debug("flushing and closing OutputStream");
            try { ostream.close(); }
            catch(IOException ignore) { }
            finally { ostream = null; }

            String responseMessage = conn.getResponseMessage();
            int code = conn.getResponseCode();
            log.debug("code: " + code);
            log.debug("responseMessage: " + responseMessage);
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

            if (!go)
                fireEvent(TransferEvent.CANCELLED);
            else if (failure != null)
                fireEvent(failure);
            else
                fireEvent(TransferEvent.COMPLETED);
        }

    }
}
