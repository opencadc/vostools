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

package ca.nrc.cadc.vosi;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.tap.schema.ColumnDesc;
import ca.nrc.cadc.tap.schema.KeyDesc;
import ca.nrc.cadc.tap.schema.SchemaDesc;
import ca.nrc.cadc.tap.schema.TableDesc;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.util.Log4jInit;

/**
 *
 * @author pdowler, Sailor Zhang
 */
public class TableSetTest
{
    private static final Logger log = Logger.getLogger(TableSetTest.class);
    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.vosi", Level.INFO);
    }

    String schemaNSKey1 = VOSI.TABLES_NS_URI;
    String schemaResource1 = "VOSITables-v1.0.xsd";

    String schemaNSKey2 = VOSI.VODATASERVICE_NS_URI;
    String schemaResource2 = "VODataService-v1.1.xsd";

    //String schemaNSKey2 = "http://www.ivoa.net/xml/RegistryInterface/v1.0";
    //String schemaResource2 = "RI-v1.0.xsd";

    //String schemaNSKey3 = "http://www.ivoa.net/xml/VODataService/v1.1";
    //String schemaResource3 = "VS-v1.1.xsd";

    Map<String, String> schemaNSMap;

    public TableSetTest()
    {
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {

    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
    }

    @Before
    public void setUp()
    {
        schemaNSMap = new HashMap<String, String>();
        schemaNSMap.put(schemaNSKey1, schemaResource1);
        schemaNSMap.put(schemaNSKey2, schemaResource2);
    }

    @After
    public void tearDown()
    {
    }

    /**
     * Test of getExtension method, of class VOTableWriter.
     */
    @Test
    public final void testUsingMockSchema()
    {
        log.debug("testMock");
        try
        {
            TapSchema ts = ca.nrc.cadc.tap.parser.TestUtil.loadDefaultTapSchema();
            TableSet tableSet = new TableSet(ts);
            Document doc = tableSet.getDocument();
            XMLOutputter xop = new XMLOutputter(Format.getPrettyFormat());
            Writer stringWriter = new StringWriter();
            xop.output(doc, stringWriter);
            String xmlString = stringWriter.toString();

            TestUtil.validateXml(xmlString, schemaNSMap);

            TestUtil.assertXmlNode(doc, "/vosi:tableset");
            TestUtil.assertXmlNode(doc, "/vosi:tableset/schema[name='tap_schema']");
            TestUtil.assertXmlNode(doc, "/vosi:tableset/schema/table[name='alldatatypes']");
            TestUtil.assertXmlNode(doc,
                    "/vosi:tableset/schema/table[name='alldatatypes']/column[name='t_string']/description");
            TestUtil.assertXmlNode(doc, "/vosi:tableset/schema/table[name='alldatatypes']/column[name='t_string']/unit");
            TestUtil.assertXmlNode(doc, "/vosi:tableset/schema/table[name='alldatatypes']/column[name='t_string']/ucd");
            TestUtil.assertXmlNode(doc, "/vosi:tableset/schema/table[name='alldatatypes']/column[name='t_string']/utype");
            TestUtil.assertXmlNode(doc, "/vosi:tableset/schema/table[name='alldatatypes']/column[name='t_string']/dataType");

            checkRootElement(doc);

            for (SchemaDesc sd : ts.schemaDescs)
                checkSchema(doc, sd);

        } catch (Throwable t)
        {
            t.printStackTrace(System.out);
            Assert.fail(t.toString());
        }
    }

    /**
     * Test of getExtension method, of class VOTableWriter.
     */
    @Test
    public final void testEmpty()
    {
        log.debug("testEmpty");
        try
        {
            TapSchema ts = new TapSchema(new ArrayList<SchemaDesc>(), new ArrayList<KeyDesc>());
            TableSet vods = new TableSet(ts);
            vods.getDocument();
        }
        catch (Throwable t)
        {
            if (t instanceof IllegalArgumentException)
                assert (true); //expected because schema is empty
            else
                Assert.fail(t.toString());
        }
    }

    private void checkSchema(Document doc, SchemaDesc sd) throws JDOMException
    {
        String schemaName = sd.getSchemaName();

        XPath xpath = XPath.newInstance("/vosi:tableset/schema/name[.='" + schemaName + "']");
        List<?> rs = xpath.selectNodes(doc);
        Assert.assertTrue(rs.size() == 1);

        for (TableDesc td : sd.getTableDescs())
        {
            xpath = XPath.newInstance("/vosi:tableset/schema[name='" + schemaName + "']/table[name='" + td.getTableName()
                    + "']");
            rs = xpath.selectNodes(doc);
            Assert.assertTrue(rs.size() == 1);
            checkColumns(doc, td);
        }
    }

    private void checkRootElement(Document doc) throws JDOMException
    {
        XPath xpath = XPath.newInstance("/vosi:tableset");
        List<?> rs = xpath.selectNodes(doc);
        Assert.assertTrue(rs.size() == 1);
    }

    private void checkColumns(Document doc, TableDesc td) throws JDOMException
    {
        XPath xpath;
        List<?> rs;
        String schemaName = td.getSchemaName();

        for (ColumnDesc cd : td.getColumnDescs())
        {
            xpath = XPath.newInstance("/vosi:tableset/schema[name='" + schemaName + "']/table[name='" + td.getTableName()
                    + "']/column[name='" + cd.getColumnName() + "']");
            rs = xpath.selectNodes(doc);
            Assert.assertTrue(rs.size() == 1);
        }
    }
}
