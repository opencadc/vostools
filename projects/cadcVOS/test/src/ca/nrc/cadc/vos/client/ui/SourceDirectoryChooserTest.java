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
 * 10/19/12 - 1:03 PM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.vos.client.ui;

import ca.nrc.cadc.vos.AbstractCADCVOSTest;

import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.io.File;

import static org.junit.Assert.*;
import static org.easymock.EasyMock.*;


public class SourceDirectoryChooserTest
        extends AbstractCADCVOSTest<SourceDirectoryChooser>
{
    /**
     * Set and initialize the Test Subject.
     *
     * @throws Exception If anything goes awry.
     */
    @Override
    protected void initializeTestSubject() throws Exception
    {
        setTestSubject(new SourceDirectoryChooser(null,
                                                  "sourceDirectoryChooser"));
    }


    @Test
    public void showDialog() throws Exception
    {
        final Component mockParent = createMock(Component.class);
        final FileChooser mockFileChooser = createMock(FileChooser.class);
        final File mockSelectedFile = createMock(File.class);

        setTestSubject(new SourceDirectoryChooser(null,
                                                  "sourceDirectoryChooser")
        {
            /**
             * Display the file chooser and return the code.
             *
             * @param fileChooser The FileChooser to display.
             * @param parent      The parent component.
             * @param acceptText  The text for acceptance.
             * @return int return code.
             */
            @Override
            protected int showDialog(final FileChooser fileChooser,
                                     Component parent, String acceptText)
            {
                return JFileChooser.APPROVE_OPTION;
            }

            /**
             * Obtain an appropriate instance of a FileChooser.
             *
             * @param parent     The Parent component (Container).
             * @param acceptText The accept text.
             * @return FileChooser instance.
             */
            @Override
            protected FileChooser getFileChooser(Component parent,
                                                 String acceptText)
            {
                return mockFileChooser;
            }
        });

        expect(mockFileChooser.getSelectedFile()).andReturn(
                mockSelectedFile).once();

        replay(mockParent, mockFileChooser, mockSelectedFile);

        final int returnCode = getTestSubject().showDialog(mockParent, "ACCEPT");

        assertEquals("Should be zero.", JFileChooser.APPROVE_OPTION,
                     returnCode);

        verify(mockParent, mockFileChooser, mockSelectedFile);
    }
}
