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

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;

public class NodeReader
{
    private static Logger log = Logger.getLogger(NodeReader.class);

    public static final String VOSPACE_SCHEMA = "http://www.ivoa.net/xml/VOSpace/v2.0";
    public static final String XML_SCHEMA_INSTANCE = "http://www.w3.org/2001/XMLSchema-instance";
    public static final String PARSER = "org.apache.xerces.parsers.SAXParser";

    protected SAXBuilder parser;
    protected Namespace xsiNamespace;

    public NodeReader()
    {
        parser = new SAXBuilder(PARSER, false);
        parser.setFeature("http://xml.org/sax/features/validation", true);
        parser.setFeature("http://apache.org/xml/features/validation/schema", true);
        parser.setFeature("http://apache.org/xml/features/validation/schema-full-checking", true);
        parser.setProperty("http://apache.org/xml/properties/schema/external-schemaLocation",
                           "http://www.ivoa.net/xml/VOSpace/v2.0 " + getVOSpaceSchema());

        xsiNamespace = Namespace.getNamespace(XML_SCHEMA_INSTANCE);        
    }

    public Node read(String xml)
        throws NodeParsingException
    {
        // Create a JDOM Document from the XML
        Document document;
        try
        {
            document = parser.build(new StringReader(xml));
        }
        catch (JDOMException jde)
        {
            String error = "XML failed schema validation: " + jde.getMessage();
            log.error(error, jde);
            throw new NodeParsingException(error, jde);
        }
        catch (IOException ioe)
        {
            String error = "Error reading XML: " + ioe.getMessage();
            log.error(error, ioe);
            throw new NodeParsingException(error, ioe);
        }

        // Root element and namespace of the Document
        Element root = document.getRootElement();
        Namespace namespace = root.getNamespace();
        log.debug("node namespace uri: " + namespace.getURI());
        log.debug("node namespace prefix: " + namespace.getPrefix());

        /* Node base elements */
        // uri attribute of the node element
        String uri = root.getAttributeValue("uri", namespace);
        if (uri == null)
        {
            String error = "uri attribute not found in node element";
            log.error(error);
            throw new NodeParsingException(error);
        }
        log.debug("node uri: " + uri);

        // Get the path part of the URI
        String path;
        try
        {
            URI nodeUri = new URI(uri);
            path = nodeUri.getPath();
        }
        catch (URISyntaxException ex)
        {
            String error = "Invalid node uri " + uri;
            log.error(error);
            throw new NodeParsingException(error, ex);
        }
        log.debug("node path: " + path);

        // Get the Node type (class) and instantiate a Node
        // Assuming attribute is called xsi:type a dumb thing to do?
        String xsiType = root.getAttributeValue("type", xsiNamespace);
        if (xsiType == null)
        {
            String error = "type attribute not found in node element " + uri;
            log.error(error);
            throw new NodeParsingException(error);
        }

        // Split type into namespace and type value
        String[] types = xsiType.split(":");
        String type = types[1];
        log.debug("node type: " + type);

        if (type.equals(ContainerNode.class.getSimpleName()))
            return readContainerNode(root, namespace, uri, path, type);
        else if (type.equals(DataNode.class.getSimpleName()))
            return readDataNode(root, namespace, uri, path, type);
        else
            throw new NodeParsingException("unknown node type " + type);
    }

    protected String getVOSpaceSchema()
    {
        return VOSPACE_SCHEMA;
    }

    protected Node readContainerNode(Element root, Namespace namespace, String uri, String path, String type)
        throws NodeParsingException
    {
        // Instantiate a ContainerNode class
        String className = "ca.nrc.cadc.vos." + type;
        ContainerNode node;
        try
        {
            Class nodeClass = Class.forName(className);
            Class[] args = new Class[] { String.class };
            Constructor constructor = nodeClass.getDeclaredConstructor(args);
            node = (ContainerNode) constructor.newInstance(new Object[] { path });
        }
        catch (Exception e)
        {
            String error = "Error creating class " + className;
            log.error(error, e);
            throw new NodeParsingException(error, e);
        }

        // properties element
        node.setProperties(getProperties(root, namespace));

        // nodes element
        Element nodes = root.getChild("nodes", namespace);
        if (nodes == null)
        {
            String error = "nodes element not found in node";
            log.error(error);
            throw new NodeParsingException(error);
        }

        // list of child nodes
        List<Element> nodesList = nodes.getChildren("node", namespace);
        for (Element childNode : nodesList)
        {
            String childNodeUri = childNode.getAttributeValue("uri");
            if (childNodeUri == null)
            {
                String error = "uri attribute not found in nodes node element";
                log.error(error);
                throw new NodeParsingException(error);
            }
            // TODO: create DataNode for now, recurse later?
            node.getNodes().add(new DataNode(childNodeUri));
            log.debug("child node: " + childNodeUri);
        }

        return node;
    }

    protected Node readDataNode(Element root, Namespace namespace, String uri, String path, String type)
        throws NodeParsingException
    {
        // Instantiate a DataNode class
        String className = "ca.nrc.cadc.vos." + type;
        DataNode node;
        try
        {
            Class nodeClass = Class.forName(className);
            Class[] args = new Class[] { String.class };
            Constructor constructor = nodeClass.getDeclaredConstructor(args);
            node = (DataNode) constructor.newInstance(new Object[] { path });
        }
        catch (Exception e)
        {
            String error = "Error creating class " + className;
            log.error(error, e);
            throw new NodeParsingException(error, e);
        }

        // busy attribute
        String busy = root.getAttributeValue("busy");
        if (busy == null)
        {
            String error = "busy element not found in DataNode";
            log.error(error);
            throw new NodeParsingException(error);
        }
        boolean isBusy = busy.equalsIgnoreCase("true") ? true : false;
        node.setBusy(isBusy);
        log.debug("busy: " + isBusy);

        // properties element
        node.setProperties(getProperties(root, namespace));

        // accepts element
        // TODO: add accepts element

        // provides element
        // TODO: add provides element

        return node;
    }

    protected List getProperties(Element root, Namespace namespace)
        throws NodeParsingException
    {
        // properties element
        Element properties = root.getChild("properties", namespace);
        if (properties == null)
        {
            String error = "properties element not found";
            log.error(error);
            throw new NodeParsingException(error);
        }

        // new NodeProperty List
        List<NodeProperty> list = new ArrayList<NodeProperty>();

        // properties property elements
        List<Element> propertyList = properties.getChildren("property", namespace);
        for (Element property : propertyList)
        {
            String propertyUri = property.getAttributeValue("uri");
            if (propertyUri == null)
            {
                String error = "uri attribute not found in property element " + property;
                log.error(error);
                throw new NodeParsingException(error);
            }
            list.add(new NodeProperty(propertyUri, property.getText(), true));
        }

        return list;
    }

    protected List getViews(Element root, Namespace namespace, String parent)
        throws NodeParsingException
    {
        // view parent element
        Element accepts = root.getChild(parent, namespace);
        if (accepts == null)
        {
            String error = parent + " element not found in node";
            log.error(error);
            throw new NodeParsingException(error);
        }

        // new View List
        List<View> list = new ArrayList<View>();

        // view elements
        List<Element> viewList = accepts.getChildren("view", namespace);
        for (Element view : viewList)
        {
            // view uri attribute
            String viewUri = view.getAttributeValue("uri");
            if (viewUri == null)
            {
                String error = "uri attribute not found in " + parent + " view element";
                log.error(error);
                throw new NodeParsingException(error);
            }
            log.debug(parent + "view uri: " + viewUri);

            // new View
//                View acceptsView = new View(viewUri, node);

            // view original attribute
            String original = view.getAttributeValue("original");
            if (original != null)
            {
                boolean isOriginal = original.equalsIgnoreCase("true") ? true : false;
//                    view.setOriginal(isOriginal);
                log.debug(parent + " view original: " + isOriginal);
            }

            List<Element> paramList = view.getChildren("param", namespace);
            for (Element param : paramList)
            {
                String paramUri = param.getAttributeValue("uri");
                if (paramUri == null)
                {
                    String error = "param uri attribute not found in accepts view element";
                    log.error(error);
                    throw new NodeParsingException(error);
                }
                log.debug("accepts view param uri: " + paramUri);
                // TODO: what are params???
            }
        }

        return list;
    }
    
}
