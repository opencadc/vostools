/*****************************************************************************
 *  
 *  Copyright (C) 2009				Copyright (C) 2009
 *  National Research Council		Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6			Ottawa, Canada, K1A 0R6
 *  All rights reserved				Tous droits reserves
 *  					
 *  NRC disclaims any warranties,	Le CNRC denie toute garantie
 *  expressed, implied, or statu-	enoncee, implicite ou legale,
 *  tory, of any kind with respect	de quelque nature que se soit,
 *  to the software, including		concernant le logiciel, y com-
 *  without limitation any war-		pris sans restriction toute
 *  ranty of merchantability or		garantie de valeur marchande
 *  fitness for a particular pur-	ou de pertinence pour un usage
 *  pose.  NRC shall not be liable	particulier.  Le CNRC ne
 *  in any event for any damages,	pourra en aucun cas etre tenu
 *  whether direct or indirect,		responsable de tout dommage,
 *  special or general, consequen-	direct ou indirect, particul-
 *  tial or incidental, arising		ier ou general, accessoire ou
 *  from the use of the software.	fortuit, resultant de l'utili-
 *  								sation du logiciel.
 *  
 *  
 *  This file is part of cadcDownloadManager.
 *  
 *  CadcDownloadManager is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  CadcDownloadManager is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with cadcDownloadManager.  If not, see <http://www.gnu.org/licenses/>.			
 *  
 *****************************************************************************/

package ca.nrc.cadc.dlm.client;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.Point;
import java.io.File;
import javax.swing.JDialog;
import javax.swing.JFileChooser;


class MyFileChooser
{
    private boolean debug = false;
    private File initialDir;
    private boolean useNativeDialog;
    private File selectedFile;
    
    public MyFileChooser(File initialDir) 
    {
        this.initialDir = initialDir;
        // force native dialog on OSX
        String os = System.getProperty("os.name");
        if ( os.equals("Mac OS X") )
            useNativeDialog = true;
    }
    
    public MyFileChooser(File initialDir, boolean useNativeDialog) 
    {
        this.initialDir = initialDir; 
        this.useNativeDialog = useNativeDialog;
    }
    private void msg(String s)
    {
        if (debug) System.out.println("[MyFileChooser] " + s);
    }
    public int showDialog(Component parent, String acceptText)
    {
        if (useNativeDialog)
        {
            // find or fabricate a parent frame
            Frame f = Util.findParentFrame(parent);
            AwtImpl impl = new AwtImpl(f, initialDir, acceptText);
            int ret = impl.showDialog(parent, acceptText);
            if (ret == JFileChooser.APPROVE_OPTION)
                this.selectedFile = impl.getSelectedFile();
            return ret;
        }
        // default: Swing
        SwingImpl impl = new SwingImpl(initialDir);
        int ret = impl.showDialog(parent, acceptText);
        if (ret == JFileChooser.APPROVE_OPTION)
            this.selectedFile = impl.getSelectedFile();
        return ret;
    }
    
    public File getSelectedFile()
    {
        return selectedFile;
    }
    
    private class SwingImpl extends JFileChooser
    {
        SwingImpl(File initialDir)
        {
            super(initialDir);
            setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        }
        
        protected JDialog createDialog(Component parent) throws HeadlessException
        {
            JDialog dialog = super.createDialog(null);
            Util.setPositionRelativeToParent(dialog, parent, 20, 20);
            return dialog;
        }
    }

    private class AwtImpl extends FileDialog
    {
        private File resultFile;
        boolean forceLocation;
        
        AwtImpl(Frame f, File initialDir, String title)
        {
            super(f, title);
            // HACK: allow directories to be selected on apple
            System.setProperty("apple.awt.fileDialogForDirectories", "true");
            String s = null;
            if (initialDir != null)
                s = initialDir.getAbsolutePath();
            setDirectory(s);
            setFile(s);
        }
        
        // wrapper to habve the JFileChooser behaviour
        public int showDialog(Component parent, String acceptText)
        {
            setVisible(true);
            String f = getFile();
            String d = getDirectory();
            String str = null;
            msg("AppleAwtImpl: file = " + f);
            msg("AppleAwtImpl:  dir = " + d);
            
            if (f != null && d != null)
            {
                this.resultFile = new File(d, f);
                msg("AppleAwtImpl: resultFile = " + resultFile);
                return JFileChooser.APPROVE_OPTION;
            }
            return JFileChooser.CANCEL_OPTION;
        }
        
         public File getSelectedFile() { return resultFile; }
    }
    
    
}