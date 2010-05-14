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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.File;
import java.net.Authenticator;
import java.util.ArrayList;
import java.util.List;

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

import ca.nrc.cadc.dlm.client.event.DownloadEvent;
import ca.nrc.cadc.dlm.client.event.DownloadListener;

/**
 * Manage download of multiple files simultaneously.
 * Created on 8-Aug-2005.
 * 
 * @version $Version$
 * @author pdowler
 */
public class JDownloadManager extends JPanel implements DownloadListener
{
    
    private static boolean testing = false;
    private boolean debug = false;
    
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
    }

    public void setDebug(boolean debug) { this.debug = debug; }
    
    private class CancelAction extends AbstractAction
    {
        public CancelAction() 
        {
            super("Cancel"); 
            putValue(Action.SHORT_DESCRIPTION, "Cancel all downloads immediately.");
        }
        
        public void actionPerformed(ActionEvent e)
        {
            msg("CancelAction.actionPerformed()");
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
                case DownloadEvent.COMPLETED:
                    putValue(Action.SHORT_DESCRIPTION, "Remove all completed downloads from the list.");
                    break;
                case DownloadEvent.CANCELLED:
                    putValue(Action.SHORT_DESCRIPTION, "Remove all cancelled downloads from the list.");
                    break;
                case DownloadEvent.FAILED:
                    putValue(Action.SHORT_DESCRIPTION, "Remove all failed downloads from the list.");
                    break;
            }
        }
        
        public void actionPerformed(ActionEvent e)
        {
            msg("ClearAction.actionPerformed()");
            ArrayList removals = new ArrayList();
            for (int i=0; i<downloads.getComponentCount(); i++)
            {
                JDownload jdl = (JDownload) downloads.getComponent(i);
                DownloadEvent de = jdl.getLastEvent();
                if (de != null && doClear(de.getState()))
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
            if (state == DownloadEvent.COMPLETED || state == DownloadEvent.CANCELLED)
                return true;
            return false;
        }
    }
    
    private void msg(String s)
    {
         if (debug) System.out.println("[JDownloadManager] " + s);
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
    public void addDownloadListener(DownloadListener dl)
    {
        if (dl == null)
            return;
        if (downloadListeners == null)
            this.downloadListeners = new ArrayList();
        downloadListeners.add(dl);
    }
    
    public void removeDownloadListener(DownloadListener dl)
    {
        if (dl == null)
            return;
        if (downloadListeners == null)
            return;
        downloadListeners.remove(dl);
    }
    
    // DownloadListener
    public void downloadEvent(DownloadEvent e)
    {
        msg("downloadEvent: " + e);
        switch(e.getState())
        {
            case DownloadEvent.FAILED:
            case DownloadEvent.CANCELLED:
            case DownloadEvent.COMPLETED: 
                if (SwingUtilities.isEventDispatchThread())
                    new UpdateUI(e).run();
                else
                    SwingUtilities.invokeLater(new UpdateUI(e));
        }
        
        // rebroadcast event to other listeners
        if (downloadListeners == null || downloadListeners.size() == 0)
            return;
        for (int i=0; i<downloadListeners.size(); i++)
        {
            DownloadListener dl = (DownloadListener) downloadListeners.get(i);
            dl.downloadEvent(e);
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
            msg("initialDir: " + getInitialDir());
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
        msg("destDir: " + downloadManager.getDestinationDir());
    }
    
    public void start()
    {
        
        downloadManager.start();
        
        if ( SwingUtilities.isEventDispatchThread() )
        {
            msg("invoking doStart() directly");
            downloadManager.startThreadControl();
        }
        else
            try
            {
                msg("invoking doStart() via invokeAndWait");
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
        DownloadEvent e;
        UpdateUI(DownloadEvent e) { this.e = e; }
        public void run()
        {
            switch(e.getState())
            {
                case DownloadEvent.FAILED:
                    numFailed++;
                    break;
                case DownloadEvent.CANCELLED:
                    numCancelled++;
                    break;
                case DownloadEvent.COMPLETED:
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
    
    /**
     * Add a new Download to the queue. This method will silently do nothing and return if the user
     * cancelled the selection of a destination directory.
     */
    public void add(final Download dl)
    {
        if ( SwingUtilities.isEventDispatchThread())
            doAddDownload(dl);
        else
            SwingUtilities.invokeLater(new Runnable()
            {
               public void run()
               {
                   doAddDownload(dl);
               }
            });
    }
    
    // implementation to be invoked in the UI event thread
    private void doAddDownload(Download dl)
    {
        if (downloadManager.getDestinationDir() == null)
            return; // user cancelled dest dir selection dialog, nothing to do
        dl.destDir = downloadManager.getDestinationDir();

        dl.setDebug(debug);
        dl.setFileOverwriteDecider(fod);

        msg("adding " + dl + " to queue");
        JDownload jdl = new JDownload(dl);
        jdl.setDebug(debug);
        Util.recursiveSetBackground(jdl, Color.WHITE);
        downloads.add(jdl);
        validateTree();
        repaint();
        downloadManager.addDownload(dl);
        numTotal++;
        updateStatus();
    }

}
