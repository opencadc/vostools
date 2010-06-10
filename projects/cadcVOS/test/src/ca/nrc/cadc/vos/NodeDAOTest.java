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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * Abstract class encompassing the logic behind running tests on the
 * NodeDAO class.  Subclasses must provide dataSource and nodeAuthorizer
 * implementations.
 * 
 * @author majorb
 *
 */
public abstract class NodeDAOTest
{
    
    private static Logger log = Logger.getLogger(NodeDAOTest.class);
    
    private NodeDAO nodeDAO;
    private String runId;
    private Connection connection;
    List<NodeProperty> propertyIgnoreList;
    
    @Before
    public void before() throws Exception
    {
        DataSource dataSource = getDataSource();
        nodeDAO = getNodeDAO(dataSource);
        connection = dataSource.getConnection();
        runId = NodeDAOTest.class.getName() + System.currentTimeMillis();
        
        propertyIgnoreList = new ArrayList<NodeProperty>();
        propertyIgnoreList.add(new NodeProperty(VOS.PROPERTY_URI_DATE, null));
    }
    
    @After
    public void after() throws Exception
    {
        PreparedStatement prepStmt = connection.prepareStatement(
                "delete from " + nodeDAO.getNodePropertyTableName()
                + " where nodeID in (select nodeID from "
                +  nodeDAO.getNodeTableName()
                + " where name like ?)");
        prepStmt.setString(1, runId + "%");
        prepStmt.executeUpdate();
        
        prepStmt = connection.prepareStatement(
            "delete from " + nodeDAO.getNodeTableName() + " where name like ?");
        prepStmt.setString(1, runId + "%");
        prepStmt.executeUpdate();
        
        prepStmt.close();
        
        connection.close();
        
    }
    
    public abstract DataSource getDataSource();
    
    public abstract NodeDAO getNodeDAO(DataSource dataSource);
    
    public abstract String getVOSURIPrefix();
    
    public abstract String getRootContainerName();
    
    public abstract String getNodeOwner();
    
    private String getNodeName(String identifier)
    {
        return runId + identifier;
    }

    @Test
    public void testPutGetDeleteNodes() throws Exception
    {
        DataNode dataNode = null;
        ContainerNode containerNode = null;
        Node putNode = null;
        
        ContainerNode rootContainer = (ContainerNode) nodeDAO.getFromParent(getRootContainerName(), null);
        
        // /a
        String nodePath1 = getRootContainerName() + "/" + getNodeName("a");
        dataNode = getCommonDataNode(nodePath1, getCommonProperties());
        putNode = nodeDAO.putInContainer(dataNode, rootContainer);
        Node nodeA = nodeDAO.getFromParent(putNode.getName(), rootContainer);
        System.out.println("PutNode: " + putNode);
        System.out.println("GetNode: " + nodeA);
        assertEquals("assert1", putNode, nodeA);
        compareProperties("assert2", putNode.getProperties(), nodeA.getProperties());
        
        // /b
        String nodePath2 = getRootContainerName() + "/" + getNodeName("b");
        containerNode = getCommonContainerNode(nodePath2, getCommonProperties());
        putNode = nodeDAO.putInContainer(containerNode, rootContainer);
        Node nodeB = nodeDAO.getFromParent(putNode.getName(), rootContainer);
        System.out.println("PutNode: " + putNode);
        System.out.println("GetNode: " + nodeB);
        assertEquals("assert3", putNode, nodeB);
        compareProperties("assert4", putNode.getProperties(), nodeB.getProperties());
        
        // /c
        String nodePath3 = getRootContainerName() + "/" + getNodeName("c");
        containerNode = getCommonContainerNode(nodePath3, getCommonProperties());
        putNode = nodeDAO.putInContainer(containerNode, rootContainer);
        Node nodeC = nodeDAO.getFromParent(putNode.getName(), rootContainer);
        assertEquals("assert5", putNode, nodeC);
        compareProperties("assert6", putNode.getProperties(), nodeC.getProperties());
        
        // /b/d
        String nodePath4 = getRootContainerName() + "/" + getNodeName("b") + "/" + getNodeName("d");
        dataNode = getCommonDataNode(nodePath4, getCommonProperties());
        putNode = nodeDAO.putInContainer(dataNode, (ContainerNode) nodeB);
        Node nodeD = nodeDAO.getFromParent(putNode.getName(), putNode.getParent());
        assertEquals("assert7", putNode, nodeD);
        compareProperties("assert8", putNode.getProperties(), nodeD.getProperties());
        
        // /c/e
        String nodePath5 = getRootContainerName() + "/" + getNodeName("c") + "/" + getNodeName("e");
        containerNode = getCommonContainerNode(nodePath5, getCommonProperties());
        putNode = nodeDAO.putInContainer(containerNode, (ContainerNode) nodeC);
        Node nodeE = nodeDAO.getFromParent(putNode.getName(), putNode.getParent());
        assertEquals("assert9", putNode, nodeE);
        compareProperties("assert10", putNode.getProperties(), nodeE.getProperties());
        
        // /c/e/f
        String nodePath6 = getRootContainerName() + "/" + getNodeName("c") + "/" + getNodeName("e") + "/" + getNodeName("f");
        dataNode = getCommonDataNode(nodePath6, getCommonProperties());
        putNode = nodeDAO.putInContainer(dataNode, (ContainerNode) nodeE);
        Node nodeF = nodeDAO.getFromParent(putNode.getName(), putNode.getParent());
        assertEquals("assert11", putNode, nodeF);
        compareProperties("assert12", putNode.getProperties(), nodeF.getProperties());
        
        // delete the three roots
        nodeDAO.delete(nodeA, true);
        nodeDAO.delete(nodeB, true);
        nodeDAO.delete(nodeC, true);
        
        // ensure deleting the roots deleted all children
        PreparedStatement prepStmt = connection.prepareStatement(
            "select count(*) from " + nodeDAO.getNodeTableName() + " where name like ?");
        prepStmt.setString(1, runId + "%");
        ResultSet rs = prepStmt.executeQuery();
        rs.next();
        int remainingNodes = rs.getInt(1);
        assertEquals("assert13", 0, remainingNodes);
        prepStmt.close();
        
    }
    
    @Test
    public void testUpdateProperties() throws Exception
    {
        // Create a node with properties
        DataNode dataNode = getCommonDataNode(getRootContainerName() + "/" + getNodeName("g"));
        dataNode.getProperties().add(new NodeProperty("uri1", "value1"));
        dataNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(1024).toString()));
        dataNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_TYPE, "text/plain"));
        
        // Put then get the node
        DataNode nodeFromPut = (DataNode) nodeDAO.putInContainer(dataNode, null);
        DataNode nodeFromGet = (DataNode) nodeDAO.getFromParent(nodeFromPut.getName(), null);
        compareProperties("assert1", nodeFromPut.getProperties(), nodeFromGet.getProperties());
        
        // Add new properties
        nodeFromGet.getProperties().add(new NodeProperty("uri2", "value1"));
        nodeFromGet.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTENCODING, "gzip"));
        nodeFromGet.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTMD5, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}.toString()));
        DataNode nodeFromUpdate1 = (DataNode) nodeDAO.updateProperties(nodeFromGet);
        DataNode nodeFromGetUpdate1 = (DataNode) nodeDAO.getFromParent(nodeFromGet.getName(), null);
        compareProperties("assert2", nodeFromGet.getProperties(), nodeFromUpdate1.getProperties());
        compareProperties("assert3", nodeFromGet.getProperties(), nodeFromGetUpdate1.getProperties());
        
        // Update property values
        nodeFromGetUpdate1.getProperties().remove(new NodeProperty("uri1", null));
        nodeFromGetUpdate1.getProperties().add(new NodeProperty("uri1", "value2"));
        nodeFromGetUpdate1.getProperties().remove(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, null));
        nodeFromGetUpdate1.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(2048).toString()));
        nodeFromGetUpdate1.getProperties().remove(new NodeProperty(VOS.PROPERTY_URI_TYPE, null));
        nodeFromGetUpdate1.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_TYPE, "application/pdf"));
        DataNode nodeFromUpdate2 = (DataNode) nodeDAO.updateProperties(nodeFromGetUpdate1);
        DataNode nodeFromGetUpdate2 = (DataNode) nodeDAO.getFromParent(nodeFromGetUpdate1.getName(), null);
        compareProperties("assert4", nodeFromGetUpdate1.getProperties(), nodeFromUpdate2.getProperties());
        compareProperties("assert5", nodeFromGetUpdate1.getProperties(), nodeFromGetUpdate2.getProperties());
        
        // Delete property values
        nodeFromGetUpdate2.getProperties().remove(new NodeProperty("uri2", null));
        NodeProperty newURI2 = new NodeProperty("uri2", "value1");
        newURI2.setMarkedForDeletion(true);
        nodeFromGetUpdate2.getProperties().add(newURI2);
        nodeFromGetUpdate2.getProperties().remove(new NodeProperty(VOS.PROPERTY_URI_CONTENTENCODING, null));
        NodeProperty newEncoding = new NodeProperty(VOS.PROPERTY_URI_CONTENTENCODING, "gzip");
        newEncoding.setMarkedForDeletion(true);
        nodeFromGetUpdate2.getProperties().add(newEncoding);
        nodeFromGetUpdate2.getProperties().remove(new NodeProperty(VOS.PROPERTY_URI_CONTENTMD5, null));
        NodeProperty newMD5 = new NodeProperty(VOS.PROPERTY_URI_CONTENTMD5, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}.toString());
        newMD5.setMarkedForDeletion(true);
        nodeFromGetUpdate2.getProperties().add(newMD5);
        DataNode nodeFromUpdate3 = (DataNode) nodeDAO.updateProperties(nodeFromGetUpdate2);
        DataNode nodeFromGetUpdate3 = (DataNode) nodeDAO.getFromParent(nodeFromGetUpdate2.getName(), null);
        nodeFromGetUpdate2.getProperties().remove(newURI2);
        nodeFromGetUpdate2.getProperties().remove(newEncoding);
        nodeFromGetUpdate2.getProperties().remove(newMD5);
        compareProperties("assert6", nodeFromGetUpdate2.getProperties(), nodeFromUpdate3.getProperties());
        compareProperties("assert7", nodeFromGetUpdate2.getProperties(), nodeFromGetUpdate3.getProperties());
        
    }
    
    private DataNode getCommonDataNode(String uri, List<NodeProperty> properties) throws Exception
    {
        DataNode dataNode = getCommonDataNode(uri);
        dataNode.setProperties(properties);
        return dataNode;
    }
    
    private DataNode getCommonDataNode(String path) throws Exception
    {
        VOSURI vosuri = new VOSURI(getVOSURIPrefix() + path);
        DataNode dataNode = new DataNode(vosuri);
        dataNode.setOwner(getNodeOwner());
        return dataNode;
    }
    
    private ContainerNode getCommonContainerNode(String path, List<NodeProperty> properties) throws Exception
    {
        ContainerNode containerNode = getCommonContainerNode(path);
        containerNode.setProperties(properties);
        return containerNode;
    }
    
    private ContainerNode getCommonContainerNode(String path) throws Exception
    {
        VOSURI vosuri = new VOSURI(getVOSURIPrefix() + path);
        ContainerNode containerNode = new ContainerNode(vosuri);
        containerNode.setOwner(getNodeOwner());
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
        NodeProperty prop6 = new NodeProperty(VOS.PROPERTY_URI_CONTENTMD5, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}.toString());

        properties.add(prop1);
        properties.add(prop2);
        properties.add(prop3);
        properties.add(prop4);
        properties.add(prop5);
        properties.add(prop6);

        return properties;
    }
    
    private void compareProperties(String assertName, List<NodeProperty> properties1, List<NodeProperty> properties2)
    {
        properties1.removeAll(propertyIgnoreList);
        properties2.removeAll(propertyIgnoreList);
        if (properties1.size() != properties2.size())
        {
            assertTrue(assertName + " property list sizes different", false);
        }
        boolean match = true;
        for (NodeProperty list1property : properties1)
        {
            if (!propertyIgnoreList.contains(list1property))
            {
                if (!properties2.contains(list1property))
                {
                    match = false;
                }
            }
        }
        assertTrue(assertName, match);
    }
    
}

