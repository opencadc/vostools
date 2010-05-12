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

import static org.junit.Assert.fail;

import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.util.Log4jInit;

/**
 *
 * @author jburke
 */
public class NodeWriterTest
{
    private static Logger log = Logger.getLogger(NodeWriterTest.class);
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    static ContainerNode containerNode;
    static DataNode dataNode;

    public NodeWriterTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // List of NodeProperty
        NodeProperties<NodeProperty> properties = new NodeProperties<NodeProperty>();
        NodeProperty nodeProperty = new NodeProperty("ivo://ivoa.net/vospace/core#description", "My award winning images");
        nodeProperty.setReadOnly(true);
        properties.add(nodeProperty);

        // List of Node
        List<Node> nodes = new ArrayList<Node>();
        nodes.add(new DataNode(new VOSURI("vos://cadc.nrc.ca!vospace/mydir/ngc4323")));
        nodes.add(new DataNode(new VOSURI("vos://cadc.nrc.ca!vospace/mydir/ngc5796")));
        nodes.add(new DataNode(new VOSURI("vos://cadc.nrc.ca!vospace/mydir/ngc6801")));

        // ContainerNode
        containerNode = new ContainerNode(new VOSURI("vos://cadc.nrc.ca!vospace/dir/subdir"));
        containerNode.setProperties(properties);
        containerNode.setNodes(nodes);

        // DataNode
        dataNode = new DataNode(new VOSURI("vos://cadc.nrc.ca!vospace/dir/subdir"));
        dataNode.setProperties(properties);
        dataNode.setBusy(true);
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of write method, of class NodeWriter.
     */
    @Test
    public void write_ContainerNode_StringBuilder()
    {
        try
        {
            log.debug("write_ContainerNode_StringBuilder");
            StringBuilder sb = new StringBuilder();
            NodeWriter instance = new NodeWriter();
            instance.write(containerNode, sb);
            log.debug(sb.toString());

            // validate the XML
            NodeReader reader = new NodeReader();
            reader.read(sb.toString());
            
            log.info("write_ContainerNode_StringBuilder passed");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    @Test
    public void write_DataNode_StringBuilder()
    {
        try
        {
            log.debug("write_DataNode_StringBuilder");
            StringBuilder sb = new StringBuilder();
            NodeWriter instance = new NodeWriter();
            instance.write(dataNode, sb);
            log.debug(sb.toString());

            // validate the XML
            NodeReader reader = new NodeReader();
            reader.read(sb.toString());

            log.info("write_DataNode_StringBuilder passed");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    /**
     * Test of write method, of class NodeWriter.
     */
//    @Test
    public void write_ContainerNode_OutputStream()
    {
        try
        {
            log.debug("write_ContainerNode_OutputStream");
            NodeWriter instance = new NodeWriter();
            instance.write(containerNode, System.out);
            log.info("write_ContainerNode_OutputStream passed");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    /**
     * Test of write method, of class NodeWriter.
     */
//    @Test
    public void write_DataNode_OutputStream()
    {
        try
        {
            log.debug("write_DataNode_OutputStream");
            NodeWriter instance = new NodeWriter();
            instance.write(dataNode, System.out);
            log.info("write_DataNode_OutputStream passed");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    /**
     * Test of write method, of class NodeWriter.
     */
//    @Test
    public void write_ContainerNode_Writer()
    {
        try
        {
            log.debug("write_ContainerNode_Writer");
            NodeWriter instance = new NodeWriter();
            instance.write(containerNode, new OutputStreamWriter(System.out, "UTF-8"));
            log.info("write_ContainerNode_Writer passed");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    /**
     * Test of write method, of class NodeWriter.
     */
//    @Test
    public void write_DataNode_Writer()
    {
        try
        {
            log.debug("write_DataNode_Writer");
            NodeWriter instance = new NodeWriter();
            instance.write(dataNode, new OutputStreamWriter(System.out, "UTF-8"));
            log.info("write_DataNode_Writer passed");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

}