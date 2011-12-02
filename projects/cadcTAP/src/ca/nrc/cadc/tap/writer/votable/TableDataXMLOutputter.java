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

package ca.nrc.cadc.tap.writer.votable;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.jdom.Comment;
import org.jdom.DocType;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.ProcessingInstruction;
import org.jdom.output.XMLOutputter;

import ca.nrc.cadc.tap.schema.TapSchema;

/**
 * Extends JDOM XMLOutputter and outputs a custom TABLEDATA element.
 *
 */
public class TableDataXMLOutputter extends XMLOutputter
{
    @SuppressWarnings("unused")
    private static Logger log = Logger.getLogger(TableDataXMLOutputter.class);

    // XML declaration.
    private static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    // Line separator.
    private static final String NEW_LINE = System.getProperty("line.separator");

    // Number of TableData rows written;
    private int rowCount;

    // Max number of rows to write.
    protected int maxRows;

    // Indicates an error occurred writing results.
    protected boolean error;

    /**
     * Default Constructor.
     */
    public TableDataXMLOutputter(int maxRows)
    {
        super();
        this.maxRows = maxRows;
        this.rowCount = 0;
    }

    /**
     * Writes the Document. First writes the XML Declaration, then
     * iterates through the document content calling the appropriate
     * print method for the content type.
     *
     * Method is overridden to avoid the parent method's use of the
     * size method for a List, which the ResultSetList implementation
     * does not support.
     *
     * @param document to write
     * @param out writer.
     * @throws IOException
     */
    @Override
    public void output(Document document, Writer out) throws IOException
    {
        out.write(XML_DECLARATION);
        out.write(NEW_LINE);
        List content = document.getContent();
        for (Iterator iterator = content.iterator(); iterator.hasNext();)
        {
            Object object = iterator.next();
            if (object instanceof Element)
            {
                printElement(out, (Element) object, 0, new MyNamespaceStack());
            }
            else if (object instanceof Comment)
            {
                printComment(out, (Comment) object);
            }
            else if (object instanceof ProcessingInstruction)
            {
                printProcessingInstruction(out, (ProcessingInstruction) object);
            }
            else if (object instanceof DocType)
            {
                printDocType(out, document.getDocType());
            }
        }
        out.flush();
    }

    /**
     * Prints a JDOM Element. If the element is a TableDataElement, prints out the
     * TABLEDATA tag, then iterates through element's content calling the parent
     * method. If the element is not a TableDataElement, calls the parent method.
     *
     * @param out writer
     * @param element element to write
     * @param level
     * @param namespaces
     * @throws IOException
     */
    @Override
    protected void printElement(Writer out, Element element, int level, XMLOutputter.NamespaceStack namespaces) throws IOException
    {
        if (element.getName().equalsIgnoreCase("TABLE"))
        {
            super.printElement(out, element, level, namespaces);
            if (rowCount > maxRows)
            {
                out.write(NEW_LINE);
                Element info = new Element("INFO", element.getNamespace());
                info.setAttribute("name", "QUERY_STATUS");
                info.setAttribute("value", "OVERFLOW");
                super.printElement(out, info, level, namespaces);
            }
            if (error)
            {
                out.write(NEW_LINE);
                Element info = new Element("INFO", element.getNamespace());
                info.setAttribute("name", "QUERY_STATUS");
                info.setAttribute("value", "ERROR");
                super.printElement(out, info, level, namespaces);
            }
        }
        else if (element instanceof TableDataElement)
        {
            out.write("<");
            out.write(element.getQualifiedName());
            out.write(">");
            out.write(NEW_LINE);
            List content = element.getContent();
            for (Iterator iterator = content.iterator(); iterator.hasNext();)
            {
                rowCount++;
                if (rowCount > maxRows) break;
                out.write(getIndentLevel(level + 1));
                try
                {
                    Element row = (Element) iterator.next();
                    super.printElement(out, row, level + 1, namespaces);
                }
                catch (Throwable t)
                {
                    log.error("failed while iterating over TableDataElement", t);
                    error = true;
                    break;
                }
                out.write(NEW_LINE);
            }
            out.write(getIndentLevel(level));
            out.write("</");
            out.write(element.getQualifiedName());
            out.write(">");
        }
        else
        {
            super.printElement(out, element, level, namespaces);
        }
    }

    protected class MyNamespaceStack extends NamespaceStack
    {
        MyNamespaceStack()
        {
        }
    }

    private String getIndentLevel(int level)
    {
        if (getFormat().getIndent() == null || getFormat().getIndent().equals("")) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++)
            sb.append(getFormat().getIndent());
        return sb.toString();
    }

}
