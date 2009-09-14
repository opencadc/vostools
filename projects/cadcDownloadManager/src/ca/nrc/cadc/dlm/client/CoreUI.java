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

import ca.nrc.cadc.dlm.DownloadUtil;
import ca.nrc.cadc.dlm.client.event.DownloadListener;
import ca.onfire.ak.AbstractApplication;
import ca.onfire.ak.ApplicationConfig;
import ca.nrc.cadc.thread.ConditionVar;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * TODO.
 *
 * @author pdowler
 */
public class CoreUI extends AbstractApplication implements ChangeListener
{
    private static String configSection = "downloads";
    private static String threadCountConfigKey = "downloadManager.threadCount";
    private static String downloadDirConfigKey = "downloadManager.downloadDir";
    private static String debugKey = "downloadManager.debug";

    private JDownloadManager downloadManager;
    private DownloadListener downloadListener;
    
    private ConditionVar uiInitCond;
    private ConditionVar engineInitCond;
    private List downloads;
    private boolean debug;
    
    public CoreUI() 
    {
        super(new BorderLayout()); 
        this.downloads = new ArrayList();
        this.downloadListener = initDownloadLister();
        
        this.uiInitCond = new ConditionVar();
        uiInitCond.set(false);
        this.engineInitCond = new ConditionVar();
        engineInitCond.set(false);
    }
    
    public void setDownloadListener(DownloadListener dl)
    {
        this.downloadListener = dl;
    }
    
    private void msg(String s)
    {
        if (debug) System.out.println("[CoreUI] " + s);
    }
    
    public void add(String[] strs, String fragment)
    {
        List<DownloadUtil.ParsedURI> uris = DownloadUtil.parseURIs(strs, fragment);
        List<DownloadUtil.GeneratedURL> urls = DownloadUtil.generateURLs(uris, fragment);
        Iterator<DownloadUtil.GeneratedURL> i = urls.iterator();
        while ( i.hasNext() )
        {
            DownloadUtil.GeneratedURL g = i.next();
            if (g.error == null)
                addDownload(g);
            else
            {
                logError(g);
            }
        }
    }
    
    private void addDownload(DownloadUtil.GeneratedURL gen)
    {
        Download dl = new Download();
        dl.url = gen.url;
        // TODO: put original URI here? URL?
        dl.label = gen.str;
        synchronized(downloads)
        {
            downloads.add(dl);
        }
    }
    
    private void logError(DownloadUtil.GeneratedURL gen)
    {
        // TODO: display in a window? create a Download in FAILED state?
        String msg = "[CoreUI] cannot download " + gen.str + " because: " + gen.error.getMessage();
        if (gen.error.getCause() != null)
            msg += ", " + gen.error.getCause().getMessage();
        System.err.println(msg);
    }
    
    public boolean quit()
    {
        boolean ret = getConfirmation("OK to quit?");
        if (ret)
            downloadManager.stop();
        return ret;
    }
    
    public void start()
    {
        if (downloadManager != null)
            downloadManager.start();
        engineInitCond.setNotifyAll();
    }
    
    // terminate threads
    public void stop()
    {
        if (downloadManager != null)
            downloadManager.stop();
        engineInitCond.set(false);
    }
    
    protected void makeUI()
    {
        // try to restore previous settings
        int initialThreads = -1;
        ApplicationConfig conf = getApplicationContainer().getConfig();
        try 
        { 
            conf.setSection(configSection, true);
            String value = conf.getValue(threadCountConfigKey);
            if (value != null)
                initialThreads = Integer.parseInt(value);
        }
        catch(Exception notConfiguredYet) { }
        File downloadDir = null;
        try 
        { 
            conf.setSection(configSection, true);
            String s = conf.getValue(downloadDirConfigKey);
            downloadDir = new File(s);
            if ( !downloadDir.exists() || !downloadDir.isDirectory() || !downloadDir.canWrite())
                downloadDir = null;
        }
        catch(Exception notConfiguredYet) { }

        this.debug = false;
        try 
        { 
            conf.setSection(configSection, true);
            String s = conf.getValue(debugKey);
            if (s != null)
                debug = new Boolean(s).booleanValue();
        }
        catch(Exception notConfiguredYet) { }
        
        this.downloadManager = new JDownloadManager(initialThreads, downloadDir);
        downloadManager.setDebug(debug);
        downloadManager.addChangeListener(this);
        downloadManager.addDownloadListener(downloadListener);

        this.add(downloadManager, BorderLayout.CENTER);
        this.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        
        Util.recursiveSetBackground(this, Color.WHITE);
        
        // fire off a thread to complete init once the app is displayed on screen
        new Thread(new DelayedInit()).start();
    }
    
    public void paint(Graphics g)
    {
        super.paint(g);
        if (uiInitCond != null)
        {
            uiInitCond.setNotifyAll();
        }
    }
    
    private class DelayedInit implements Runnable
    {
        public void run()
        {
            try
            {
                uiInitCond.waitForTrue();
                SwingUtilities.invokeAndWait(new Runnable()
                {
                    public void run()
                    {
                        downloadManager.choseDestinationDir(CoreUI.this);
                    }
                });
                
                engineInitCond.waitForTrue();
                synchronized (downloads)
                {
                    for (int i=0; i<downloads.size(); i++)
                        downloadManager.add((Download) downloads.get(i));
                    downloads.clear();
                }
            }
            catch(Throwable t) 
            { 
                msg("DelayedInit failed: " + t);
            }
        }
    }
    
    // react to JDownloadManager state-changed events
    public void stateChanged(ChangeEvent e)
    {
        ApplicationConfig conf = getApplicationContainer().getConfig();

        int threadCount = downloadManager.getThreadCount();
        File destDir = downloadManager.getDestinationDir();

        try 
        {
            msg("updating configuration...");
            conf.setSection(configSection, true);
            conf.putValue(threadCountConfigKey, Integer.toString(threadCount)); 
            if (destDir != null)
                conf.putValue(downloadDirConfigKey, destDir.getAbsolutePath()); 
            msg("updating configuration... done");
        }
        catch(IOException ioex) 
        {
            msg("updating configuration... failed: " + ioex.getMessage());
        }
    }
    
    private static DownloadListener initDownloadLister()
    {
        // TODO: read a class name from a config file and try to instantiate it 
        String cname = "ca.nrc.cadc.logserver.HttpDownloadLogger";
        
        try
        {
            System.out.println("[CoreUI] loading: " + cname);
            Class c = Class.forName(cname);
            System.out.println("[CoreUI] instantiating: " + c);
            DownloadListener dl = (DownloadListener) c.newInstance();
            System.out.println("[CoreUI] success: " + dl);
            return dl;
        }
        catch(Throwable oops)
        {
            System.out.println("[CoreUI] failed to create DownloadListener: " + cname + ", " + oops);
        }
        return null;
    }
    
    
    
}
