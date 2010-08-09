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
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.restlet.Request;
import org.restlet.data.Encoding;
import org.restlet.data.MediaType;

import ca.nrc.cadc.util.HexUtil;
import ca.nrc.cadc.util.StringBuilderWriter;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.server.util.FixedSizeTreeSet;

/**
 * Writes a RSS feed consisting of the late modified child nodes of the
 * given node to an output.
 *
 * @author jburke
 */
public class RssView extends AbstractView
{
    private static Logger log = Logger.getLogger(RssView.class);

    // Default maximum number of nodes to display in the feed.
    private static final int DEFAULT_MAX_NUMBER_NODES = 10;
    
    // The RSS Feed element
    private Element feed;
    
    // A String version of the XML
    StringBuilder xmlString;

    /**
     * Maximum number of nodes to display.
     */
    protected int maxNodes;
    
    /**
     * RssView constructor.
     */
    public RssView()
    {
        super();
        maxNodes = DEFAULT_MAX_NUMBER_NODES;
    }
    
    /**
     * RssView constructor.
     * @param uri
     */
    public RssView(URI uri)
    {
        super(uri);
        maxNodes = DEFAULT_MAX_NUMBER_NODES;
    }
    
    /**
     * Setup the RRSView with the given node.
     */
    @Override
    public void setNode(Node node, Request request, String viewReference) throws UnsupportedOperationException
    {
        super.setNode(node, request, viewReference);
        if (!(node instanceof ContainerNode))
        {
            throw new UnsupportedOperationException("RssView is only for container nodes.");
        }
        
        // TreeSet to hold the Nodes sorted by their date property.
        FixedSizeTreeSet<Node> nodeSet = new FixedSizeTreeSet<Node>();
        nodeSet.setMaxSize(maxNodes);
        nodeSet.addAll(((ContainerNode) node).getNodes());

        // Build the RSS feed XML.
        feed = RssFeed.createFeed(node, nodeSet);
        
        // Create a string version of the XML for metadata calculations
        xmlString = new StringBuilder();
        try
        {
            write(xmlString);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Write a RSS feed listing any updated Nodes within the specified
     * ContainerNode to the specified StringBuilder.
     *
     * @param builder StringBuilder to write to.
     * @throws IOException thrown if there was some problem writing to the StringBuilder.
     */
    public void write(StringBuilder builder)
        throws IOException
    {
        write(new StringBuilderWriter(builder));
    }

    /**
     * Write a RSS feed listing any updated Nodes within the specified
     * ContainerNode to the specified OutputStream.
     *
     * @param out OutputStream to write to.
     * @throws IOException thrown if there was some problem writing to the OutputStream.
     */
    @Override
    public void write(OutputStream out)
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
        write(new BufferedWriter(outWriter));
    }

    /**
     * Write a RSS feed listing any updated Nodes within the specified
     * ContainerNode to the specified Writer.
     *
     * @param writer Writer to write to.
     * @throws IOException thrown if there was some problem writing to the Writer.
     */
    public void write(Writer writer)
        throws IOException
    {
        try
        {
            // Write out the feed.
            write(feed, writer);
        }
        catch (Exception e)
        {
            log.debug(e);
            Element feed = RssFeed.createErrorFeed(node, e.getMessage());
            write(feed, writer);
        }
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
        Document document = new Document();
        root.detach();
        document.setRootElement(root);
        outputter.output(document, writer);
    }
    
    /**
     * Return the content length of the data for the view.
     */
    @Override
    public long getContentLength()
    {
        return xmlString.length();
    }
    
    /**
     * Return the content type of the data for the view.
     */
    @Override
    public MediaType getMediaType()
    {
        return MediaType.APPLICATION_RSS;
    }
    
    /**
     * Return the content encoding of the data for the view.
     */
    @Override
    public List<Encoding> getEncodings()
    {
        return new ArrayList<Encoding>(0);
    }
    
    /**
     * Return the MD5 Checksum of the data for the view.
     */
    @Override
    public String getContentMD5()
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] md5hash = new byte[32];
            md.update(xmlString.toString().getBytes("iso-8859-1"), 0, xmlString.toString().length());
            md5hash = md.digest();
            return HexUtil.toHex(md5hash);
        }
        catch (NoSuchAlgorithmException e)
        {
            log.warn("Algorithm MD5 not found.", e);
        }
        catch (UnsupportedEncodingException e)
        {
            log.warn("ISO-8859-1 encoding not found.", e);
        }
        return null;
    }

}
