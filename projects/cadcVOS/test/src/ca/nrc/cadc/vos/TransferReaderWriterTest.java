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

import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.View.Parameter;

/**
 * @author zhangsa
 *
 */
public class TransferReaderWriterTest
{
    static Logger log = Logger.getLogger(TransferReaderWriterTest.class);

    private String baseURI = "vos://example.com!vospace";
    private List<Protocol> protocols;
    private VOSURI target;

    @BeforeClass
    public static void setUpBeforeClass() 
        throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc", org.apache.log4j.Level.DEBUG);
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
        target = new VOSURI(baseURI + "/mydir/myfile");
        protocols = new ArrayList<Protocol>();
        protocols.add(new Protocol(VOS.PROTOCOL_HTTP_GET));
        protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_GET));
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

        Assert.assertEquals("target", transfer1.getTarget(), transfer2.getTarget());

        Assert.assertEquals("direction", transfer1.getDirection(), transfer2.getDirection());

        if (transfer1.getView() != null)
        {
            Assert.assertNotNull("view", transfer2.getView());
            Assert.assertEquals("view uri", transfer1.getView().getURI(), transfer2.getView().getURI());
            Assert.assertEquals("view param size", transfer1.getView().getParameters().size(), transfer2.getView().getParameters().size());
            Assert.assertTrue("view params", transfer1.getView().getParameters().containsAll(transfer2.getView().getParameters()));
            Assert.assertTrue("view params", transfer2.getView().getParameters().containsAll(transfer1.getView().getParameters()));
        }
        else
             Assert.assertNull("view", transfer2.getView());

        if (transfer1.getProtocols() != null)
        {
            Assert.assertNotNull("protocols", transfer2.getProtocols());
            Assert.assertEquals("protocols size", transfer1.getProtocols().size(), transfer2.getProtocols().size());
            Assert.assertTrue("protocols content", transfer1.getProtocols().containsAll(transfer2.getProtocols()));
            Assert.assertTrue("protocols content", transfer2.getProtocols().containsAll(transfer1.getProtocols()));
        }
        else
            Assert.assertNull("protocols", transfer2.getProtocols());
    }

    @Test
    public void testPushPullTransfer()
    {
        try
        {
            Transfer transfer = new Transfer(target, Direction.pullFromVoSpace, protocols);
            log.debug("testPushPullTransfer: " + transfer);

            StringWriter dest = new StringWriter();
            TransferWriter writer = new TransferWriter();
            writer.write(transfer, dest);
            String xml = dest.toString();

            log.debug("testPushPullTransfer\n" + xml);

            TransferReader reader = new TransferReader();
            Transfer transfer2 = reader.read(xml);

            compareTransfers(transfer, transfer2);


            transfer = new Transfer(target, Direction.pushToVoSpace, protocols);
            log.debug("testPushPullTransfer: " + transfer);

            dest = new StringWriter();
            writer.write(transfer, dest);
            xml = dest.toString();

            log.debug("testPushPullTransfer\n" + xml);

            transfer2 = reader.read(xml);

            compareTransfers(transfer, transfer2);
        }
        catch(Exception unexpected)
        {
            unexpected.printStackTrace();
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    @Test
    public void testTransferWithViewAndNoParameters()
    {
        try
        {
            View view = new View(new URI(VOS.VIEW_ANY));
            Transfer transfer = new Transfer(target, Direction.pullFromVoSpace, view, protocols);
            log.debug("testTransferWithViewAndNoParameters: " + transfer);
            
            StringWriter dest = new StringWriter();
            TransferWriter writer = new TransferWriter();
            writer.write(transfer, dest);
            String xml = dest.toString();

            log.debug("testTransferWithViewAndNoParameters\n" + xml);

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
    public void testTransferWithViewParameters()
    {
        try
        {
            View view = new View(new URI(VOS.VIEW_ANY));
            List<Parameter> params = new ArrayList<Parameter>();
            params.add(new Parameter(new URI(VOS.VIEW_ANY), "cutoutParameter1"));
            params.add(new Parameter(new URI(VOS.VIEW_BINARY), "cutoutParameter2"));
            params.add(new Parameter(new URI("ivo://cadc.nrc.ca/vospace/viewparam#someotherparam"),
                    "[]{}/;,+=-'\"@#$%^"));
            view.setParameters(params);

            Transfer transfer = new Transfer(target, Direction.pullFromVoSpace, view, protocols);
            log.debug("testTransferWithViewParameters: " + transfer);

            StringWriter dest = new StringWriter();
            TransferWriter writer = new TransferWriter();
            writer.write(transfer, dest);
            String xml = dest.toString();

            log.debug("testTransferWithViewParameters\n" + xml);

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
            List<Protocol> pe = new ArrayList<Protocol>();
            pe.add(new Protocol(VOS.PROTOCOL_HTTP_GET, "http://example.com/someplace/123", null));
            pe.add(new Protocol(VOS.PROTOCOL_HTTPS_GET, "https://example.com/otherplace/456", null));

            Transfer transfer = new Transfer(target, Direction.pullFromVoSpace, pe);
            log.debug("testTransferWithProtocolEndpoints: " + transfer);
            
            StringWriter dest = new StringWriter();
            TransferWriter writer = new TransferWriter();
            writer.write(transfer, dest);
            String xml = dest.toString();

            log.debug("testTransferWithProtocolEndpoints\n" + xml);

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
    public void testTransferMoveNode()
    {
        try
        {
            VOSURI dest = new VOSURI(baseURI + "/mydir/otherfile");
            Transfer transfer = new Transfer(target, dest, false);
            log.debug("testTransferMoveNode: " + transfer);

            StringWriter sw = new StringWriter();
            TransferWriter writer = new TransferWriter();
            writer.write(transfer, sw);
            String xml = sw.toString();

            log.debug("testTransferMoveNode\n" + xml);

            TransferReader reader = new TransferReader();
            Transfer transfer2 = reader.read(xml);

            compareTransfers(transfer, transfer2);
        }
        catch(Exception unexpected)
        {
            unexpected.printStackTrace();
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
        finally
        {
            log.debug("testTransferMoveNode - DONE");
        }
    }
    
    @Test
    public void testTransferCopyNode()
    {
        try
        {
            VOSURI dest = new VOSURI(baseURI + "/mydir/otherfile");
            Transfer transfer = new Transfer(target, dest, true);
            log.debug("testTransferCopyNode: " + transfer);

            StringWriter sw = new StringWriter();
            TransferWriter writer = new TransferWriter();
            writer.write(transfer, sw);
            String xml = sw.toString();

            log.debug("testTransferCopyNode\n" + xml);

            TransferReader reader = new TransferReader();
            Transfer transfer2 = reader.read(xml);

            compareTransfers(transfer, transfer2);
        }
        catch(Exception unexpected)
        {
            unexpected.printStackTrace();
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    
    @Test
    public void testInvalidTransferXml()
    {
        StringBuffer sb = new StringBuffer();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<vos:transfer></vos:transfer>");
        String xml = sb.toString();

        log.debug(xml);

        TransferReader reader = new TransferReader();
        try
        {
            Transfer transfer2 = reader.read(xml);
            Assert.fail("Did not handle invalid Transfer XML properly");
        }
        catch(Exception expected)
        {
            // expected
        }
    }
}
