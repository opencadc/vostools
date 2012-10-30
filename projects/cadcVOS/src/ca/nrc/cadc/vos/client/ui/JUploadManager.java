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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.text.MessageFormat;


public class JUploadManager extends JPanel implements CommandQueueListener,
                                                      ActionListener
{
    private static final String COMPLETED = " Completed: ";
    private final UploadManager uploadManager;

    private final JProgressBar progressBar;
    private final JLabel progressLabel;
    private final JButton abortButton;


    /**
     * For testing.
     */
    JUploadManager()
    {
        progressBar = null;
        progressLabel = null;
        abortButton = null;
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
                                                   vospaceClient);

        registerCommandQueueListener(this);

        final Box statusBox = new Box(BoxLayout.Y_AXIS);
        final Box progressHolder = new Box(BoxLayout.X_AXIS);
        this.progressBar = new JProgressBar();
        this.progressLabel = new JLabel(COMPLETED + "0%");

        abortButton = new JButton("Abort");
        abortButton.setActionCommand("Abort");
        abortButton.addActionListener(this);

        getProgressBar().setMinimum(0);

        // add an empty border to the exterior
        getProgressBar().setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(6, 40, 6, 40),
                getProgressBar().getBorder()));

        progressHolder.add(getProgressBar());
        progressHolder.add(getAbortButton());

        statusBox.add(progressHolder);
        statusBox.add(getProgressLabel());

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
            getAbortButton().setEnabled(false);
            getUploadManager().abort();
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
        if (!getUploadManager().isAbortIssued())
        {
            getProgressBar().setMaximum(commandsProcessed.intValue()
                                        + commandsRemaining.intValue());
            getProgressBar().setValue(commandsProcessed.intValue());

            getProgressLabel().setText(
                     MessageFormat.format(COMPLETED + "{0,number,#.00%}",
                                          getProgressBar().getPercentComplete()));
        }
    }

    /**
     * Indicates that an Abort was issued.
     */
    @Override
    public void onAbort()
    {
        getProgressLabel().setText(getProgressLabel().getText() + " (Aborted)");
    }

    /**
     * Indicates that processing has started.
     */
    @Override
    public void processingStarted()
    {
        getAbortButton().setEnabled(true);
    }

    /**
     * Indicates that processing is complete.
     */
    @Override
    public void processingComplete()
    {
        getAbortButton().setEnabled(false);
        getProgressLabel().setText(getProgressLabel().getText()
                                   + "  -  Please use the refresh button in the "
                                   + "VOSpace Browser to see the new "
                                   + "Directory.");
    }

    public void start()
    {
        getUploadManager().start();
    }

    /**
     * Terminate all downloads and release resources.
     */
    public void stop()
    {
        getUploadManager().stop();
    }

    public UploadManager getUploadManager()
    {
        return uploadManager;
    }

    public JProgressBar getProgressBar()
    {
        return progressBar;
    }

    public JLabel getProgressLabel()
    {
        return progressLabel;
    }

    protected JButton getAbortButton()
    {
        return abortButton;
    }

    public void registerCommandQueueListener(
            final CommandQueueListener commandQueueListener)
    {
        getUploadManager().registerCommandQueueListener(commandQueueListener);
    }
}
