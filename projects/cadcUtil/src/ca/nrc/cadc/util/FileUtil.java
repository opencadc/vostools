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

package ca.nrc.cadc.util;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.MissingResourceException;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

/**
 * @author zhangsa
 *
 */
public class FileUtil
{
    public static boolean delete(File f, boolean recursive)
        throws IOException
    {
        if ( !f.exists() )
            return false;
        
        if (recursive && f.isDirectory())
        {
            File[] children = f.listFiles();
            for (File c : children)
            {
                if ( ! delete(c, true) )
                    return false; // return immediately if we fail to delete something
            }
        }
        return f.delete();
    }

    /**
     * Compare the contents of two files.
     *  
     * @param file1
     * @param file2
     * @return true if the contents of two files are identical.
     * 
     * @throws IOException
     */
    public static boolean compare(File file1, File file2) throws IOException
    {
        long cs1 = checksum(file1);
        long cs2 = checksum(file2);
        return (cs1 == cs2);
    }
    
    /**
     * Get checksum value of a file.
     * 
     * @param file
     * @return
     * @throws IOException
     */
    public static long checksum(File file) throws IOException
    {
        FileInputStream fInStream = new FileInputStream(file);
        CheckedInputStream cInStream = new CheckedInputStream(fInStream, new CRC32());
        BufferedInputStream bInStream = new BufferedInputStream(cInStream);
        while (bInStream.read() != -1)
        {
            // Read file in completely
        }
        return cInStream.getChecksum().getValue();
    }

    /**
     * Read a (small) file into a byte array.
     * 
     * @param f
     * @return byte array containing the content of the file
     * @throws IOException
     */
    public static byte[] readFile(File f) throws IOException
    {
        DataInputStream dis = null;
        try
        {
            dis = new DataInputStream(new FileInputStream(f));
            byte[] ret = new byte[(int) f.length()];
            dis.readFully(ret);
            dis.close();
            return ret;
        }
        finally
        {
            if (dis != null)
                try { dis.close(); }
                catch(IOException ignore) { }
        }
    }

    public static File getFileFromResource(String resourceFileName, Class runningClass)
    {
        URL url = runningClass.getClassLoader().getResource(resourceFileName);
        if (url == null)
            throw new MissingResourceException("Resource not found: " + resourceFileName, runningClass.getName(), resourceFileName);
        String path = url.getPath();
        File rtn = new File(path);
        return rtn;
    }

}
