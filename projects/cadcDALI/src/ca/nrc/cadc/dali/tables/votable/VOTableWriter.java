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

import ca.nrc.cadc.dali.tables.TableWriter;
import ca.nrc.cadc.dali.util.Format;
import ca.nrc.cadc.dali.util.FormatFactory;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.XMLOutputter;

/**
 *
 * @author pdowler
 */
public class VOTableWriter implements TableWriter<VOTable>
{
    private static final Logger log = Logger.getLogger(VOTableWriter.class);

    public static final String CONTENT_TYPE = "text/x-votable+xml";
    
    // VOTable Version number.
    public static final String VOTABLE_VERSION  = "1.2";

    // Uri to the XML schema.
    public static final String XSI_SCHEMA = "http://www.w3.org/2001/XMLSchema-instance";

    // Uri to the VOTable schema.
    public static final String VOTABLE_11_NS_URI = "http://www.ivoa.net/xml/VOTable/v1.1";
    public static final String VOTABLE_12_NS_URI = "http://www.ivoa.net/xml/VOTable/v1.2";

    private boolean binaryTable;

    /**
     * Default constructor.
     */
    public VOTableWriter()
    {
        this(false);
    }

    /**
     * Create a VOTableWriter and optionally support binary TABLEDATA cells (not
     * yet implemented.)
     *
     * @param binaryTable
     */
    public VOTableWriter(boolean binaryTable) 
    {
        this.binaryTable = binaryTable;
    }

    /**
     * Get the Content-Type for the VOTable.
     *
     * @return  VOTable Content-Type.
     */
    public String getContentType()
    {
        return CONTENT_TYPE;
    }

    /**
     * Get the extension for the VOTable.
     *
     * @return VOTable extension.
     */
    public String getExtension()
    {
        return "xml";
    }

    /**
     * Write the VOTable to the specified OutputStream.
     *
     * @param votable VOTable object to write.
     * @param ostream OutputStream to write to.
     * @throws IOException if problem writing to OutputStream.
     */
    public void write(VOTable votable, OutputStream ostream)
        throws IOException
    {
        write(votable, ostream, Long.MAX_VALUE);
    }

    /**
     * Write the VOTable to the specified OutputStream, only writing maxrec rows.
     * If the VOTable contains more than maxrec rows, appends an INFO element with
     * name="QUERY_STATUS" value="OVERFLOW" to the VOTable.
     *
     * @param votable VOTable object to write.
     * @param ostream OutputStream to write to.
     * @param maxRec maximum number of rows to write.
     * @throws IOException if problem writing to OutputStream.
     */
    public void write(VOTable votable, OutputStream ostream, Long maxrec)
        throws IOException
    {
        Writer writer = new BufferedWriter(new OutputStreamWriter(ostream, "UTF-8"));
        write(votable, writer, maxrec);
    }

    /**
     * Write the VOTable to the specified Writer.
     *
     * @param votable VOTable object to write.
     * @param writer Writer to write to.
     * @throws IOException if problem writing to the writer.
     */
    public void write(VOTable votable, Writer writer)
        throws IOException
    {
        write(votable, writer, Long.MAX_VALUE);
    }

    /**
     * Write the VOTable to the specified Writer, only writing maxrec rows.
     * If the VOTable contains more than maxrec rows, appends an INFO element with
     * name="QUERY_STATUS" value="OVERFLOW" to the VOTable.
     *
     * @param votable VOTable object to write.
     * @param writer Writer to write to.
     * @param maxRec maximum number of rows to write.
     * @throws IOException if problem writing to the writer.
     */
    public void write(VOTable votable, Writer writer, Long maxrec)
        throws IOException
    {
        // VOTable document and root element.
        Document document = createDocument();
        Element root = document.getRootElement();
        Namespace namespace = root.getNamespace();

        // Create the RESOURCE element and add to the VOTABLE element.
        Element resource = new Element("RESOURCE", namespace);
        if (votable.getResourceName() != null)
        {
            resource.setAttribute("name", votable.getResourceName());
        }
        root.addContent(resource);

        // Create the INFO element and add to the RESOURCE element.
        for (Info in : votable.getInfos())
        {
            Element info = new Element("INFO", namespace);
            info.setAttribute("name", in.getName());
            info.setAttribute("value", in.getValue());
            resource.addContent(info);
        }

        // Create the TABLE element and add to the RESOURCE element.
        Element table = new Element("TABLE", namespace);
        resource.addContent(table);
        
        // Add the metadata elements.
        for (TableParam param : votable.getParams())
        {
            table.addContent(new ParamElement(param, namespace));
        }
        for (TableField field : votable.getColumns())
        {
            table.addContent(new FieldElement(field, namespace));
        }
        
        // Create the DATA and TABLEDATA elements.
        Element data = new Element("DATA", namespace);
        table.addContent(data);
        Element tabledata = new Element("TABLEDATA", namespace);
        data.addContent(tabledata);
        
        // Add content.
        int rowCount = 0;
        try
        {
            Iterator<List<Object>> it = votable.getTableData().iterator();
            while (it.hasNext())
            {
                // If maxRec reached, write overflow INFO and exit loop.
                if (rowCount++ > maxrec)
                {
                    Element info = new Element("INFO", namespace);
                    info.setAttribute("name", "QUERY_STATUS");
                    info.setAttribute("value", "OVERFLOW");
                    resource.addContent(info);
                    break;
                }

                // TR element.
                Element tr = new Element("TR", namespace);
                tabledata.addContent(tr);

                // TD elements.
                List<Object> columns = it.next();
                for (int i = 0; i < columns.size(); i++)
                {
                    Object column = columns.get(i);
                    Class c = null;
                    if (column != null)
                    {
                        c = column.getClass();
                    }
                    Format format = FormatFactory.getFormat(c);
                    Element td = new Element("TD", namespace);
                    td.setText(format.format(column));
                    tr.addContent(td);
                }
            }
        }
        catch(Throwable t)
        {
            log.debug("failure while iterating", t);
            Element info = new Element("INFO", namespace);
            info.setAttribute("name", "QUERY_STATUS");
            info.setAttribute("value", "ERROR");
            info.setText(t.toString());
            resource.addContent(info);
        }

        // Write out the VOTABLE.
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(org.jdom2.output.Format.getPrettyFormat());
        outputter.output(document, writer);
    }

    /**
     * Write the Throwable to a VOTable, creating an INFO element with
     * name="QUERY_STATUS" value="ERROR" and setting the stacktrace as
     * the INFO text.
     *
     * @param thrown Throwable to write.
     * @param output OutputStream to write to.
     * @throws IOException if problem writing to the stream.
     */
    public void write(Throwable thrown, OutputStream output)
        throws IOException
    {
        Document document = createDocument();
        Element root = document.getRootElement();
        Namespace namespace = root.getNamespace();

        // Create the RESOURCE element and add to the VOTABLE element.
        Element resource = new Element("RESOURCE", namespace);
        resource.setAttribute("type", "results");
        root.addContent(resource);

        // Create the INFO element and add to the RESOURCE element.
        Element info = new Element("INFO", namespace);
        info.setAttribute("name", "QUERY_STATUS");
        info.setAttribute("value", "ERROR");
        info.setText(getThrownExceptions(thrown));
        resource.addContent(info);

        // Write out the VOTABLE.
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(org.jdom2.output.Format.getPrettyFormat());
        outputter.output(document, output);
    }

    /**
     * Builds a empty VOTable document with the appropriate namespaces and
     * attributes.
     *
     * @return VOTable document.
     */
    protected Document createDocument()
    {
        // the root VOTABLE element
        Namespace vot = Namespace.getNamespace(VOTABLE_12_NS_URI);
        Namespace xsi = Namespace.getNamespace("xsi", XSI_SCHEMA);
        Element votable = new Element("VOTABLE", vot);
        votable.setAttribute("version", VOTABLE_VERSION);
        votable.addNamespaceDeclaration(xsi);
        
        Document document = new Document();
        document.addContent(votable);
        
        return document;
    }

    // Build a String containing the nested Exception messages.
    private String getThrownExceptions(Throwable thrown)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(thrown.getClass().getSimpleName());
        sb.append(": ");
        if (thrown.getMessage() == null)
            sb.append("");
        else
            sb.append(thrown.getMessage());
        while (thrown.getCause() != null)
        {
            thrown = thrown.getCause();
            sb.append(" ");
            sb.append(thrown.getClass().getSimpleName());
            sb.append(": ");
            if (thrown.getMessage() == null)
                sb.append("");
            else
                sb.append(thrown.getMessage());
        }
        return sb.toString();
    }

}
