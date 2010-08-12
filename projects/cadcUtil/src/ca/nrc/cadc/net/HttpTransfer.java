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

import ca.nrc.cadc.auth.SSLUtil;
import ca.nrc.cadc.auth.X509CertificateChain;
import ca.nrc.cadc.net.event.ProgressListener;
import ca.nrc.cadc.net.event.TransferEvent;
import ca.nrc.cadc.net.event.TransferListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivateKey;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.Subject;
import org.apache.log4j.Logger;

/**
 *
 * @author pdowler
 */
public abstract class HttpTransfer implements Runnable
{
    private static Logger log = Logger.getLogger(HttpTransfer.class);
    public static String DEFAULT_USER_AGENT;

    protected int bufferSize = 8192;
    protected OverwriteChooser overwriteChooser;
    protected ProgressListener progressListener;
    protected TransferListener transferListener;
    protected boolean fireEvents = false;
    protected boolean fireCancelOnce = true;

    protected String userAgent;
    protected boolean use_nio = false; // throughput not great, needs work before use

    protected boolean go;
    protected Thread thread;

    // state set by caller
    protected URL remoteURL;
    protected File localFile;

    // state that observer(s) might be interested in
    public String eventID = null;
    public Throwable failure;

    private SSLSocketFactory sslSocketFactory;

    static
    {
        String jv = "Java " + System.getProperty("java.version") + ";" + System.getProperty("java.vendor");
        String os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        DEFAULT_USER_AGENT = "OpenCADC/" + HttpTransfer.class.getName() + "/" + jv + "/" + os;
    }
    

    protected HttpTransfer() 
    {
        this.go = true;
    }

    public URL getURL() { return remoteURL; }

    public void setBufferSize(int bufferSize)
    {
        this.bufferSize = bufferSize;
    }

    public void setUserAgent(String userAgent) 
    {
        this.userAgent = userAgent;
        if (userAgent == null)
            this.userAgent = DEFAULT_USER_AGENT;
    }

    public void setSSLSocketFactory(SSLSocketFactory sslSocketFactory)
    {
        this.sslSocketFactory = sslSocketFactory;
    }
    
    public void setOverwriteChooser(OverwriteChooser overwriteChooser) { this.overwriteChooser = overwriteChooser; }

    public void setProgressListener(ProgressListener listener)
    {
        this.progressListener = listener;
        fireEvents = (progressListener != null || transferListener != null);
    }

    public void setTransferListener(TransferListener listener)
    {
        this.transferListener = listener;
        fireEvents = (progressListener != null || transferListener != null);
    }

    public Throwable getThrowable() { return failure; }
    
    public void terminate()
    {
        this.fireEvents = false; // prevent run() and future calls to terminate from firing the CANCELLED event
        this.go = false;
        synchronized(this) // other synchronized block in in the finally part of run()
        {
            if (thread != null)
            {
                // give it a poke just in case it is blocked/slow
                log.debug("terminate(): interrupting " + thread.getName());
                try
                {
                    thread.interrupt();
                }
                catch(Throwable ignore) { }
            }
        }
        fireCancelledEvent();
        this.fireCancelOnce = false;
    }

    protected void findEventID(HttpURLConnection conn)
    {
        String eventHeader = null;
        if (transferListener != null)
            eventHeader = transferListener.getEventHeader();
        if (eventHeader != null)
            this.eventID = conn.getHeaderField(eventHeader);
    }

    private void fireCancelledEvent()
    {
        if (fireCancelOnce)
        {
            TransferEvent e = new TransferEvent(this, eventID, remoteURL, localFile, TransferEvent.CANCELLED);
            fireEvent(e);
        }
    }
    private void fireEvent(TransferEvent e)
    {
        log.debug("fireEvent: " + e);
        if (transferListener != null)
            transferListener.transferEvent(e);
        if (progressListener != null)
            progressListener.transferEvent(e);
    }

    protected void fireEvent(int state)
    {
        fireEvent(localFile, state);
    }

    protected void fireEvent(File file, int state)
    {
        if (fireEvents)
        {
            TransferEvent e = new TransferEvent(this, eventID, remoteURL, file, state);
            fireEvent(e);
        }
    }

    protected void fireEvent(Throwable t)
    {
        fireEvent(localFile, t);
    }
    
    protected void fireEvent(File file, Throwable t)
    {
        if (fireEvents)
        {
            TransferEvent e = new TransferEvent(this, eventID, remoteURL, file, t);
            fireEvent(e);
        }
    }

    /**
     * @param sslConn
     */
    protected void initHTTPS(HttpsURLConnection sslConn)
    {
        if (sslSocketFactory == null) // lazy init
        {
            log.debug("initHTTPS: lazy init");
            AccessControlContext ac = AccessController.getContext();
            Subject s = Subject.getSubject(ac);
            this.sslSocketFactory = SSLUtil.getSocketFactory(s);
        }
        if (sslSocketFactory != null)
        {
            log.debug("setting SSLSocketFactory on " + sslConn.getClass().getName());
            sslConn.setSSLSocketFactory(sslSocketFactory);
        }
    }

    /**
     * Perform the IO loop. This method reads from the input and writes to the output using an
     * internal byte array of the specified size.
     * 
     * @param istream
     * @param ostream
     * @param sz
     * @param startingPos for resumed transfers, this effects the reported value seen by
     * the progressListener (if set)
     * @throws IOException
     * @throws InterruptedException
     */
    protected void ioLoop(InputStream istream, OutputStream ostream, int sz, int startingPos)
        throws IOException, InterruptedException
    {
        log.debug("ioLoop: using java.io with byte[] buffer size " + sz);
        byte[] buf = new byte[sz];

        int nb = 0;
        int nb2 = 0;
        int tot = startingPos; // non-zero for resumed transfer
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
                //log.debug("write buffer: " + nb);
                ostream.write(buf, 0, nb);
                tot += nb;
                if (progressListener != null)
                    progressListener.update(nb, tot);
            }
            
        }
    }

    /**
     * Perform the IO loop using the nio library.
     * 
     * @param istream
     * @param ostream
     * @param sz
     * @param startingPos
     * @throws IOException
     * @throws InterruptedException
     */
    protected void nioLoop(InputStream istream, OutputStream ostream, int sz, int startingPos)
        throws IOException, InterruptedException
    {
        log.debug("[Download] nioLoop: using java.nio with ByteBuffer size " + sz);
        // check/clear interrupted flag and throw if necessary
        if ( Thread.interrupted() )
            throw new InterruptedException();

        ReadableByteChannel rbc = Channels.newChannel(istream);
        WritableByteChannel wbc = Channels.newChannel(ostream);

        int tot = startingPos; // non-zero for resumed transfer
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
}
