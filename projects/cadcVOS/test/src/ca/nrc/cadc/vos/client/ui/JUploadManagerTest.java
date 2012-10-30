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
 * 10/17/12 - 12:24 PM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.vos.client.ui;

import ca.nrc.cadc.vos.AbstractCADCVOSTest;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.client.VOSpaceClient;

import java.io.File;
import java.io.IOException;
import java.net.URI;


import org.junit.Test;

import javax.swing.*;

import static org.easymock.EasyMock.*;


public class JUploadManagerTest extends AbstractCADCVOSTest<JUploadManager>
{
    private final File sourceDirectory;
    private final VOSURI targetVOSpaceURI;
    private final VOSpaceClient mockVOSpaceClient =
            createMock(VOSpaceClient.class);


    public JUploadManagerTest() throws Exception
    {
        sourceDirectory = createTempDirectory();
        this.targetVOSpaceURI = new VOSURI(
                URI.create("vos://cadc.nrc.ca!vospace/CADCtest"));
    }


    /**
     * Set and initialize the Test Subject.
     *
     * @throws Exception If anything goes awry.
     */
    @Override
    protected void initializeTestSubject() throws Exception
    {
        setTestSubject(new JUploadManager(getSourceDirectory(),
                                          getTargetVOSpaceURI(),
                                          getMockVOSpaceClient()));
    }

    @Test
    public void processingComplete() throws Exception
    {
        final JLabel mockProgressLabel = createMock(JLabel.class);
        final JButton mockAbortButton = createMock(JButton.class);

        setTestSubject(new JUploadManager()
        {
            @Override
            public JLabel getProgressLabel()
            {
                return mockProgressLabel;
            }

            @Override
            protected JButton getAbortButton()
            {
                return mockAbortButton;
            }
        });

        mockAbortButton.setEnabled(false);
        expectLastCall().once();

        expect(mockProgressLabel.getText()).andReturn("Completed 100%").once();
        mockProgressLabel.setText("Completed 100%  -  "
                                  + "Please use the refresh button in the "
                                  + "VOSpace Browser to see the new "
                                  + "Directory.");

        replay(mockAbortButton, mockProgressLabel);

        getTestSubject().processingComplete();

        verify(mockAbortButton, mockProgressLabel);
    }

    @Test
    public void commandProcessed() throws Exception
    {
        final JProgressBar mockProgressBar = createMock(JProgressBar.class);
        final JLabel mockProgressLabel = createMock(JLabel.class);
        final UploadManager mockUploadManager = createMock(UploadManager.class);

        setTestSubject(new JUploadManager()
        {
            @Override
            public JProgressBar getProgressBar()
            {
                return mockProgressBar;
            }

            @Override
            public JLabel getProgressLabel()
            {
                return mockProgressLabel;
            }

            @Override
            public UploadManager getUploadManager()
            {
                return mockUploadManager;
            }
        });

        mockProgressBar.setMaximum(1977);
        expectLastCall().once();

        mockProgressBar.setValue(325);
        expectLastCall().once();

        expect(mockProgressBar.getPercentComplete()).andReturn(
                0.196731235d).once();

        expect(mockUploadManager.isAbortIssued()).andReturn(false).once();

        mockProgressLabel.setText(" Completed: 19.67%");
        expectLastCall().once();

        replay(mockProgressBar, mockProgressLabel, mockUploadManager);

        // 1977 jobs
        // 19.67% complete
        getTestSubject().commandProcessed(325l, 1652l);

        verify(mockProgressBar, mockProgressLabel, mockUploadManager);
    }

    /**
     * Create a temporary directory.
     *
     * @return      The temporary directory.
     * @throws java.io.IOException
     */
    protected File createTempDirectory() throws IOException
    {
        final File temp =
                File.createTempFile(UploadManagerImplTest.class.getCanonicalName(),
                                    Long.toString(System.nanoTime()));

        if (!temp.delete())
        {
            throw new IOException("Could not delete temp file: "
                                  + temp.getAbsolutePath());
        }
        else if (!temp.mkdir())
        {
            throw new IOException("Could not create temp directory: "
                                  + temp.getAbsolutePath());
        }

        return temp;
    }

    public File getSourceDirectory()
    {
        return sourceDirectory;
    }

    public VOSURI getTargetVOSpaceURI()
    {
        return targetVOSpaceURI;
    }

    public VOSpaceClient getMockVOSpaceClient()
    {
        return mockVOSpaceClient;
    }
}
