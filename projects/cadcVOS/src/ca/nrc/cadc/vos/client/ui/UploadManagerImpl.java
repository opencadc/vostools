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
 * 10/17/12 - 1:13 PM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.vos.client.ui;

import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.client.VOSpaceClient;
import org.apache.log4j.Logger;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * The main manager to manage the logic of the uploads.  Reports to UI elements
 * via the listeners.
 */
public class UploadManagerImpl implements UploadManager, ChangeListener,
                                          CommandQueueListener
{
    private static final Logger LOGGER =
            Logger.getLogger(UploadManagerImpl.class);
    private static final int MAX_COMMAND_COUNT = 200;

    private final File sourceDirectory;
    private final VOSURI targetVOSpaceURI;
    private final VOSpaceClient voSpaceClient;

    private boolean abortIssued;

    private ExecutorService commandController;
    private CommandQueue commandQueue;

    private final List<ChangeListener> changeListeners =
            new ArrayList<ChangeListener>();
    private final List<CommandQueueListener> commandQueueListeners =
            new ArrayList<CommandQueueListener>();


    /**
     * Only available constructor.  Complete.
     *
     * @param sourceDirectory       The Source directory to upload.
     * @param targetVOSpaceURI      The URI of the target VOSpace.
     * @param vospaceClient         The VOSpace client instance to use.
     */
    public UploadManagerImpl(final File sourceDirectory,
                             final VOSURI targetVOSpaceURI,
                             final VOSpaceClient vospaceClient)
    {
        this.sourceDirectory = sourceDirectory;
        this.targetVOSpaceURI = targetVOSpaceURI;
        this.voSpaceClient = vospaceClient;
    }


    /**
     * Begin the UploadManager's Producer and Consumer threads.
     */
    @Override
    public void start()
    {
        LOGGER.info("Starting process.");
        initializeCommandQueue();
        initializeCommandController();
    }

    /**
     * Create a command queue and set it.
     */
    protected void initializeCommandQueue()
    {
        setCommandQueue(new CommandQueue(MAX_COMMAND_COUNT, this));
    }

    /**
     * Create the command controller and set it.
     */
    protected void initializeCommandController()
    {
        setCommandController(Executors.newFixedThreadPool(2));

        getCommandController().execute(
                new CommandExecutor(getVOSpaceClient(), getCommandQueue()));
        getCommandController().submit(
                new FileSystemScanner(getSourceDirectory(),
                                      getTargetVOSpaceURI(),
                                      getCommandQueue()));

        // Ensure the producer/consumer thread counts do not grow.  Shutdown
        // after the queue is done.
        getCommandController().shutdown();
    }

    /**
     * Abort the process(es) while they're working.
     */
    @Override
    public void abort()
    {
        LOGGER.info("Abort issued.");
        abortIssued = true;

        getCommandQueue().abortProduction();
        getCommandController().shutdownNow();
    }

    /**
     * Shutdown the Manager.  This is a hard stop issued after completion.
     */
    @Override
    public void stop()
    {
        LOGGER.info("Full stop.");
        abortIssued = false;

        getCommandController().shutdownNow();
    }

    /**
     * Obtain whether an Abort was issued.
     *
     * @return True if aborted, False otherwise.
     */
    @Override
    public boolean isAbortIssued()
    {
        return abortIssued;
    }

    /**
     * Invoked when the target of the listener has changed its state.
     *
     * @param e a ChangeEvent object
     */
    @Override
    public void stateChanged(final ChangeEvent e)
    {
        for (final ChangeListener changeListener : getChangeListeners())
        {
            changeListener.stateChanged(e);
        }
    }

    /**
     * Indicates that an Abort was issued.
     */
    @Override
    public void onAbort()
    {
        for (final CommandQueueListener commandQueueListener
                : getCommandQueueListeners())
        {
            commandQueueListener.onAbort();
        }
    }

    /**
     * Indicates that a command has been processed.
     *
     * @param commandsProcessed Total number that have been processed.
     * @param commandsRemaining Total known number remaining to be processed.
     */
    @Override
    public void commandProcessed(final Long commandsProcessed,
                                 final Long commandsRemaining)
    {
//        try
//        {
//            SwingUtilities.invokeAndWait(new Runnable()
//            {
//                @Override
//                public void run()
//                {
//                    for (final CommandQueueListener commandQueueListener
//                            : getCommandQueueListeners())
//                    {
//                        commandQueueListener.commandProcessed(commandsProcessed,
//                                                              commandsRemaining);
//                    }
//                }
//            });
//        }
//        catch (final Throwable t)
//        {
//            System.out.println("Bad bug found.");
//            t.printStackTrace();
//        }
        for (final CommandQueueListener commandQueueListener
                : getCommandQueueListeners())
        {
            commandQueueListener.commandProcessed(commandsProcessed,
                                                  commandsRemaining);
        }
    }

    /**
     * Indicates that processing has started.
     */
    @Override
    public void processingStarted()
    {
        LOGGER.info("Started processing.");
//        try
//        {
//            SwingUtilities.invokeAndWait(new Runnable()
//            {
//                @Override
//                public void run()
//                {
//                    for (final CommandQueueListener commandQueueListener
//                            : getCommandQueueListeners())
//                    {
//                        commandQueueListener.processingStarted();
//                    }
//                }
//            });
//        }
//        catch (final Throwable t)
//        {
//            System.out.println("Bad bug found.");
//            t.printStackTrace();
//        }
        for (final CommandQueueListener commandQueueListener
                : getCommandQueueListeners())
        {
            commandQueueListener.processingStarted();
        }
    }

    /**
     * Indicates that processing is complete.
     */
    @Override
    public void processingComplete()
    {
        LOGGER.info("Completed processing.");
//        try
//        {
//            SwingUtilities.invokeAndWait(new Runnable()
//            {
//                @Override
//                public void run()
//                {
//                    for (final CommandQueueListener commandQueueListener
//                            : getCommandQueueListeners())
//                    {
//                        commandQueueListener.processingComplete();
//                    }
//                }
//            });
//        }
//        catch (final Throwable t)
//        {
//            System.out.println("Bad bug found.");
//            t.printStackTrace();
//        }
        for (final CommandQueueListener commandQueueListener
                : getCommandQueueListeners())
        {
            commandQueueListener.processingComplete();
        }
    }

    public File getSourceDirectory()
    {
        return sourceDirectory;
    }

    public VOSURI getTargetVOSpaceURI()
    {
        return targetVOSpaceURI;
    }

    public VOSpaceClient getVOSpaceClient()
    {
        return voSpaceClient;
    }

    public ExecutorService getCommandController()
    {
        return commandController;
    }

    public void setCommandController(ExecutorService commandController)
    {
        this.commandController = commandController;
    }

    public CommandQueue getCommandQueue()
    {
        return commandQueue;
    }

    public void setCommandQueue(CommandQueue commandQueue)
    {
        this.commandQueue = commandQueue;
    }

    public final List<ChangeListener> getChangeListeners()
    {
        return changeListeners;
    }

    public void registerCommandQueueListener(
            final CommandQueueListener commandQueueListener)
    {
        getCommandQueueListeners().add(commandQueueListener);
    }

    public List<CommandQueueListener> getCommandQueueListeners()
    {
        return commandQueueListeners;
    }

}
