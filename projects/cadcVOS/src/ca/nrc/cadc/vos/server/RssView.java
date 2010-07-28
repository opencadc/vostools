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

import ca.nrc.cadc.util.StringBuilderWriter;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.View;
import ca.nrc.cadc.vos.server.util.FixedSizeTreeSet;
import ca.nrc.cadc.vos.server.util.NodeWalker;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URI;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/**
 * Writes a RSS feed consisting of the late modified child nodes of the
 * given node to an output.
 *
 * @author jburke
 */
public class RssView extends View
{
    private static Logger log = Logger.getLogger(RssView.class);

    // Default maximum number of nodes to display in the feed.
    private static final int DEFAULT_MAX_NUMBER_NODES = 10;

    // ContainerNode
    protected Node node;
    
    /**
     * Job persistence
     */
    protected NodeDAO nodeDAO;

    /**
     * Maximum number of nodes to display.
     */
    protected int maxNodes;

    /**
     * View constructor.
     *
     * @param uri The view identifier.
     * @param node The node applicable to the view.
     */
    public RssView(URI uri, Node node)
    {
        super(uri);
        this.node = node;

        // Set the default maximum number of nodes to return.
        maxNodes = DEFAULT_MAX_NUMBER_NODES;
    }

    /**
     * Set the NodePersistence to use.
     *
     * @param nodeDAO
     */
    public void setNodeDAO(NodeDAO nodeDAO)
    {
        this.nodeDAO = nodeDAO;
    }

    /**
     * Set the maximum number of nodes to return in the feed.
     *
     * @param maxNodes
     */
    public void setMaxNodes(int maxNodes)
    {
        this.maxNodes = maxNodes;
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
        // Check we have persistence.
        if (nodeDAO == null)
            throw new IllegalStateException("NodeDAO must be set using setNodeDAO()");

        // Check that the Node specified is a ContainerNode.
        if (!(node instanceof ContainerNode))
        {
            Element feed = RssFeed.createErrorFeed(node, "RssView only supports ContainerNodes");
            write(feed, writer);
            return;
        }

        try
        {
            // TreeSet to hold the Nodes sorted by their date property.
            FixedSizeTreeSet<Node> nodeSet = new FixedSizeTreeSet<Node>();
            nodeSet.setMaxSize(maxNodes);

            // Fill the Set with the node and all child nodes.
            NodeWalker walker = new NodeWalker(nodeDAO);
            walker.traverseNodes(node, null, nodeSet);

            // Build the RSS feed XML.
            Element feed = RssFeed.createFeed(node, nodeSet);

            // Write out the feed.
            write(feed, writer);
        }
        catch (Exception e)
        {
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
        outputter.output(new Document(root), writer);
    }

}
