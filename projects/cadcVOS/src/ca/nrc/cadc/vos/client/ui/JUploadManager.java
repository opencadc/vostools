/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2012.                         (c) 2012.
 * National Research Council            Conseil national de recherches
 * Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 * All rights reserved                  Tous droits reserves
 *
 * NRC disclaims any warranties         Le CNRC denie toute garantie
 * expressed, implied, or statu-        enoncee, implicite ou legale,
 * tory, of any kind with respect       de quelque nature que se soit,
 * to the software, including           concernant le logiciel, y com-
 * without limitation any war-          pris sans restriction toute
 * ranty of merchantability or          garantie de valeur marchande
 * fitness for a particular pur-        ou de pertinence pour un usage
 * pose.  NRC shall not be liable       particulier.  Le CNRC ne
 * in any event for any damages,        pourra en aucun cas etre tenu
 * whether direct or indirect,          responsable de tout dommage,
 * special or general, consequen-       direct ou indirect, particul-
 * tial or incidental, arising          ier ou general, accessoire ou
 * from the use of the software.        fortuit, resultant de l'utili-
 *                                      sation du logiciel.
 *
 *
 * @author jenkinsd
 * 10/17/12 - 12:25 PM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.vos.client.ui;

import ca.nrc.cadc.util.StringUtil;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.client.VOSpaceClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.Authenticator;
import java.text.MessageFormat;

import org.apache.log4j.Logger;


public class JUploadManager extends JPanel implements CommandQueueListener,
                                                      ActionListener
{
    private static final Logger LOGGER = Logger.getLogger(JUploadManager.class);

    private static final String COMPLETED_SCANNING = " Completed scanning:";
    private static final String COMPLETED_UPLOADING = " Completed uploading:";

    private final UploadManager uploadManager;

    private final JProgressBar uploadProgressBar;
    private final JLabel uploadProgressLabel;
    private final JLabel uploadProgressPercentageLabel;
    private final JProgressBar scannerProgressBar;
    private final JLabel scannerProgressLabel;
    private final JLabel scannerProgressPercentageLabel;
    private final JButton abortButton;
    private final JLabel messageLabel;


    /**
     * For testing.
     */
    JUploadManager()
    {
        uploadProgressBar = null;
        uploadProgressLabel = null;
        uploadProgressPercentageLabel = null;
        scannerProgressBar = null;
        scannerProgressLabel = null;
        scannerProgressPercentageLabel = null;
        abortButton = null;
        messageLabel = null;
        uploadManager = null;
    }

    /**
     * Creates a new <code>JPanel</code> with a double buffer
     * and a flow layout.
     */
    public JUploadManager(final File sourceDirectory,
                          final VOSURI targetVOSpaceURI,
                          final VOSpaceClient vospaceClient)
    {
        this.uploadManager = new UploadManagerImpl(sourceDirectory,
                                                   targetVOSpaceURI,
                                                   vospaceClient, this);

        registerCommandQueueListener(this);

        // configure custom authentication
        Authenticator.setDefault(new HttpAuthenticator(this));

        final Box statusBox = new Box(BoxLayout.Y_AXIS);
        final Box uploadProgressHolder = new Box(BoxLayout.X_AXIS);
        final Box scannerProgressHolder = new Box(BoxLayout.X_AXIS);

        this.uploadProgressBar = new JProgressBar();
        this.uploadProgressLabel = new JLabel(" Waiting for scan...");
        this.uploadProgressPercentageLabel = new JLabel("0%");

        this.scannerProgressBar = new JProgressBar();
        this.scannerProgressLabel = new JLabel(COMPLETED_SCANNING);
        this.scannerProgressPercentageLabel = new JLabel("0%");

        abortButton = new JButton("Abort");
        abortButton.setActionCommand("Abort");
        abortButton.addActionListener(this);

        messageLabel = new JLabel();
        getMessageLabel().setForeground(Color.RED);

        getUploadProgressBar().setMinimum(0);
        getUploadProgressBar().setIndeterminate(true);
        getScannerProgressBar().setMinimum(0);

        // add an empty border to the exterior
        getUploadProgressBar().setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(6, 40, 6, 40),
                getUploadProgressBar().getBorder()));
        getScannerProgressBar().setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(6, 40, 6, 40),
                getScannerProgressBar().getBorder()));

        getUploadProgressPercentageLabel().setHorizontalAlignment(
                SwingConstants.LEFT);
        getScannerProgressPercentageLabel().setHorizontalAlignment(
                SwingConstants.LEFT);

        uploadProgressHolder.add(getUploadProgressLabel());
        uploadProgressHolder.add(getUploadProgressBar());
        uploadProgressHolder.add(getUploadProgressPercentageLabel());

        scannerProgressHolder.add(getScannerProgressLabel());
        scannerProgressHolder.add(getScannerProgressBar());
        scannerProgressHolder.add(getScannerProgressPercentageLabel());

        statusBox.add(scannerProgressHolder);
        statusBox.add(uploadProgressHolder);
        statusBox.add(new Box.Filler(new Dimension(10, 10),
                                     new Dimension(10, 10),
                                     new Dimension(10, 10)));
        statusBox.add(getAbortButton());
        statusBox.add(new Box.Filler(new Dimension(10, 10),
                                     new Dimension(10, 10),
                                     new Dimension(10, 10)));
        statusBox.add(getMessageLabel());

        add(statusBox);
    }


    /**
     * Invoked when an action occurs.
     */
    @Override
    public void actionPerformed(final ActionEvent e)
    {
        if (StringUtil.hasText(e.getActionCommand())
            && e.getActionCommand().equals("Abort"))
        {
            executeInEDT(new AbortAction());
        }
    }

    /**
     * Indicates that a command has been processed
     *
     * @param commandsProcessed Total number that have been processed.
     * @param commandsRemaining Total known number remaining to be processed.
     */
    @Override
    public void commandProcessed(final Long commandsProcessed,
                                 final Long commandsRemaining)
    {
        executeInEDT(new CommandProcessedAction(commandsProcessed,
                                                commandsRemaining));
    }

    /**
     * Indicates that an Abort was issued.
     */
    @Override
    public void onAbort()
    {
        executeInEDT(new AbortListenerAction());
    }

    /**
     * Indicates that processing has started.
     */
    @Override
    public void processingStarted()
    {
        executeInEDT(new ProcessingStartedAction());
    }

    /**
     * Indicates that processing is complete.
     */
    @Override
    public void processingComplete()
    {
        executeInEDT(new ProcessingCompletedAction());
    }

    /**
     * Ensure the given action is executed in the Event Dispatch Thread.
     *
     * @param action        The Action to run.
     */
    protected void executeInEDT(final Runnable action)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            try
            {
                SwingUtilities.invokeAndWait(action);
            }
            catch (Throwable t)
            {
                t.printStackTrace();
            }
        }
        else
        {
            action.run();
        }
    }

    public void start()
    {
        executeInEDT(new StartUploadManagerAction());
    }

    /**
     * Terminate all downloads and release resources.
     */
    public void stop()
    {
        executeInEDT(new StopUploadManagerAction());
    }

    public UploadManager getUploadManager()
    {
        return uploadManager;
    }

    public JProgressBar getUploadProgressBar()
    {
        return uploadProgressBar;
    }

    public JLabel getUploadProgressLabel()
    {
        return uploadProgressLabel;
    }

    public JLabel getUploadProgressPercentageLabel()
    {
        return uploadProgressPercentageLabel;
    }

    public JProgressBar getScannerProgressBar()
    {
        return scannerProgressBar;
    }

    public JLabel getScannerProgressLabel()
    {
        return scannerProgressLabel;
    }

    public JLabel getScannerProgressPercentageLabel()
    {
        return scannerProgressPercentageLabel;
    }

    protected JButton getAbortButton()
    {
        return abortButton;
    }

    protected JLabel getMessageLabel()
    {
        return messageLabel;
    }

    public void registerCommandQueueListener(
            final CommandQueueListener commandQueueListener)
    {
        getUploadManager().registerCommandQueueListener(commandQueueListener);
    }


    private class StartUploadManagerAction implements Runnable
    {
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
            getUploadManager().start();
        }
    }

    private class StopUploadManagerAction implements Runnable
    {
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
            getUploadManager().stop();
        }
    }

    private class AbortAction implements Runnable
    {
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
            getAbortButton().setEnabled(false);
            getUploadManager().abort();
        }
    }

    private class AbortListenerAction implements Runnable
    {
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
            getUploadProgressLabel().setText(getUploadProgressLabel().getText()
                                       + " (Aborted)");
            getScannerProgressLabel().setText(
                    getScannerProgressLabel().getText() + " (Aborted)");
        }
    }

    private class ProcessingStartedAction implements Runnable
    {
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
            LOGGER.info("Processing started.");
            getUploadProgressBar().setIndeterminate(false);
            getAbortButton().setEnabled(true);
        }
    }

    private class CommandProcessedAction implements Runnable
    {
        Long commandsProcessed;
        Long commandsRemaining;

        private CommandProcessedAction(final Long commandsProcessed,
                                       final Long commandsRemaining)
        {
            this.commandsProcessed = commandsProcessed;
            this.commandsRemaining = commandsRemaining;
        }

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
            LOGGER.info("Command processed.");

            getUploadProgressBar().setIndeterminate(false);

            if (!getUploadManager().isAbortIssued())
            {
                getUploadProgressBar().setMaximum(commandsProcessed.intValue()
                                            + commandsRemaining.intValue());
                getUploadProgressBar().setValue(commandsProcessed.intValue());

                if (!getUploadProgressLabel().getText().equals(
                        COMPLETED_UPLOADING))
                {
                    getUploadProgressLabel().setText(COMPLETED_UPLOADING);
                }

                getUploadProgressPercentageLabel().setText(
                        MessageFormat.format("{0,number,#.00%}",
                                             getUploadProgressBar().
                                                     getPercentComplete()));
            }
        }
    }

    private class ProcessingCompletedAction implements Runnable
    {
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
            if (getAbortButton().isEnabled())
            {
                LOGGER.info("Processing completed.");
                getAbortButton().setEnabled(false);
                getMessageLabel().setText(
                        "Please use the refresh button in the "
                        + "VOSpace Browser to see the new Directory.");
            }
        }
    }
}
