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


package ca.nrc.cadc.dlm.client;

import ca.nrc.cadc.net.OverwriteChooser;
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
public class FileOverwriteDecider implements OverwriteChooser
{
    private Component parent;
    private boolean overwriteInit = false;
    private boolean overwriteAll = false;
    private boolean skipAll = false;
    
    public FileOverwriteDecider(Component parent)
    {
        this.parent = parent;
    }
    
    public boolean overwriteFile(String fileName, long oldSize, long oldLastModified, Long newSize, Long newLastModified)
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
