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

package ca.nrc.cadc.tap;

import ca.nrc.cadc.tap.parser.adql.TapSelectItem;
import ca.nrc.cadc.tap.schema.Column;
import ca.nrc.cadc.tap.schema.Schema;
import ca.nrc.cadc.tap.schema.Table;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.tap.votable.TableDataElement;
import ca.nrc.cadc.tap.votable.TableDataXMLOutputter;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import ca.nrc.cadc.tap.parser.adql.TapSelectItem;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.uws.ExecutionPhase;

public class VOTableWriter implements TableWriter
{
    public static final String XML_DECLARATION = "<?xml version=\"1.0\"?>";
    public static final String VOTABLE_VERSION  = "1.2";
    public static final String XSI_NS = "xmlns:xsi";
    public static final String XSI_NS_URI = "http://www.w3.org/2001/XMLSchema-instance";
    public static final String XSI_NO_NS_LOCATION = "xsi:noNamespaceSchemaLocation";
    public static final String XSI_NO_NS_LOCATION_URI = "http://www.ivoa.net/xml/VOTable/v1.2";
    public static final String STC_NS = "xmlns:stc";
    public static final String STC_NS_URI = "http://www.ivoa.net/xml/STC/v1.30";

    protected TapSchema tapSchema;

    protected List<TapSelectItem> selectList;

    public VOTableWriter() { }

    public String getExtension()
    {
        return "xml";
    }

    public void setSelectList(List<TapSelectItem> items)
    {
        this.selectList = items;
    }

    public void setTapSchema(TapSchema schema)
    {
        this.tapSchema = schema;
    }
    
    public void write(ResultSet resultSet, OutputStream output)
        throws IOException
    {
        if (selectList == null)
            throw new IllegalStateException("SelectList must be set using setSelectList(), it cannot be null.");
        if (tapSchema == null)
            throw new IllegalStateException("TapSchema must be set using setTapSchema(), it cannnot be null.");

        // Create the jdom document.
        Document document = new Document();

        // Root VOTABLE element.
        Element votable = new Element("VOTABLE");
        votable.setAttribute("version", VOTABLE_VERSION);
        votable.setNamespace(Namespace.getNamespace(XSI_NS_URI));
//        votable.addNamespaceDeclaration(Namespace.getNamespace(XSI_NO_NS_LOCATION_URI));
        votable.addNamespaceDeclaration(Namespace.getNamespace("stc", STC_NS_URI));
        document.addContent(votable);

        // Create the RESOURCE element and add to the VOTABLE element.
        Element resource = new Element("RESOURCE");
        votable.addContent(resource);

        // Create the TABLE element and add to the RESOURCE element.
        Element table = new Element("TABLE");
        resource.addContent(table);

        // Add the metadata elements.
        for (TapSelectItem selectItem : selectList)
            table.addContent(getMetaDataElement(selectItem));

        // Create the DATA element and add to the TABLE element.
        Element data = new Element("DATA");
        table.addContent(data);

        // Create the TABLEDATA element and add the to DATA element.
        Element tableData = new TableDataElement(resultSet);
        data.addContent(tableData);

        // Write out the VOTABLE.
        XMLOutputter outputter = new TableDataXMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        outputter.output(document, output);
    }
	
    public void write( Throwable thrown, OutputStream output )
        throws IOException
    {
         // Create the jdom document.
        Document document = new Document();

        // Root VOTABLE element.
        Element votable = new Element("VOTABLE");
        votable.setAttribute("version", VOTABLE_VERSION);
        votable.setNamespace(Namespace.getNamespace(XSI_NS_URI));
//        votable.addNamespaceDeclaration(Namespace.getNamespace(XSI_NO_NS_LOCATION_URI));
        votable.addNamespaceDeclaration(Namespace.getNamespace("stc", STC_NS_URI));
        document.addContent(votable);

        // Create the RESOURCE element and add to the VOTABLE element.
        Element resource = new Element("RESOURCE");
        votable.addContent(resource);

        // Create the INFO element and add to the RESOURCE element.
        Element info = new Element("INFO");
        info.setAttribute("name", "QUERY_STATUS");
        info.setAttribute("value", "ERROR");
        resource.addContent(info);

        // Create the DESCRIPTION element and add to the INFO element.
        Element description = new Element("DESCRIPTION");
        description.setText(getThrownExceptions(thrown));
        info.addContent(description);

        // Write out the VOTABLE.
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        outputter.output(document, output);
    }

    private Element getMetaDataElement(TapSelectItem selectItem)
    {
        Element field = new Element("FIELD");

        // TODO: assumes first schema is the one we want.
        Schema schema = tapSchema.schemas.get(0);
        for (Table table : schema.tables)
        {
            if (table.tableName.equals(selectItem.getTableName()))
            {
                for (Column column: table.columns)
                {
                    if (column.columnName.equals(selectItem.getColumnName()))
                    {
                        if (column.columnName != null)
                            field.setAttribute("name", column.columnName);
                        if (column.utype != null)
                            field.setAttribute("utype", column.utype);
                        if (column.ucd != null)
                            field.setAttribute("ucd", column.ucd);
                        if (column.unit != null)
                            field.setAttribute("unit", column.unit);
                        if (column.datatype != null)
                            field.setAttribute("datatype", column.datatype);
                        if (column.size != null)
                            field.setAttribute("width", String.valueOf(column.size));
                        if (column.description != null)
                        {
                            Element description = new Element("DESCRIPTION");
                            description.setText(column.description);
                            field.addContent(description);
                        }
                        break;
                    }
                }
            }
        }
        return field;
    }

    private String getThrownExceptions(Throwable thrown)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(thrown.getClass().getSimpleName());
        sb.append(": ");
        sb.append(thrown.getMessage() == null ? "" : thrown.getMessage());
        while (thrown.getCause() != null)
        {
            thrown = thrown.getCause();
            sb.append(" ");
            sb.append(thrown.getClass().getSimpleName());
            sb.append(": ");
            sb.append(thrown.getMessage() == null ? "" : thrown.getMessage());
        }
        return sb.toString();
    }

}
