/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2011.                            (c) 2011.
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
*  $Revision: 5 $
*
************************************************************************
*/

package ca.nrc.cadc.dali.tables.votable;

import ca.nrc.cadc.dali.tables.TableData;
import ca.nrc.cadc.dali.util.Format;
import ca.nrc.cadc.dali.util.FormatFactory;
import ca.nrc.cadc.xml.XmlUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import org.apache.log4j.Logger;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaderSAX2Factory;

/**
 *
 * @author pdowler
 */
public class VOTableReader 
{
    private static final Logger log = Logger.getLogger(VOTableReader.class);
    
    protected static final String PARSER = "org.apache.xerces.parsers.SAXParser";
    protected static final String GRAMMAR_POOL = "org.apache.xerces.parsers.XMLGrammarCachingConfiguration";
    protected static final String VOTABLE_11_SCHEMA = "VOTable-v1.1.xsd";
    protected static final String VOTABLE_12_SCHEMA = "VOTable-v1.2.xsd";
    
    private static final String votable11SchemaUrl;
    private static final String votable12SchemaUrl;
    static
    {
        votable11SchemaUrl = getSchemaURL(VOTABLE_11_SCHEMA);
        log.debug("votable11SchemaUrl: " + votable11SchemaUrl);

        votable12SchemaUrl = getSchemaURL(VOTABLE_12_SCHEMA);
        log.debug("votable12SchemaUrl: " + votable12SchemaUrl);
    }

    static String getSchemaURL(String name)
    {
        URL url = VOTableReader.class.getClassLoader().getResource(name);
        if (url == null)
            throw new MissingResourceException("schema not found", VOTableReader.class.getName(), name);
        return url.toString();
    }
    private SAXBuilder docBuilder;

    /**
     *
     */
    public VOTableReader()
    {
        this(true);
    }

    /**
     *
     * @param enableSchemaValidation
     */
    public VOTableReader(boolean enableSchemaValidation)
    {
        Map<String, String> schemaMap = null;
        if (enableSchemaValidation)
        {
            schemaMap = new HashMap<String, String>();
            schemaMap.put(ca.nrc.cadc.dali.tables.votable.VOTableWriter.VOTABLE_11_NS_URI, votable11SchemaUrl);
            schemaMap.put(ca.nrc.cadc.dali.tables.votable.VOTableWriter.VOTABLE_12_NS_URI, votable12SchemaUrl);
            log.debug("schema validation enabled");
        }
        else
        {
            log.debug("schema validation disabled");
        }
        this.docBuilder = createBuilder(schemaMap);
    }

    /**
     *
     * @param xml
     * @return
     * @throws IOException
     */
    public VOTable read(String xml)
        throws IOException
    {
        Reader reader = new StringReader(xml);
        return read(reader);
    }

    /**
     *
     * @param istream
     * @return
     * @throws IOException
     */
    public VOTable read(InputStream istream)
        throws IOException
    {
        Reader reader = new BufferedReader(new InputStreamReader(istream, "UTF-8"));
        return read(reader);
    }

    /**
     * 
     * @param reader
     * @return
     * @throws IOException
     */
    public VOTable read(Reader reader)
        throws IOException
    {
        // Parse the input document.
        Document document;
        try
        {   
            document = docBuilder.build(reader);
        }
        catch (JDOMException e)
        {
            throw new IOException("Unable to parse " + e.getMessage());
        }
        
        // Returned VOTable object.
        VOTable votable = new VOTable();
        
        // Document root element.
        Element root = document.getRootElement();
        
        // Namespace for the root element.
        Namespace namespace = root.getNamespace();
        log.debug("Namespace: " + namespace);
        
        // RESOURCE element.
        Element resource = root.getChild("RESOURCE", namespace);
        if (resource != null)
        {
            // Get the RESOURCE name attribute.
            Attribute resourceName = resource.getAttribute("name");
            if (resourceName != null)
            {
                votable.setResourceName(resourceName.getValue());
            }

            // INFO element.
            List<Element> infos = resource.getChildren("INFO", namespace);
            votable.getInfos().addAll(getInfos(infos, namespace));
            
            // TABLE element.
            Element table = resource.getChild("TABLE", namespace);
            if (table != null)
            {
                // PARAM elements.
                List<Element> params = table.getChildren("PARAM", namespace);
                votable.getParams().addAll(getParams(params, namespace));

                // FIELD elements.
                List<Element> fields = table.getChildren("FIELD", namespace);
                votable.getColumns().addAll(getFields(fields, namespace));

                // DATA element.
                Element data = table.getChild("DATA", namespace);
                if (data != null)
                {
                    // TABLEDATA element.
                    Element tableData = data.getChild("TABLEDATA", namespace);
                    votable.setTableData(getTableData(tableData, namespace, votable.getColumns()));
                }
            }
        }
        return votable;
    }

    /**
     * 
     * @param elements
     * @param namespace
     * @return
     */
    protected List<Info> getInfos(List<Element> elements, Namespace namespace)
    {
        ArrayList<Info> infos = new ArrayList<Info>();
        for (Element element : elements)
        {
            String name=  element.getAttributeValue("name");
            String value = element.getAttributeValue("value");
            if (name != null && !name.trim().isEmpty() &&
                value != null && !value.trim().isEmpty())
            {
                infos.add(new Info(name, value));
            }
        }
        return infos;
    }

    protected List<TableParam> getParams(List<Element> elements, Namespace namespace)
    {
        ArrayList<TableParam> params = new ArrayList<TableParam>();
        for (Element element : elements)
        {
            String datatype = element.getAttributeValue("datatype");
            if (datatype == null)
            {
                datatype = element.getAttributeValue("xtype");
            }
            String name = element.getAttributeValue("name");
            String value = element.getAttributeValue("value");
            TableParam tableParam = new TableParam(name, datatype, value);
            updateTableField(tableParam, element, namespace);
            params.add(tableParam);
        }
        return params;
    }
    
    /**
     *
     * @param elements
     * @param namespace
     * @return
     */
    protected List<TableField> getFields(List<Element> elements, Namespace namespace)
    {
        ArrayList<TableField> fields = new ArrayList<TableField>();
        for (Element element : elements)
        {
            String datatype = element.getAttributeValue("datatype");
            if (datatype == null)
            {
                datatype = element.getAttributeValue("xtype");
            }
            String name = element.getAttributeValue("name");
            TableField tableField = new TableField(name, datatype);
            updateTableField(tableField, element, namespace);
            fields.add(tableField);
        }
        return fields;
    }

    /**
     * 
     * @param tableField
     * @param element
     * @param namespace
     */
    protected void updateTableField(TableField tableField, Element element, Namespace namespace)
    {
        tableField.id = element.getAttributeValue("ID");
        tableField.ucd = element.getAttributeValue("ucd");
        tableField.unit = element.getAttributeValue("unit");
        tableField.utype = element.getAttributeValue("utype");
        tableField.xtype = element.getAttributeValue("xtype");

        String arraysize = element.getAttributeValue("arraysize");
        if (arraysize != null)
        {
            int index = arraysize.indexOf("*");
            if (index == -1)
            {
                tableField.variableSize = Boolean.FALSE;
            }
            else
            {
                arraysize = arraysize.substring(0, index);
                tableField.variableSize = Boolean.TRUE;
            }
            if (!arraysize.trim().isEmpty())
            {
                tableField.arraysize = Integer.parseInt(arraysize);
            }
        }

        // DESCRIPTION element for the FIELD.
        Element description = element.getChild("DESCRIPTION", namespace);
        if (description != null)
        {
            tableField.description = description.getText();
        }

        // VALUES element for the PARAM.
        Element values = element.getChild("VALUES", namespace);
        if (values != null)
        {
            List<Element> options = values.getChildren("OPTION", namespace);
            if (!options.isEmpty())
            {
                tableField.values = new ArrayList<String>();
                for (Element option : options)
                {
                    tableField.values.add(option.getAttributeValue("value"));
                }
            }
        }
    }
    
    /**
     * 
     * @param element
     * @param namespace
     * @return
     */
    TableData getTableData(Element element, Namespace namespace, List<TableField> fields)
    {
        ArrayListTableData tableData = new ArrayListTableData();

        if (element != null)
        {
            List<Element> trs = element.getChildren("TR", namespace);
            for (Element tr : trs)
            {
                List<Object> row = new ArrayList<Object>();
                List<Element> tds = tr.getChildren("TD", namespace);
                for (int i = 0; i < tds.size(); i++)
                {
                    Element td = tds.get(i);
                    TableField field = fields.get(i);
                    Format format = FormatFactory.getFormat(field);
                    String text = td.getText();
                    row.add(format.parse(text));
                    Object o = format.parse(text);
                }
                tableData.getArrayList().add(row);
            }
        }
        return tableData;
    }

    /**
     * 
     * @param schemaMap
     * @return
     */
    protected SAXBuilder createBuilder(Map<String, String> schemaMap)
    {
        long start = System.currentTimeMillis();
        boolean schemaVal = (schemaMap != null);
        String schemaResource;
        String space = " ";
        StringBuilder sbSchemaLocations = new StringBuilder();
        if (schemaVal)
        {
            log.debug("schemaMap.size(): " + schemaMap.size());
            for (String schemaNSKey : schemaMap.keySet())
            {
                schemaResource = (String) schemaMap.get(schemaNSKey);
                sbSchemaLocations.append(schemaNSKey).append(space).append(schemaResource).append(space);
            }
            // enable xerces grammar caching
            System.setProperty("org.apache.xerces.xni.parser.XMLParserConfiguration", GRAMMAR_POOL);
        }

        XMLReaderSAX2Factory factory = new XMLReaderSAX2Factory(schemaVal, PARSER);
        SAXBuilder builder = new SAXBuilder(factory);
        if (schemaVal)
        {
            builder.setFeature("http://xml.org/sax/features/validation", true);
            builder.setFeature("http://apache.org/xml/features/validation/schema", true);
            if (schemaMap.size() > 0)
            {
                builder.setProperty("http://apache.org/xml/properties/schema/external-schemaLocation",
                    sbSchemaLocations.toString());
            }
        }
        long finish = System.currentTimeMillis();
        log.debug("SAXBuilder in " + (finish - start) + "ms");
        return builder;
    }

}
