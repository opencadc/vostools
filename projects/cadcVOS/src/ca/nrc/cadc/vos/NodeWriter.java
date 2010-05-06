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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/**
 * Writes a Node as XML to an output.
 * 
 * @author jburke
 */
public class NodeWriter
{
    /*
     * The VOSpace Namespace.
     */
    protected static Namespace defaultNamespace;
    protected static Namespace vosNamespace;
    protected static Namespace xsiNamespace;
    static
    {
        defaultNamespace = Namespace.getNamespace("http://www.ivoa.net/xml/VOSpace/v2.0");
        vosNamespace = Namespace.getNamespace("vos", "http://www.ivoa.net/xml/VOSpace/v2.0");
        xsiNamespace = Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
    }

    private static Logger log = Logger.getLogger(NodeWriter.class);

    /**
     * Write a ContainerNode to a StringBuilder.
     */
    public NodeWriter() { }

    public void write(ContainerNode node, StringBuilder builder)
        throws IOException
    {
        write(node, new StringBuilderWriter(builder));
    }

    /**
     * Write a ContainerNode to an OutputStream.
     *
     * @param node Node to write.
     * @param out OutputStream to write to.
     * @throws IOException if the writer fails to write.
     */
    public void write(ContainerNode node, OutputStream out)
        throws IOException
    {
        OutputStreamWriter outWriter;
        try
        {
            outWriter = new OutputStreamWriter(out, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("UTF-8 encoding not supported", e);
        }
        write(node, new BufferedWriter(outWriter));
    }

    /**
     * Write a ContainerNode to a Writer.
     *
     * @param node Node to write.
     * @param writer Writer to write to.
     * @throws IOException if the writer fails to write.
     */
    public void write(ContainerNode node, Writer writer)
        throws IOException
    {
        // Create the root node element
        Element root = getRootElement(node);

        // properties element
        root.addContent(getPropertiesElement(node));

        // nodes element
        root.addContent(getNodesElement(node));

        // write out the Document
        write(root, writer);
    }

    /**
     * Write a DataNode to a StringBuilder.
     * 
     * @param node Node to write.
     * @param builder StringBuilder to write to.
     * @throws IOException if the writer fails to write.
     */
    public void write(DataNode node, StringBuilder builder)
        throws IOException
    {
        write(node, new StringBuilderWriter(builder));
    }

    /**
     * Write a DataNode to an OutputStream.
     *
     * @param node Node to write.
     * @param out OutputStream to write to.
     * @throws IOException if the writer fails to write.
     */
    public void write(DataNode node, OutputStream out)
        throws IOException
    {
        OutputStreamWriter outWriter;
        try
        {
            outWriter = new OutputStreamWriter(out, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("UTF-8 encoding not supported", e);
        }
        write(node, new BufferedWriter(outWriter));
    }

    /**
     * Write a DataNode to a Writer.
     *
     * @param node Node to write.
     * @param writer Writer to write to.
     * @throws IOException if the writer fails to write.
     */
    public void write(DataNode node, Writer writer)
        throws IOException
    {
        // Create the root node element
        Element root = getRootElement(node);

        // busy attribute
        root.setAttribute("busy", (node.isBusy() ? "true" : "false"));

        // properties element
        root.addContent(getPropertiesElement(node));

        // write out the Document
        write(root, writer);
    }

    /**
     *  Build the root Element of a Node.
     *
     * @param node Node.
     * @return root Element.
     */
    protected Element getRootElement(Node node)
    {
        // Create the root element (node).
        Element root = new Element("node", defaultNamespace);
        root.addNamespaceDeclaration(vosNamespace);
        root.addNamespaceDeclaration(xsiNamespace);
        root.setAttribute("uri", node.getPath());
        root.setAttribute("type", "vos:" + node.getClass().getSimpleName() + "Type", xsiNamespace);
        return root;
    }

    /**
     * Build the properties Element of a Node.
     *
     * @param node Node.
     * @return properties Element.
     */
    protected Element getPropertiesElement(Node node)
    {
        Element properties = new Element("properties", defaultNamespace);
        for (NodeProperty nodeProperty : node.getProperties())
        {
            Element property = new Element("property", defaultNamespace);
            property.setAttribute("uri", nodeProperty.getPropertyURI());
            property.setText(nodeProperty.getPropertyValue());
            property.setAttribute("readOnly", (nodeProperty.isReadOnly() ? "true" : "false"));
            properties.addContent(property);
        }
        return properties;
    }

    /**
     * Build the nodes Element of a ContainerNode.
     * 
     * @param node Node.
     * @return nodes Element.
     */
    protected Element getNodesElement(ContainerNode node)
    {
        Element nodes = new Element("nodes", defaultNamespace);
        for (Node childNode : node.getNodes())
        {
            Element nodeElement = new Element("node", defaultNamespace);
            nodeElement.setAttribute("uri", childNode.getPath());
            nodes.addContent(nodeElement);
        }
        return nodes;
    }

    /**
     * Write to root Element to a writer.
     *
     * @param root Root Element to write.
     * @param writer Writer to write to.
     * @throws IOException if the writer fails to write.
     */
    protected void write(Element root, Writer writer)
        throws IOException
    {
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        outputter.output(new Document(root), writer);
    }

    /**
     * Class wraps a Writer around a StringBuilder.
     */
    public class StringBuilderWriter extends Writer
    {
        private StringBuilder sb;

        public StringBuilderWriter(StringBuilder sb)
        {
            this.sb = sb;
        }

        @Override
        public void write(char[] cbuf)
            throws IOException
        {
            sb.append(cbuf);
        }

        @Override
        public void write(char[] cbuf, int off, int len)
            throws IOException
        {
            sb.append(cbuf, off, len);
        }

        @Override
        public void write(int c)
            throws IOException
        {
            sb.append((char) c);
        }

        @Override
        public void write(String str)
            throws IOException
        {
            sb.append(str);
        }

        @Override
        public void write(String str, int off, int len)
            throws IOException
        {
            sb.append(str.substring(off, off + len));
        }

        @Override
        public void flush() throws IOException { }

        @Override
        public void close() throws IOException { }

        public void reset()
        {
            sb.setLength(0);
        }

    }

}
