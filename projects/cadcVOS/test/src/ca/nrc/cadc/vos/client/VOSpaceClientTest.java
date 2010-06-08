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

package ca.nrc.cadc.vos.client;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import junit.framework.Assert;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.DataView;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.NodeWriterTest;
import ca.nrc.cadc.vos.Protocol;
import ca.nrc.cadc.vos.TestUtil;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.View;
import ca.nrc.cadc.vos.VOS.NodeBusyState;
import ca.nrc.cadc.vos.util.NodeUtil;

/**
 * @author zhangsa
 *
 */
public class VOSpaceClientTest
{
    private static Logger log = Logger.getLogger(NodeWriterTest.class);
    {
        Log4jInit.setLevel("ca", Level.DEBUG);
    }
    static String ENDPOINT = "https://arran.cadc.dao.nrc.ca";
    static String ROOT_NODE = "zhangsa/";
    VOSpaceClient client = new VOSpaceClient(ENDPOINT + "/vospace");
    DataNode dataNode;
    ContainerNode containerNode;
    ContainerNode containerNode2;
    Transfer transfer;
    View view;
    Protocol protocol;
    List<Protocol> protocols = new ArrayList<Protocol>();

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
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
        this.view = new DataView("ivo://myregegistry/vospace/views#myview", this.dataNode);

        this.protocol = new Protocol(VOS.PROTOCOL_HTTPS_GET, this.ENDPOINT, null);
        this.protocols.add(this.protocol);

        this.transfer = new Transfer();
        this.transfer.setEndpoint(ENDPOINT);
        this.transfer.setTarget(this.dataNode);
        this.transfer.setView(this.view);

        this.transfer.setProtocols(this.protocols);
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception
    {
    }

    //@Test
    public void testGetNode() throws Exception
    {
        String slashPath1 = "/" + ROOT_NODE + TestUtil.uniqueStringOnTime();
        ContainerNode cnode = new ContainerNode(new VOSURI(VOS.VOS_URI + slashPath1));

        Node nodeRtn = client.createNode(cnode);
        log.debug("Returned Node: " + nodeRtn);
        log.debug("XML of Returned Node: " + NodeUtil.xmlString(nodeRtn));

        Node nodeRtn2 = client.getNode(nodeRtn.getPath());
        log.debug("GetNode: " + nodeRtn2);
        log.debug("XML of GetNode: " + NodeUtil.xmlString(nodeRtn2));
        Assert.assertEquals(nodeRtn.getPath(), nodeRtn2.getPath());
    }

@Test
    public void testCreateContainerNode() throws Exception
    {
        String slashPath1 = "/" + ROOT_NODE + TestUtil.uniqueStringOnTime();
        ContainerNode cnode = new ContainerNode(new VOSURI(VOS.VOS_URI + slashPath1));

        Node nodeRtn = client.createNode(cnode);
        log.debug("Returned Node: " + nodeRtn);
        log.debug("XML of Returned Node: " + NodeUtil.xmlString(nodeRtn));
        Assert.assertEquals("/" + nodeRtn.getPath(), slashPath1);
    }

    //@Test
    public void testCreateSemanticContainerNode() throws Exception
    {
        String dir1 = TestUtil.uniqueStringOnTime();
        String slashPath1 = "/" + ROOT_NODE + dir1;
        String slashPath1a = slashPath1 + "/" + TestUtil.uniqueStringOnTime();
        String slashPath1b = slashPath1 + "/" + TestUtil.uniqueStringOnTime();

        // List of NodeProperty
        List<NodeProperty> properties = new ArrayList<NodeProperty>();
        NodeProperty nodeProperty = new NodeProperty(VOS.PROPERTY_URI_DESCRIPTION, "My award winning images");
        nodeProperty.setReadOnly(true);
        properties.add(nodeProperty);

        // List of Node
        List<Node> nodes = new ArrayList<Node>();
        nodes.add(new DataNode(new VOSURI(VOS.VOS_URI + slashPath1a)));
        nodes.add(new DataNode(new VOSURI(VOS.VOS_URI + slashPath1b)));

        // ContainerNode
        ContainerNode cnode;
        cnode = new ContainerNode(new VOSURI(VOS.VOS_URI + slashPath1));
        cnode.setProperties(properties);
        cnode.setNodes(nodes);

        Node nodeRtn = client.createNode(cnode);
        log.debug("Returned Node: " + nodeRtn);
        log.debug("XML of Returned Node: " + NodeUtil.xmlString(nodeRtn));
    }

    //@Test
    public void testCreateDataNode() throws Exception
    {
        String slashPath1 = "/" + ROOT_NODE + TestUtil.uniqueStringOnTime();
        DataNode dnode = new DataNode(new VOSURI(VOS.VOS_URI + slashPath1));

        Node nodeRtn = client.createNode(dnode);
        log.debug("Returned Node: " + nodeRtn);
        log.debug("XML of Returned Node: " + NodeUtil.xmlString(nodeRtn));
    }

    //@Test
    public void testPushToVoSpace()
    {
        this.transfer.setDirection(Transfer.Direction.pushToVoSpace);
        Transfer transferRtn = client.pushToVoSpace(transfer);
        log.debug(transferRtn.toXmlString());
    }

    //@Test
    public void testPullFromVoSpace()
    {
        this.transfer.setDirection(Transfer.Direction.pullFromVoSpace);
        Transfer transferRtn = client.pullFromVoSpace(transfer);
        log.debug(transferRtn.toXmlString());
    }

}
