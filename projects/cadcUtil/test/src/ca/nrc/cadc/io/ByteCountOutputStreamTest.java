/**
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2010.                            (c) 2010.
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
 ************************************************************************
 */
package ca.nrc.cadc.io;


import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.OutputStream;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for ByteCountInputStream
 * 
 * @author jenkinsd
 *
 */
public class ByteCountOutputStreamTest
{
    // Eighty-eight (5) bytes.
    protected static long BYTE_LIMIT = 5L;
    
    private OutputStream mockOutputStream = EasyMock.createMock(OutputStream.class);
    private ByteCountOutputStream testSubject;

    @Before
    public void resetTestSubject()
    {
        setTestSubject(null);
        setTestSubject(new ByteCountOutputStream(getMockOutputStream(),
                                                BYTE_LIMIT));        
    }

    @Test
    public void write() throws Exception
    {
        byte b = 0x01b;
        getMockOutputStream().write(b);
        EasyMock.expectLastCall().times((int) BYTE_LIMIT);
        EasyMock.replay(getMockOutputStream());

        for (long i = 0; i < BYTE_LIMIT; i++)
        {
            getTestSubject().write(b);
        }

        EasyMock.verify(getMockOutputStream());

        // TEST 2

        resetTestSubject();
        EasyMock.reset(getMockOutputStream());

        getMockOutputStream().write(b);
        EasyMock.expectLastCall().times((int) BYTE_LIMIT);
        EasyMock.replay(getMockOutputStream());

        try
        {
            for (long i = 0; i < (BYTE_LIMIT + 1); i++)
            {
                getTestSubject().write(b);
            }

            fail(String.format("Limit is for %d bytes, which has been exceeded.",
                               BYTE_LIMIT));
        }
        catch (IOException e)
        {
            if (!(e instanceof ByteLimitExceededException))
            {
                fail("Exception should be ByteLimitExceededException.");
            }
            
            // Otherwise, all good!
        }

        EasyMock.verify(getMockOutputStream());
    }

    @Test
    public void writeBuffer() throws Exception
    {
        byte b = 0x01b;
        final int bufferLength = (int) BYTE_LIMIT;
        byte[] buffer = new byte[bufferLength];
        for (int i = 0; i < BYTE_LIMIT; i++)
        {
            buffer[i] = b;
        }
        
        getMockOutputStream().write(EasyMock.aryEq(buffer));
        EasyMock.expectLastCall().once();

        EasyMock.replay(getMockOutputStream());

        for (long i = 0; i < BYTE_LIMIT; i += buffer.length)
        {
             getTestSubject().write(buffer);
        }

        EasyMock.verify(getMockOutputStream());

        // TEST 2

        buffer = new byte[bufferLength];
        for (int i = 0; i < BYTE_LIMIT; i++)
        {
            buffer[i] = b;
        }
        resetTestSubject();
        EasyMock.reset(getMockOutputStream());

        getMockOutputStream().write(EasyMock.aryEq(buffer));
        EasyMock.expectLastCall().once();

        EasyMock.replay(getMockOutputStream());

        try
        {
            for (long i = 0; i < (BYTE_LIMIT + 1); i += buffer.length)
            {
                getTestSubject().write(buffer);
            }

            fail(String.format("Limit is for %d bytes, which has been exceeded.",
                               BYTE_LIMIT));
        }
        catch (IOException e)
        {
            if (!(e instanceof ByteLimitExceededException))
            {
                fail("Exception should be ByteLimitExceededException.");
            }

            // Otherwise, all good!
        }

        EasyMock.verify(getMockOutputStream());
    }

    @Test
    public void writeBufferWithOffsets() throws Exception
    {
        byte b = 0x01b;
        final int bufferLength = (int) BYTE_LIMIT;
        byte[] buffer = new byte[bufferLength];
        for (int i = 0; i < BYTE_LIMIT; i++)
        {
            buffer[i] = b;
        }

        getMockOutputStream().write(buffer, 0, buffer.length);
        EasyMock.expectLastCall().once();

        EasyMock.replay(getMockOutputStream());

        for (long i = 0; i < BYTE_LIMIT; i += buffer.length)
        {
            getTestSubject().write(buffer, 0, buffer.length);
        }

        EasyMock.verify(getMockOutputStream());

        // TEST 2

        buffer = new byte[bufferLength];
        for (int i = 0; i < BYTE_LIMIT; i++)
        {
            buffer[i] = b;
        }
        
        resetTestSubject();
        EasyMock.reset(getMockOutputStream());

        getMockOutputStream().write(buffer, 0, buffer.length);
        EasyMock.expectLastCall().once();

        EasyMock.replay(getMockOutputStream());

        try
        {
            for (long i = 0; i < (BYTE_LIMIT + 1); i += buffer.length)
            {
                getTestSubject().write(buffer, 0, buffer.length);
            }

            fail(String.format("Limit is for %d bytes, which has been exceeded.",
                               BYTE_LIMIT));
        }
        catch (IOException e)
        {
            if (!(e instanceof ByteLimitExceededException))
            {
                fail("Exception should be ByteLimitExceededException.");
            }

            // Otherwise, all good!
        }

        EasyMock.verify(getMockOutputStream());
    }
    
    public ByteCountOutputStream getTestSubject()
    {
        return testSubject;
    }
    
    public void setTestSubject(ByteCountOutputStream testSubject)
    {
        this.testSubject = testSubject;
    }

    public OutputStream getMockOutputStream()
    {
        return mockOutputStream;
    }
}
