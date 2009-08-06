/*****************************************************************************
 *  
 *  Copyright (C) 2009				Copyright (C) 2009
 *  National Research Council		Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6			Ottawa, Canada, K1A 0R6
 *  All rights reserved				Tous droits reserves
 *  					
 *  NRC disclaims any warranties,	Le CNRC denie toute garantie
 *  expressed, implied, or statu-	enoncee, implicite ou legale,
 *  tory, of any kind with respect	de quelque nature que se soit,
 *  to the software, including		concernant le logiciel, y com-
 *  without limitation any war-		pris sans restriction toute
 *  ranty of merchantability or		garantie de valeur marchande
 *  fitness for a particular pur-	ou de pertinence pour un usage
 *  pose.  NRC shall not be liable	particulier.  Le CNRC ne
 *  in any event for any damages,	pourra en aucun cas etre tenu
 *  whether direct or indirect,		responsable de tout dommage,
 *  special or general, consequen-	direct ou indirect, particul-
 *  tial or incidental, arising		ier ou general, accessoire ou
 *  from the use of the software.	fortuit, resultant de l'utili-
 *  								sation du logiciel.
 *  
 *  
 *  This file is part of cadcDownloadManager.
 *  
 *  CadcDownloadManager is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  CadcDownloadManager is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with cadcDownloadManager.  If not, see <http://www.gnu.org/licenses/>.			
 *  
 *****************************************************************************/

package ca.nrc.cadc.dlm.client;

import ca.nrc.cadc.dlm.DownloadUtil;
import ca.nrc.cadc.dlm.client.event.DownloadListener;
import ca.nrc.cadc.net.SchemeHandler;
import ca.nrc.cadc.net.MultiSchemeHandler;
import ca.onfire.ak.AbstractApplication;
import ca.onfire.ak.ApplicationConfig;
import ca.nrc.cadc.thread.ConditionVar;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.io.File;
import java.io.IOException;
import java.net.URI;
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
        DownloadUtil.schemeHandler = initSchemeHandler();
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
    
    private static MultiSchemeHandler initSchemeHandler()
    {
        MultiSchemeHandler uc = new MultiSchemeHandler();
        // TODO: read class name(s) from a config file
        String[] uris = new String[] 
        {
            "ad:ca.nrc.cadc.ad.AdSchemeHandler",
            "plane:ca.nrc.cadc.caom.util.PlaneSchemeHandler"
        };
        
        for (int i=0; i<uris.length; i++)
        {
            try
            {
                System.out.println("[CoreUI] configuring: " + uris[i]);
                URI u = new URI(uris[i]);
                String scheme = u.getScheme();
                String cname = u.getSchemeSpecificPart();
                System.out.println("[CoreUI] loading: " + cname);
                Class c = Class.forName(cname);
                System.out.println("[CoreUI] instantiating: " + c);
                SchemeHandler handler = (SchemeHandler) c.newInstance();
                System.out.println("[CoreUI] adding: " + scheme + "," + handler);
                uc.addSchemeHandler(scheme, handler);
                System.out.println("[CoreUI] success: " + scheme + " is supported");
            }
            catch(Throwable oops)
            {
                System.out.println("[CoreUI] failed to create SchemeHandler: " + uris[i] + ", " + oops);
            }
        }
        return uc;
    }
    
}
