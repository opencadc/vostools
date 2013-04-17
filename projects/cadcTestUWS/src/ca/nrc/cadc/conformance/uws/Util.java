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

package ca.nrc.cadc.conformance.uws;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.output.XMLOutputter;

import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public abstract class Util
{
    public static final String[] PHASES =
    {
        "PENDING", "QUEUED", "EXECUTING", "COMPLETED", "ERROR", "ABORTED"
    };

    public static boolean validatePhase(String value)
    {
        for (int i = 0; i < PHASES.length; i++)
        {
            if (PHASES[i].equals(value))
                return true;
        }
        return false;
    }

    public static String getHostName()
    {
        try
        {
            return InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e)
        {
            throw new RuntimeException("Unable to determine hostname for localhost: " + e.getMessage());
        }
    }

    public static String getResponseHeaders(WebResponse response)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Response headers:");
        sb.append("\r\n");
        String[] headers = response.getHeaderFieldNames();
        for (int i = 0; i < headers.length; i++)
        {
            sb.append("\t");
            sb.append(headers[i]);
            sb.append("=");
            sb.append(response.getHeaderField(headers[i]));
            sb.append("\r\n");
        }
        return sb.toString();
    }

    public static String getRequestHeaders(WebRequest request)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Request headers:");
        sb.append("\r\n");
        Dictionary headers = request.getHeaders();
        Enumeration keys = headers.keys();
        while (keys.hasMoreElements())
        {
            String key = (String) keys.nextElement();
            String value = (String) headers.get(key);
            sb.append("\t");
            sb.append(key);
            sb.append(" = ");
            sb.append(value);
            sb.append("\r\n");
        }
        return sb.toString();
    }
    
    public static String getRequestParameters(WebRequest request)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Request parameters:");
        sb.append("\r\n");
        String[] headers = request.getRequestParameterNames();
        for (int i = 0; i < headers.length; i++)
        {
            sb.append("\t");
            sb.append(headers[i]);
            sb.append("=");
            sb.append(request.getParameter(headers[i]));
            sb.append("\r\n");
        }
        return sb.toString();
    }

    public static String inputStreamToString(InputStream inputStream)
        throws IOException
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder sb = new StringBuilder();
        String line = null;
        try
        {
            while ((line = reader.readLine()) != null)
            {
                sb.append(line);
                sb.append("\n");
            }
        }
        finally
        {
            try
            {
                inputStream.close();
            }
            catch (IOException e) {}
        }
        return sb.toString();
    }

    /**
     * Get a list of XML file with the given filename prefix in the given directory.
     * 
     * @param directoryPath
     * @param prefix, file name prefix
     * @return
     * @throws IOException
     * @author zhangsa
     */
    public static List<File> loadXmlFileList(String directoryPath, String prefix) throws IOException
    {
        File directory = new File(directoryPath);
        if (!directory.canRead())
            throw new IOException(String.format("Cannot read directory: [%s]", directoryPath));
        
        XmlFilenameFilter filter = new XmlFilenameFilter(prefix);
        File[] fileArray = directory.listFiles(filter);
        return Arrays.asList(fileArray);
    }
    
    /**
     * Get the XML string of an XML Document object.
     *  
     * @param document
     * @return String
     * @author zhangsa
     */
    public static String getXmlString(Document document)
    {
        XMLOutputter xmlOutputter = new XMLOutputter();
        return xmlOutputter.outputString(document);
    }


    /**
     * Read File as String.
     * 
     * @param file
     * @return
     * @throws java.io.IOException
     */
    public static String readFileAsString(File file) throws java.io.IOException
    {
        byte[] buffer = new byte[(int) file.length()];
        BufferedInputStream f = null;
        try
        {
            f = new BufferedInputStream(new FileInputStream(file));
            f.read(buffer);
        }
        finally
        {
            if (f != null) try
            {
                f.close();
            }
            catch (IOException ignored)
            {
            }
        }
        return new String(buffer);
    }

}
