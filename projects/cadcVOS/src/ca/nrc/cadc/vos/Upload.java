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

package ca.nrc.cadc.vos;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;

import javax.management.RuntimeErrorException;
import javax.net.ssl.HttpsURLConnection;

import org.apache.log4j.Logger;

import ca.nrc.cadc.vos.client.VOSpaceClient;

/**
 * Perform an upload.
 * 
 * Following the structure of Download.java
 * 
 * @see /cadcDownloadManager/src/ca/nrc/cadc/dlm/client/Download.java
 * 
 * @author zhangsa
 *
 */
public class Upload implements Runnable
{
    private static Logger log = Logger.getLogger(Upload.class);
    private int ioBufferSize = 4096;

    private URL url;
    private File localFile;

    private transient boolean go;
    private transient Thread thread; // the thread that runs the upload

    public Upload(File file, URL url)
    {
        this.localFile = file;
        this.url = url;
    }

    @Override
    public void run()
    {
        if (this.url == null) throw new IllegalArgumentException("URL is null.");
        if (this.localFile == null) throw new IllegalArgumentException("Local File is null.");
        
        log.debug("Upload.run(). url=" + this.url.toString() + " localFile=" + this.localFile.getPath());

        try
        {
            this.thread = Thread.currentThread();
            HttpURLConnection httpsCon;
            try
            {
                httpsCon = (HttpURLConnection) this.url.openConnection();
            }
            catch (IOException e)
            {
                log.error("cannot connect to URL");
                e.printStackTrace();
                throw new RuntimeException(e);
            }

            try
            {
                httpsCon.setRequestMethod("PUT");
            }
            catch (ProtocolException e)
            {
                log.error("cannot set request method.");
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            httpsCon.setUseCaches(false);
            httpsCon.setDoInput(true);
            httpsCon.setDoOutput(true);

            OutputStream outStream = null;
            try
            {
                outStream = httpsCon.getOutputStream();
            }
            catch (IOException e)
            {
                log.error("cannot obtain outputStream.");
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            InputStream inStream = null;
            try
            {
                inStream = new FileInputStream(this.localFile);
            }
            catch (FileNotFoundException e)
            {
                log.error("local file not found.");
                e.printStackTrace();
                throw new RuntimeException(e);
            }

            try
            {
                ioLoop(inStream, outStream, this.ioBufferSize);
                inStream.close();
                outStream.close();
                String responseMessage = httpsCon.getResponseMessage();
                int responseCode = httpsCon.getResponseCode();
                log.debug("run(). responseCode=" + responseCode + "; res msg=" + responseMessage);
            }
            catch (IOException e)
            {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            
            
            log.debug("Upload completed.");
        }
        catch (RuntimeException re)
        {
            throw re;
        }
        finally
        {
            this.thread = null;
        }

    }

    //    private void ioLoop(InputStream istream, OutputStream ostream, int bufferSize) throws IOException, InterruptedException
    public void ioLoop(InputStream istream, OutputStream ostream, int bufferSize) throws IOException, InterruptedException
    {
        log.debug("ioLoop: using java.io with byte[] buffer size " + bufferSize);
        byte[] buffer = new byte[bufferSize];

        int bytesRead = 0;
        int nb2 = 0;
        int tot = 0;

        while (bytesRead != -1)
        {
            // check/clear interrupted flag and throw if necessary
            if (Thread.interrupted()) throw new InterruptedException();

            bytesRead = istream.read(buffer, 0, bufferSize);
            if (bytesRead != -1)
            {
                if (bytesRead < bufferSize / 2)
                {
                    // try to get more data: merges a small chunk with a 
                    // subsequent one to minimise write calls
                    nb2 = istream.read(buffer, bytesRead, bufferSize - bytesRead);
                    if (nb2 > 0) bytesRead += nb2;
                }

                ostream.write(buffer, 0, bytesRead);
                tot += bytesRead;
            }
        }
    }

}
