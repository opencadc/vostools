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

import java.security.SecureRandom;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit tests for SSLUtil.
 *
 * @author pdowler
 */
public class HexUtilTest
{
    private static Logger log = Logger.getLogger(HexUtilTest.class);

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc.util", Level.INFO);
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterClass
    public static void tearDownAfterClass() throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception
    {
    }

    @Test
    public void testRoundTripBytes() throws Exception
    {
        short[] ts = new short[] { Short.MIN_VALUE, (short) -1, (short) 0, (short) 1, Short.MAX_VALUE };
        int[] ti = new int[] { Integer.MIN_VALUE, (int) -1, (int) 0, (int) 1, Integer.MAX_VALUE };
        long[] tl = new long[] { Long.MIN_VALUE, (long) -1, (long) 0, (long) 1, Long.MAX_VALUE };
        try
        {
            for (short v1 : ts)
            {
                byte[] tmp = HexUtil.toBytes(v1);
                short v2 = HexUtil.toShort(tmp);
                Assert.assertEquals(v1, v2);
            }
            for (int v1: ti)
            {
                byte[] tmp = HexUtil.toBytes(v1);
                int v2 = HexUtil.toInt(tmp);
                Assert.assertEquals(v1, v2);
            }
            for (long v1 : tl)
            {
                byte[] tmp = HexUtil.toBytes(v1);
                long v2 = HexUtil.toLong(tmp);
                Assert.assertEquals(v1, v2);
            }
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    @Test
    public void testRoundTripHex() throws Exception
    {
        byte[] tb = new byte[] { Byte.MIN_VALUE, (byte) -1, (byte) 0, (byte) 1, Byte.MAX_VALUE };
        short[] ts = new short[] { Short.MIN_VALUE, (short) -1, (short) 0, (short) 1, Short.MAX_VALUE };
        int[] ti = new int[] { Integer.MIN_VALUE, (int) -1, (int) 0, (int) 1, Integer.MAX_VALUE };
        long[] tl = new long[] { Long.MIN_VALUE, (long) -1, (long) 0, (long) 1, Long.MAX_VALUE };
        
        try
        {
            for (byte v1 : tb)
            {
                String hex = HexUtil.toHex(v1);
                log.debug(v1 + " -> " + hex);
                byte v2 = HexUtil.toByte(hex);
                Assert.assertEquals(v1, v2);
            }
            for (short v1 : ts)
            {
                String hex = HexUtil.toHex(v1);
                log.debug(v1 + " -> " + hex);
                short v2 = HexUtil.toShort(hex);
                Assert.assertEquals(v1, v2);
            }
            for (int v1: ti)
            {
                String hex = HexUtil.toHex(v1);
                log.debug(hex);
                int v2 = HexUtil.toInt(hex);
                Assert.assertEquals(v1, v2);
            }
            for (long v1 : tl)
            {
                String hex = HexUtil.toHex(v1);
                log.debug(hex);
                long v2 = HexUtil.toLong(hex);
                Assert.assertEquals(v1, v2);
            }
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    @Test
    public void testRoundTripHexByteArray() throws Exception
    {
        try
        {
            SecureRandom sr = new SecureRandom();
            byte[] v1 = new byte[128];
            sr.nextBytes(v1);
            String hex = HexUtil.toHex(v1);
            log.debug(hex);
            byte[] v2 = HexUtil.toBytes(hex);
            compare(v1, v2);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    private void compare(byte[] v1, byte[] v2)
    {
        Assert.assertEquals(v1.length, v2.length);
        for (int i=0; i<v1.length; i++)
            Assert.assertEquals(v1[i], v2[i]);
    }
}
