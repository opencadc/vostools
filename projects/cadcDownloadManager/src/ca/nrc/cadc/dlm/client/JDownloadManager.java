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
import ca.nrc.cadc.dlm.ManifestReader;
import ca.nrc.cadc.net.HttpDownload;
import ca.nrc.cadc.net.HttpTransfer;
import ca.nrc.cadc.net.event.TransferEvent;
import ca.nrc.cadc.net.event.TransferListener;
import ca.nrc.cadc.util.FileMetadata;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.Authenticator;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import org.apache.log4j.Logger;

/**
 * Manage download of multiple files simultaneously.
 * Created on 8-Aug-2005.
 * 
 * @version $Version$
 * @author pdowler
 */
public class JDownloadManager extends JPanel implements TransferListener
{
    private static final long serialVersionUID = 201008051500L;
    private static Logger log = Logger.getLogger(JDownloadManager.class);

    private static String userAgent = "CADC DownloadManager(GraphicUI) " + HttpTransfer.DEFAULT_USER_AGENT;

    private static String COMPLETED = " Completed: ";
    private static String CANCELLED = " Cancelled: ";
    private static String FAILED = " Failed: ";
    private static String TOTAL = " Total: ";
    
    private boolean autoRemove = false;
    private boolean autoDecompress = false;
    private JPanel tc;
    private Box status;
    private Box downloads;
    
    // status/controls
    private Component clearButton;
    private Component cancelButton;
    private JLabel completeLabel;
    private JLabel cancelLabel;
    private JLabel failLabel;
    private JLabel totalLabel;
    private JProgressBar progress;
    private int numTotal = 0;
    private int numCompleted = 0;
    private int numCancelled = 0;
    private int numFailed = 0;

    private FileOverwriteDecider fod;
    
    private List downloadListeners;
    private DownloadManager downloadManager;
    private Set<URL> queuedURL = new HashSet<URL>();
    
    private File initialDir;
    
    public JDownloadManager() { this(DownloadManager.DEFAULT_THREAD_COUNT, null); }
    
    public JDownloadManager(int initialThreadCount) { this(initialThreadCount, null); }
    
    public JDownloadManager(int initialThreadCount, File initialDir)
    {
        super(new BorderLayout());
        if (initialThreadCount < 1)
            initialThreadCount = 1;
        if (initialThreadCount > DownloadManager.MAX_THREAD_COUNT)
            initialThreadCount = DownloadManager.MAX_THREAD_COUNT;

        SpinnerThreadControl threadControl = new SpinnerThreadControl(new Integer(initialThreadCount), DownloadManager.MAX_THREAD_COUNT);
        downloadManager = new DownloadManager(threadControl, initialThreadCount, initialDir);
        downloadManager.addDownloadListener(this);

        // configure custom authentication
        Authenticator.setDefault(new HttpAuthenticator(this));
        
        // use a singe shared FOD so that global decisions stick
        this.fod = new FileOverwriteDecider(this);
        
        JPanel scrollable = new JPanel(new BorderLayout());
        this.downloads = new Box(BoxLayout.Y_AXIS);
        scrollable.add(downloads, BorderLayout.NORTH);
        JPanel extraSpace = new JPanel();
        extraSpace.setBackground(Color.WHITE);
        scrollable.add(extraSpace, BorderLayout.CENTER); // take up all extra space
        
        this.tc = new JPanel();        
        
        JFormattedTextField tf = ((JSpinner.DefaultEditor)threadControl.getEditor()).getTextField();
        tf.setEditable(false);
        tc.add(new JLabel("max simultaneous downloads:"));
        tc.add(threadControl);
        
        this.status = new Box(BoxLayout.Y_AXIS);
        this.completeLabel = new JLabel(COMPLETED + "0");
        this.cancelLabel =   new JLabel(CANCELLED + "0");
        this.failLabel =     new JLabel(FAILED + "0");
        this.totalLabel =    new JLabel(TOTAL + "0");
        this.progress = new JProgressBar();
        progress.setMinimum(0);
        // add an empty border to the exterior
        progress.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(4,20,4,20), progress.getBorder()));
        status.add(progress);
        Box tmp = new Box(BoxLayout.X_AXIS);
        tmp.add(completeLabel);
        tmp.add(cancelLabel);
        tmp.add(failLabel);
        tmp.add(totalLabel);
        status.add(tmp);
        
        JPanel buttons = new JPanel();
        this.clearButton = buttons.add(new JButton(new ClearAction()));
        this.cancelButton = buttons.add(new JButton(new CancelAction()));
        
        Box south = new Box(BoxLayout.Y_AXIS);
        south.add(tc);
        south.add(status);
        south.add(buttons);
        
        
        JScrollPane sp = new JScrollPane(scrollable);
        this.add(sp, BorderLayout.CENTER);
        this.add(south, BorderLayout.SOUTH);

        setMinimumSize(new Dimension(300, 500));
        setInitialDir(initialDir);

        // support recursive download via manifest file
        addDownloadListener(new ManifestDownloadListener());
    }

    private class CancelAction extends AbstractAction
    {
        public CancelAction() 
        {
            super("Cancel"); 
            putValue(Action.SHORT_DESCRIPTION, "Cancel all downloads immediately.");
        }
        
        public void actionPerformed(ActionEvent e)
        {
            log.debug("CancelAction.actionPerformed()");
            stop();
            cancelButton.setEnabled(false);
        }
    }
    private class ClearAction extends AbstractAction
    {
        private int type = -1;

        public ClearAction()
        {
            super("Clear");
            putValue(Action.SHORT_DESCRIPTION, "Remove all completed and cancelled downloads from the list.");
        }
        
        public ClearAction(String s, int type) 
        {
            super(s);
            this.type = type;
            switch(type)
            {
                case TransferEvent.COMPLETED:
                    putValue(Action.SHORT_DESCRIPTION, "Remove all completed downloads from the list.");
                    break;
                case TransferEvent.CANCELLED:
                    putValue(Action.SHORT_DESCRIPTION, "Remove all cancelled downloads from the list.");
                    break;
                case TransferEvent.FAILED:
                    putValue(Action.SHORT_DESCRIPTION, "Remove all failed downloads from the list.");
                    break;
            }
        }
        
        public void actionPerformed(ActionEvent e)
        {
            log.debug("ClearAction.actionPerformed()");
            ArrayList removals = new ArrayList();
            for (int i=0; i<downloads.getComponentCount(); i++)
            {
                JDownload jdl = (JDownload) downloads.getComponent(i);
                TransferEvent te = jdl.getLastEvent();
                if (te != null && doClear(te.getState()))
                    removals.add(jdl);
            }
            for (int i=0; i<removals.size(); i++)
                downloads.remove( (Component) removals.get(i));
            validateTree();
            repaint();
        }
        
        private boolean doClear(int state)
        {
            if (this.type == state)
                return true;
            if (state == TransferEvent.COMPLETED || state == TransferEvent.CANCELLED)
                return true;
            return false;
        }
    }
    
    public int getThreadCount() 
    {
        return downloadManager.getThreadCount();
    }
    
    public void setThreadCount(int tc) 
    {
        downloadManager.setThreadCount(tc);
    }
       
    public void setInitialDir(File initialDir) { this.initialDir = initialDir; }
    
    public File getInitialDir() { return initialDir; }

    public void setDestinationDir(File destDir) {
        downloadManager.setDestinationDir(destDir);
    }

    public File getDestinationDir() { 
        return downloadManager.getDestinationDir();
    }
    
    public void addChangeListener(ChangeListener listener)
    {
        downloadManager.addChangeListener(listener);
    }
    
    /**
     * Register for download events.
     *
     * @param dl the listener
     */
    public void addDownloadListener(TransferListener dl)
    {
        if (dl == null)
            return;
        if (downloadListeners == null)
            this.downloadListeners = new ArrayList();
        downloadListeners.add(dl);
    }
    
    public void removeDownloadListener(TransferListener dl)
    {
        if (dl == null)
            return;
        if (downloadListeners == null)
            return;
        downloadListeners.remove(dl);
    }
    
    // DownloadListener
    public void transferEvent(TransferEvent e)
    {
        log.debug("transferEvent: " + e);
        switch(e.getState())
        {
            case TransferEvent.CANCELLED:
            case TransferEvent.COMPLETED:
            case TransferEvent.FAILED:
                if (SwingUtilities.isEventDispatchThread())
                    new UpdateUI(e).run();
                else
                    SwingUtilities.invokeLater(new UpdateUI(e));
        }
        
        // rebroadcast event to other listeners
        if (downloadListeners == null || downloadListeners.size() == 0)
        {
            log.debug("downloadListeners: none");
            return;
        }
        log.debug("downloadListeners: " + downloadListeners.size());
        for (int i=0; i<downloadListeners.size(); i++)
        {
            TransferListener tl = (TransferListener) downloadListeners.get(i);
            tl.transferEvent(e);
        }
    }
    
    public String getEventHeader()
    {
        return null;
    }
    
    public void choseDestinationDir(Component parent)
    {
        try
        {
            log.debug("initialDir: " + getInitialDir());
            MyFileChooser chooser = new MyFileChooser(getInitialDir());
            
            int returnVal = chooser.showDialog(parent, "Select");
            if (returnVal == JFileChooser.APPROVE_OPTION)
            {
                downloadManager.setDestinationDir(chooser.getSelectedFile());
                String estr = null;
                if (!downloadManager.getDestinationDir().isDirectory()) // in case the user types something in
                    estr = "'" + downloadManager.getDestinationDir().getAbsolutePath() + "' is not a directory";
                else if (!downloadManager.getDestinationDir().canWrite())
                    estr = "'" + downloadManager.getDestinationDir().getAbsolutePath() + "' is not writable";
                if (estr != null)
                {
                    JOptionPane.showMessageDialog(parent, estr, "Error", JOptionPane.ERROR_MESSAGE);
                    downloadManager.setDestinationDir(null);
                    choseDestinationDir(parent); // recursive
                }
                else
                {
                    downloadManager.fireChangeEvent();
                }
            }
        }
        catch(RuntimeException rex)
        {
            rex.printStackTrace();
        }
        log.debug("destDir: " + downloadManager.getDestinationDir());
    }
    
    public void start()
    {
        
        downloadManager.start();
        
        if ( SwingUtilities.isEventDispatchThread() )
        {
            log.debug("invoking doStart() directly");
            downloadManager.startThreadControl();
        }
        else
            try
            {
                log.debug("invoking doStart() via invokeAndWait");
                SwingUtilities.invokeAndWait(new Runnable() 
                { 
                    public void run() {downloadManager.startThreadControl(); } 
                });
            }
            catch(Throwable ignore) { }
    }
    
    /**
     * Terminate all downloads and release resources.
     */
    public void stop()
    {

        downloadManager.stop();

        if ( SwingUtilities.isEventDispatchThread() )
            downloadManager.stopThreadControl();
        else
            try
            {
                SwingUtilities.invokeAndWait(new Runnable() 
                { 
                    public void run() { downloadManager.stopThreadControl(); } 
                });
            }
            catch(Throwable ignore) { }
    }
    
    private class UpdateUI implements Runnable
    {
        TransferEvent e;
        UpdateUI(TransferEvent e) { this.e = e; }
        public void run()
        {
            switch(e.getState())
            {
                case TransferEvent.FAILED:
                    log.error(e.getStateLabel() + ": " + e.getURL() + " -> " + e.getFile(), e.getError());
                    numFailed++;
                    break;
                case TransferEvent.CANCELLED:
                    log.info(e.getStateLabel() + ": " + e.getURL() + " -> " + e.getFile());
                    numCancelled++;
                    break;
                case TransferEvent.COMPLETED:
                    log.info(e.getStateLabel() + ": " + e.getURL() + " -> " + e.getFile());
                    numCompleted++;
                    break;
            }
            updateStatus();
        }
    }
    private void updateStatus()
    {
        int tot = numCompleted + numCancelled + numFailed;
        completeLabel.setText(COMPLETED + numCompleted);
        cancelLabel.setText(CANCELLED + numCancelled);
        failLabel.setText(FAILED + numFailed);
        totalLabel.setText(TOTAL + tot + " of " + numTotal);
        progress.setMaximum(numTotal);
        progress.setValue(tot);
        if (tot == numTotal && cancelButton.isEnabled())
            cancelButton.setEnabled(false);
        else if ( !cancelButton.isEnabled() )
            cancelButton.setEnabled(true);
        repaint();
    }

    public void add(Iterator<DownloadDescriptor> downloads)
    {
        while ( downloads.hasNext() )
        {
            DownloadDescriptor dd = downloads.next();
            if (DownloadDescriptor.OK.equals(dd.status))
            {
                File dest = downloadManager.getDestinationDir();
                if (dd.destination != null)
                    dest = new File(dest, dd.destination);
                HttpDownload dl = new HttpDownload(userAgent, dd.url, dest);
                this.add(dd, dl);
            }
            else
            {
                StringBuffer sb = new StringBuffer();
                sb.append("failed to setup download: ");
                if (dd.uri != null)
                    sb.append(dd.uri);
                if (dd.destination != null)
                {
                    sb.append(" -> ");
                    sb.append(dd.destination);
                }
                sb.append("\n\treason: ");
                sb.append(dd.error);
                log.error(sb.toString());
            }
        }
    }

    /**
     * Add a new Download to the queue. This method will silently do nothing and return if the user
     * cancelled the selection of a destination directory.
     * @param dl
     */
    private void add(final DownloadDescriptor dd, final HttpDownload dl)
    {
        if ( SwingUtilities.isEventDispatchThread())
            doAddDownload(dd, dl);
        else
            SwingUtilities.invokeLater(new Runnable()
            {
               public void run()
               {
                   doAddDownload(dd, dl);
               }
            });
    }
    
    // implementation to be invoked in the UI event thread
    private void doAddDownload(DownloadDescriptor dd, HttpDownload dl)
    {
        if (downloadManager.getDestinationDir() == null)
            return; // user cancelled dest dir selection dialog, nothing to do

        if ( queuedURL.contains(dd.url) )
        {
            StringBuffer sb = new StringBuffer();
            sb.append("duplicate URL: ");
            if (dd.uri != null)
                sb.append(dd.uri);
            sb.append(" -> ");
                sb.append(dd.url);
            if (dd.destination != null)
            {
                sb.append(" -> ");
                sb.append(dd.destination);
            }
            log.warn(sb.toString());
            return;
        }
        queuedURL.add(dd.url);
        dl.setOverwriteChooser(fod);

        log.debug("adding " + dl + " to queue");
        JDownload jdl = new JDownload(dl);
        Util.recursiveSetBackground(jdl, Color.WHITE);
        downloads.add(jdl);
        validateTree();
        repaint();
        downloadManager.addDownload(dl);
        numTotal++;
        updateStatus();
    }

    private class ManifestDownloadListener implements TransferListener
    {
        public String getEventHeader()
        {
            return null;
        }

        public void transferEvent(TransferEvent e)
        {
            log.debug("ManifestDownloadListener.transferEvent: " + e);
            // check for possible recursive download
            if (e.getState() == TransferEvent.COMPLETED)
            {
                FileMetadata meta = e.getFileMetadata();
                log.debug("file metadata: " + meta);
                if (meta != null && ManifestReader.CONTENT_TYPE.equals(meta.getContentType()))
                {
                    // TODO: figure out which thread calls this...
                    // should spawn a thread to do this or have one standing
                    // by since the current thread is probably the one running the
                    // download and we want to free it up asap
                    log.debug("ManifestDownloadListener.transferEvent: current thread: " + Thread.currentThread().getName());
                    try
                    {
                        log.debug("manifest download: " + e.getFile());
                        Iterator<DownloadDescriptor> iter = new ManifestReader(new FileReader(e.getFile()));
                        add(iter);
                    }
                    catch(IOException ex)
                    {
                        log.error("failed to read download manifest " + e.getFile(), ex);
                    }
                }
            }
        }

    }
}
