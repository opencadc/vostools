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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeAuthorizer;
import ca.nrc.cadc.vos.NodeDAO;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.dao.NodePropertyMapper;

/**
 * Abstract class encompassing the logic behind running tests on the
 * NodeDAO class.  Subclasses must provide dataSource and nodeAuthorizer
 * implementations.
 * 
 * @author majorb
 *
 */
public abstract class NodeDAOTests
{
    
    private NodeDAO nodeDAO;
    private String runId;
    private Connection connection;
    
    @Before
    public void before() throws Exception
    {
        DataSource dataSource = getDataSource();
        nodeDAO = getNodeDAO(dataSource, getNodeAuthorizer());
        connection = dataSource.getConnection();
        runId = NodeDAOTests.class.getName() + System.currentTimeMillis();
    }
    
    @After
    public void after() throws Exception
    {
        PreparedStatement prepStmt = connection.prepareStatement(
            "delete from " + nodeDAO.getNodeTableName() + " where name like ?");
        prepStmt.setString(1, runId + "%");
        prepStmt.executeUpdate();
        prepStmt.close();
        connection.close();
    }
    
    public abstract DataSource getDataSource();
    
    public abstract NodeAuthorizer getNodeAuthorizer();
    
    public abstract NodeDAO getNodeDAO(DataSource dataSource, NodeAuthorizer nodeAuthorizer);
    
    private String getNodeName(String identifier)
    {
        return runId + identifier;
    }

    @Test
    public void testPutGetDeleteDataNode() throws Exception
    {
        DataNode dataNode = null;
        ContainerNode containerNode = null;
        Node putNode = null;
        Node getNode = null;
        
        // /a
        String nodePath1 = "/" + getNodeName("a");
        dataNode = new DataNode(nodePath1, getProperties());
        putNode = nodeDAO.put(dataNode);
        getNode = nodeDAO.get(dataNode);
        System.out.println("PutNode: " + putNode);
        System.out.println("GetNode: " + getNode);
        assertEquals(putNode, getNode);
        assertEquals(putNode.getProperties(), getNode.getProperties());
        
        // /b
        String nodePath2 = "/" + getNodeName("b");
        containerNode = new ContainerNode(nodePath2, getProperties());
        putNode = nodeDAO.put(containerNode);
        getNode = nodeDAO.get(containerNode);
        System.out.println("PutNode: " + putNode);
        System.out.println("GetNode: " + getNode);
        assertEquals(putNode, getNode);
        assertEquals(putNode.getProperties(), getNode.getProperties());
        
        // /c
        String nodePath3 = "/" + getNodeName("c");
        containerNode = new ContainerNode(nodePath3, getProperties());
        putNode = nodeDAO.put(containerNode);
        getNode = nodeDAO.get(containerNode);
        assertEquals(putNode, getNode);
        assertEquals(putNode.getProperties(), getNode.getProperties());
        
        // /b/d
        String nodePath4 = "/" + getNodeName("b") + "/" + getNodeName("d");
        dataNode = new DataNode(nodePath4, getProperties());
        putNode = nodeDAO.put(dataNode);
        getNode = nodeDAO.get(dataNode);
        assertEquals(putNode, getNode);
        assertEquals(putNode.getProperties(), getNode.getProperties());
        
        // /c/e
        String nodePath5 = "/" + getNodeName("c") + "/" + getNodeName("e");
        containerNode = new ContainerNode(nodePath5, getProperties());
        putNode = nodeDAO.put(containerNode);
        getNode = nodeDAO.get(containerNode);
        assertEquals(putNode, getNode);
        assertEquals(putNode.getProperties(), getNode.getProperties());
        
        // /c/e/f
        String nodePath6 = "/" + getNodeName("c") + "/" + getNodeName("e") + "/" + getNodeName("f");
        dataNode = new DataNode(nodePath6, getProperties());
        putNode = nodeDAO.put(dataNode);
        getNode = nodeDAO.get(dataNode);
        assertEquals(putNode, getNode);
        assertEquals(putNode.getProperties(), getNode.getProperties());
        
        // delete the three roots
        nodeDAO.delete(new DataNode(nodePath1));
        nodeDAO.delete(new ContainerNode(nodePath2));
        nodeDAO.delete(new ContainerNode(nodePath3));
        
        // ensure deleting the roots deleted all children
        PreparedStatement prepStmt = connection.prepareStatement(
            "select count(*) from " + nodeDAO.getNodeTableName() + " where name like ?");
        prepStmt.setString(1, runId + "%");
        ResultSet rs = prepStmt.executeQuery();
        rs.next();
        int remainingNodes = rs.getInt(1);
        assertEquals(0, remainingNodes);
        prepStmt.close();
        
    }
    
    private List<NodeProperty> getProperties()
    {
        List<NodeProperty> properties = new ArrayList<NodeProperty>();
        NodeProperty prop1 = new NodeProperty("uri1", "value1");
        NodeProperty prop2 = new NodeProperty("uri2", "value2");
        NodeProperty prop3 = new NodeProperty(NodePropertyMapper.PROPERTY_CONTENTLENGTH_URI, new Long(1024).toString());
        NodeProperty prop4 = new NodeProperty(NodePropertyMapper.PROPERTY_CONTENTTYPE_URI, "text/plain");
        NodeProperty prop5 = new NodeProperty(NodePropertyMapper.PROPERTY_CONTENTENCODING_URI, "gzip");
        NodeProperty prop6 = new NodeProperty(NodePropertyMapper.PROPERTY_CONTENTMD5_URI, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}.toString());

        properties.add(prop3);
        properties.add(prop4);
        properties.add(prop5);
        properties.add(prop6);
        properties.add(prop1);
        properties.add(prop2);
        return properties;
    }
    
}

