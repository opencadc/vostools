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

import java.util.Collection;

import org.apache.log4j.Logger;
import org.jdom2.Element;

import ca.nrc.cadc.util.StringUtil;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;

/**
 * 
 * @author jburke
 */
public class RssFeed
{
    private static Logger log = Logger.getLogger(RssFeed.class);

    /**
     * Builds a JDOM Element representing the RRS feed of set of Nodes.
     *
     * @param parent The parent Node of all other Nodes in the feed.
     * @param nodes Collection of nodes to display in the feed.
     * @param baseURL base url for the nodes resource
     * @return JDOM Element representing the RRS feed.
     */
    public static Element createFeed(Node parent, Collection<RssFeedItem> nodes, String baseURL)
    {
        // Get the title and description from the parent properties.
        String parentTitle = null;
        String parentDescription = null;
        for (NodeProperty nodeProperty : parent.getProperties())
        {
            if (nodeProperty.getPropertyURI().equals(VOS.PROPERTY_URI_TITLE))
            {
                parentTitle = nodeProperty.getPropertyValue();
            }
            if (nodeProperty.getPropertyURI().equals(VOS.PROPERTY_URI_DESCRIPTION))
            {
                parentDescription = nodeProperty.getPropertyValue();
            }
        }

        // Root element.
        Element rss = new Element("rss");
        rss.setAttribute("version", "2.0");

        // channel element.
        Element channel = new Element("channel");
        rss.addContent(channel);

        // channel title.
        if (parentTitle == null)
        {
            parentTitle = "Last modified nodes for " + parent.getName();
        }
        Element channelTitle = new Element("title");
        channelTitle.setText(parentTitle);
        channel.addContent(channelTitle);

        // channel description.
        if (parentDescription == null)
        {
            parentDescription = nodes.size() + " last modified nodes";
        }
        Element channelDescription = new Element("description");
        channelDescription.setText(parentDescription);
        channel.addContent(channelDescription);

        // channel link.
        Element link = new Element("link");
        link.setText(baseURL + parent.getUri().getPath() + "?view=rss");
        channel.addContent(link);

        // channel author
        NodeProperty creator = parent.findProperty(VOS.PROPERTY_URI_CREATOR);
        if (creator != null && StringUtil.hasText(creator.getPropertyValue()))
        {
            Element author = new Element("author");
            author.setText(creator.getPropertyValue());
            channel.addContent(author);
        }
        log.debug("num nodes: " + nodes.size());
        // Create an item for each Node and add to channel.
        for (RssFeedItem rssFeedItem : nodes)
        {
            // Get the title, description, and date from the node properties.
            String nodeTitle = null;
            String nodeDescription = null;
            String nodeDate = null;
            for (NodeProperty nodeProperty : rssFeedItem.node.getProperties())
            {
                if (nodeProperty.getPropertyURI().equals(VOS.PROPERTY_URI_TITLE))
                {
                    nodeTitle = nodeProperty.getPropertyValue();
                }
                if (nodeProperty.getPropertyURI().equals(VOS.PROPERTY_URI_DESCRIPTION))
                {
                    nodeDescription = nodeProperty.getPropertyValue();
                }
                if (nodeProperty.getPropertyURI().equals(VOS.PROPERTY_URI_DATE))
                {
                    nodeDate = nodeProperty.getPropertyValue();
                }
            }

            // item element.
            Element item = new Element("item");

            // item title.
            if (nodeTitle == null)
            {
                nodeTitle = rssFeedItem.node.getName();
            }
            Element itemTitle = new Element("title");
            itemTitle.setText(nodeTitle);
            item.addContent(itemTitle);

            // item description.
            if (nodeDescription == null)
            {
                if (rssFeedItem.node instanceof DataNode)
                {
                    nodeDescription = "File";
                }
                else
                {
                    nodeDescription = "Directory";
                }
            }
            Element itemDescription = new Element("description");
            itemDescription.setText(nodeDescription);
            item.addContent(itemDescription);

            // item pubDate
            if (nodeDate != null)
            {
                Element itemPubDate = new Element("pubDate");
                itemPubDate.setText(nodeDate);
                item.addContent(itemPubDate);
            }

            // item link, comment
            Element itemLink = new Element("link");
            Element comments = new Element("comments");
            String linkText = baseURL + rssFeedItem.node.getUri().getPath();
            if (rssFeedItem.node instanceof DataNode)
            {
                linkText += "?view=data";
                comments.setText("Click to download this file.");
            }
            else
            {
                linkText += "?view=rss";
                comments.setText("Click to see the last modified nodes within this directory.");
            }
            itemLink.setText(linkText);
            item.addContent(itemLink);
            item.addContent(comments);

            // item author
            creator = rssFeedItem.node.findProperty(VOS.PROPERTY_URI_CREATOR);
            if (creator != null && StringUtil.hasText(creator.getPropertyValue()))
            {
                Element author = new Element("author");
                author.setText(creator.getPropertyValue());
                item.addContent(author);
            }

            // Add item to channel
            channel.addContent(item);
        }
        return rss;
    }

    /**
     * Builds a JDOM Element representing a RRS feed that displays an error state.
     *
     * @param node The Node causing the error.
     * @param throwable The Throwable containing the message to display.
     * @param baseURL base url for the nodes resource
     * @return JDOM Element representing the RRS feed.
     */
    public static Element createErrorFeed(Node node, Throwable throwable, String baseURL)
    {
        return createErrorFeed(node, throwable.getMessage(), baseURL);
    }

    /**
     * Builds a JDOM Element representing a RRS feed that displays an error state.
     *
     * @param node The Node causing the error.
     * @param message The message to display.
     * @param baseURL base url for the nodes resource
     * @return JDOM Element representing the RRS feed.
     */
    public static Element createErrorFeed(Node node, String message, String baseURL)
    {
        // Root element.
        Element rss = new Element("rss");
        rss.setAttribute("version", "2.0");

        // channel element.
        Element channel = new Element("channel");
        rss.addContent(channel);

        // channel title.
        Element title = new Element("title");
        String nodeName = node.getName();
        if (nodeName == null)
        {
            nodeName = "unknown node";
        }
        title.setText("Error processing Node " + nodeName);
        channel.addContent(title);

        // channel link.
        Element link = new Element("link");
        String nodePath = node.getUri().getPath();
        if (nodePath == null)
        {
            link.setText("unknown link");
        }
        else
        {
            link.setText(baseURL + nodePath + "?view=rss");
        }
        channel.addContent(link);

        // channel description.
        Element description = new Element("description");
        description.setText(message);
        channel.addContent(description);

        return rss;
    }

}
