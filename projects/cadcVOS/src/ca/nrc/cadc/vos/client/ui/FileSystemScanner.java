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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.log4j.Logger;

import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.VOSURI;

/**
 * Starting from the specified source directory or file, creates Node commands
 * to replicate the file structure in VOSpace.
 * 
 * @author jburke
 */
public class FileSystemScanner implements Runnable
{
    private static Logger log = Logger.getLogger(FileSystemScanner.class);

    // Queue to hold the Node commands
    private final CommandQueue commandQueue;

    // Root file of the file structure to be replicated.
    private final File sourceFile;

    // Base VOSURI of the target VOSpace.
    private final VOSURI targetURI;

    /**
     * Package access constructor for unit testing.
     */
    FileSystemScanner()
    {
        commandQueue = null;
        sourceFile = null;
        targetURI = null;
    }
    
    /**
     * Constructor.
     * 
     * @param sourceFile    root of the file structure to be uploaded.
     * @param targetURI     target node URI.
     * @param commandQueue  CommandQueue containing VOSpaceCommands.
     */
    public FileSystemScanner(File sourceFile, VOSURI targetURI,
                             CommandQueue commandQueue)
    {
        this.sourceFile = sourceFile;
        this.targetURI = targetURI;
        this.commandQueue = commandQueue;
    }


    /**
     * Runs the scanner.
     */
    public void run()
    {
        try
        {
            commandQueue.startProduction();

            // Queue to hold file paths.
            final Queue<String> filePathQueue = new LinkedList<String>();
            filePathQueue.add(sourceFile.getPath());

            while (!filePathQueue.isEmpty())
            {
                // Next file in the queue.
                final File file = new File(filePathQueue.remove());

                try
                {
                    // Check if file is a symlink.
                    if (isSymLink(file))
                    {
                        log.warn("Symbolic link found: "
                                 + file.getAbsolutePath());
                    }

                    // Create a DataNode command and add it to the CommandQueue.
                    else if (file.isFile())
                    {
                        queueDataNode(file);
                    }
                    
                    // Create a ContainerNode command and add it to the CommandQueue
                    // and add directory listing to the queue.
                    else
                    {
                        queueContainerNode(file);
                        for (final String filename : file.list())
                        {
                            filePathQueue.add(file.getPath() + File.separator
                                              + filename);
                        }
                    }
                    
                }
                catch (IOException ioe)
                {
                    log.error("Unable to read " + file.getPath() +
                              " because " + ioe.getMessage());
                }
                catch (URISyntaxException use)
                {
                    log.error("Invalid VOSpace URI for " + file.getPath() +
                              " because " + use.getMessage());
                }
                catch (RuntimeException rte)
                {
                    log.error("Unable to process " + file.getPath() +
                              " because " + rte.getMessage());
                }
            }
        }
        catch (InterruptedException ie)
        {
            log.debug("Processing stopped");
        }
        catch (Exception e)
        {
            log.error("Bug found!", e);
        }
        finally
        {
            commandQueue.doneProduction();
        }
    }

    /**
     * Attempt to determine if a file contains a symbolic link in the path
     * by comparing the given path with a path that has all references
     * resolved.
     *
     * @param file the file to check.
     * @return true if the file path contains a symlink, false otherwise.
     * @throws IOException
     */
    protected boolean isSymLink(File file) throws IOException
    {
        if (file == null)
        {
            throw new RuntimeException("null file");
        }
        return !file.getAbsolutePath().equals(file.getCanonicalPath());
    }
    
    /**
     * Create a ContainerNode from the given file and
     * add it to the CommandQueue.
     *
     * @param file the file to add to the CommandQueue
     * @throws URISyntaxException   For invalid URI characters.
     * @throws InterruptedException
     */
    protected void queueContainerNode(File file)
        throws URISyntaxException, InterruptedException
    {
        // Get the path starting from the root file.
        String path = getRelativePath(file);

        // Create a DataNode.
        final URI uri = new URI(targetURI.toString() + path);
        final ContainerNode node = new ContainerNode(new VOSURI(uri));

        // Add node and InputStream from file.
        final VOSpaceCommand command = new CreateDirectory(node);
        commandQueue.put(command);
    }

    /**
     * Create a DataNode from the given file and
     * add it to the CommandQueue.
     *
     * @param file the file to add to the CommandQueue
     * @throws URISyntaxException   For invalid URI characters.
     * @throws InterruptedException
     */
    protected void queueDataNode(File file)
        throws URISyntaxException, InterruptedException
    {
        // Get the path starting from the root file.
        String path = getRelativePath(file);

        // Create a DataNode.
        URI uri = new URI(targetURI.toString() + path);
        DataNode node = new DataNode(new VOSURI(uri));

        // Add node and InputStream from file.
        VOSpaceCommand command = new UploadFile(node, file);
        commandQueue.put(command);
    }

    /**
     * Get the relative path from the root file to the given file.
     *
     * @param file      The file to get the path for.
     * @return String of relative path to the given file.
     */
    protected String getRelativePath(File file)
    {
        int index = sourceFile.getAbsolutePath().lastIndexOf(File.separator);
        if (index == -1)
        {
            throw new RuntimeException("file " + file.getAbsolutePath() + 
                                       " not in source directory " +
                                       sourceFile.getAbsolutePath());
        }
        return file.getAbsolutePath().substring(index);
    }

}
