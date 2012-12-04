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

package ca.nrc.cadc.vos.client.ui;


import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;

import javax.security.auth.Subject;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import ca.nrc.cadc.thread.ConditionVar;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.client.VOSpaceClient;
import ca.onfire.ak.AbstractApplication;


/**
 * The main class for graphical output display.  Care has been taken to adhere
 * to the rules of conduct in Swing:
 *
 * 1. Swing Components must be created in the EDT (Event Dispatch Thread)
 * 2. Swing Components must be accessed in the EDT, unless calling methods
 *    documented as "thread safe".
 *
 * Even if a Component is documented as thread-safe, it is not necessarily to
 * be trusted as such.
 *
 * @author jenkinsd
 */
public class GraphicUI extends AbstractApplication
        implements ChangeListener, UserInterface
{
    private static final String NAME = "MainUI";

    private static final long serialVersionUID = 201210201041L;
    private static Logger LOGGER = Logger.getLogger(GraphicUI.class);

    private LogWriter logWriter;
    private JTabbedPane tabPane;
    private JUploadManager uploadManager;
    private ConditionVar uiInitCond;

    private final VOSURI targetVOSpaceURI;
    private final VOSpaceClient voSpaceClient;
    
    private Subject subject;


    public GraphicUI(final Level logLevel, final VOSURI targetVOSpaceURI,
                     final VOSpaceClient voSpaceClient, Subject subject)
    {
        super(new BorderLayout());
        LOGGER.setLevel(logLevel);
        setName(NAME);

        this.targetVOSpaceURI = targetVOSpaceURI;
        this.voSpaceClient = voSpaceClient;
        this.subject = subject;
        
        this.uiInitCond = new ConditionVar();
        uiInitCond.set(false);
    }


    /**
     * The GUI can be constructed using information from the
     * <code>getApplicationContainer()</code> method, which includes
     * the access to the AppletContext (if in applet mode) and
     * possibly to an ApplicationConfig object.
     */
    @Override
    protected void makeUI()
    {
        
        tabPane = new JTabbedPane();
        getTabPane().setName("tabPane");

        logWriter = new LogWriter(new JTextArea());

        Log4jInit.setLevel("ca.nrc.cadc", LOGGER.getLevel(),
                           new BufferedWriter(getLogWriter()));
        LOGGER.debug("Executing tasks against VOSpace found at "
                + getVOSpaceClient().getBaseURL());

        addMainPane();
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        
        setUploadManager(
                new JUploadManager(getTargetVOSpaceURI(),
                        getVOSpaceClient(), subject));

        getTabPane().addTab("Upload",
                getUploadManager());
        
        final JScrollPane sp =
                createLogScrollPane(
                        getLogWriter().getLogTextArea());
        getTabPane().addTab("Log Messages", sp);

        Util.recursiveSetBackground(GraphicUI.this,
                Color.WHITE);
        getTabPane().setVisible(true);
        
        DirectoryChooser dirChooser = new DirectoryChooser();
        Thread dirChooserThread = new Thread(dirChooser);
        dirChooserThread.start();
    }
    
    @Override
    public void paint(Graphics g)
    {
        super.paint(g);
        uiInitCond.setNotifyAll();
    }

    /**
     * Add the tabbed pane.
     */
    protected void addMainPane()
    {
        add(getTabPane(), BorderLayout.CENTER);
    }

    /**
     * Create an instance of a JScrollPane to contain the log output.
     *
     * @param logTextArea   The JTextArea to scroll.
     * @return              The JScrollPane instance.
     */
    protected JScrollPane createLogScrollPane(final JTextArea logTextArea)
    {
        return new JScrollPane(logTextArea);
    }

    /**
     * Invoked when the target of the listener has changed its state.
     *
     * @param e a ChangeEvent object
     */
    @Override
    public void stateChanged(final ChangeEvent e)
    {

    }

    /**
     * The default method always returns true immediately.
     *
     * @return true
     */
    @Override
    public boolean quit()
    {
        final boolean ret = getConfirmation("OK to quit?");

        if (ret && (getUploadManager() != null))
        {
            getUploadManager().stop();
        }

        return ret;
    }

    public JTabbedPane getTabPane()
    {
        return tabPane;
    }

    public LogWriter getLogWriter()
    {
        return logWriter;
    }

    public JUploadManager getUploadManager()
    {
        return uploadManager;
    }

    public void setUploadManager(final JUploadManager uploadManager)
    {
        this.uploadManager = uploadManager;
    }

    public VOSURI getTargetVOSpaceURI()
    {
        return targetVOSpaceURI;
    }

    public VOSpaceClient getVOSpaceClient()
    {
        return voSpaceClient;
    }

    public void selectSourceDirectory(final Component parent,
                                      final SourceDirectoryChooserCallback callback)
    {
        try
        {
            final SourceDirectoryChooser fileChooser =
                    getSourceDirectoryChooser();
            final int returnVal = fileChooser.showDialog(parent, "Select");

            if (returnVal == JFileChooser.APPROVE_OPTION)
            {
                final File sourceDirectory = fileChooser.getSelectedFile();

                final String estr;
                // in case the user types something in
                if (!sourceDirectory.isDirectory())
                {
                    estr = "'" + sourceDirectory.getAbsolutePath()
                           + "' is not a directory";
                }
                else if (!sourceDirectory.canRead())
                {
                    estr = "'" + sourceDirectory.getAbsolutePath()
                           + "' is not writable";
                }
                else
                {
                    estr = null;
                }

                if (estr != null)
                {
                    handleError(estr, parent, callback); // recursive
                }
                else
                {
                    LOGGER.info("Source directory: "
                                + sourceDirectory.getAbsolutePath());

                    if (callback != null)
                    {
                        callback.onCallback(sourceDirectory);
                    }
                }
            }
        }
        catch (RuntimeException rex)
        {
            rex.printStackTrace();
            LOGGER.error("Failed to determine Source Directory", rex);
        }
    }

    protected void handleError(final String message, final Component parent,
                               final SourceDirectoryChooserCallback callback)
    {
        JOptionPane.showMessageDialog(parent, message, "Error",
                                      JOptionPane.ERROR_MESSAGE);
        selectSourceDirectory(parent, callback);
    }

    protected SourceDirectoryChooser getSourceDirectoryChooser()
    {
        return new SourceDirectoryChooser(null, "sourceDirectoryChooser");
    }


    private class LogWriter extends Writer
    {
        private final JTextArea logTextArea;


        LogWriter(final JTextArea textArea)
        {
            super();
            logTextArea = textArea;
        }


        @Override
        public void close() throws IOException
        {
        }

        @Override
        public void flush() throws IOException
        {
        }

        public JTextArea getLogTextArea()
        {
            return logTextArea;
        }

        @Override
        public void write(final char[] cbuf, final int off, final int len)
                throws IOException
        {
            String logMessage = new String(cbuf, off, len);
            if (getLogTextArea() != null)
            {
                getLogTextArea().append(logMessage);
            }
            else
            {
                System.out.println(logMessage);
            }
//            if (!SwingUtilities.isEventDispatchThread())
//            {
//                try
//                {
//                    SwingUtilities.invokeAndWait(new Runnable()
//                    {
//                        @Override
//                        public void run()
//                        {
//                            doWrite(new String(cbuf, off, len));
//                        }
//                    });
//                }
//                catch (Throwable t)
//                {
//                    System.out.println("Error writing to log.  Possible abort "
//                                       + "issued.");
//                    t.printStackTrace();
//                }
//            }
//            else
//            {
//                doWrite(new String(cbuf, off, len));
//            }
        }

        protected void doWrite(final String s)
        {
            if (getLogTextArea() != null)
            {
                getLogTextArea().append(s);
            }
        }
    }
    
    protected class DirectoryChooser implements Runnable
    {
    
        @Override
        public void run()
        {
            try
            {
                uiInitCond.waitForTrue();
                
                SwingUtilities.invokeAndWait((new Runnable()
                {
                
                    public void run()
                    {
                        selectSourceDirectory(GraphicUI.this,
                                new SourceDirectoryChooserCallback()
                                {
                                    @Override
                                    public void onCallback(
                                            final File chosenDirectory)
                                    {
                                        getUploadManager().start(chosenDirectory);
                                    }
                                });
                    }}));
            }
            catch (final Exception e)
            {
                LOGGER.fatal("Error caught.", e);
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }
}