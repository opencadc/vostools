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

package ca.nrc.cadc.vos.server;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.db.ConnectionConfig;
import ca.nrc.cadc.db.DBConfig;
import ca.nrc.cadc.db.DBUtil;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import ca.nrc.cadc.util.HexUtil;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOS.NodeBusyState;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.server.NodeDAO.NodeSchema;
import java.net.URI;
import java.util.Date;
import javax.sql.DataSource;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;


/**
 * Test class for the NodeDAO class in cadcVOS.
 * 
 * @author pdowler
 *
 */
public class NodeDAOTest
{   
    private static Logger log = Logger.getLogger(NodeDAOTest.class);

    static final String SERVER = "VOSPACE_WS_TEST";
    static final String DATABASE = "cadctest";

    static final String VOS_AUTHORITY = "cadc.nrc.ca!vospace";
    static final String ROOT_CONTAINER = "CADCRegtest1";
    static final String NODE_OWNER = "CN=CADC Regtest1 10577,OU=CADC,O=HIA";

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc", Level.INFO);
    }
    
    DataSource dataSource;
    NodeDAO nodeDAO;
    String runID;

    public NodeDAOTest() throws Exception
    {
        this.runID = "test"+ new Date().getTime();
        log.debug("runID = " + runID);

        try
        {
            DBConfig dbConfig = new DBConfig();
            ConnectionConfig connConfig = dbConfig.getConnectionConfig(SERVER, DATABASE);
            this.dataSource = DBUtil.getDataSource(connConfig);
            NodeSchema ns = new NodeSchema("Node", "NodeProperty");
            this.nodeDAO = new NodeDAO(dataSource, ns, VOS_AUTHORITY);

            ContainerNode root = (ContainerNode) nodeDAO.getPath(ROOT_CONTAINER);
            log.debug("found base node: " + root);
            if (root == null)
            {
                VOSURI vos = new VOSURI(new URI("vos", VOS_AUTHORITY, "/"+ROOT_CONTAINER, null, null));
                root = new ContainerNode(vos);
                root.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CREATOR, NODE_OWNER));
                root = (ContainerNode) nodeDAO.put(root);
                log.debug("created base node: " + root);
            }
        }
        catch(Exception ex)
        {
            // make sure it gets fully dumped
            ex.printStackTrace();
            throw ex;
        }
    }

    private String getNodeName(String s)
    {
        return runID + s;
    }

    private void assertRecursiveDelete()
        throws Exception
    {
        // ensure deleting the roots deleted all children
        PreparedStatement prepStmt = dataSource.getConnection().prepareStatement(
            "select count(*) from Node where name like ?");
        prepStmt.setString(1, runID + "%");
        ResultSet rs = prepStmt.executeQuery();
        rs.next();
        int remainingNodes = rs.getInt(1);
        Assert.assertEquals("recursive delete", 0, remainingNodes);
        prepStmt.close();
    }

    @Test
    public void testGetRootNode()
    {
        log.debug("testGetRootNode - START");
        try
        {
            Node root = nodeDAO.getPath(ROOT_CONTAINER);
            Assert.assertNotNull(root);
            Assert.assertEquals(ContainerNode.class, root.getClass());
            Assert.assertEquals(ROOT_CONTAINER, root.getName());
            Assert.assertEquals(NODE_OWNER, root.getOwner());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
        finally
        {
            log.debug("testGetRootNode - DONE");
        }
    }

    @Test
    public void testPutGetDeleteNodes()
    {
        log.debug("testPutGetDeleteNodes - START");
        try
        {
            DataNode dataNode = null;
            ContainerNode containerNode = null;
            Node putNode = null;

            ContainerNode rootContainer = (ContainerNode) nodeDAO.getPath(ROOT_CONTAINER);
            log.debug("ROOT: " + rootContainer);
            Assert.assertNotNull(rootContainer);
            String basePath = "/" + ROOT_CONTAINER + "/";

            // /a
            String nodePath1 = basePath + getNodeName("a");
            dataNode = getCommonDataNode(nodePath1, getCommonProperties());
            dataNode.setParent(rootContainer); // back link to persistent parent required
            putNode = nodeDAO.put(dataNode);
            Node nodeA = nodeDAO.getPath(nodePath1);
            nodeDAO.getProperties(nodeA);
            log.debug("PutNode: " + putNode);
            log.debug("GetNode: " + nodeA);
            compareNodes("assert1", putNode, nodeA);
            compareProperties("assert2a", dataNode.getProperties(), putNode.getProperties());
            compareProperties("assert2b", putNode.getProperties(), nodeA.getProperties());
            // test the timestamp on this one node
            String dateStr = nodeA.getPropertyValue(VOS.PROPERTY_URI_DATE);
            Date d = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC).parse(dateStr);
            Date now = new Date();
            long delta = now.getTime() - d.getTime();
            Assert.assertTrue("assert2c: timestamp delta", (delta < 1000L));
            
            // /b
            String nodePath2 = basePath + getNodeName("b");
            containerNode = getCommonContainerNode(nodePath2, getCommonProperties());
            containerNode.setParent(rootContainer); // back link to persistent parent required
            putNode = nodeDAO.put(containerNode);
            ContainerNode nodeB = (ContainerNode) nodeDAO.getPath(nodePath2);
            nodeDAO.getProperties(nodeB);
            log.debug("PutNode: " + putNode);
            log.debug("GetNode: " + nodeB);
            compareNodes("assert3", putNode, nodeB);
            compareProperties("assert4", putNode.getProperties(), nodeB.getProperties());

            // /c
            String nodePath3 = basePath + getNodeName("c");
            containerNode = getCommonContainerNode(nodePath3, getCommonProperties());
            containerNode.setParent(rootContainer); // back link to persistent parent required
            putNode = nodeDAO.put(containerNode);
            ContainerNode nodeC = (ContainerNode) nodeDAO.getPath(nodePath3);
            nodeDAO.getProperties(nodeC);
            compareNodes("assert5", putNode, nodeC);
            compareProperties("assert6", putNode.getProperties(), nodeC.getProperties());

            // /b/d
            String nodePath4 = basePath + getNodeName("b") + "/" + getNodeName("d");
            dataNode = getCommonDataNode(nodePath4, getCommonProperties());
            dataNode.setParent(nodeB); // back link to persistent parent required
            putNode = nodeDAO.put(dataNode);
            Node nodeD = nodeDAO.getPath(nodePath4);
            nodeDAO.getProperties(nodeD);
            compareNodes("assert7", putNode, nodeD);
            compareProperties("assert8", putNode.getProperties(), nodeD.getProperties());

            // /c/e
            String nodePath5 = basePath + getNodeName("c") + "/" + getNodeName("e");
            containerNode = getCommonContainerNode(nodePath5, getCommonProperties());
            containerNode.setParent(nodeC); // back link to persistent parent required
            putNode = nodeDAO.put(containerNode);
            ContainerNode nodeE = (ContainerNode) nodeDAO.getPath(nodePath5);
            nodeDAO.getProperties(nodeE);
            compareNodes("assert9", putNode, nodeE);
            compareProperties("assert10", putNode.getProperties(), nodeE.getProperties());

            // /c/e/f
            String nodePath6 = basePath + getNodeName("c") + "/" + getNodeName("e") + "/" + getNodeName("f");
            dataNode = getCommonDataNode(nodePath6, getCommonProperties());
            dataNode.setParent(nodeE); // back link to persistent parent required
            putNode = nodeDAO.put(dataNode);
            Node nodeF = nodeDAO.getPath(nodePath6);
            nodeDAO.getProperties(nodeF);
            compareNodes("assert11", putNode, nodeF);
            compareProperties("assert12", putNode.getProperties(), nodeF.getProperties());

            log.debug("testPutGetDeleteNodes - CLEANUP");

            // delete the three roots
            nodeDAO.delete(nodeA);
            nodeDAO.delete(nodeB);
            nodeDAO.delete(nodeC);

            assertRecursiveDelete();
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
        finally
        {
            log.debug("testPutGetDeleteNodes - DONE");
        }
    }

    @Test
    public void testGetChildren()
    {
        log.debug("testGetChildren - START");
        try
        {
            ContainerNode rootContainer = (ContainerNode) nodeDAO.getPath(ROOT_CONTAINER);
            log.debug("ROOT: " + rootContainer);
            Assert.assertNotNull(rootContainer);

            String basePath = "/" + ROOT_CONTAINER + "/";

            // create a container
            String path = basePath + getNodeName("dir");
            ContainerNode testNode = getCommonContainerNode(path);
            testNode.setParent(rootContainer);
            ContainerNode cn = (ContainerNode) nodeDAO.put(testNode);
            
            ContainerNode cur = (ContainerNode) nodeDAO.getPath(path);
            Assert.assertNotNull(cur);

            DataNode n1 = getCommonDataNode(path + "/one");
            ContainerNode n2 = getCommonContainerNode(path + "/two");
            DataNode n3 = getCommonDataNode(path + "/three");
            ContainerNode n4 = getCommonContainerNode(path + "/four");

            n1.setParent(cn);
            n2.setParent(cn);
            n3.setParent(cn);
            n4.setParent(cn);
            nodeDAO.put(n1);
            nodeDAO.put(n2);
            nodeDAO.put(n3);
            nodeDAO.put(n4);

            // get a vanilla container with no children
            cur = (ContainerNode) nodeDAO.getPath(path);
            Assert.assertNotNull(cur);
            Assert.assertEquals("empty child list", 0, cur.getNodes().size());
            // load the child nodes
            nodeDAO.getChildren(cur);
            Assert.assertEquals("full child list", 4, cur.getNodes().size());
            // rely on implementation of Node.equals
            Assert.assertTrue(cur.getNodes().contains(n1));
            Assert.assertTrue(cur.getNodes().contains(n2));
            Assert.assertTrue(cur.getNodes().contains(n3));
            Assert.assertTrue(cur.getNodes().contains(n4));

            nodeDAO.delete(cur);
            assertRecursiveDelete();
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
        finally
        {
            log.debug("testGetChildren - DONE");
        }
    }

    @Test
    public void testMarkForDeletion()
    {
        log.debug("testMarkForDeletion - START");
        try
        {
            ContainerNode rootContainer = (ContainerNode) nodeDAO.getPath(ROOT_CONTAINER);
            log.debug("ROOT: " + rootContainer);
            Assert.assertNotNull(rootContainer);

            String basePath = "/" + ROOT_CONTAINER + "/";

            // create a container
            String path = basePath + getNodeName("dir");
            ContainerNode testNode = getCommonContainerNode(path);
            testNode.setParent(rootContainer);
            ContainerNode cn = (ContainerNode) nodeDAO.put(testNode);

            ContainerNode cur = (ContainerNode) nodeDAO.getPath(path);
            Assert.assertNotNull(cur);

            DataNode n1 = getCommonDataNode(path + "/one");
            ContainerNode n2 = getCommonContainerNode(path + "/two");
            DataNode n3 = getCommonDataNode(path + "/three");
            ContainerNode n4 = getCommonContainerNode(path + "/four");

            n1.setParent(cn);
            n2.setParent(cn);
            n3.setParent(cn);
            n4.setParent(cn);
            nodeDAO.put(n1);
            nodeDAO.put(n2);
            nodeDAO.put(n3);
            nodeDAO.put(n4);

            // get a vanilla container with no children
            cur = (ContainerNode) nodeDAO.getPath(path);
            Assert.assertNotNull(cur);
            Assert.assertEquals("empty child list", 0, cur.getNodes().size());
            nodeDAO.getChildren(cur);
            Assert.assertEquals("full child list", 4, cur.getNodes().size());

            nodeDAO.markForDeletion(cur);
            Node gone = nodeDAO.getPath(path);
            Assert.assertNull(gone);

            nodeDAO.delete(cur);
            assertRecursiveDelete();
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
        finally
        {
            log.debug("testMarkForDeletion - DONE");
        }
    }

    @Test
    public void testGetChild()
    {
        log.debug("testGetChild - START");
        try
        {
            ContainerNode rootContainer = (ContainerNode) nodeDAO.getPath(ROOT_CONTAINER);
            log.debug("ROOT: " + rootContainer);
            Assert.assertNotNull(rootContainer);

            String basePath = "/" + ROOT_CONTAINER + "/";

            // create a container
            String path = basePath + getNodeName("dir");
            ContainerNode testNode = getCommonContainerNode(path);
            testNode.setParent(rootContainer);
            ContainerNode cn = (ContainerNode) nodeDAO.put(testNode);

            ContainerNode cur = (ContainerNode) nodeDAO.getPath(path);
            Assert.assertNotNull(cur);

            DataNode n1 = getCommonDataNode(path + "/one");
            ContainerNode n2 = getCommonContainerNode(path + "/two");
            DataNode n3 = getCommonDataNode(path + "/three");
            ContainerNode n4 = getCommonContainerNode(path + "/four");

            n1.setParent(cn);
            n2.setParent(cn);
            n3.setParent(cn);
            n4.setParent(cn);
            nodeDAO.put(n1);
            nodeDAO.put(n2);
            nodeDAO.put(n3);
            nodeDAO.put(n4);

            // get a vanilla container with no children
            cur = (ContainerNode) nodeDAO.getPath(path);
            Assert.assertNotNull(cur);
            Assert.assertEquals("empty child list", 0, cur.getNodes().size());
            // load the child nodes one at a time
            nodeDAO.getChild(cur, n1.getName());
            Assert.assertEquals("contains 1", 1, cur.getNodes().size());
            Assert.assertTrue("contains n1", cur.getNodes().contains(n1));
            
             nodeDAO.getChild(cur, n4.getName());
            Assert.assertEquals("contains 2", 2, cur.getNodes().size());
            Assert.assertTrue("contains n4", cur.getNodes().contains(n4));

             nodeDAO.getChild(cur, n2.getName());
            Assert.assertEquals("contains 3", 3, cur.getNodes().size());
            Assert.assertTrue("contains n2", cur.getNodes().contains(n2));

             nodeDAO.getChild(cur, n3.getName());
            Assert.assertEquals("contains 4", 4, cur.getNodes().size());
            Assert.assertTrue("contains n3", cur.getNodes().contains(n3));

            nodeDAO.delete(cur);
            assertRecursiveDelete();
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
        finally
        {
            log.debug("testGetChild - DONE");
        }
    }


    @Test
    public void testUpdateProperties()
    {
        log.debug("testUpdateProperties - START");
        try
        {
            ContainerNode rootContainer = (ContainerNode) nodeDAO.getPath(ROOT_CONTAINER);
            log.debug("ROOT: " + rootContainer);
            Assert.assertNotNull(rootContainer);

            String basePath = "/" + ROOT_CONTAINER + "/";

            // Create a node with properties
            String path = basePath + getNodeName("g");
            DataNode testNode = getCommonDataNode(path);
            testNode.getProperties().add(new NodeProperty("uri1", "value1"));
            testNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(1024).toString()));
            testNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_TYPE, "text/plain"));
            testNode.setParent(rootContainer);

            // put + get + compare
            nodeDAO.put(testNode);
            DataNode nodeFromGet = (DataNode) nodeDAO.getPath(path);
            Assert.assertNotNull(nodeFromGet);
            nodeDAO.getProperties(nodeFromGet);
            compareProperties("assert1", testNode.getProperties(), nodeFromGet.getProperties());

            // add
            List<NodeProperty> props = new ArrayList<NodeProperty>();
            props.add(new NodeProperty("uri2", "value1"));
            props.add(new NodeProperty(VOS.PROPERTY_URI_CONTENTENCODING, "gzip"));
            props.add(new NodeProperty(VOS.PROPERTY_URI_CONTENTMD5, HexUtil.toHex(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16})));
            testNode.getProperties().addAll(props); // for comparison below

            DataNode nodeFromUpdate1 = (DataNode) nodeDAO.updateProperties(nodeFromGet, props);
            Assert.assertNotNull(nodeFromUpdate1);
            compareProperties("assert2", testNode.getProperties(), nodeFromUpdate1.getProperties());

            DataNode reGet1 = (DataNode) nodeDAO.getPath(path);
            nodeDAO.getProperties(reGet1);
            Assert.assertNotNull(reGet1);
            compareProperties("assert3", testNode.getProperties(), reGet1.getProperties());

            // replace values
            testNode.getProperties().remove(new NodeProperty("uri1", null));
            testNode.getProperties().add(new NodeProperty("uri1", "value2"));
            testNode.getProperties().remove(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, null));
            testNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(2048).toString()));
            testNode.getProperties().remove(new NodeProperty(VOS.PROPERTY_URI_TYPE, null));
            testNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_TYPE, "application/pdf"));

            DataNode nodeFromUpdate2 = (DataNode) nodeDAO.updateProperties(nodeFromUpdate1, testNode.getProperties());
            Assert.assertNotNull(nodeFromUpdate2);
            compareProperties("assert4", testNode.getProperties(), nodeFromUpdate2.getProperties());

            DataNode reGet2 = (DataNode) nodeDAO.getPath(path);
            nodeDAO.getProperties(reGet2);
            Assert.assertNotNull(reGet2);
            compareProperties("assert5", testNode.getProperties(), nodeFromUpdate2.getProperties());


            // remove
            props = new ArrayList<NodeProperty>();
            NodeProperty newURI2 = new NodeProperty("uri2", "value1");
            newURI2.setMarkedForDeletion(true);
            props.add(newURI2);
            NodeProperty newEncoding = new NodeProperty(VOS.PROPERTY_URI_CONTENTENCODING, "gzip");
            newEncoding.setMarkedForDeletion(true);
            props.add(newEncoding);
            NodeProperty newMD5 = new NodeProperty(VOS.PROPERTY_URI_CONTENTMD5, HexUtil.toHex(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}));
            newMD5.setMarkedForDeletion(true);
            props.add(newMD5);

            testNode.getProperties().removeAll(props); // remove from client side

            DataNode nodeFromUpdate3 = (DataNode) nodeDAO.updateProperties(nodeFromUpdate2, props); // remove from server
            Assert.assertNotNull(nodeFromUpdate3);
            compareProperties("assert6", testNode.getProperties(), nodeFromUpdate3.getProperties());

            DataNode reGet3 = (DataNode) nodeDAO.getPath(path);
            nodeDAO.getProperties(reGet3);
            Assert.assertNotNull(reGet3);
            compareProperties("assert7", testNode.getProperties(), reGet3.getProperties());

            nodeDAO.delete(nodeFromGet);
            assertRecursiveDelete();
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
        finally
        {
            log.debug("testUpdateProperties - DONE");
        }
    }

    @Test
    public void testUpdateContentLength()
    {
        log.debug("testUpdateContentLength - START");
        try
        {
            ContainerNode rootContainer = (ContainerNode) nodeDAO.getPath(ROOT_CONTAINER);
            log.debug("ROOT: " + rootContainer);
            Assert.assertNotNull(rootContainer);

            String basePath = "/" + ROOT_CONTAINER + "/";

            // Create a node with properties
            String path = basePath + getNodeName("cl-test");
            DataNode testNode = getCommonDataNode(path);
            testNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, Long.toString(1024)));
            testNode.setParent(rootContainer);

            // put + get + compare
            nodeDAO.put(testNode);
            Node persistNode = nodeDAO.getPath(path);
            Assert.assertNotNull(persistNode);
            //nodeDAO.getProperties(nodeFromGet1); // not needed for this impl
            Assert.assertEquals("assert1024", 1024, getContentLength(persistNode));

            // update the quick way
            nodeDAO.updateContentLength(persistNode, 1024);

            Node nodeFromGet2 = nodeDAO.getPath(path);
            Assert.assertNotNull(nodeFromGet2);
            Assert.assertEquals("assert2048", 2048, getContentLength(nodeFromGet2));

            nodeDAO.delete(persistNode);
            assertRecursiveDelete();
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
        finally
        {
            log.debug("testUpdateContentLength - DONE");
        }
    }

    @Test
    public void testUpdateBusyState()
    {
        log.debug("testUpdateBusyState - START");
        try
        {
            ContainerNode rootContainer = (ContainerNode) nodeDAO.getPath(ROOT_CONTAINER);
            log.debug("ROOT: " + rootContainer);
            Assert.assertNotNull(rootContainer);

            String basePath = "/" + ROOT_CONTAINER + "/";

            // Create a node with properties
            String path = basePath + getNodeName("cl-test");
            DataNode testNode = getCommonDataNode(path);
            testNode.setParent(rootContainer);

            // put and check default
            nodeDAO.put(testNode);
            DataNode persistNode = (DataNode) nodeDAO.getPath(path);
            Assert.assertNotNull(persistNode);
            Assert.assertEquals("assert not busy", NodeBusyState.notBusy, persistNode.getBusy());

            // -> read
            nodeDAO.setBusyState(persistNode, NodeBusyState.busyWithRead);
            persistNode = (DataNode) nodeDAO.getPath(path);
            Assert.assertNotNull(persistNode);
            Assert.assertEquals("assert not busy", NodeBusyState.busyWithRead, persistNode.getBusy());
            
            // -> write
            nodeDAO.setBusyState(persistNode, NodeBusyState.busyWithWrite);
            persistNode = (DataNode) nodeDAO.getPath(path);
            Assert.assertNotNull(persistNode);
            Assert.assertEquals("assert not busy", NodeBusyState.busyWithWrite, persistNode.getBusy());

            // -> not busy
            nodeDAO.setBusyState(persistNode, NodeBusyState.notBusy);
            persistNode = (DataNode) nodeDAO.getPath(path);
            Assert.assertNotNull(persistNode);
            Assert.assertEquals("assert not busy", NodeBusyState.notBusy, persistNode.getBusy());

            nodeDAO.delete(persistNode);
            assertRecursiveDelete();
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
        finally
        {
            log.debug("testUpdateBusyState - DONE");
        }
    }

    private long getContentLength(Node node)
    {
        int index = node.getProperties().indexOf(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, null));
        return Long.parseLong(((NodeProperty) node.getProperties().get(index)).getPropertyValue());
    }
    
    private DataNode getCommonDataNode(String path, List<NodeProperty> properties) throws Exception
    {
        DataNode dataNode = getCommonDataNode(path);
        dataNode.getProperties().addAll(properties);
        return dataNode;
    }
    
    private DataNode getCommonDataNode(String path) throws Exception
    {
        VOSURI vosuri = new VOSURI(new URI("vos", VOS_AUTHORITY, path, null, null));
        DataNode dataNode = new DataNode(vosuri);
        dataNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CREATOR, NODE_OWNER));
        return dataNode;
    }
    
    private ContainerNode getCommonContainerNode(String path, List<NodeProperty> properties) throws Exception
    {
        ContainerNode containerNode = getCommonContainerNode(path);
        containerNode.getProperties().addAll(properties);
        return containerNode;
    }
    
    private ContainerNode getCommonContainerNode(String path) throws Exception
    {
        VOSURI vosuri = new VOSURI(new URI("vos", VOS_AUTHORITY, path, null, null));
        ContainerNode containerNode = new ContainerNode(vosuri);
        containerNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CREATOR, NODE_OWNER));
        return containerNode;
    }

    private List<NodeProperty> getCommonProperties()
    {
        List<NodeProperty> properties = new ArrayList<NodeProperty>();
        NodeProperty prop1 = new NodeProperty("uri1", "value1");
        NodeProperty prop2 = new NodeProperty("uri2", "value2");
        NodeProperty prop3 = new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(1024).toString());
        NodeProperty prop4 = new NodeProperty(VOS.PROPERTY_URI_TYPE, "text/plain");
        NodeProperty prop5 = new NodeProperty(VOS.PROPERTY_URI_CONTENTENCODING, "gzip");
        NodeProperty prop6 = new NodeProperty(VOS.PROPERTY_URI_CONTENTMD5, HexUtil.toHex(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}));

        properties.add(prop1);
        properties.add(prop2);
        properties.add(prop3);
        properties.add(prop4);
        properties.add(prop5);
        properties.add(prop6);

        return properties;
    }

    private void compareNodes(String assertName, Node a, Node b)
    {
        Assert.assertNotNull(b);
        Assert.assertEquals(assertName+  "URI", a.getUri(), b.getUri());
        Assert.assertEquals(assertName + "type", a.getClass().getName(), b.getClass().getName());
        Assert.assertEquals(assertName + "name", a.getName(), b.getName());
        Assert.assertEquals(assertName + "owner", a.getOwner(), b.getOwner());
    }

    private void compareProperties(String assertName, List<NodeProperty> expected, List<NodeProperty> actual)
    {
        for (NodeProperty np : expected)
            log.debug(assertName+".expected: " + np);
        for (NodeProperty np : actual)
            log.debug(assertName+".actual: " + np);
        boolean match = true;
        for (NodeProperty e : expected)
        {
            if (!actual.contains(e))
            {
                Assert.fail(assertName + ": missing " + e);
            }
        }
    }
    
}

