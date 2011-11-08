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

package ca.nrc.cadc.io;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.http.HttpServletResponse;

/**
 * Simple OutputStream wrapper that keeps track of bytes written.
 * 
 * Alternate constructors allow for byte limits to be set and for
 * the lazy retrieval of the output stream from an HttpServletResponse object.
 * 
 * @author pdowler
 */
public class ByteCountOutputStream extends OutputStream implements ByteCounter
{
    private OutputStream outputStream;
    private HttpServletResponse response;
    
    private long byteCount = 0L;
    private Long byteLimit = null;

    /**
     * Constructor.
     *
     * @param outputStream
     */
    public ByteCountOutputStream(OutputStream outputStream)
    {
        super();
        this.outputStream = outputStream;
    }
    
    /**
     * Constructor that takes the target output stream and a byte limit.
     * 
     * @param outputStream       The OutputStream to wrap.
     * @param byteLimit    The quota space left to be written to, in bytes.
     */
    public ByteCountOutputStream(OutputStream outputStream,
                                long byteLimit)
    {
        this.outputStream = outputStream;
        if (byteLimit > 0)
            this.byteLimit = byteLimit;
    }
    
    /**
     * Constructor that takes the http servlet response.
     * 
     * he output stream is retrieved from the response at the last possible
     * moment so as to not commit the state of the response.
     * 
     * @param outputStream       The OutputStream to wrap.
     * @param byteLimit    The quota space left to be written to, in bytes.
     */
    public ByteCountOutputStream(HttpServletResponse response)
    {
        this.response = response;
    }
    
    /**
     * Constructor that takes the http servlet response and a byte limit.
     * 
     * The output stream is retrieved from the response at the last possible
     * moment so as to not commit the state of the response.
     * 
     * @param response     The HttpServletResponse holding the output stream.
     * @param byteLimit    The quota space left to be written to, in bytes.
     */
    public ByteCountOutputStream(HttpServletResponse response,
                                long byteLimit)
    {
        this.response = response;
        if (byteLimit > 0)
            this.byteLimit = byteLimit;
    }

    /**
     * Get the number of bytes written to the underlying output stream to far.
     * This value is up to date right after flush() is called; otherwise, it may
     * be less than the total number of bytes written via write() method calls of
     * the underlying stream.
     *
     * @return number of bytes written to underlying output stream
     */
    @Override
    public long getByteCount()
    {
        return byteCount;
    }

    @Override
    public void close()
        throws IOException
    {
        getOutputStream().close();
    }

    @Override
    public void flush()
        throws IOException
    {
        getOutputStream().flush();
    }

    @Override
    public void write(int b)
        throws IOException
    {
        if (hasReachedLimit())
            throw new ByteLimitExceededException(byteLimit);
        getOutputStream().write(b);
        byteCount++;
    }

    @Override
    public void write(byte[] b)
        throws IOException
    {
        if (hasReachedLimit())
            throw new ByteLimitExceededException(byteLimit);
        getOutputStream().write(b);
        byteCount += b.length;
    }

    @Override
    public void write(byte[] b, int offset, int num)
        throws IOException
    {
        if (hasReachedLimit())
            throw new ByteLimitExceededException(byteLimit);
        getOutputStream().write(b, offset, num);
        byteCount += num;
    }
    
    /**
     * Return the local output stream or get it from the
     * response object.
     */
    private OutputStream getOutputStream() throws IOException
    {
        if (outputStream != null)
            return outputStream;
        
        outputStream = response.getOutputStream();
        return outputStream;
    }
    
    /**
     * Obtain whether the byte limit has been reached.
     */
    private boolean hasReachedLimit()
    {
        if (byteLimit == null)
            return false;
        return byteCount >= byteLimit;
    }
}
