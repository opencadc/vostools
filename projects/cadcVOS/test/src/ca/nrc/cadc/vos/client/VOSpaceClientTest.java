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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.util.FileUtil;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.ClientTransfer;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.DataView;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.NodeWriterTest;
import ca.nrc.cadc.vos.Protocol;
import ca.nrc.cadc.vos.TestUtil;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;

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
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception
    {
    }

    //@Test
    public void testSetNode() throws Exception
    {
        String slashPath1 = "/" + ROOT_NODE + TestUtil.uniqueStringOnTime();
        ContainerNode cnode = new ContainerNode(new VOSURI(VOS.VOS_URI + slashPath1));

        Node nodeRtn = client.createNode(cnode);
        log.debug("Returned Node: " + nodeRtn);
        log.debug("XML of Returned Node: " + VOSClientUtil.xmlString(nodeRtn));

        Node nodeRtn2 = client.getNode(nodeRtn.getPath());
        log.debug("GetNode: " + nodeRtn2);
        log.debug("XML of GetNode: " + VOSClientUtil.xmlString(nodeRtn2));
        Assert.assertEquals(nodeRtn.getPath(), nodeRtn2.getPath());

        List<NodeProperty> properties = new ArrayList<NodeProperty>();

        String newUniqueValue = TestUtil.uniqueStringOnTime();
        NodeProperty nodeProperty = new NodeProperty(VOS.PROPERTY_URI_CONTRIBUTOR, newUniqueValue);
        nodeProperty.setReadOnly(true);
        properties.add(nodeProperty);

        String newUniqueValue2 = TestUtil.uniqueStringOnTime();
        NodeProperty nodeProperty2 = new NodeProperty(VOS.PROPERTY_URI_COVERAGE, newUniqueValue2);
        nodeProperty2.setReadOnly(true);
        properties.add(nodeProperty2);

        nodeRtn2.setProperties(properties);

        Node nodeRtn3 = client.setNode(nodeRtn2);
        String propValue = nodeRtn3.findProperty(VOS.PROPERTY_URI_CONTRIBUTOR).getPropertyValue();
        String propValue2 = nodeRtn3.findProperty(VOS.PROPERTY_URI_COVERAGE).getPropertyValue();
        log.debug("XML of SetNode: " + VOSClientUtil.xmlString(nodeRtn3));
        Assert.assertEquals(newUniqueValue, propValue);
        Assert.assertEquals(newUniqueValue2, propValue2);
    }

    //@Test
    public void testDeleteNode() throws Exception
    {
        String slashPath1 = "/" + ROOT_NODE + TestUtil.uniqueStringOnTime();
        ContainerNode cnode = new ContainerNode(new VOSURI(VOS.VOS_URI + slashPath1));

        Node nodeRtn = client.createNode(cnode);
        log.debug("Returned Node: " + nodeRtn);
        log.debug("XML of Returned Node: " + VOSClientUtil.xmlString(nodeRtn));

        client.deleteNode(nodeRtn.getPath());

        boolean exceptionThrown = false;
        try
        {
            client.getNode(nodeRtn.getPath());
        }
        catch (IllegalArgumentException ex)
        {
            exceptionThrown = true;
        }
        Assert.assertEquals(exceptionThrown, true);
    }

    //@Test
    public void testGetNode() throws Exception
    {
        String slashPath1 = "/" + ROOT_NODE + TestUtil.uniqueStringOnTime();
//        String slashPath1 = "/" + ROOT_NODE;
        ContainerNode cnode = new ContainerNode(new VOSURI(VOS.VOS_URI + slashPath1));

        Node nodeRtn = client.createNode(cnode);
        log.debug("Returned Node: " + nodeRtn);
        log.debug("XML of Returned Node: " + VOSClientUtil.xmlString(nodeRtn));

        Node nodeRtn2 = client.getNode(nodeRtn.getPath());
        log.debug("GetNode: " + nodeRtn2);
        log.debug("XML of GetNode: " + VOSClientUtil.xmlString(nodeRtn2));
        Assert.assertEquals(nodeRtn.getPath(), nodeRtn2.getPath());
    }


    //@Test
    public void testGetRootNode() throws Exception
    {
        String slashPath1 = "/" + ROOT_NODE ;
//        String slashPath1 = "/zhangsa/Jun15_09.25_0631";
        
        ContainerNode cnode = new ContainerNode(new VOSURI(VOS.VOS_URI + slashPath1));

        Node nodeRtn2 = client.getNode(cnode.getPath());
        log.debug("GetNode: " + nodeRtn2);
        log.debug("XML of GetNode: " + VOSClientUtil.xmlString(nodeRtn2));
        //Assert.assertEquals(nodeRtn.getPath(), nodeRtn2.getPath());
    }


    //@Test
    public void testCreateContainerNode() throws Exception
    {
        String slashPath1 = "/" + ROOT_NODE + TestUtil.uniqueStringOnTime();
        ContainerNode cnode = new ContainerNode(new VOSURI(VOS.VOS_URI + slashPath1));

        Node nodeRtn = client.createNode(cnode);
        log.debug("Returned Node: " + nodeRtn);
        log.debug("XML of Returned Node: " + VOSClientUtil.xmlString(nodeRtn));
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
        log.debug("XML of Returned Node: " + VOSClientUtil.xmlString(nodeRtn));
        ContainerNode cnode2 = (ContainerNode) nodeRtn;
        List<Node> nodes2 = cnode2.getNodes();
        Assert.assertEquals(nodes2.size(), 2);
    }

    //@Test
    public void testCreateDataNode() throws Exception
    {
        String slashPath1 = "/" + ROOT_NODE + TestUtil.uniqueStringOnTime();
        DataNode dnode = new DataNode(new VOSURI(VOS.VOS_URI + slashPath1));

        Node nodeRtn = client.createNode(dnode);
        log.debug("Returned Node: " + nodeRtn);
        log.debug("XML of Returned Node: " + VOSClientUtil.xmlString(nodeRtn));
        Assert.assertEquals("/" + nodeRtn.getPath(), slashPath1);
    }

    public ClientTransfer pushToVoSpace() throws Exception
    {
        File testFile = TestUtil.getTestFile();
        Assert.assertNotNull(testFile);
        log.debug(testFile.getAbsolutePath());
        log.debug(testFile.getCanonicalPath());

        String slashPath1 = "/" + ROOT_NODE + TestUtil.uniqueStringOnTime();
        DataNode dnode = new DataNode(new VOSURI(VOS.VOS_URI + slashPath1));
        dnode = (DataNode) client.createNode(dnode);
        DataView dview = new DataView(VOS.VIEW_DEFAULT, dnode);

        List<Protocol> protocols = new ArrayList<Protocol>();
        protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_PUT, ENDPOINT, null));

        Transfer transfer = new Transfer();
        transfer.setTarget(dnode);
        transfer.setView(dview);
        transfer.setProtocols(protocols);
        transfer.setDirection(Transfer.Direction.pushToVoSpace);

        ClientTransfer clientTransfer = (ClientTransfer) client.pushToVoSpace(transfer);
        log.debug(clientTransfer.toXmlString());
        
        clientTransfer.doUpload(testFile);
        Node node = clientTransfer.getTarget();
        Node nodeRtn = client.getNode(node.getPath());
        log.debug(VOSClientUtil.xmlString(nodeRtn));
        return clientTransfer;
    }

    @Test
    public void testPushToVoSpace() throws Exception
    {
        this.pushToVoSpace();
    }

    ////@Test
    public void testPullFromVoSpace() throws Exception
    {
        ClientTransfer txUpload = this.pushToVoSpace();
        
        List<Protocol> protocols = new ArrayList<Protocol>();
        protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_GET, ENDPOINT, null));
        
        Transfer txSent = new Transfer();
        txSent.setTarget(txUpload.getTarget());
        txSent.setDirection(Transfer.Direction.pullFromVoSpace);
        txSent.setView(txUpload.getView());
        txSent.setProtocols(protocols);
        
        ClientTransfer txRtn = (ClientTransfer) client.pullFromVoSpace(txSent);
        log.debug(txRtn.toXmlString());
        
        File file = new File("/tmp/" + TestUtil.uniqueStringOnTime());
        log.debug(file.getAbsolutePath());
        log.debug(file.getCanonicalPath());
        txRtn.doDownload(file);

        File origFile = TestUtil.getTestFile();
        Assert.assertNotNull(origFile);
        log.debug(origFile.getAbsolutePath());
        log.debug(origFile.getCanonicalPath());
        
        Assert.assertEquals(FileUtil.compare(origFile, file), true);
        // file.delete();
    }


    @Override
    public String toString()
    {
        return "VOSpaceClientTest [client=" + client + ", getClass()=" + getClass() + ", hashCode()=" + hashCode()
                + ", toString()=" + super.toString() + "]";
    }
}
