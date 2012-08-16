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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;

import ca.nrc.cadc.vos.VOS.NodeBusyState;
import ca.nrc.cadc.xml.XmlUtil;

/**
 * Constructs a Node from an XML source. This class is not thread safe but it is
 * re-usable  so it can safely be used to sequentially parse multiple XML node
 * documents.
 *
 * @author jburke
 */
public class NodeReader
{
    private static final String VOSPACE_SCHEMA_URL = "http://www.ivoa.net/xml/VOSpace/v2.0";
    private static final String VOSPACE_SCHEMA_RESOURCE = "VOSpace-2.0.xsd";
    private static final String XLINK_SCHEMA_URL = "http://www.w3.org/1999/xlink";
    private static final String XLINK_SCHEMA_RESOURCE = "XLINK.xsd";

    private static final Logger log = Logger.getLogger(NodeReader.class);
    
    protected Map<String, String> schemaMap;
    protected Namespace xsiNamespace;

    /**
     * Constructor. XML Schema validation is enabled by default.
     */
    public NodeReader() { this(true); }

    /**
     * Constructor. XML schema validation may be disabled, in which case the client
     * is likely to fail in horrible ways (e.g. NullPointerException) if it receives
     * invalid documents. However, performance may be improved.
     *
     * @param enableSchemaValidation
     */
    public NodeReader(boolean enableSchemaValidation)
    {
        if (enableSchemaValidation)
        {
            String vospaceSchemaUrl = XmlUtil.getResourceUrlString(VOSPACE_SCHEMA_RESOURCE, NodeReader.class);
            log.debug("vospaceSchemaUrl: " + vospaceSchemaUrl);

            String xlinkSchemaUrl = XmlUtil.getResourceUrlString(XLINK_SCHEMA_RESOURCE, NodeReader.class);
            log.debug("xlinkSchemaUrl: " + xlinkSchemaUrl);

            if (vospaceSchemaUrl == null)
                throw new RuntimeException("failed to load " + VOSPACE_SCHEMA_RESOURCE + " from classpath");
            if (vospaceSchemaUrl == null)
                throw new RuntimeException("failed to load " + XLINK_SCHEMA_RESOURCE + " from classpath");

            schemaMap = new HashMap<String, String>();
            schemaMap.put(VOSPACE_SCHEMA_URL, vospaceSchemaUrl);
            schemaMap.put(XLINK_SCHEMA_URL, xlinkSchemaUrl);
            log.debug("schema validation enabled");
        }
        else
            log.debug("schema validation disabled");

        xsiNamespace = Namespace.getNamespace("http://www.w3.org/2001/XMLSchema-instance");
    }

    /**
     *  Construct a Node from an XML String source.
     *
     * @param xml String of the XML.
     * @return Node Node.
     * @throws NodeParsingException if there is an error parsing the XML.
     */
    public Node read(String xml) throws NodeParsingException
    {
        if (xml == null)
            throw new IllegalArgumentException("XML must not be null");
        try
        {
            return read(new StringReader(xml));
        }
        catch (IOException ioe)
        {
            String error = "Error reading XML: " + ioe.getMessage();
            throw new NodeParsingException(error, ioe);
        }
    }

    /**
     * Construct a Node from a InputStream.
     *
     * @param in InputStream.
     * @return Node Node.
     * @throws NodeParsingException if there is an error parsing the XML.
     */
    public Node read(InputStream in) throws IOException, NodeParsingException
    {
        if (in == null)
            throw new IOException("stream closed");
        try
        {
            return read(new InputStreamReader(in, "UTF-8"));
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("UTF-8 encoding not supported");
        }
    }

    /**
     *  Construct a Node from a Reader.
     *
     * @param reader Reader.
     * @return Node Node.
     * @throws NodeParsingException if there is an error parsing the XML.
     */
    public Node read(Reader reader) 
    		throws NodeParsingException, IOException
    {
        if (reader == null)
            throw new IllegalArgumentException("reader must not be null");

        // Create a JDOM Document from the XML
        Document document;
        try
        {
            // TODO: investigate creating a SAXBuilder once and re-using it
            // as long as we can detect concurrent access (a la java collections)
            document = XmlUtil.validateXml(reader, schemaMap);
        }
        catch (JDOMException jde)
        {
            String error = "XML failed schema validation: " + jde.getMessage();
            throw new NodeParsingException(error, jde);
        }

        // Root element and namespace of the Document
        Element root = document.getRootElement();
        Namespace namespace = root.getNamespace();
        log.debug("node namespace uri: " + namespace.getURI());
        log.debug("node namespace prefix: " + namespace.getPrefix());

        /* Node base elements */
        // uri attribute of the node element
        String uri = root.getAttributeValue("uri");
        if (uri == null)
        {
            String error = "uri attribute not found in root element";
            throw new NodeParsingException(error);
        }
        log.debug("node uri: " + uri);

        // Get the xsi:type attribute which defines the Node class
        String xsiType = root.getAttributeValue("type", xsiNamespace);
        if (xsiType == null)
        {
            String error = "xsi:type attribute not found in node element " + uri;
            throw new NodeParsingException(error);
        }

        // Split the type attribute into namespace and Node type
        String[] types = xsiType.split(":");
        String type = types[1];
        log.debug("node type: " + type);

        try
        {
            if (type.equals(ContainerNode.class.getSimpleName()))
                return buildContainerNode(root, namespace, uri);
            else if (type.equals(DataNode.class.getSimpleName()))
                return buildDataNode(root, namespace, uri);
            else if (type.equals(UnstructuredDataNode.class.getSimpleName()))
            	return buildUnstructuredDataNode(root, namespace, uri);
            else if (type.equals(LinkNode.class.getSimpleName()))
                return buildLinkNode(root, namespace, uri);
            else if (type.equals(StructuredDataNode.class.getSimpleName()))
            	return buildStructuredDataNode(root, namespace, uri);
            else
                throw new NodeParsingException("unsupported node type " + type);
        }
        catch (URISyntaxException e)
        {
            throw new NodeParsingException("invalid uri in xml: " + e.getMessage());
        }
    }

    /**
     *  Get an String representation of the URL
     *  to the VOSpace schema document.
     *
     * @return String of the VOSpace schema document URL
     */
//    protected String getVOSpaceSchema()
//    {
//        return vospaceSchemaUrl;
//    }

    /**
     * Constructs a LinkNode from the given root Element of the
     * Document, Document Namespace, and Node path.
     *
     * @param el a node Element in the Document.
     * @param namespace Document Namespace.
     * @param uri Node uri attribute.
     * @param target Target resource pointed to by the LinkNode
     * @return LinkNode.
     * @throws NodeParsingException if there is an error parsing the XML.
     * @throws URISyntaxException 
     */
    protected Node buildLinkNode(Element el, Namespace namespace, String uri)
        throws NodeParsingException, URISyntaxException
    {
        // Instantiate a LinkNode class
        LinkNode node;
        VOSURI vosuri;
        
        // target element in the node element
        Element target = el.getChild("target", namespace);
        if (target == null)
        {
            String error = "target element not found in node element";
            throw new NodeParsingException(error);
        }
        log.debug("node target: " + target.getText());

        try
        {
        	vosuri = new VOSURI(uri);
        }
        catch (URISyntaxException e)
        {
        	String error = "invalid node uri " + uri;
            throw new NodeParsingException(error, e);
        }
        
        try
        {
            node = new LinkNode(vosuri, new URI(target.getText()));
        }
        catch (URISyntaxException e)
        {
            String error = "invalid node target " + target.getText();
            throw new NodeParsingException(error, e);
        }

        // properties element
        node.setProperties(getProperties(el, namespace));

        return node;
    }
    
    /**
     * Constructs a ContainerNode from the given Element of the
     * Document, Document Namespace, and Node path.
     *
     * @param el a node Element of the Document.
     * @param namespace Document Namespace.
     * @param uri Node uri attribute.
     * @return ContainerNode
     * @throws NodeParsingException if there is an error parsing the XML.
     * @throws URISyntaxException 
     */
    protected Node buildContainerNode(Element el, Namespace namespace, String uri)
        throws NodeParsingException, URISyntaxException
    {
        // Instantiate a ContainerNode class
        ContainerNode node;
        try
        {
            node = new ContainerNode(new VOSURI(uri));
        }
        catch (URISyntaxException e)
        {
            String error = "invalid node uri " + uri;
            throw new NodeParsingException(error, e);
        }

        // properties element
        node.setProperties(getProperties(el, namespace));

        // nodes element
        Element nodes = el.getChild("nodes", namespace);
        if (nodes == null)
        {
            String error = "nodes element not found in node";
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
                throw new NodeParsingException(error);
            }
            // Get the xsi:type attribute which defines the Node class
            String xsiType = childNode.getAttributeValue("type", xsiNamespace);
            if (xsiType == null)
            {
                String error = "xsi:type attribute not found in node element " + uri;
                throw new NodeParsingException(error);
            }

            // Split the type attribute into namespace and Node type
            String[] types = xsiType.split(":");
            String type = types[1];
            log.debug("node type: " + type);

            if (type.equals(ContainerNode.class.getSimpleName()))
                node.getNodes().add( buildContainerNode(childNode, namespace, childNodeUri) );
            else if (type.equals(DataNode.class.getSimpleName()))
                node.getNodes().add( buildDataNode(childNode, namespace, childNodeUri) );
            else if (type.equals(LinkNode.class.getSimpleName()))
                node.getNodes().add( buildLinkNode(childNode, namespace, childNodeUri) );            	
            else
                throw new NodeParsingException("unsupported node type " + type);
            
            log.debug("added child node: " + childNodeUri);
        }

        return node;
    }
    
    /**
     * Sets the attributes of a DataNode from the given DataNode, Element 
     * of the Document and Document Namespace.
     *
     * @parm node a DataNode
     * @param el a node Element in the Document.
     * @param namespace Document Namespace.
     * @throws NodeParsingException if there is an error parsing the XML.
     * @throws URISyntaxException 
     */
    protected void setDataNodeAttributes(DataNode node, Element el, Namespace namespace) 
    		throws NodeParsingException, URISyntaxException
    {
        // busy attribute
        String busy = el.getAttributeValue("busy");
        if (busy == null)
        {
            String error = "busy element not found in DataNode";
            throw new NodeParsingException(error);
        }
        boolean isBusy = busy.equalsIgnoreCase("true") ? true : false;
        
        // TODO: BM: Change the XML schema to support the three possible
        // values for the busy state: not busy, busy with read, busy
        // with write.  For now, we'll consider busy to be the more
        // restrictive busy with write.
        if (isBusy)
        {
            node.setBusy(NodeBusyState.busyWithWrite);
        }
        else
        {
            node.setBusy(NodeBusyState.notBusy);
        }
        log.debug("busy: " + isBusy);

        // properties element
        node.setProperties(getProperties(el, namespace));

        // accepts element
        node.accepts().addAll(getViewURIs(el, namespace, "accepts"));

        // provides element
        node.provides().addAll(getViewURIs(el, namespace, "provides"));

    }
    
    /**
     * Constructs a DataNode from the given root Element of the
     * Document, Document Namespace, and Node path.
     *
     * @param el a node Element in the Document.
     * @param namespace Document Namespace.
     * @param uri Node uri attribute.
     * @return DataNode.
     * @throws NodeParsingException if there is an error parsing the XML.
     * @throws URISyntaxException 
     */
    protected Node buildDataNode(Element el, Namespace namespace, String uri)
        throws NodeParsingException, URISyntaxException
    {
        // Instantiate a DataNode class
        DataNode node;
        try
        {
            node = new DataNode(new VOSURI(uri));
        }
        catch (URISyntaxException e)
        {
            String error = "invalid node uri " + uri;
            throw new NodeParsingException(error, e);
        }

        setDataNodeAttributes(node, el, namespace);

        return node;
    }

    /**
     * Constructs an UnstructuredDataNode from the given root Element of the
     * Document, Document Namespace, and Node path.
     *
     * @param el a node Element in the Document.
     * @param namespace Document Namespace.
     * @param uri Node uri attribute.
     * @return UnstructuredDataNode.
     * @throws NodeParsingException if there is an error parsing the XML.
     * @throws URISyntaxException 
     */
    protected Node buildUnstructuredDataNode(Element el, Namespace namespace, String uri) 
    		throws NodeParsingException, URISyntaxException
    {
        // Instantiate an UnstructuredDataNode class
        UnstructuredDataNode node;
        try
        {
            node = new UnstructuredDataNode(new VOSURI(uri));
        }
        catch (URISyntaxException e)
        {
            String error = "invalid node uri " + uri;
            throw new NodeParsingException(error, e);
        }

        setDataNodeAttributes(node, el, namespace);

        return node;
    }

    /**
     * Constructs an StructuredDataNode from the given root Element of the
     * Document, Document Namespace, and Node path.
     *
     * @param el a node Element in the Document.
     * @param namespace Document Namespace.
     * @param uri Node uri attribute.
     * @return StructuredDataNode.
     * @throws NodeParsingException if there is an error parsing the XML.
     * @throws URISyntaxException 
     */
    protected Node buildStructuredDataNode(Element el, Namespace namespace, 
    		String uri) throws NodeParsingException, URISyntaxException
    {
        // Instantiate an UnstructuredDataNode class
        StructuredDataNode node;
        try
        {
            node = new StructuredDataNode(new VOSURI(uri));
        }
        catch (URISyntaxException e)
        {
            String error = "invalid node uri " + uri;
            throw new NodeParsingException(error, e);
        }

        setDataNodeAttributes(node, el, namespace);
        
        return node;
    }
    
    /**
     * Builds a List of NodeProperty objects from the Document property Elements.
     *
     * @param el a node Element of the Document.
     * @param namespace Document Namespace.
     * @return List of NodeProperty objects.
     * @throws NodeParsingException if there is an error parsing the XML.
     */
    protected List<NodeProperty> getProperties(Element el, Namespace namespace)
        throws NodeParsingException
    {
        // properties element
        Element properties = el.getChild("properties", namespace);
        if (properties == null)
        {
            String error = "properties element not found";
            throw new NodeParsingException(error);
        }

        // new NodeProperty List
        List<NodeProperty> set = new ArrayList<NodeProperty>();

        // properties property elements
        List<Element> propertyList = properties.getChildren("property", namespace);
        for (Element property : propertyList)
        {
            String propertyUri = property.getAttributeValue("uri");
            if (propertyUri == null)
            {
                String error = "uri attribute not found in property element " + property;
                throw new NodeParsingException(error);
            }

            // xsi:nil set to true indicates Property is to be deleted
            String xsiNil = property.getAttributeValue("nil", xsiNamespace);
            boolean markedForDeletion = false;
            if (xsiNil != null)
                markedForDeletion = xsiNil.equalsIgnoreCase("true") ? true : false;

            // if marked for deletetion, property can not contain text content
            String text = property.getText();
            if (markedForDeletion)
                text = "";

            // create new NodeProperty
            NodeProperty nodeProperty = new NodeProperty(propertyUri, text);

            // set readOnly attribute
            String readOnly = property.getAttributeValue("readOnly");
            if (readOnly != null)
                nodeProperty.setReadOnly((readOnly.equalsIgnoreCase("true") ? true : false));

            // markedForDeletion attribute
            nodeProperty.setMarkedForDeletion(markedForDeletion);
            set.add(nodeProperty);
        }

        return set;
    }
    
    protected List<URI> getViewURIs(Element root, Namespace namespace, String parent)
        throws NodeParsingException, URISyntaxException
    {
        
        // new View List
        List<URI> list = new ArrayList<URI>();
        
        // view parent element
        Element parentElement = root.getChild(parent, namespace);
        if (parentElement == null)
        {
            return list;
        }

        // view elements
        List<Element> uriList = parentElement.getChildren("view", namespace);
        for (Element view : uriList)
        {
            // view uri attribute
            String viewUri = view.getAttributeValue("uri");
            if (viewUri == null)
            {
                String error = "uri attribute not found in " + parent + " view element";
                throw new NodeParsingException(error);
            }
            log.debug(parent + "view uri: " + viewUri);
            list.add(new URI(viewUri));
        }

        return list;
    }

    /**
     * Builds a List of View objects from the view elements contained within
     * the given parent element.
     *
     * @param root Root Element of the Document.
     * @param namespace Document Namespace.
     * @param parent View Parent Node.
     * @return List of View objects.
     * @throws NodeParsingException if there is an error parsing the XML.
     */
    protected List<View> getViews(Element root, Namespace namespace, String parent)
        throws NodeParsingException
    {
        // view parent element
        Element parentElement = root.getChild(parent, namespace);
        if (parentElement == null)
        {
            String error = parent + " element not found in node";
            throw new NodeParsingException(error);
        }

        // new View List
        List<View> list = new ArrayList<View>();

        // view elements
        List<Element> viewList = parentElement.getChildren("view", namespace);
        for (Element view : viewList)
        {
            // view uri attribute
            String viewUri = view.getAttributeValue("uri");
            if (viewUri == null)
            {
                String error = "uri attribute not found in " + parent + " view element";
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
                    throw new NodeParsingException(error);
                }
                log.debug("accepts view param uri: " + paramUri);
                // TODO: what are params???
            }
        }

        return list;
    }

}
