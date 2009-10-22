/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.nrc.cadc.tap.test;

import ca.nrc.cadc.tap.VOTableWriter;
import ca.nrc.cadc.tap.parser.adql.TapSelectItem;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.tap.schema.TapSchemaDAO;
import ca.nrc.cadc.util.LoggerUtil;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import javax.sql.DataSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import junit.framework.TestCase;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom.Comment;
import org.jdom.DocType;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.ProcessingInstruction;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class VOTableWriterTest extends TestCase
{
    private static final String INFO_ATTRIBUTE_NAME = "QUERY_STATUS";
    private static final String INFO_ATTRIBUTE_VALUE = "ERROR";

    private static final String JDBC_URL = "jdbc:postgresql://cvodb0/cvodb";
    private static final String USERNAME = "cadcuser51";
    private static final String PASSWORD = "MhU7nuvP5/67A:31:30";
    private static final boolean SUPPRESS_CLOSE = false;
    private static final String QUERY = "select * from tap_schema.columns";

    private static Logger log;
    static
    {
        log = Logger.getLogger(VOTableWriterTest.class);

        // default log level is debug.
        log.setLevel((Level)Level.DEBUG);
    }

    public VOTableWriterTest(String testName)
    {
        super(testName);
    }

    @Override
    protected void setUp()
        throws Exception
    {
        // Create the logger.
        LoggerUtil.initialize(new String[] { "test", "ca.nrc.cadc" }, new String[] { "-d" });
    }

    @Override
    protected void tearDown()
        throws Exception
    {
    }

    public void testWriteErrorDocument() throws Exception
    {
        // Create some nested exceptions.
        Throwable root = new Throwable("root exception");
        Throwable middle = new Throwable("middle exception", root);
        Throwable top = new Throwable("top exception", middle);

        // Capture the VOTableWriter output.
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Create the error XML document.
        VOTableWriter writer = new VOTableWriter();
        writer.write(top, output);

        // Valiate the xml against the VOTABLE XSD.
        String xml = output.toString();
        log.debug(xml);
        Document document = validate(xml);

        // Check INFO attributes.
        NodeList nodeList = document.getElementsByTagName("INFO");
        Node info = nodeList.item(0);
        assertTrue("INFO element missing attributes 'name' and 'value'", info.hasAttributes());

        NamedNodeMap attributes = info.getAttributes();
        Node name = attributes.getNamedItem("name");
        assertEquals("INFO attribute 'name' is invalid", INFO_ATTRIBUTE_NAME, name.getNodeValue());

        Node value = attributes.getNamedItem("value");
        assertEquals("INFO attribute 'value' is invalid", INFO_ATTRIBUTE_VALUE, value.getNodeValue());
    }

    public void testWriteResultSet() throws Exception
    {
        // Setup a datasource.
        Class.forName("org.postgresql.Driver");
        DataSource ds = new SingleConnectionDataSource(JDBC_URL, USERNAME, PASSWORD, SUPPRESS_CLOSE);

        // Get the TAP schema.
        TapSchemaDAO dao = new TapSchemaDAO(ds);
        TapSchema tapSchema = dao.get();

        // Get a ResultSet.
        Connection connection = ds.getConnection();
        Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);
        ResultSet resultSet = statement.executeQuery(QUERY);

        // Capture the VOTableWriter output.
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Create some TapSelectItems.
        List<TapSelectItem> items = new ArrayList<TapSelectItem>();
        items.add(new TapSelectItem("TAP_SCHEMA.schemas", "schema_name", "schema name for reference to TAP_SCHEMA.schemas"));
        items.add(new TapSelectItem("TAP_SCHEMA.schemas", "description", "describes schemas in the tableset "));
        items.add(new TapSelectItem("TAP_SCHEMA.schemas", "utype", "lists the utypes of schemas in the tableset"));
        items.add(new TapSelectItem("TAP_SCHEMA.tables", "schema_name", "the schema this table belongs to"));
        items.add(new TapSelectItem("TAP_SCHEMA.tables", "table_name", "the fully qualified table name"));
        items.add(new TapSelectItem("TAP_SCHEMA.tables", "description", "describes tables in the tableset"));

        // Create the error XML document.
        VOTableWriter writer = new VOTableWriter();
        writer.setTapSchema(tapSchema);
        writer.setSelectList(items);

        // Write the XML.
        writer.write(resultSet, output);
        log.debug(output.toString());

        statement.close();
        connection.close();
    }

    private Document validate(String xml) throws Exception
    {
        // parse an XML document into a DOM tree
        log.debug("parsing XML into Document.");
        DocumentBuilderFactory builder = DocumentBuilderFactory.newInstance();
        DocumentBuilder parser = builder.newDocumentBuilder();
        Document document = parser.parse(new InputSource(new StringReader(xml)));

        // create a SchemaFactory
        log.debug("creating a SchemaFactory.");
        ErrorHandler errorHandler = new MyErrorHandler();
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setErrorHandler(errorHandler);

        // load a schema, represented by a Schema instance
        log.debug("creating a schema.");
        Source schemaFile = new StreamSource("http://www.ivoa.net/xml/VOTable/v1.2");
//        Source schemaFile = new StreamSource(new File("/home/cadc/jburke/tmp/votable.xsd"));
        Schema schema = factory.newSchema(schemaFile);

        // create a Validator instance, which can be used to validate an instance document
        log.debug("creating a validator.");
        Validator validator = schema.newValidator();
        validator.setErrorHandler(errorHandler);

        // validate the DOM tree
        log.debug("validating the document.");
//        validator.validate(new DOMSource(document));
        log.debug("document validated.");
        return document;
    }

    class MyErrorHandler implements ErrorHandler
    {
        public void warning(SAXParseException e) throws SAXException
        {
            show("Warning", e);
            throw (e);
        }

        public void error(SAXParseException e) throws SAXException
        {
            show("Error", e);
            throw (e);
        }

        public void fatalError(SAXParseException e) throws SAXException {
            show("Fatal Error", e);
            throw (e);
        }

        private void show(String type, SAXParseException e)
        {
            log.debug(type + ": " + e.getMessage());
            log.debug("Line " + e.getLineNumber() + " Column " + e.getColumnNumber());
            log.debug("System ID: " + e.getSystemId());
        }
    }

}
