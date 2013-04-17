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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.List;

import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import ca.nrc.cadc.util.StringBuilderWriter;

/**
 * Class responsible for creating the XML version of the Views resource.
 * 
 * @author majorb
 *
 */
public class ViewsWriter
{
    
    /*
     * The VOSpace Namespaces.
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

    private static Logger log = Logger.getLogger(ViewsWriter.class);
    
    /**
     * ViewsWriter constructor.
     */
    public ViewsWriter()
    {
    }
    
    /**
     * Write accepts and provides to a StringBuilder.
     */
    public void write(List<String> accepts, List<String> provides, StringBuilder builder) throws IOException
    {
        write(accepts, provides, new StringBuilderWriter(builder));
    }

    /**
     * Write accepts and provides to an OutputStream.
     *
     * @param node Node to write.
     * @param out OutputStream to write to.
     * @throws IOException if the writer fails to write.
     */
    public void write(List<String> accepts, List<String> provides, OutputStream out) throws IOException
    {
        OutputStreamWriter outWriter;
        try
        {
            outWriter = new OutputStreamWriter(out, "UTF-8");
        } catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("UTF-8 encoding not supported", e);
        }
        write(accepts, provides, new BufferedWriter(outWriter));        
    }

    /**
     * Write accepts and provides to a Writer.
     *
     * @param node Node to write.
     * @param writer Writer to write to.
     * @throws IOException if the writer fails to write.
     */
    public void write(List<String> accepts, List<String> provides, Writer writer) throws IOException
    {
        // Create the root node element
        Element root = getRootElement();

        // accepts element
        root.addContent(getViewsListElement("accepts", accepts));

        // provides element
        root.addContent(getViewsListElement("provides", provides));

        // write out the Document
        write(root, writer);
    }
    
    /**
     * Create the root views element.
     * @return
     */
    protected Element getRootElement()
    {
        // Create the root element (node).
        Element root = new Element("views", defaultNamespace);
        root.addNamespaceDeclaration(vosNamespace);
        root.addNamespaceDeclaration(xsiNamespace);
        return root;
    }
    
    /**
     * Create a URI list for the views.
     * @param name
     * @param uris
     * @return
     */
    protected Element getViewsListElement(String name, List<String> uris)
    {
        Element viewList = new Element(name, defaultNamespace);
        for (String uri : uris)
        {
            Element property = new Element("view", defaultNamespace);
            property.setAttribute("uri", uri);
            viewList.addContent(property);
        }
        return viewList;
    }
    
    /**
     * Write to root Element to a writer.
     *
     * @param root Root Element to write.
     * @param writer Writer to write to.
     * @throws IOException if the writer fails to write.
     */
    protected void write(Element root, Writer writer) throws IOException
    {
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        outputter.output(new Document(root), writer);
    }

}
