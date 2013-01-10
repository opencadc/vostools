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

import ca.nrc.cadc.dlm.DownloadDescriptor;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ca.nrc.cadc.dlm.DownloadUtil;
import ca.nrc.cadc.net.HttpDownload;
import ca.nrc.cadc.net.HttpTransfer;
import ca.nrc.cadc.net.event.TransferEvent;
import ca.nrc.cadc.net.event.TransferListener;
import ca.nrc.cadc.thread.ConditionVar;
import ca.nrc.cadc.util.Log4jInit;
import java.util.Map;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * The interface for system console output display.
 * 
 * @author majorb
 *
 */
public class ConsoleUI implements UserInterface, TransferListener
{
    private static Logger log = Logger.getLogger(ConsoleUI.class);

    private File downloadDir;
    private DownloadManager downloadManager;
    private final List<HttpDownload> downloads = new ArrayList<HttpDownload>();
    private ConditionVar engineInitCond;
    private ConditionVar downloadsCompeleteCond;
    private Integer activeDownloadRequests;
    private String userAgent;
    private boolean decompress;
    private boolean overwrite;
    
    public ConsoleUI(Level logLevel, Integer threads, Boolean retry, String dest, boolean decompress, boolean overwrite, ConditionVar downloadsCompleteCond)
    {
        Log4jInit.setLevel("ca.nrc.cadc", logLevel);
        
        this.downloadsCompeleteCond = downloadsCompleteCond;
        this.userAgent = "CADC DownloadManager(ConsoleUI) " + HttpTransfer.DEFAULT_USER_AGENT;
        this.decompress = decompress;
        this.overwrite = overwrite;
        
        int initialThreads = DownloadManager.DEFAULT_THREAD_COUNT;
        if (threads != null)
        {
            if (threads.intValue() < 1 || threads.intValue() > DownloadManager.MAX_THREAD_COUNT)
                throw new IllegalArgumentException("number of threads is out of allowed range [1,"
                        + DownloadManager.MAX_THREAD_COUNT + "]");
            initialThreads = threads.intValue();
        }

        boolean initialRetryEnabled = false;
        if (retry != null)
        {
            initialRetryEnabled = retry.booleanValue();
        }
                
        if (dest != null)
        {
            File tmp = new File(dest);
            if (!tmp.exists() ||  !tmp.isDirectory() || !tmp.canWrite())
                throw new IllegalArgumentException("cannot download file(s) to " + dest);
            this.downloadDir = tmp;
        }
        
        if (downloadDir == null)
        {
            String currentDir = System.getProperty("user.dir");
            downloadDir = new File(currentDir);
            if ( !downloadDir.exists() || !downloadDir.isDirectory() || !downloadDir.canWrite())
                throw new RuntimeException("Cannot write to directory " + currentDir);
        }

        ThreadControl threadControl = new StaticThreadControl(initialThreads);
        log.debug("creating Downloadmanager: " + initialThreads + "," + downloadDir);
        this.downloadManager = new DownloadManager(threadControl, initialRetryEnabled, initialThreads, downloadDir);
        downloadManager.addDownloadListener(this);
        
        this.engineInitCond = new ConditionVar();
        engineInitCond.set(false);
        
        // start the download initialization process
        new Thread(new DelayedInit()).start();
        
    }
    
    public void add(List<String> uris, Map<String,List<String>> params)
    {
        Iterator<DownloadDescriptor> i = DownloadUtil.iterateURLs(uris, params);
        while ( i.hasNext() )
        {
            DownloadDescriptor dd = i.next();
            if (dd.error == null)
                addDownload(dd);
            else
            {
                logError(dd);
            }
        }
    }

    private void addDownload(DownloadDescriptor dd)
    {
        if (downloadDir == null)
            this.downloadDir = new File(System.getProperty("user.dir"));
        HttpDownload dl = new HttpDownload(userAgent, dd.url, downloadDir);
        dl.setOverwrite(overwrite);
        dl.setDecompress(decompress);
        synchronized(downloads)
        {
            downloads.add(dl);
        }
    }
    
    private void logError(DownloadDescriptor dd)
    {
        // TODO: display in a window? create a Download in FAILED state?
        String msg = "[error] cannot download " + dd.uri + " because: " + dd.error;
        System.err.println(msg);
    }
    
    public void start()
    {
        if (downloadManager != null)
            downloadManager.start();
        engineInitCond.setNotifyAll();
    }
    
    private class DelayedInit implements Runnable
    {
        public void run()
        {
            try
            {
                log.debug("waiting for engineInit");
                engineInitCond.waitForTrue();
                log.debug("initializing: " + downloads.size() + " downloads");
                synchronized (downloads)
                {
                    activeDownloadRequests = downloads.size();
                    for (int i=0; i<downloads.size(); i++)
                        downloadManager.addDownload(downloads.get(i));
                    downloads.clear();
                }
            }
            catch(Throwable t) 
            { 
                log.error("DelayedInit failed: " + t);
            }
        }
    }

    @Override
    public void transferEvent(TransferEvent e)
    {
        switch(e.getState())
        {
            case TransferEvent.TRANSFERING:
                log.info("downloading -> " + e.getFile());
                break;

            case TransferEvent.DECOMPRESSING:
                log.info("decompressing -> " + e.getFile());
                break;

            case TransferEvent.DELETED:
                log.info("removed: " + e.getFile());
                break;
                
            case TransferEvent.COMPLETED:
            case TransferEvent.CANCELLED:
            case TransferEvent.FAILED:
                synchronized (downloads)
                {
                    activeDownloadRequests--;
                }
                StringBuffer sb = new StringBuffer();
                sb.append(e.getStateLabel());
                sb.append(": ");

                sb.append(e.getURL().toString());
                if (e.getFile() != null)
                {
                    sb.append(" -> ");
                    sb.append(e.getFile().getAbsolutePath());
                }
                log.info(sb.toString());
                break;

            default:
                log.debug("transferEvent: " + e);
        }
        if (activeDownloadRequests == 0)
        {
            log.debug("All downloads in a final state, exiting.");
            downloadsCompeleteCond.setNotifyAll();
        }
        
    }

    @Override
    public String getEventHeader()
    {
        return null;
    }
    
}
