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

package ca.nrc.cadc.uws;

import ca.nrc.cadc.util.HexUtil;
import ca.nrc.cadc.util.Log4jInit;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test the ID unique generation if the DatabasePersistence base class.
 * 
 * @author pdowler
 */
public class DatabasePersistenceTest 
{
    private static Logger log = Logger.getLogger(DatabasePersistenceTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.uws", Level.INFO);
    }

    byte[] clock;
    byte[] addr;

    public DatabasePersistenceTest()
        throws Exception
    {
        this.clock = HexUtil.toBytes(System.currentTimeMillis());
        this.addr = InetAddress.getLocalHost().getAddress();
    }

    @Test
    public void testGenerateID_NoSeed()
    {
        try
        {
            DatabasePersistence dp1 = new MyDatabasePersistence();
            dp1.initRNG(null, null);
            DatabasePersistence dp2 = new MyDatabasePersistence();
            dp2.initRNG(null, null);

            Set<String> ids = new HashSet<String>();
            for (int i=0; i<1000; i++)
            {
                ids.add(dp1.generateID());
                ids.add(dp2.generateID());
            }
            log.debug("testGenerateID_NoSeed: generated " + ids.size() + " unique ID strings");
            Assert.assertEquals(2000, ids.size());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testGenerateID_SameSeed()
    {
        try
        {
            DatabasePersistence dp1 = new MyDatabasePersistence();
            dp1.initRNG(clock, addr);
            DatabasePersistence dp2 = new MyDatabasePersistence();
            dp2.initRNG(clock, addr);

            Set<String> ids = new HashSet<String>();
            for (int i=0; i<1000; i++)
            {
                ids.add(dp1.generateID());
                ids.add(dp2.generateID());
            }
            log.debug("testGenerateID_SameSeed: generated " + ids.size() + " unique ID strings");
            Assert.assertEquals(2000, ids.size());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }


    private class MyDatabasePersistence extends DatabasePersistence
    {
        public MyDatabasePersistence() { } // do not init

        @Override
        protected DataSource getDataSource()
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        protected String getJobTable()
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        protected String getParameterTable(String name)
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        protected List<String> getParameterTables()
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        protected String getResultTable()
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }


    }
}
