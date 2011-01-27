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

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.util.Log4jInit;
import java.io.StringWriter;
import java.net.URI;
import org.junit.Assert;

/**
 * @author zhangsa
 *
 */
public class TransferReaderWriterTest
{
    static Logger log = Logger.getLogger(TransferReaderWriterTest.class);

    private Transfer transfer;

    @BeforeClass
    public static void setUpBeforeClass() 
        throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc", org.apache.log4j.Level.INFO);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {}

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() 
        throws Exception
    {
        Node target = new DataNode(new VOSURI("vos://example.com!vospace/mydir/myfile"));
        Node dataNode = new DataNode(new VOSURI("vos://example.com!vospace/mydir/myfile"));
        View view = new View(new URI(VOS.VIEW_DEFAULT));

        transfer = new Transfer();
        transfer.setDirection(Transfer.Direction.pullFromVoSpace);
        transfer.setKeepBytes(true);
        
        transfer.setServiceUrl("http://service.url.for.transfer");
        transfer.setTarget(target);
        transfer.setView(view);
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {}

    public void compareTransfers(Transfer transfer1, Transfer transfer2)
    {
        Assert.assertNotNull(transfer1);
        Assert.assertNotNull(transfer2);

        Assert.assertNotNull(transfer2.getDirection());
        Assert.assertEquals(transfer1.getDirection(), transfer2.getDirection());

        Assert.assertNotNull(transfer2.getTarget());
        Assert.assertEquals(transfer1.getTarget(), transfer2.getTarget());

        Assert.assertNotNull(transfer2.getView());
        Assert.assertEquals(transfer1.getView().getURI(), transfer2.getView().getURI());
        // TODO: compare view parameters

        Assert.assertEquals(transfer1.getDirection(), transfer2.getDirection());

        Assert.assertEquals(transfer1.getProtocols().size(), transfer2.getProtocols().size());
        Assert.assertTrue( transfer1.getProtocols().containsAll(transfer2.getProtocols()));
        Assert.assertTrue( transfer2.getProtocols().containsAll(transfer1.getProtocols()));
    }

    @Test
    public void testTransferRequest() // no protocol endpoints
    {
        try
        {
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_GET));
            protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_GET));
            transfer.setProtocols(protocols);

            StringWriter dest = new StringWriter();
            TransferWriter writer = new TransferWriter();
            writer.write(transfer, dest);
            String xml = dest.toString();

            log.debug(xml);

            TransferReader reader = new TransferReader();
            Transfer transfer2 = reader.read(xml);

            compareTransfers(transfer, transfer2);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testTransferWithProtocolEndpoints()
    {
        try
        {
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_GET, "http://example.com/someplace/123", null));
            protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_GET, "https://example.com/otherplace/456", null));
            transfer.setProtocols(protocols);
            
            StringWriter dest = new StringWriter();
            TransferWriter writer = new TransferWriter();
            writer.write(transfer, dest);
            String xml = dest.toString();

            log.debug(xml);

            TransferReader reader = new TransferReader();
            Transfer transfer2 = reader.read(xml);

            compareTransfers(transfer, transfer2);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
}
