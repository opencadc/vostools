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

package ca.nrc.cadc.tap.writer;

import ca.nrc.cadc.tap.schema.ParamDesc;
import ca.nrc.cadc.tap.parser.TestUtil;
import org.junit.BeforeClass;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.xml.XmlUtil;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jburke
 */
public class VOTableWriterTest
{
    private static final String VOTABLE_SCHEMA_RESOURCE = "VOTable-v1.2.xsd";
    private static final String INFO_ATTRIBUTE_NAME = "QUERY_STATUS";
    private static final String INFO_ATTRIBUTE_VALUE = "ERROR";

    private static final Logger LOG = Logger.getLogger(VOTableWriterTest.class);

    static TapSchema TAP_SCHEMA;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc.tap", org.apache.log4j.Level.INFO);
        TAP_SCHEMA = TestUtil.loadDefaultTapSchema();
    }

    // just need a job with an ID
    Job job = new Job()
    {
        @Override
        public String getID() { return "123"; }
    };

    public VOTableWriterTest() { }

    /**
     * Test of getExtension method, of class VOTableWriter.
     */
    @Test
    public final void testGetExtension()
    {
        LOG.debug("getExtension");
        VOTableWriter instance = new VOTableWriter();
        String expResult = "xml";
        String result = instance.getExtension();
        assertEquals(expResult, result);
        LOG.info("getExtension passed");
    }

    /**
     * Test of setSelectList method, of class VOTableWriter.
     */
    @Test
    public final void testSetSelectList()
    {
        LOG.debug("setSelectList");
        List<ParamDesc> items = new ArrayList<ParamDesc>();
        VOTableWriter instance = new VOTableWriter();
        instance.setSelectList(items);
        assertNotNull(instance.selectList);
        LOG.info("testSetSelectList passed");
    }

    /**
     * Test of write method, of class VOTableWriter.
     */
    @Test
    public final void testWriteThrowableOutputStream()
    {
        LOG.debug("testWriteThrowableOutputStream");
        try
        {
            // Create some nested exceptions.
            Throwable root = new Throwable("root exception");
            Throwable middle = new Throwable("middle exception", root);
            Throwable top = new Throwable("top exception", middle);

            // Capture the VOTableWriter output.
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            // Write out the VOTABLE.
            VOTableWriter writer = new VOTableWriter();
            job.setExecutionPhase(ExecutionPhase.ERROR);
            writer.setJob(job);
            writer.write(top, output);
            output.close();

            // Validate the xml against the VOTABLE XSD.
            String xml = output.toString();
            LOG.debug("XML: \n" + xml);
            Document document = validate(xml);

            // Get the root VOTABLE element.
            Element votable = document.getRootElement();
            Namespace namespace = votable.getNamespace();

            // Get the RESOURCE element.
            Element resource = votable.getChild("RESOURCE", namespace);
            assertNotNull("RESOURCE element is missing", resource);

            // Check INFO attributes.
            Element info = resource.getChild("INFO", namespace);
            assertNotNull("Child INFO elemet of RESOURCE is missing", info);

            // Check the INFO attribute 'name'.
            Attribute name = info.getAttribute("name");
            assertNotNull("INFO attribute 'name' is missing", name);
            assertEquals("INFO attribute 'name' is invalid", INFO_ATTRIBUTE_NAME, name.getValue());

            // Check the INFO attribute 'value'.
            Attribute value = info.getAttribute("value");
            assertNotNull("INFO attribute 'value' is missing", value);
            assertEquals("INFO attribute 'value' is invalid", INFO_ATTRIBUTE_VALUE, value.getValue());

            LOG.info("testWriteThrowableOutputStream passed");
        }
        catch(Exception unexpected)
        {
            LOG.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
        finally
        {
            job.setExecutionPhase(ExecutionPhase.PENDING);
        }
    }

//
//    @Test
//    public void testGetFunctionMetaDataElement()
//    {
//        LOG.debug("testGetFunctionMetaDataElement");
//        try
//        {
//            VOTableWriter writer = new VOTableWriter();
//
//            ByteArrayOutputStream output = new ByteArrayOutputStream();
//            XMLOutputter outputter = new XMLOutputter();
//            outputter.setFormat(Format.getPrettyFormat());
//            outputter.output(element, output);
//            output.close();
//            String xml = output.toString();
//            LOG.debug("XML: \n" + xml);
//
//            int start = xml.indexOf("xtype=\"");
//            int end = xml.indexOf("\"", start + "xtype\"".length() + 1);
//            if (start == -1 || end == -1)
//                fail("xtype attribute not found in FIELD element");
//            String xtype = xml.substring(start + 7, end);
//            assertEquals("adql:DOUBLE", xtype);
//
//            start = xml.indexOf("datatype=\"");
//            end = xml.indexOf("\"", start + "datatype\"".length() + 1);
//            if (start == -1 || end == -1)
//                fail("datatype attribute not found in FIELD element");
//            String datatype = xml.substring(start + 10, end);
//            assertEquals("double", datatype);
//
//            LOG.info("testGetFunctionMetaDataElement passed");
//        }
//        catch(Exception unexpected)
//        {
//            LOG.error("unexpected exception", unexpected);
//            fail("unexpected exception: " + unexpected);
//        }
//    }

    private Map<String,String> schemaMap;
    
    private Document validate(String xml) throws Exception
    {
        if (schemaMap == null)
        {
            this.schemaMap = new HashMap<String,String>();
            String url = XmlUtil.getResourceUrlString(VOTABLE_SCHEMA_RESOURCE, VOTableWriter.class);
            LOG.debug("VOTable-1.2 schema location: " + url);
            schemaMap.put(VOTableWriter.VOTABLE_12_NS_URI, url);

        }
        SAXBuilder builder = XmlUtil.createBuilder(schemaMap);
                //new SAXBuilder("org.apache.xerces.parsers.SAXParser", true);
        //builder.setFeature("http://xml.org/sax/features/validation", true);
        //builder.setFeature("http://apache.org/xml/features/validation/schema", true);
        //builder.setFeature("http://apache.org/xml/features/validation/schema-full-checking", true);
        //builder.setProperty("http://apache.org/xml/properties/schema/external-schemaLocation",
        //                    "http://www.ivoa.net/xml/VOTable/v1.2 " + votableSchema);
        return builder.build(new StringReader(xml));
    }

}
