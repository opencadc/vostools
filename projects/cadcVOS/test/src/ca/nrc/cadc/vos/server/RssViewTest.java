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
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.xml.XmlUtil;
import java.net.URI;
import java.net.URL;
import java.security.Principal;
import java.security.PrivilegedExceptionAction;
import java.text.DateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.sql.DataSource;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author pdowler
 */
public class RssViewTest
{
    private static final Logger log = Logger.getLogger(RssViewTest.class);
    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.vos.server", Level.DEBUG);
    }

    private static DateFormat dateFormat = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);

    static final String SERVER = "VOSPACE_WS_TEST";
    static final String DATABASE = "vospace";
    static final String SCHEMA = "dbo";

    static final String VOS_AUTHORITY = "cadc.nrc.ca!vospace";
    static final String ROOT_CONTAINER = "CADCRsstest1";
    static final String NODE_OWNER = "CN=CADC Regtest1 10577,OU=CADC,O=HIA";

    protected Subject owner;
    protected Principal principal;
    protected NodePersistence nodePersistence;
    
    public RssViewTest()
    {
        nodePersistence = new TestNodePersistence(SERVER, DATABASE, SCHEMA);
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {

    }

    @Test
    public void testGetBaseURL()
    {
        try
        {
            RssView v = new RssView();

            String expected = "http://example.com/vospace/nodes";
            URL req = new URL(expected + "/foo/bar");
            VOSURI vos = new VOSURI(new URI("vos://example.com~vospace/foo/bar"));
            ContainerNode n = new ContainerNode(vos);
            String actual = v.getBaseURL(n, req);
            log.debug("request: " + req);
            Assert.assertEquals(expected, actual);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testSetNode()
    {
        try
        {
            Subject subject = new Subject();
            subject.getPrincipals().add(new X500Principal(NODE_OWNER));

            // Get the root container node for the test.
            GetRootNodeAction getRootNodeAction = new GetRootNodeAction(nodePersistence);
            ContainerNode root = (ContainerNode) Subject.doAs(subject, getRootNodeAction);
            log.debug("root node: " + root);

            // Get the RSS XML from the view.
            SetNodeAction setNodeAction = new SetNodeAction(nodePersistence, root);
            StringBuilder rssXml = (StringBuilder) Subject.doAs(subject, setNodeAction);

            Document doc = XmlUtil.buildDocument(rssXml.toString());
            Element rss = doc.getRootElement();
            Assert.assertNotNull(rss);

            Element channel = rss.getChild("channel");
            Assert.assertNotNull(channel);

            List<Element> items = channel.getChildren("item");
            Assert.assertEquals(5, items.size());

            Iterator<Element> it = items.iterator();

            Element item1 = it.next();
            Element pubDate1 = item1.getChild("pubDate");
            Assert.assertNotNull(pubDate1);
            Date date1 = dateFormat.parse(pubDate1.getText());
            log.debug("Date 1: " + date1);

            Element item2 = it.next();
            Element pubDate2 = item2.getChild("pubDate");
            Assert.assertNotNull(pubDate2);
            Date date2 = dateFormat.parse(pubDate2.getText());
            log.debug("Date 2: " + date2);

            Element item3 = it.next();
            Element pubDate3 = item3.getChild("pubDate");
            Assert.assertNotNull(pubDate3);
            Date date3 = dateFormat.parse(pubDate3.getText());
            log.debug("Date 3: " + date3);

            Element item4 = it.next();
            Element pubDate4 = item4.getChild("pubDate");
            Assert.assertNotNull(pubDate4);
            Date date4 = dateFormat.parse(pubDate4.getText());
            log.debug("Date 4: " + date4);

            Element item5 = it.next();
            Element pubDate5 = item5.getChild("pubDate");
            Assert.assertNotNull(pubDate5);
            Date date5 = dateFormat.parse(pubDate5.getText());
            log.debug("Date 5: " + date5);

            Assert.assertTrue(date1.after(date2));
            Assert.assertTrue(date2.after(date3));
            Assert.assertTrue(date3.after(date4));
            Assert.assertTrue(date4.after(date5));
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    class TestNodePersistence extends DatabaseNodePersistence
    {
        private String server;
        private String database;
        private String schema;
        private DataSource dataSource;

        public TestNodePersistence(String server, String database, String schema)
        {
            super();
            this.server = server;
            this.database = database;
            this.schema = schema;
        }

        @Override
        public String getPropertyTableName()
        {
            return database + "." + schema + ".NodeProperty";
        }

        @Override
        public String getNodeTableName()
        {
            return database + "." + schema + ".Node";
        }

        @Override
        public DataSource getDataSource()
        {
            if (dataSource != null)
                return dataSource;

            try
            {
                DBConfig dbConfig = new DBConfig();
                ConnectionConfig connConfig = dbConfig.getConnectionConfig(server, database);
                dataSource = DBUtil.getDataSource(connConfig);
                return dataSource;
            }
            catch (Exception e)
            {
                log.error(e);
                return null;
            }
        }

    }

    private class GetRootNodeAction implements PrivilegedExceptionAction<Object>
    {
        private NodePersistence nodePersistence;

        GetRootNodeAction(NodePersistence nodePersistence)
        {
            this.nodePersistence = nodePersistence;
        }

        public Object run() throws Exception
        {
            // Root container.
            VOSURI vos = new VOSURI(new URI("vos", VOS_AUTHORITY, "/" + ROOT_CONTAINER, null, null));
            ContainerNode root = new ContainerNode(vos);
            root.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CREATOR, NODE_OWNER));
            root.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, Boolean.TRUE.toString()));
            root = (ContainerNode) getNode(vos, root);

            // Child container node of root.
            VOSURI child1Uri = new VOSURI(new URI("vos", VOS_AUTHORITY, "/" + ROOT_CONTAINER + "/child1", null, null));
            ContainerNode child1 = new ContainerNode(child1Uri);
            child1.setParent(root);
            child1.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CREATOR, NODE_OWNER));
            child1.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, Boolean.TRUE.toString()));
            child1 = (ContainerNode) getNode(child1Uri, child1);

            // Child data node of root.
            VOSURI child2Uri = new VOSURI(new URI("vos", VOS_AUTHORITY, "/" + ROOT_CONTAINER + "/child2", null, null));
            DataNode child2 = new DataNode(child2Uri);
            child2.setParent(root);
            child2.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CREATOR, NODE_OWNER));
            child2.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, Boolean.TRUE.toString()));
            child2 = (DataNode) getNode(child2Uri, child2);

            // Child data node of child1.
            VOSURI child3Uri = new VOSURI(new URI("vos", VOS_AUTHORITY, "/" + ROOT_CONTAINER + "/child1/child3", null, null));
            DataNode child3 = new DataNode(child3Uri);
            child3.setParent(child1);
            child3.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CREATOR, NODE_OWNER));
            child3.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, Boolean.TRUE.toString()));
            child3 = (DataNode) getNode(child3Uri, child3);

            // Child data node of child2.
            VOSURI child4Uri = new VOSURI(new URI("vos", VOS_AUTHORITY, "/" + ROOT_CONTAINER + "/child1/child4", null, null));
            DataNode child4 = new DataNode(child4Uri);
            child4.setParent(child1);
            child4.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CREATOR, NODE_OWNER));
            child4.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, Boolean.TRUE.toString()));
            child4 = (DataNode) getNode(child4Uri, child4);

            return root;
        }

        private Node getNode(VOSURI vos, Node node)
        {
            try
            {
                node = nodePersistence.get(vos);
                log.debug("found root node: " + node.getName());
            }
            catch (NodeNotFoundException e)
            {
                node = nodePersistence.put(node);
                log.debug("put root node: " + node.getName());
            }
            return node;
        }

    }

    private class SetNodeAction implements PrivilegedExceptionAction<Object>
    {
        private NodePersistence nodePersistence;
        private ContainerNode root;

        SetNodeAction(NodePersistence nodePersistence, ContainerNode root)
        {
            this.nodePersistence = nodePersistence;
            this.root = root;
        }

        public Object run() throws Exception
        {
            RssView view = new RssView();
            view.setNodePersistence(nodePersistence);
            view.setNode(root, "", new URL("http://" + VOS_AUTHORITY + "/" + ROOT_CONTAINER));

            log.debug("xml: " + view.xmlString);

            return view.xmlString;
        }

    }

}
