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
import java.net.InetAddress;
import java.net.URI;
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

import ca.nrc.cadc.auth.BasicX509TrustManager;
import ca.nrc.cadc.auth.SSLUtil;
import ca.nrc.cadc.util.FileUtil;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Direction;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.Protocol;
import ca.nrc.cadc.vos.TestUtil;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.View;

/**
 * Base VOSpaceClient test code. This test code requires a running VOSpace service
 * and (probably) valid X509 proxy certficates. TODO: provide this as an integration
 * test  or rewrite it using a mock http layer (sounds hard).
 * 
 * @author zhangsa
 */
//@Ignore("Broken - Please fix soon.\n" +
//        "jenkinsd 2011.01.17")
public class VOSpaceClientTest
{
    private static Logger log = Logger.getLogger(VOSpaceClientTest.class);
    private static String ROOT_NODE;
    private static String VOS_URI =  "vos://cadc.nrc.ca!vospace";
    private static String TEST_CERT ="proxy.crt";
    private static String TEST_KEY = "proxy.key";

    String endpoint;
    VOSpaceClient client;

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc.vos.client", Level.INFO);
        System.setProperty(BasicX509TrustManager.class.getName() + ".trust",
                           "true");

        File cert = FileUtil.getFileFromResource(TEST_CERT,
                                                 VOSpaceClientTest.class);
        File key = FileUtil.getFileFromResource(TEST_KEY,
                                                VOSpaceClientTest.class);
        SSLUtil.initSSL(cert, key);

        ROOT_NODE = System.getProperty("user.name") + "/";
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
        InetAddress localhost = InetAddress.getLocalHost();
        String hostname = localhost.getCanonicalHostName();
        log.debug("hostname=" + hostname);
        endpoint = "https://" + hostname;
        log.debug("endpoint=" + endpoint);
        client = new VOSpaceClient(endpoint + "/vospace");
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
        ContainerNode cnode = new ContainerNode(new VOSURI(VOS_URI + slashPath1));

        Node nodeRtn = client.createNode(cnode);
        log.debug("Returned Node: " + nodeRtn);
        log.debug("XML of Returned Node: " + VOSClientUtil.xmlString(nodeRtn));

        Node nodeRtn2 = client.getNode(nodeRtn.getUri().getPath());
        log.debug("GetNode: " + nodeRtn2);
        log.debug("XML of GetNode: " + VOSClientUtil.xmlString(nodeRtn2));
        Assert.assertEquals(nodeRtn.getUri().getPath(), nodeRtn2.getUri().getPath());

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
        ContainerNode cnode = new ContainerNode(new VOSURI(VOS_URI + slashPath1));

        Node nodeRtn = client.createNode(cnode);
        log.debug("Returned Node: " + nodeRtn);
        log.debug("XML of Returned Node: " + VOSClientUtil.xmlString(nodeRtn));

        log.debug("getPath(): " + nodeRtn.getUri().getPath());
        client.deleteNode(nodeRtn.getUri().getPath());

        boolean exceptionThrown = false;
        try
        {
            client.getNode(nodeRtn.getUri().getPath());
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
        ContainerNode cnode = new ContainerNode(new VOSURI(VOS_URI + slashPath1));

        Node nodeRtn = client.createNode(cnode);
        log.debug("Returned Node: " + nodeRtn);
        log.debug("XML of Returned Node: " + VOSClientUtil.xmlString(nodeRtn));

        Node nodeRtn2 = client.getNode(nodeRtn.getUri().getPath());
        log.debug("GetNode: " + nodeRtn2);
        log.debug("XML of GetNode: " + VOSClientUtil.xmlString(nodeRtn2));
        Assert.assertEquals(nodeRtn.getUri().getPath(), nodeRtn2.getUri().getPath());
    }


    //@Test
    public void testGetRootNode() throws Exception
    {
        String slashPath1 = "/" + ROOT_NODE ;
        ContainerNode cnode = new ContainerNode(new VOSURI(VOS_URI + slashPath1));

        Node nodeRtn2 = client.getNode(cnode.getUri().getPath());
        log.debug("GetNode: " + nodeRtn2);
        log.debug("XML of GetNode: " + VOSClientUtil.xmlString(nodeRtn2));
        //Assert.assertEquals(nodeRtn.getUri().getPath(), nodeRtn2.getUri().getPath());
    }


    @Test
    public void testCreateContainerNode() throws Exception
    {
        final String slashPath1 = "/" + ROOT_NODE
                                  + TestUtil.uniqueStringOnTime();
        final ContainerNode cnode =
                new ContainerNode(new VOSURI(VOS_URI + slashPath1));

        final Node nodeRtn = client.createNode(cnode);

        log.debug("Returned Node: " + nodeRtn);
        log.debug("XML of Returned Node: " + VOSClientUtil.xmlString(nodeRtn));
        Assert.assertEquals(nodeRtn.getUri().getPath(), slashPath1);
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
        nodes.add(new DataNode(new VOSURI(VOS_URI + slashPath1a)));
        nodes.add(new DataNode(new VOSURI(VOS_URI + slashPath1b)));

        // ContainerNode
        ContainerNode cnode;
        cnode = new ContainerNode(new VOSURI(VOS_URI + slashPath1));
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
        DataNode dnode = new DataNode(new VOSURI(VOS_URI + slashPath1));

        Node nodeRtn = client.createNode(dnode);
        log.debug("Returned Node: " + nodeRtn);
        log.debug("XML of Returned Node: " + VOSClientUtil.xmlString(nodeRtn));
        Assert.assertEquals(nodeRtn.getUri().getPath(), slashPath1);
    }

    //@Test
    public void testCreateDataNodeWithProperties() throws Exception
    {
//        String slashPath1 = "/" + ROOT_NODE + "nodeWithPropertiesA";
        String slashPath1 = "/" + ROOT_NODE + TestUtil.uniqueStringOnTime();
        DataNode dnode = new DataNode(new VOSURI(VOS_URI + slashPath1));

        Node nodeRtn = client.createNode(dnode);
        Node nodeRtn2 = client.getNode(nodeRtn.getUri().getPath());
        Assert.assertEquals(nodeRtn.getUri().getPath(), nodeRtn2.getUri().getPath());

        List<NodeProperty> properties = new ArrayList<NodeProperty>();
        properties.add(new NodeProperty(VOS.PROPERTY_URI_TITLE, "sz_title")); 
        properties.add(new NodeProperty(VOS.PROPERTY_URI_CREATOR, "sz_creator"));
        properties.add(new NodeProperty(VOS.PROPERTY_URI_SUBJECT, "sz_subject"));
        properties.add(new NodeProperty(VOS.PROPERTY_URI_DESCRIPTION, "sz_description"));
        properties.add(new NodeProperty(VOS.PROPERTY_URI_PUBLISHER, "sz_publisher"));
        properties.add(new NodeProperty(VOS.PROPERTY_URI_CONTRIBUTOR, "sz_contributor"));
        properties.add(new NodeProperty(VOS.PROPERTY_URI_DATE, "sz_date"));
        properties.add(new NodeProperty(VOS.PROPERTY_URI_TYPE, "sz_type"));
        properties.add(new NodeProperty(VOS.PROPERTY_URI_FORMAT, "sz_format"));
        properties.add(new NodeProperty(VOS.PROPERTY_URI_IDENTIFIER, "sz_identifier"));
        properties.add(new NodeProperty(VOS.PROPERTY_URI_SOURCE, "sz_source")); 
        properties.add(new NodeProperty(VOS.PROPERTY_URI_LANGUAGE, "sz_language")); 
        properties.add(new NodeProperty(VOS.PROPERTY_URI_RELATION, "sz_relation")); 
        properties.add(new NodeProperty(VOS.PROPERTY_URI_COVERAGE, "sz_coverage"));
        properties.add(new NodeProperty(VOS.PROPERTY_URI_RIGHTS, "sz_rights")); 
        properties.add(new NodeProperty(VOS.PROPERTY_URI_AVAILABLESPACE, "sz_availableSpace"));

        nodeRtn2.setProperties(properties);

        Node nodeRtn3 = client.setNode(nodeRtn2);
        log.debug("After setNode: " + nodeRtn3);
        for (NodeProperty np : nodeRtn3.getProperties())
        {
            Assert.assertEquals(np.getPropertyValue().startsWith("sz"), true);
        }
    }

    //@Test
    public void testPushPull() throws Exception
    {
        File testFile = TestUtil.getTestFile();
        Assert.assertNotNull(testFile);
        log.debug("testfile exists? " + testFile.exists() );
        log.debug("testfile absolutePath: " + testFile.getAbsolutePath());
        log.debug("testfile Canonical path: " + testFile.getCanonicalPath());

        String slashPath1 = "/" + ROOT_NODE + TestUtil.uniqueStringOnTime();
        VOSURI vosURI = new VOSURI(VOS_URI + slashPath1);
        View dview = new View(new URI(VOS.VIEW_DEFAULT));

        List<Protocol> protocols = new ArrayList<Protocol>();
        protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_PUT));

        // upload
        Transfer transfer = new Transfer(vosURI, Direction.pushToVoSpace, dview, protocols);
        ClientTransfer clientTransfer = client.createTransfer(transfer);
        clientTransfer.setFile(testFile);
        clientTransfer.run();
        Assert.assertEquals("final upload phase", ExecutionPhase.COMPLETED, clientTransfer.getPhase());

        File file = new File("/tmp/" + TestUtil.uniqueStringOnTime());
        log.debug(file.getAbsolutePath());
        log.debug(file.getCanonicalPath());

        // download
        Transfer trans2 = new Transfer(vosURI, Direction.pullFromVoSpace, dview, protocols);
        ClientTransfer txRtn = client.createTransfer(trans2);
        txRtn.setFile(file);
        txRtn.run();
        Assert.assertEquals("final download phase", ExecutionPhase.COMPLETED, txRtn.getPhase());

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
