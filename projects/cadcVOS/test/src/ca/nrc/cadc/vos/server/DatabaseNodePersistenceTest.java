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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;

/**
 * Class to test DatabaseNodePersistence in cadcVOS.
 *
 */
public class DatabaseNodePersistenceTest
{
    private static final Logger log = Logger.getLogger(DatabaseNodePersistenceTest.class);
    
    DatabaseNodePersistence nodePersistence;
    static final String DELETED_NODES = "/DeletedNodes";
    
    @Before
    public void before()
    {
        nodePersistence = new DatabaseNodePersistenceStub();
    }
    
    @Test
    public void testInheritParentPermissions() throws Exception
    {
        ContainerNode parent = new ContainerNode(new VOSURI("vos://cadc.nrc.ca~vospace/parent"));
        DataNode child = new DataNode(new VOSURI("vos://cadc.nrc.ca~vospace/parent/child"));
        child.setParent(parent);
        
        // test public property
        parent.setProperties(new ArrayList<NodeProperty>());
        child.setProperties(new ArrayList<NodeProperty>());
        parent.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, "true"));
        nodePersistence.inheritParentPermissions(child);
        assertEquals("1. Wrong number of properties.", 1, child.getProperties().size());
        assertTrue("1. Missing property.", containsProperty(child, VOS.PROPERTY_URI_ISPUBLIC, "true"));
        
        // test group read property
        parent.setProperties(new ArrayList<NodeProperty>());
        child.setProperties(new ArrayList<NodeProperty>());
        parent.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_GROUPREAD, "gr"));
        nodePersistence.inheritParentPermissions(child);
        assertEquals("2. Wrong number of properties.", 1, child.getProperties().size());
        assertTrue("2. Missing property.", containsProperty(child, VOS.PROPERTY_URI_GROUPREAD, "gr"));
        
        // test group write property
        parent.setProperties(new ArrayList<NodeProperty>());
        child.setProperties(new ArrayList<NodeProperty>());
        parent.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_GROUPWRITE, "gw"));
        nodePersistence.inheritParentPermissions(child);
        assertEquals("3. Wrong number of properties.", 1, child.getProperties().size());
        assertTrue("3. Missing property.", containsProperty(child, VOS.PROPERTY_URI_GROUPWRITE, "gw"));
        
        // test 2 properties
        parent.setProperties(new ArrayList<NodeProperty>());
        child.setProperties(new ArrayList<NodeProperty>());
        parent.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_GROUPREAD, "gr"));
        parent.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_GROUPWRITE, "gw"));
        nodePersistence.inheritParentPermissions(child);
        assertEquals("4. Wrong number of properties.", 2, child.getProperties().size());
        assertTrue("4. Missing property.", containsProperty(child, VOS.PROPERTY_URI_GROUPREAD, "gr"));
        assertTrue("4. Missing property.", containsProperty(child, VOS.PROPERTY_URI_GROUPWRITE, "gw"));
        
        // test 3 properties
        parent.setProperties(new ArrayList<NodeProperty>());
        child.setProperties(new ArrayList<NodeProperty>());
        parent.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, "true"));
        parent.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_GROUPREAD, "gr"));
        parent.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_GROUPWRITE, "gw"));
        nodePersistence.inheritParentPermissions(child);
        assertEquals("5. Wrong number of properties.", 3, child.getProperties().size());
        assertTrue("5. Missing property.", containsProperty(child, VOS.PROPERTY_URI_ISPUBLIC, "true"));
        assertTrue("5. Missing property.", containsProperty(child, VOS.PROPERTY_URI_GROUPREAD, "gr"));
        assertTrue("5. Missing property.", containsProperty(child, VOS.PROPERTY_URI_GROUPWRITE, "gw"));
        
        // test non-permission property
        parent.setProperties(new ArrayList<NodeProperty>());
        child.setProperties(new ArrayList<NodeProperty>());
        parent.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, "true"));
        child.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, "55"));
        nodePersistence.inheritParentPermissions(child);
        assertEquals("6. Wrong number of properties.", 2, child.getProperties().size());
        assertTrue("6. Missing property.", containsProperty(child, VOS.PROPERTY_URI_ISPUBLIC, "true"));
        assertTrue("6. Missing property.", containsProperty(child, VOS.PROPERTY_URI_CONTENTLENGTH, "55"));
        
        // test parent is null
        parent.setProperties(new ArrayList<NodeProperty>());
        child.setProperties(new ArrayList<NodeProperty>());
        child.setParent(null);
        parent.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, "true"));
        nodePersistence.inheritParentPermissions(child);
        assertEquals("7. Wrong number of properties.", 0, child.getProperties().size());
        child.setParent(parent);
        
        // test child has existing public property
        parent.setProperties(new ArrayList<NodeProperty>());
        child.setProperties(new ArrayList<NodeProperty>());
        parent.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, "true"));
        child.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, "false"));
        nodePersistence.inheritParentPermissions(child);
        assertEquals("8. Wrong number of properties.", 1, child.getProperties().size());
        assertTrue("8. Wrong property value.", containsProperty(child, VOS.PROPERTY_URI_ISPUBLIC, "false"));
        
        // test child has existing properties
        parent.setProperties(new ArrayList<NodeProperty>());
        child.setProperties(new ArrayList<NodeProperty>());
        parent.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, "true"));
        child.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, "false"));
        child.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_GROUPWRITE, "gw"));
        nodePersistence.inheritParentPermissions(child);
        assertEquals("9. Wrong number of properties.", 2, child.getProperties().size());
        assertTrue("9. Wrong property value.", containsProperty(child, VOS.PROPERTY_URI_ISPUBLIC, "false"));
        assertTrue("9. Missing property.", containsProperty(child, VOS.PROPERTY_URI_GROUPWRITE, "gw"));
        
        // test child has property marked for deletion
        parent.setProperties(new ArrayList<NodeProperty>());
        child.setProperties(new ArrayList<NodeProperty>());
        parent.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, "true"));
        parent.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_GROUPWRITE, "ignored"));
        NodeProperty nodeProperty = new NodeProperty(VOS.PROPERTY_URI_GROUPWRITE, "");
        nodeProperty.setMarkedForDeletion(true);
        child.getProperties().add(nodeProperty);
        nodePersistence.inheritParentPermissions(child);
        assertEquals("10. Wrong number of properties.", 2, child.getProperties().size());
        assertTrue("10. Missing property.", containsProperty(child, VOS.PROPERTY_URI_ISPUBLIC, "true"));
        assertTrue("10. Missing property.", containsProperty(child, VOS.PROPERTY_URI_GROUPWRITE, ""));
        
        // test replace null value property
        parent.setProperties(new ArrayList<NodeProperty>());
        child.setProperties(new ArrayList<NodeProperty>());
        parent.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, "true"));
        parent.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_GROUPWRITE, "gw"));
        child.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, ""));
        nodePersistence.inheritParentPermissions(child);
        assertEquals("11. Wrong number of properties.", 2, child.getProperties().size());
        assertTrue("11. Wrong property value.", containsProperty(child, VOS.PROPERTY_URI_ISPUBLIC, "true"));
        assertTrue("11. Missing property.", containsProperty(child, VOS.PROPERTY_URI_GROUPWRITE, "gw"));
        
    }
    
    private boolean containsProperty(Node node, String uri, String value)
    {
        List<NodeProperty> properties = node.getProperties();
        for (NodeProperty property : properties)
        {
            if (property.getPropertyURI().equals(uri) && property.getPropertyValue().equals(value))
            {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testGetRootNode()
    {
        try
        {
            VOSURI vos = new VOSURI(new URI("vos", "cadc.nrc.ca!vospace", null, null, null));
            Node root = nodePersistence.get(vos);
            Assert.assertNotNull(root);
            Assert.assertEquals("", root.getName());

            vos = new VOSURI(new URI("vos", "cadc.nrc.ca!vospace", "", null, null));
            root = nodePersistence.get(vos);
            Assert.assertNotNull(root);
            Assert.assertEquals("", root.getName());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    class DatabaseNodePersistenceStub extends DatabaseNodePersistence
    {

        public DatabaseNodePersistenceStub()
        {
            super(new NodeDAO.NodeSchema("Node", "NodeProperty", true), DELETED_NODES);
        }

        @Override
        protected DataSource getDataSource()
        {
            return null;
        }
        
    }

}
