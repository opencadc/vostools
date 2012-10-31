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
 * 10/31/12 - 10:12 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.vos.client.ui.integration;

import ca.nrc.cadc.auth.SSOCookieCredential;
import ca.nrc.cadc.vos.client.ui.Main;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;

import org.uispec4j.*;
import org.uispec4j.interception.FileChooserHandler;
import org.uispec4j.interception.MainClassAdapter;
import org.uispec4j.interception.WindowInterceptor;

import javax.swing.*;


public class UploadApplicationTest extends AbstractGUIIntegrationTest
{
    private final String vospaceWebServiceURL =
            System.getProperty("vospaceWebServiceURL");
    private final String vospaceRootName =
            System.getProperty("vospaceRootName");
    private final String testWorkingDirectoryName = generateTestDirectoryName();
    private static final File WORKING_DIR =
            new File(System.getProperty("user.dir"));

    private final String username = System.getProperty("username");
    private final char[] password =
            System.getProperty("password").toCharArray();
    private SSOCookieCredential ssoCookieCredential;


    @Override
    protected void setUp() throws Exception
    {
        ssoCookieCredential =
                login(new URL(getVOspaceWebServiceURL()).getHost(),
                      getUsername(), getPassword());

        System.setProperty("ca.nrc.cadc.auth.BasicX509TrustManager.trust",
                           "true");

        // Create the main directory to upload.
        final File topLevelTestDirectory = new File(WORKING_DIR,
                                                    "TEST_UPLOAD");
        if (topLevelTestDirectory.exists() && !delete(topLevelTestDirectory))
        {
            throw new IOException("Unable to delete old directory.");
        }
        else if (topLevelTestDirectory.mkdir())
        {
            // Create a test directory structure.
            for (int i = 1; i <= 10; i++)
            {
                final File testDirectory = new File(topLevelTestDirectory,
                                                    i + "_TESTDIR");
                if (testDirectory.mkdir())
                {
                    for (int j = 1; j <= 5; j++)
                    {
                        final File testFile = new File(testDirectory,
                                                       j + ".txt");
                        final FileWriter fileWriter = new FileWriter(testFile);

                        fileWriter.write("Test file for upload >> " + i + "."
                                         + j);
                        fileWriter.flush();
                        fileWriter.close();
                    }
                }

            }
        }
    }

    /**
     * Recursively delete the given File System Item.
     *
     * @param f     The File System Item to delete.
     * @return      True if successful, False otherwise.
     */
    protected static boolean delete(final File f)
    {
        if (f.isDirectory())
        {
            for (final File c : f.listFiles())
            {
                delete(c);
            }
        }

        return f.delete();
    }

    /**
     * JUnit 3
     * @throws Exception
     */
    public void testLayout() throws Exception
    {
        setAdapter(new MainClassAdapter(Main.class,
                                        "--dest=vos://cadc.nrc.ca!vospace/"
                                        + getVOspaceRootName() + "/"
                                        + getTestWorkingDirectoryName(),
                                        "--vospaceWebServiceURL="
                                        + getVOspaceWebServiceURL(),
                                        "--ssocookiedomain="
                                        + getSSOCookieCredential().getDomain(),
                                        "--ssocookie="
                                        + getSSOCookieCredential().getSsoCookieValue()));

        final Window mainWindow = getMainWindow();

        WindowInterceptor
                .init(new Trigger()
                {
                    @Override
                    public void run() throws Exception
                    {
                        // Should start on load.
                    }
                })
                .process(FileChooserHandler.init()
                                 .titleEquals("sourceDirectoryChooser")
                                 .assertAcceptsDirectoriesOnly()
                                 .select(WORKING_DIR.getAbsolutePath()
                                         + "/TEST_UPLOAD"))
                .run();

        final Panel rootPanel = mainWindow.getPanel("MainUI");
        final TabGroup tabGroup = rootPanel.getTabGroup();

        assertEquals("Wrong tab group.", "tabPane", tabGroup.getName());

        final JTabbedPane tabPane = tabGroup.getAwtComponent();
        assertEquals("Wrong selected tab.", 1, tabPane.getSelectedIndex());
        assertEquals("Wrong selected tab label", "Upload",
                     tabPane.getTitleAt(tabPane.getSelectedIndex()));
    }

    protected String getVOspaceWebServiceURL()
    {
        return vospaceWebServiceURL;
    }

    protected String getVOspaceRootName()
    {
        return vospaceRootName;
    }

    protected String getTestWorkingDirectoryName()
    {
        return testWorkingDirectoryName;
    }

    public SSOCookieCredential getSSOCookieCredential()
    {
        return ssoCookieCredential;
    }

    protected String getUsername()
    {
        return username;
    }

    protected char[] getPassword()
    {
        return password;
    }
}
