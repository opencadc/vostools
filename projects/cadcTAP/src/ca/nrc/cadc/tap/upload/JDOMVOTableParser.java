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

package ca.nrc.cadc.tap.upload;

import ca.nrc.cadc.tap.BasicUploadManager;
import ca.nrc.cadc.tap.schema.ColumnDesc;
import ca.nrc.cadc.tap.schema.TableDesc;
import ca.nrc.cadc.tap.upload.datatype.ADQLDataType;
import ca.nrc.cadc.tap.writer.VOTableWriter;
import ca.nrc.cadc.xml.XmlUtil;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;

/**
 * Implements the VOTableParser interface using JDOM.
 * 
 * @author jburke
 */
public class JDOMVOTableParser implements VOTableParser
{
    private static final Logger log = Logger.getLogger(JDOMVOTableParser.class);

    private Element root;
    private Namespace namespace;
    private boolean hasTableRows;
    private Iterator<Element> tableRowIter;

    protected String tableName;
    protected InputStream in;
    protected Document document;

    private Map<String,String> schemaMap;

    /**
     *
     */
    public JDOMVOTableParser()
    {
        tableName = null;
        in = null;
        hasTableRows = false;
        initSchemaMap();
    }

    private void initSchemaMap()
    {
        schemaMap = new HashMap<String,String>();
        String url;

        url = XmlUtil.getResourceUrlString(VOTABLE_11_SCHEMA, JDOMVOTableParser.class);
        schemaMap.put(VOTableWriter.VOTABLE_11_NS_URI, url);
        
        url = XmlUtil.getResourceUrlString(VOTABLE_12_SCHEMA, JDOMVOTableParser.class);
        schemaMap.put(VOTableWriter.VOTABLE_12_NS_URI, url);
    }

    /**
     * Set the name of the VOTable.
     *
     * @param tableName the VOTable name.
     */
    public void setTableName(String tableName)
    {
        this.tableName = tableName;
    }

    /**
     * Set the URI used to access the VOTabe.
     *
     * @param URI URI tot the VOTable.
     */
    public void setInputStream(InputStream in)
    {
        this.in = in;
    }

    /**
     * Get a List that describes each VOTable column.
     *
     * @throws VOTableParserException if unable to parse the VOTable.
     * @return List of ColumnDesc describing the VOTable columns.
     */
    public TableDesc getTableDesc()
        throws VOTableParserException
    {
        if (in == null)
            throw new IllegalStateException("Inputstream to the VOTable must be set before calling getMetaData()");

        SAXBuilder parser = XmlUtil.createBuilder(schemaMap);
        List<ColumnDesc> columns = new ArrayList<ColumnDesc>();
        
        try
        {
            document = parser.build(in);
            root = document.getRootElement();
            namespace = root.getNamespace();
            Element resource = root.getChild("RESOURCE", namespace);
            if (resource != null)
            {
                Element table = resource.getChild("TABLE", namespace);
                if (table != null)
                {
                    List<Element> fields = table.getChildren("FIELD", namespace);
                    for (Element field : fields)
                    {
                        String datatype = field.getAttributeValue("xtype");
                        if (datatype == null)
                            datatype = field.getAttributeValue("datatype");
                        String width = field.getAttributeValue("arraysize");
                        String name = field.getAttributeValue("name");
                        log.debug("column: '"+name+"'");
                        UploadUtil.isValidateIdentifier(name);
                        ColumnDesc columnDesc = new ColumnDesc();
                        columnDesc.tableName = tableName;
                        columnDesc.columnName = name;
                        columnDesc.datatype = ADQLDataType.getDataType(datatype, width);
                        columnDesc.size = ADQLDataType.getWidth(width);
                        columns.add(columnDesc);
                        log.debug("column: " + columnDesc);
                    }
                }
            }
        }
        catch (JDOMException e)
        {
            throw new VOTableParserException("Error parsing VOTable", e);
        }
        catch (IOException e)
        {
            throw new VOTableParserException("Error reading VOTable", e);
        }
        catch (ADQLIdentifierException e)
        {
            throw new VOTableParserException("Invalid column name", e);
        }

        TableDesc tableDesc = new TableDesc();
        tableDesc.schemaName = BasicUploadManager.SCHEMA;
        tableDesc.tableName = tableName;
        tableDesc.columnDescs = columns;
        log.debug("table: " + tableDesc);
        return tableDesc;
    }

    /**
     * Returns an Iterator to the VOTable data.
     *
     * @return Iterator to the VOTable data.
     */
    public Iterator iterator()
    {
        return new VOTableIterator();
    }
    
    /**
     * Implements the Iterator interface over the VOTable data rows.
     */
    class VOTableIterator implements Iterator
    {

        /**
         *
         * @return true if there is another data row, false otherwise.
         */
        public boolean hasNext()
        {
            if (!hasTableRows)
            {
                Element resource = root.getChild("RESOURCE", namespace);
                if (resource == null)
                    return false;
                Element table = resource.getChild("TABLE", namespace);
                if (table == null)
                    return false;
                Element data = table.getChild("DATA", namespace);
                if (data == null)
                    return false;
                Element tableData = data.getChild("TABLEDATA", namespace);
                if (tableData == null)
                    return false;
                List<Element> tableRowList = tableData.getChildren("TR", namespace);
                if (tableRowList.isEmpty())
                    return false;
                tableRowIter = tableRowList.iterator();
                hasTableRows = true;
            }
            return tableRowIter.hasNext();
        }

        /**
         *
         * @return String array containing the next data row.
         */
        public Object next()
        {
            Element tableRow = tableRowIter.next();
            List<String> rowData = new ArrayList<String>();
            List<Element> tableDatas = tableRow.getChildren("TD", namespace);
            for (Element tableData : tableDatas)
            {
                String s = tableData.getTextTrim();
                if (s.length() == 0)
                    s = null;
                rowData.add(s);
            }
            return (String[]) rowData.toArray(new String[0]);
        }

        /**
         * Not supported.
         */
        public void remove()
        {
            throw new UnsupportedOperationException("remove on a VOTable row not supported.");
        }

    }

}
