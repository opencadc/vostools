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

import ca.nrc.cadc.dlm.client.event.DownloadListener;
import ca.nrc.cadc.dlm.client.event.DownloadEvent;
import ca.nrc.cadc.thread.Queue;
import ca.nrc.cadc.thread.QueueUpdater;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.net.Authenticator;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.SwingUtilities;

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
    
    private static final int MAX_THREAD_COUNT = 11;
    private static final int DEFAULT_THREAD_COUNT = 1;
    private static String COMPLETED = " Completed: ";
    private static String CANCELLED = " Cancelled: ";
    private static String FAILED = " Failed: ";
    private static String TOTAL = " Total: ";
    
    private boolean autoRemove = false;
    private boolean autoDecompress = false;
    private JPanel tc;
    private Box status;
    private Box downloads;
    private JSpinner threadControl;
    private ThreadPool pool;
    
    private List changeListeners;
    private List downloadListeners;
    
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

    private File initialDir;
    private File destDir;
    private FileOverwriteDecider fod;


    public JDownloadManager() { this(DEFAULT_THREAD_COUNT, null); }
    
    public JDownloadManager(int initialThreadCount) { this(initialThreadCount, null); }
    
    public JDownloadManager(int initialThreadCount, File initialDir)
    {
        super(new BorderLayout());

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

        if (initialThreadCount < 1)
            initialThreadCount = 1;
        if (initialThreadCount > MAX_THREAD_COUNT)
            initialThreadCount = MAX_THREAD_COUNT;
        
        this.tc = new JPanel();
        this.threadControl = new JSpinner();
        SpinnerModel sm = new SpinnerNumberModel(new Integer(initialThreadCount), new Integer(1), new Integer(MAX_THREAD_COUNT), new Integer(1));
        threadControl.setModel(sm);
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
        return ((Integer)threadControl.getValue()).intValue(); 
    }
    
    public void setThreadCount(int tc) 
    {
        threadControl.setValue(new Integer(tc));
    }
    
       
    public void setInitialDir(File initialDir) { this.initialDir = initialDir; }

    public void setDestinationDir(File destDir) { this.destDir = destDir; }

    public File getDestinationDir() { return destDir; } 
    
    public void choseDestinationDir(Component parent)
    {
        try
        {
            msg("initialDir: " + initialDir);
            MyFileChooser chooser = new MyFileChooser(initialDir);
            
            int returnVal = chooser.showDialog(parent, "Select");
            if (returnVal == JFileChooser.APPROVE_OPTION)
            {
                destDir = chooser.getSelectedFile();
                String estr = null;
                if (!destDir.isDirectory()) // in case the user types something in
                    estr = "'" + destDir.getAbsolutePath() + "' is not a directory";
                else if (!destDir.canWrite())
                    estr = "'" + destDir.getAbsolutePath() + "' is not writable";
                if (estr != null)
                {
                    JOptionPane.showMessageDialog(parent, estr, "Error", JOptionPane.ERROR_MESSAGE);
                    destDir = null;
                    choseDestinationDir(parent); // recursive
                }
                else
                {
                    fireChangeEvent();
                }
            }
        }
        catch(RuntimeException rex)
        {
            rex.printStackTrace();
        }
        msg("destDir: " + destDir);
    }
    
    public void start()
    {
        
        if (pool == null)
        {
            msg("creating ThreadPool");
            this.pool = new ThreadPool();
            threadControl.getModel().addChangeListener(pool);
        }
        
        if ( SwingUtilities.isEventDispatchThread() )
        {
            msg("invoking doStart() directly");
            threadControl.setEnabled(true);
        }
        else
            try
            {
                msg("invoking doStart() via invokeAndWait");
                SwingUtilities.invokeAndWait(new Runnable() 
                { 
                    public void run() {threadControl.setEnabled(true); } 
                });
            }
            catch(Throwable ignore) { }
    }
    
    /**
     * Terminate all downloads and release resources.
     */
    public void stop()
    {
        if (pool != null)
        {    
            pool.terminate();
            pool = null;
        }
        if ( SwingUtilities.isEventDispatchThread() )
            threadControl.setEnabled(false);
        else
            try
            {
                SwingUtilities.invokeAndWait(new Runnable() 
                { 
                    public void run() { threadControl.setEnabled(false); } 
                });
            }
            catch(Throwable ignore) { }
    }
    
    /**
     * Register for change events. This class fires events when configuration items 
     * (thread pool size, destination directory) are changed/set by the user.
     * 
     * @param cl the listener
     */
    public void addChangeListener(ChangeListener cl)
    {
        if (changeListeners == null)
            this.changeListeners = new ArrayList();
        changeListeners.add(cl);
    }
    
    public void removeChangeListener(ChangeListener cl)
    {
        if (changeListeners == null)
            return;
        changeListeners.remove(cl);
    }
    
    private void fireChangeEvent()
    {
        if (changeListeners == null)
            return;
        ChangeEvent e = new ChangeEvent(this);
        for (int i = 0; i < changeListeners.size(); i++)
            ((ChangeListener) changeListeners.get(i)).stateChanged(e);
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
        if (destDir == null)
            return; // user cancelled dest dir selection dialog, nothing to do
        dl.destDir = destDir;

        dl.setDebug(debug);
        dl.setDownloadListener(this);
        dl.setFileOverwriteDecider(fod);

        msg("adding " + dl + " to queue");
        JDownload jdl = new JDownload(dl);
        jdl.setDebug(debug);
        Util.recursiveSetBackground(jdl, Color.WHITE);
        downloads.add(jdl);
        validateTree();
        repaint();
        pool.add(dl);
        numTotal++;
        updateStatus();
    }
    
    private String workerBasename = "JDownloadManager.WorkerThread: ";

    private class ThreadPool implements ChangeListener
    {
        private Queue tasks;
        private ArrayList threads;
        private int numWorkers;

        public ThreadPool()
        {
            this.tasks = new Queue();
            this.threads = new ArrayList(MAX_THREAD_COUNT);
            stateChanged(null); // init threads
        }
        void msg(String s)
        {
            if (debug) System.out.println("[ThreadPool] " + s);
        }
        
        public void add(Download task)
        {
            msg("queueing: " + task);
            tasks.push(task);
        }

        public void terminate()
        {
            msg("ThreadPool.terminate()");
            
            // terminate thread pool members
            numWorkers = 0;
            synchronized(threads) // vs sync block in WorkerThread.run() that calls threads.remove(this) after an interrupt
            {
                for (int i=0; i<threads.size(); i++)
                {
                    msg("ThreadPool.terminate() interrupting WorkerThread " + i);
                    WorkerThread wt = (WorkerThread) threads.get(i);
                    synchronized(wt)
                    {
                        msg("ThreadPool.terminate(): interrupting " + wt.getName());
                        wt.interrupt();
                    }
                }
            }

            // pop remaining tasks from queue and cancel them
            msg("ThreadPool.terminate() flushing queue");
            tasks.update(new QueueFlush());
            msg("ThreadPool.terminate() DONE");
        }
        
        public void stateChanged(ChangeEvent e)
        {
            msg("ThreadPool: stateChanged("+e+")");
            this.numWorkers = ((Integer) threadControl.getValue()).intValue();
            if (numWorkers < 1)
            {
                threadControl.setValue(new Integer(1));
                return;
            }
            if (numWorkers > MAX_THREAD_COUNT)
            {
                threadControl.setValue(new Integer(MAX_THREAD_COUNT));
                return;
            }
                
            msg("current thread count: " + threads.size() + " new size: " + numWorkers);
            if (threads.size() == numWorkers)
                return;
            
            synchronized(threads)
            {
                while (threads.size() < numWorkers)
                {
                    msg("adding worker thread");
                    WorkerThread t = new WorkerThread();
                    t.setPriority(Thread.MIN_PRIORITY); // mainly IO blocked anyway, so keep the UI thread happy
                    threads.add(t);
                    t.start();
                }
            }
            
            if (numWorkers == 11)
            {
                // TODO: play a little guitar riff?
            }
            
            // it is possible that the state didn't actually change in cases where 
            // we enforced the min or max values above, but this should be harmless
            fireChangeEvent();
        }

        // this updater runs through the queue, removes Downloads, and terminates them
        private class QueueFlush implements QueueUpdater
        {
            // this method has exclusive access to the queue contents, so we do not
            // have to worry about a WorkerThread taking something with pop()
            public boolean update(List list)
            {
                int count = 0;
                ListIterator i = list.listIterator();
                while ( i.hasNext() )
                {
                    Download dl = (Download) i.next();
                    msg("ThreadPool: terminating queued download");
                    dl.terminate();
                    i.remove();
                    count++;
                }
                return (count > 0);
            }
        }
    
        private class WorkerThread extends Thread
        {
            Download currentTask;
            
            WorkerThread()
            {
                super();
                setDaemon(true);
                setName(workerBasename);
            }

            // threads keep running as long as they are in the threads list
            public void run()
            {
                msg(workerBasename + "START");
                boolean cont = true;
                while (cont)
                {
                    try
                    {
                        Object tmp = tasks.pop(); // block here
                        synchronized(this) 
                        {
                            currentTask = (Download) tmp;
                        }
                        synchronized(threads) 
                        {
                            cont = threads.contains(this); 
                        }
                        if (cont)
                        {
                            msg(workerBasename + "still part of pool");
                            // set thread name so thread dumps are intelligible
                            setName(workerBasename + currentTask);
                            currentTask.run();
                        }
                        else
                        {
                            msg(workerBasename + "no longer part of pool");
                            synchronized(this) // vs sync block in terminate()
                            {
                                // make sure to clear interrupt flag from an interrupt() in stateChanged()
                                // in case it comes after pop() and before threads.contains()
                                interrupted();
                                // we should quit, so put task back
                                msg(workerBasename + "OOPS (put it back): " + tmp);
                                tasks.push(tmp);
                                currentTask = null;
                            }
                        }
                    }
                    catch (InterruptedException ignore)
                    {
                        // pop() was interrupted, let finally and while condition decide if we
                        // should loop or return
                    }
                    finally
                    {
                        setName(workerBasename);
                        synchronized(this) { currentTask = null; }
                        synchronized(threads)
                        {
                            if (threads.size() > numWorkers)
                            {
                                msg(workerBasename + "numWorkers=" + numWorkers + " threads.size() = " + threads.size());
                                threads.remove(this);
                            }
                            cont = threads.contains(this);
                        }
                    }
                }
                msg(workerBasename + "DONE");
            }
        }
    }
}
