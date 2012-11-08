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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.URI;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.uws.server.RandomStringGenerator;
import ca.nrc.cadc.vos.AbstractCADCVOSTest;
import ca.nrc.cadc.vos.VOSURI;

/**
 *
 * @author jburke
 */
public class FileSystemScannerTest
        extends AbstractCADCVOSTest<FileSystemScanner>
{
    private static Logger log = Logger.getLogger(FileSystemScannerTest.class);

    private static VOSURI TEST_VOSURI;

    private static final char[] SEED_CHARS;

    static
    {
        final StringBuilder chars = new StringBuilder(128);

        for (char c = 'a'; c <= 'z'; c++)
        {
            chars.append(c);
        }

        for (char c = 'A'; c <= 'Z'; c++)
        {
            chars.append(c);
        }

        for (char c = '0'; c <= '9'; c++)
        {
            chars.append(c);
        }

        chars.append("_-()=+!,;:@&*$.");

        SEED_CHARS = chars.toString().toCharArray();
    }

    public FileSystemScannerTest()
    {
    }


    /**
     * Generate an ASCII string, replacing the '\' and '+' characters with
     * underscores to keep them URL friendly.
     *
     * @param length        The desired length of the generated string.
     * @return              An ASCII string of the given length.
     */
    protected String generateAlphaNumeric(final int length)
    {
        return new RandomStringGenerator(length,
                                         String.copyValueOf(
                                                 SEED_CHARS)).getID();
    }


    @BeforeClass
    public static void setUpClass() throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc.vos.client.ui", Level.INFO);
        TEST_VOSURI = new VOSURI(new URI("vos://cadc.nrc.ca!vospace/root"));
    }


    /**
     * Set and initialize the Test Subject.
     *
     * @throws Exception If anything goes awry.
     */
    @Override
    protected void initializeTestSubject() throws Exception
    {
        // Just a basic one.  Each test should set their own.
        setTestSubject(new FileSystemScanner());
    }

    @Test
    public void testIsSymLink() throws Exception
    {
        try
        {
            final FileSystemScanner scanner = getTestSubject();

            // a directory
            File file = new File("test/src/resources/testDir");
            assertFalse("Directory is not a symlink", scanner.isSymLink(file));

            // a file
            file = new File("test/src/resources/testFile");
            assertFalse("File is not a symlink", scanner.isSymLink(file));

            // test/src/resources/testDirSymlink is a symlink to test/src/resoruces/testDir
            file = new File("test/src/resources/testDirSymlink");
            assertTrue("Should return true for a symlink", scanner.isSymLink(file));

            // parent directory as a symlink
            file = new File("test/src/resources/testDirSymlink/testDirFile");
            assertTrue("Should return true for a symlink", scanner.isSymLink(file));

            // test/src/resource/testFileSymlink is a symlink to test/src/resources/testFile
            file = new File("test/src/resources/testFileSymlink");
            assertTrue("Should return true for a symlink", scanner.isSymLink(file));
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }
    
    @Test
    public void testQueueContainerNode()
    {
        try
        {
            CommandQueue queue = new CommandQueue(1, null);
            File sourceFile = new File("test");
            FileSystemScanner scanner = new FileSystemScanner(sourceFile, TEST_VOSURI, queue);

            File file = new File("test/src/resources/testFile");
            scanner.queueContainerNode(file);

            assertNotNull(queue.take());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testQueueDataNode()
    {
        try
        {
            CommandQueue queue = new CommandQueue(1, null);
            File sourceFile = new File("test");
            FileSystemScanner scanner = new FileSystemScanner(sourceFile,
                                                              TEST_VOSURI,
                                                              queue);

            File file = new File("test/src/resources/testFile");
            scanner.queueDataNode(file);

            assertNotNull(queue.take());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testGetRelativePath()
    {
        try
        {
            File sourceFile = new File("/a/b/c");
            FileSystemScanner scanner = new FileSystemScanner(sourceFile, null,
                                                              null);
            
            File file = new File("/a/b/c/d/e/f");
            String path = scanner.getRelativePath(file);
            assertEquals("/c/d/e/f", path);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void buildQueue() throws Exception
    {
        final String sourceDirectoryName = generateAlphaNumeric(16);
        final File sourceDirectory = File.createTempFile(sourceDirectoryName,
                                                         "");
        assertTrue("Couldn't make temp directory", sourceDirectory.delete()
                                                   && sourceDirectory.mkdir());

        final File sourceDirectoryFile =
                File.createTempFile("tempfile", generateAlphaNumeric(8),
                                    sourceDirectory);

        assertTrue("Couldn't make temp file.", sourceDirectoryFile.exists());

        final CommandQueue commandQueue = new CommandQueue(5, null);

        setTestSubject(new FileSystemScanner(sourceDirectory, TEST_VOSURI,
                                             commandQueue));

        getTestSubject().run();

        assertTrue("Should be done production.",
                   commandQueue.isDoneProduction());
        assertEquals("Queue size should be 1 for the file and 1 dir.", 2,
                     commandQueue.size());

        final CreateDirectory createDirectory =
                (CreateDirectory) commandQueue.take();
        assertEquals("Temp dir is not in command queue.",
                     "Create directory vos://cadc.nrc.ca!vospace/root/"
                     + sourceDirectory.getName(), createDirectory.toString());

        final UploadFile uploadFile = (UploadFile) commandQueue.take();
        assertEquals("Temp file is not in command queue.",
                     "Upload file vos://cadc.nrc.ca!vospace/root/"
                     + sourceDirectory.getName() + File.separator
                     + sourceDirectoryFile.getName(), uploadFile.toString());
    }
}
