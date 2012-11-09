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


import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.client.VOSpaceClient;
import ca.onfire.ak.AbstractApplication;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.security.auth.Subject;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.security.AccessController;
import java.security.PrivilegedAction;


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

    private final VOSURI targetVOSpaceURI;
    private final VOSpaceClient voSpaceClient;


    public GraphicUI(final Level logLevel, final VOSURI targetVOSpaceURI,
                     final VOSpaceClient voSpaceClient)
    {
        super(new BorderLayout());
        LOGGER.setLevel(logLevel);
        setName(NAME);

        this.targetVOSpaceURI = targetVOSpaceURI;
        this.voSpaceClient = voSpaceClient;
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
//        new Thread(new UICreator()).start();
        new UICreator().run();
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
     * Start the source directory chooser.
     */
    protected void run()
    {
        // Fire off a thread to complete init once the app is displayed on
        // screen.
        new Thread(new DelayedInit()).start();
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
            if (!SwingUtilities.isEventDispatchThread())
            {
                try
                {
                    SwingUtilities.invokeAndWait(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            doWrite(new String(cbuf, off, len));
                        }
                    });
                }
                catch (Throwable t)
                {
                    System.out.println("Error writing to log.  Possible abort "
                                       + "issued.");
                    t.printStackTrace();
                }
            }
            else
            {
                doWrite(new String(cbuf, off, len));
            }
        }

        protected void doWrite(final String s)
        {
            if (getLogTextArea() != null)
            {
                getLogTextArea().append(s);
            }
        }
    }

    protected class DelayedInit implements Runnable
    {
        public DelayedInit()
        {

        }

        public void run()
        {
            try
            {
                getUploadManager().start();
            }
            catch (final Throwable t)
            {
                t.printStackTrace();

                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.error("DelayedInit failed", t);
                }
                else
                {
                    LOGGER.error("DelayedInit failed: " + t);
                }
            }
        }
    }

    protected class UICreator implements Runnable
    {
        private final Subject currentSubject =
                Subject.getSubject(AccessController.getContext());

        /**
         * When an object implementing interface <code>Runnable</code> is used
         * to create a thread, starting the thread causes the object's
         * <code>run</code> method to be called in that separately executing
         * thread.
         * <p/>
         * The general contract of the method <code>run</code> is that it may
         * take any action whatsoever.
         *
         * @see Thread#run()
         */
        @Override
        public void run()
        {
            try
            {
                SwingUtilities.invokeAndWait(new Runnable()
                {
                    public void run()
                    {
                        final Subject subjectInContext =
                                Subject.getSubject(
                                        AccessController.getContext());

                        if (subjectInContext == null)
                        {
                            Subject.doAs(currentSubject,
                                         new PrivilegedAction<Void>()
                            {
                                @Override
                                public Void run()
                                {
                                    runPrivileged();
                                    return null;
                                }
                            });
                        }
                        else
                        {
                            runPrivileged();
                        }
                    }

                    private void runPrivileged()
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

                        final JScrollPane sp =
                                createLogScrollPane(
                                        getLogWriter().getLogTextArea());
                        getTabPane().addTab("Log Messages", sp);

                        Util.recursiveSetBackground(GraphicUI.this,
                                Color.WHITE);
                        getTabPane().setVisible(true);

                        selectSourceDirectory(GraphicUI.this,
                                new SourceDirectoryChooserCallback()
                                {
                                    @Override
                                    public void onCallback(
                                            final File chosenDirectory)
                                    {
                                        setUploadManager(
                                                new JUploadManager(chosenDirectory,
                                                        getTargetVOSpaceURI(),
                                                        getVOSpaceClient()));

                                        getTabPane().addTab("Upload",
                                                            getUploadManager());
                                        getTabPane().setSelectedIndex(1);

                                        GraphicUI.this.run();
                                    }
                                });
                    }
                }) ;
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