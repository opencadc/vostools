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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.io.File;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import ca.nrc.cadc.dlm.DownloadUtil;
import ca.nrc.cadc.thread.ConditionVar;
import ca.nrc.cadc.thread.Queue;
import ca.nrc.cadc.util.Log4jInit;
import ca.onfire.ak.AbstractApplication;
import ca.onfire.ak.ApplicationConfig;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * The main class for graphical output display.
 *
 * @author pdowler
 */
public class GraphicUI extends AbstractApplication implements ChangeListener, UserInterface
{
    private static final long serialVersionUID = 201008051500L;
    private static Logger log = Logger.getLogger(GraphicUI.class);

    private LogWriter writer = new LogWriter();
    private JDownloadManager downloadManager;
    
    private ConditionVar uiInitCond;
    private ConditionVar engineInitCond;
    private Queue inputQueue = new Queue();
    
    public GraphicUI(Level logLevel)
    {
        super(new BorderLayout());
        Log4jInit.setLevel("ca.nrc.cadc", logLevel, writer);
        
        this.uiInitCond = new ConditionVar();
        uiInitCond.set(false);
        this.engineInitCond = new ConditionVar();
        engineInitCond.set(false);
    }
    
    public void add(List<String> uris, Map<String,List<String>> params)
    {
        Iterator<DownloadDescriptor> iter = DownloadUtil.iterateURLs(uris, params);
        this.inputQueue.push(iter);
    }

    @Override
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
        // create component to write log messages to
        JTextArea jta = new JTextArea();
        writer.setUI(jta);

        // try to restore previous settings
        ApplicationConfig conf = getApplicationContainer().getConfig();
        int initialThreads = DownloadManager.DEFAULT_THREAD_COUNT;
        try
        { 
            conf.setSection(configSection, true);
            String value = conf.getValue(threadCountConfigKey);
            if (value != null)
                initialThreads = Integer.parseInt(value);
        }
        catch(Exception notConfiguredYet) { }

        boolean initialRetryEnabled = true;
        try
        {
            conf.setSection(configSection, true);
            String value = conf.getValue(retryConfigKey);
            if (value != null)
                initialRetryEnabled = Boolean.parseBoolean(value);
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

        this.downloadManager = new JDownloadManager(initialThreads, initialRetryEnabled, downloadDir);
        downloadManager.addChangeListener(this);
        JTabbedPane tabs = new JTabbedPane();
        this.add(tabs, BorderLayout.CENTER);
        tabs.addTab("Downloads", downloadManager);

        JScrollPane sp = new JScrollPane(jta);
        tabs.addTab("Log Messages", sp);
        this.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        
        Util.recursiveSetBackground(this, Color.WHITE);
        
        // fire off a thread to complete init once the app is displayed on screen
        new Thread(new DelayedInit()).start();
    }

    private class LogWriter extends Writer
    {
        StringBuffer sb = new StringBuffer();

        private JTextArea jta;

        LogWriter()
        {
            super();
        }

        public void setUI(JTextArea jta)
        {
            this.jta = jta;
            jta.append(sb.toString()); // catch up
        }
        @Override
        public void close() throws IOException { }

        @Override
        public void flush() throws IOException { }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException
        {
            String s = new String(cbuf, off, len);
            sb.append(s);
            if (jta != null)
                jta.append(s);
        }

    }

    @Override
    public void paint(Graphics g)
    {
        super.paint(g);
        uiInitCond.setNotifyAll();
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
                        downloadManager.choseDestinationDir(GraphicUI.this);
                    }
                });
                
                engineInitCond.waitForTrue();

                if (downloadManager.getDestinationDir() == null)
                    return; // cancelled

                // TODO: if we wanted to continue to accept input from someplace,
                // we would loop here and keep calling pop()
                Object obj = inputQueue.pop();
                if (obj != null)
                {
                    try
                    {
                        Iterator<DownloadDescriptor> iter = (Iterator<DownloadDescriptor>) obj;
                        downloadManager.add(iter);
                    }
                    catch(ClassCastException ex)
                    {
                        log.error("BUG: got unexpected object from input queue: " + obj.getClass().getName());
                    }
                }
                
            }
            catch(Throwable t) 
            { 
                if (log.isDebugEnabled())
                    log.error("DelayedInit failed", t);
                else
                    log.error("DelayedInit failed: " + t);
            }
            finally
            {
                // TODO: stop showing busy
            }
        }
    }
    
    // react to JDownloadManager state-changed events
    public void stateChanged(ChangeEvent e)
    {
        ApplicationConfig conf = getApplicationContainer().getConfig();

        boolean retryEnabled = downloadManager.getRetryEnabled();
        int threadCount = downloadManager.getThreadCount();
        File destDir = downloadManager.getDestinationDir();

        try 
        {
            log.debug("updating configuration: " + retryEnabled + "," + threadCount + "," + destDir);
            conf.setSection(configSection, true);
            conf.putValue(threadCountConfigKey, Integer.toString(threadCount));
            conf.putValue(retryConfigKey, Boolean.toString(retryEnabled));
            if (destDir != null)
                conf.putValue(downloadDirConfigKey, destDir.getAbsolutePath()); 
            log.debug("updating configuration... done");
        }
        catch(IOException ioex) 
        {
            log.debug("updating configuration... failed: " + ioex.getMessage());
        }
    }
}
