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

import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.File;


class SourceDirectoryChooser
{
    private static final Logger LOGGER =
            Logger.getLogger(SourceDirectoryChooser.class);

    private File initialDir;
    private boolean useNativeDialog;
    private File selectedFile;
    private final String fileChooserName;


    public SourceDirectoryChooser(final File initialDir,
                                  final String fileChooserName)
    {
        this.initialDir = initialDir;
        this.fileChooserName = fileChooserName;

        // force native dialog on OSX
        //final String os = System.getProperty("os.name");
        //useNativeDialog = os.equals("Mac OS X");
    }


    private void msg(String s)
    {
        LOGGER.debug(s);
    }

    /**
     * Display the contained FileChooser.
     *
     * @param parent        The parent component (Container).
     * @param acceptText    The accept text.
     * @return              The return code.
     */
    public int showDialog(final Component parent, final String acceptText)
    {
        final FileChooser fileChooser = getFileChooser(parent, acceptText);
        final int ret = showDialog(fileChooser, parent, acceptText);

        if (ret == JFileChooser.APPROVE_OPTION)
        {
            setSelectedFile(fileChooser.getSelectedFile());
        }

        return ret;
    }

    /**
     * Obtain an appropriate instance of a FileChooser.
     *
     * @param parent        The Parent component (Container).
     * @param acceptText    The accept text.
     * @return              FileChooser instance.
     */
    protected FileChooser getFileChooser(final Component parent,
                                         final String acceptText)
    {
        final FileChooser fileChooser;

        if (isUseNativeDialog())
        {
            // find or fabricate a parent frame
            final Frame f = Util.findParentFrame(parent);
            fileChooser = new AwtImpl(f, getInitialDir(), acceptText);
        }
        else
        {
            // default: Swing
            fileChooser = new SwingImpl(getInitialDir());
        }

        return fileChooser;
    }

    /**
     * Display the file chooser and return the code.
     *
     * @param fileChooser       The FileChooser to display.
     * @param parent            The parent component.
     * @param acceptText        The text for acceptance.
     * @return                  int return code.
     */
    protected int showDialog(final FileChooser fileChooser,
                             final Component parent, final String acceptText)
    {
        return fileChooser.showOpenDialog(parent);
    }

    private class SwingImpl extends JFileChooser implements FileChooser
    {
        SwingImpl(final File initialDir)
        {
            super(initialDir);

            setName(getFileChooserName());
            setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        }
        
        protected JDialog createDialog(Component parent)
                throws HeadlessException
        {
            final JDialog dialog = super.createDialog(null);
            Util.setPositionRelativeToParent(dialog, parent, 20, 20);

            return dialog;
        }
    }

    private class AwtImpl extends FileDialog implements FileChooser
    {
        private File resultFile;

        AwtImpl(Frame f, File initialDir, String title)
        {
            super(f, title);

            setName(getFileChooserName());

            // HACK: allow directories to be selected on Apple systems.
            System.setProperty("apple.awt.fileDialogForDirectories", "true");
            String s = null;

            if (initialDir != null)
            {
                s = initialDir.getAbsolutePath();
            }

            setDirectory(s);
            setFile(s);
        }
        
        // wrapper to habve the JFileChooser behaviour
        public int showDialog(Component parent, String acceptText)
        {
            setVisible(true);

            final String f = getFile();
            final String d = getDirectory();
            final int returnCode;

            msg("AppleAwtImpl: file = " + f);
            msg("AppleAwtImpl:  dir = " + d);
            
            if ((f != null) && (d != null))
            {
                this.resultFile = new File(d, f);
                msg("AppleAwtImpl: resultFile = " + resultFile);
                returnCode = JFileChooser.APPROVE_OPTION;
            }
            else
            {
                returnCode = JFileChooser.CANCEL_OPTION;
            }

            return returnCode;
        }

        /**
         * Show the open file item chooser dialog.
         *
         * @param parent The Component parent (Container).
         * @return integer return code.
         */
        @Override
        public int showOpenDialog(Component parent)
        {
            return showDialog(parent, null);
        }

        /**
         * Obtain the selected file item.
         *
         * @return File object, or null if none chosen.
         */
        @Override
        public File getSelectedFile()
        {
            return resultFile;
        }
    }

    public String getFileChooserName()
    {
        return fileChooserName;
    }

    public File getInitialDir()
    {
        return initialDir;
    }

    public boolean isUseNativeDialog()
    {
        return useNativeDialog;
    }

    public File getSelectedFile()
    {
        return selectedFile;
    }

    public void setSelectedFile(File selectedFile)
    {
        this.selectedFile = selectedFile;
    }
}