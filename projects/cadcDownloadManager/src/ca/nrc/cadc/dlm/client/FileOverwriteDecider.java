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
import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Simple interface for class(es) that decides if a file should be overritten by a different one.
 * That is one horrendous interface name though :).
 *
 * @author pdowler
 */
public class FileOverwriteDecider 
{
    private Component parent;
    private boolean overwriteInit = false;
    private boolean overwriteAll = false;
    private boolean skipAll = false;
    
    public FileOverwriteDecider(Component parent)
    {
        this.parent = parent;
    }
    
    public boolean overwriteFile(String fileName, long oldSize, long oldLastModified, long newSize, long newLastModified)
    {
        // unsynchronised non-blocking response
        if (overwriteAll)
            return true;
        if (skipAll)
            return false;

        try
        {
            FOD fod = new FOD(fileName, oldSize, oldLastModified, newSize, newLastModified);
            synchronized(this)
            {
                if ( SwingUtilities.isEventDispatchThread())
                {
                    fod.run();
                }
                else
                {
                    SwingUtilities.invokeAndWait(fod);
                }
                if (fod.madeDecision)
                {
                    this.overwriteAll = fod.overwriteAll;
                    this.skipAll = fod.skipAll;
                }
            }
            return fod.overwrite;
        }
        catch(InterruptedException oops) 
        { 
            oops.printStackTrace();
            return false; 
        }
        catch(InvocationTargetException itex)
        {
            itex.printStackTrace();
            throw (RuntimeException) itex.getTargetException(); // a RuntimeException from FOD.run()
        }
    }
    
    private class FOD implements Runnable
    {
        public boolean overwrite = false;
        public boolean overwriteAll = false;
        public boolean skipAll = false;
        
        public boolean madeDecision = false;
        
        String fileName;
        long oldSize, oldLastModified, newSize, newLastModified;
        
        public String toString() { return "FOD[" + fileName + "]"; }
        
        FOD(String fileName, long oldSize, long oldLastModified, long newSize, long newLastModified)
        {
            this.fileName = fileName;
            this.oldSize = oldSize;
            this.oldLastModified = oldLastModified;
            this.newSize = newSize;
            this.newLastModified = newLastModified;
        }
        
        public void run()
        {
            // check if another caller got in and user decided something global
            if (FileOverwriteDecider.this.overwriteAll || FileOverwriteDecider.this.skipAll)
            {
                this.overwrite = FileOverwriteDecider.this.overwriteAll;
                return;
            }
            
            try
            {
                Object[] possibilities = { "Yes", "Yes to All", "No", "No to All" };

                String oldSizeStr = Long.toString(oldSize) + " bytes";
                String newSizeStr = Long.toString(newSize) + " bytes";
                if (newSize == -1) // unknown size
                    newSizeStr = "?";
                String relativeAge = "a NEWER";
                if (newLastModified < oldLastModified)
                    relativeAge = "an OLDER";

                int option = JOptionPane.showOptionDialog(parent,
                    "File exists: \n"
                        + "\n  filename: " + fileName
                        + "\n  size: " + oldSizeStr
                        + "\n  modified: " + new Date(oldLastModified)
                        + "\n\nReplace the existing file with "+relativeAge+" one?\n"
                        + "\n  size: " + newSizeStr  
                        + "\n  modified: " + new Date(newLastModified),
                        "Save File",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, possibilities,
                    possibilities[0]);
                switch (option)
                {
                    case 0:
                        this.overwrite = true;
                        break;
                    case 1:
                        this.overwriteAll = true;
                        this.skipAll = false;
                        this.overwrite = true;
                        this.madeDecision = true;
                        break;
                    case 2:
                        overwrite = false;
                        break;
                    case 3:
                        this.skipAll = true;
                        this.overwrite = false;
                        this.madeDecision = true;
                        break;
                    default:
                        throw new RuntimeException("oops: found a bug!");
                }
            }
            catch(Throwable t) { t.printStackTrace(); }
        }
    }
}
