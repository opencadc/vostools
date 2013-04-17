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
import java.net.URL;
import java.security.AccessControlException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.restlet.data.Encoding;
import org.restlet.data.MediaType;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.util.HexUtil;
import ca.nrc.cadc.util.StringBuilderWriter;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;
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

    private DateFormat dateFormat = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);

    // Default maximum number of nodes to display in the feed.
    private static final int DEFAULT_MAX_NUMBER_NODES = 10;
    
    // The RSS Feed element
    private Element feed;
    private String baseURL;
    
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
    
    @Override
    public void setNode(Node node, String viewReference, URL requestURL)
        throws UnsupportedOperationException, TransientException
    {
        super.setNode(node, viewReference, requestURL);
        if (!(node instanceof ContainerNode))
        {
            throw new UnsupportedOperationException("RssView is only for container nodes.");
        }

        if (nodePersistence == null)
        {
            throw new IllegalStateException("NodePersistence must be set.");
        }

        if (voSpaceAuthorizer == null)
        {
            throw new IllegalStateException("VOSpaceAuthorizer must be set.");
        }

        // Check we have Node read permissions.
        baseURL = getBaseURL(node, requestURL);
        xmlString = new StringBuilder();
        try
        {
            voSpaceAuthorizer.getReadPermission(node);
        }
        catch (AccessControlException e)
        {
            Element ef = RssFeed.createErrorFeed(node, e.getMessage(), baseURL);
            try
            {
                write(ef, new StringBuilderWriter(xmlString));
                return;
            }
            catch (IOException ioe)
            {
                throw new IllegalStateException(ioe);
            }
        }
        
        // TreeSet to hold the Nodes sorted by their date property.
        FixedSizeTreeSet<RssFeedItem> nodeSet = new FixedSizeTreeSet<RssFeedItem>();
        nodeSet.setMaxSize(maxNodes);
        addNodeToFeed((ContainerNode) node, nodeSet);

        // Build the RSS feed XML.
        feed = RssFeed.createFeed(node, nodeSet, baseURL);
        
        // Create a string version of the XML for metadata calculations
        try
        {
            write(xmlString);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Date getLastModified()
    {
        // must return the date from the first node in the list (most recent)
        return null; // for now, this forces client to read the feed
    }

    protected void addNodeToFeed(ContainerNode node, FixedSizeTreeSet set)
        throws TransientException
    {
        // Add Node to set if it has a valid last modified date.
        try
        {
            set.add(new RssFeedItem(getLastModifiedDate(node), node));
            log.debug("added container node to feed: " + node.getName());
        }
        catch (ParseException ignore) {}

        // Process all the child Nodes.
        nodePersistence.getChildren((ContainerNode) node);
        List<Node> children = ((ContainerNode) node).getNodes();
        for (Node child : children)
        {
            if (child instanceof ContainerNode)
            {
                try
                {
                    voSpaceAuthorizer.getReadPermission(child);
                    addNodeToFeed((ContainerNode) child, set);
                }
                catch(AccessControlException ignore) {  }
            }
            else
            {
                // Add Node to set if it has a valid last modified date.
                try
                {
                    set.add(new RssFeedItem(getLastModifiedDate(child), child));
                    log.debug("added data node to feed: " + child.getName());
                }
                catch (ParseException ignore) {}
            }
        }
    }

    // determine the base URL to the nodes resource
    String getBaseURL(Node n, URL r)
    {
        String nPath = n.getUri().getPath();
        StringBuilder sb = new StringBuilder();
        sb.append(r.getProtocol());
        sb.append("://");
        sb.append(r.getHost());
        if (r.getPort() > 0)
        {
            sb.append(":");
            sb.append(Integer.toString(r.getPort()));
        }
        String uPath = r.getPath();
        int i = uPath.indexOf(nPath);
        String basePath = uPath.substring(0, i);
        sb.append(basePath);
        return sb.toString();
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
            Element ef = RssFeed.createErrorFeed(node, e.getMessage(), baseURL);
            write(ef, writer);
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
     * RSSView not accepted for any nodes.
     */
    @Override
    public boolean canAccept(Node node)
    {
        return false;
    }
    
    /**
     * RSSView is provided for all container nodes.
     */
    @Override
    public boolean canProvide(Node node)
    {
        return (node instanceof ContainerNode);
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

    private Date getLastModifiedDate(Node node)
        throws ParseException
    {
        // Get the Nodes lastModified date.
        List<NodeProperty> nodeProperties = node.getProperties();
        String uriDate = null;
        for (NodeProperty nodeProperty : nodeProperties)
        {
            if (nodeProperty.getPropertyURI().equals(VOS.PROPERTY_URI_DATE))
                uriDate = nodeProperty.getPropertyValue();
        }

        // Try and parse uriDate into a Date.
        return dateFormat.parse(uriDate);
    }
    
}
