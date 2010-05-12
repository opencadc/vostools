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

package ca.nrc.cadc.conformance.vos;

import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.VOSURI;
import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.PutMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom.JDOMException;
import static org.junit.Assert.*;
import org.xml.sax.SAXException;

public abstract class AbstractVOSTest
{
    public static final String CADC_VOSPACE_URI = "vos://cadc.nrc.ca!vospace";

    private static Logger log = Logger.getLogger(AbstractVOSTest.class);
    {
        Log4jInit.setLevel("ca", Level.DEBUG);
    }

    private String serviceUrl;

    public AbstractVOSTest()
    {
        serviceUrl = System.getProperty("service.url");
        if (serviceUrl == null)
            throw new RuntimeException("service.url System property not set");
        log.debug("serviceUrl: " + serviceUrl);

    }

    protected String getServiceUrl()
    {
        return serviceUrl;
    }

    protected ContainerNode getSampleContainerNode() throws URISyntaxException
    {
         // List of NodeProperty
        NodeProperty nodeProperty = new NodeProperty("ivo://ivoa.net/vospace/core#description", "My award winning images");
        nodeProperty.setReadOnly(true);

        // List of Node
        List<Node> nodes = new ArrayList<Node>();
        nodes.add(new DataNode(new VOSURI("vos://cadc.nrc.ca!vospace/mydir/ngc4323")));
        nodes.add(new DataNode(new VOSURI("vos://cadc.nrc.ca!vospace/mydir/ngc5796")));
        nodes.add(new DataNode(new VOSURI("vos://cadc.nrc.ca!vospace/mydir/ngc6801")));

        // ContainerNode
        ContainerNode node = new ContainerNode(new VOSURI(AbstractVOSTest.CADC_VOSPACE_URI + "/A"));
        node.getProperties().add(nodeProperty);
        node.setNodes(nodes);
        return node;
    }

    protected DataNode getSampleDataNode() throws URISyntaxException
    {
        // List of NodeProperty
        NodeProperty nodeProperty = new NodeProperty("ivo://ivoa.net/vospace/core#description", "My award winning images");
        nodeProperty.setReadOnly(true);

        // DataNode
        DataNode node = new DataNode(new VOSURI(AbstractVOSTest.CADC_VOSPACE_URI + "/A"));
        node.getProperties().add(nodeProperty);
        node.setBusy(true);
        return node;
    }

    protected WebResponse delete(Node node)
        throws IOException, SAXException, JDOMException
    {
        String resourceUrl = getServiceUrl() + node.getPath();
        log.debug("**************************************************");
        log.debug("HTTP DELETE: " + resourceUrl);
        WebRequest request = new DeleteMethodWebRequest(resourceUrl);

        log.debug(getRequestParameters(request));

        WebConversation conversation = new WebConversation();
        conversation.setExceptionsThrownOnErrorStatus(false);
        WebResponse response = conversation.getResponse(request);
        assertNotNull("Response to request is null", response);

        log.debug(getResponseHeaders(response));
        log.debug("response code: " + response.getResponseCode());
        log.debug("Content-Type: " + response.getContentType());

        return response;
    }

    protected WebResponse get(Node node)
        throws IOException, SAXException, JDOMException
    {
        return get(node, null);
    }

    protected WebResponse get(Node node, Map<String, String> parameters)
        throws IOException, SAXException, JDOMException
    {
        String resourceUrl = getServiceUrl() + node.getPath();
        log.debug("**************************************************");
        log.debug("HTTP GET: " + resourceUrl);
        WebRequest request = new GetMethodWebRequest(resourceUrl);

        if (parameters != null)
        {
            List<String> keyList = new ArrayList<String>(parameters.keySet());
            for (String key : keyList)
            {
                String value = parameters.get(key);
                request.setParameter(key, value);
            }
        }
        log.debug(getRequestParameters(request));

        WebConversation conversation = new WebConversation();
        conversation.setExceptionsThrownOnErrorStatus(false);
        WebResponse response = conversation.getResponse(request);
        assertNotNull("Response to request is null", response);

        log.debug(getResponseHeaders(response));
        log.debug("response code: " + response.getResponseCode());
        log.debug("Content-Type: " + response.getContentType());

        return response;
    }

    protected WebResponse post(ContainerNode node)
        throws IOException, SAXException
    {
        String resourceUrl = getServiceUrl() + node.getPath();
        log.debug("**************************************************");
        log.debug("HTTP POST: " + resourceUrl);
        WebRequest request = new PostMethodWebRequest(resourceUrl);


        StringBuilder sb = new StringBuilder();
        NodeWriter nodeWriter = new NodeWriter();
        nodeWriter.write(node, sb);
        request.setParameter(null, sb.toString());

        log.debug(getRequestParameters(request));

        WebConversation conversation = new WebConversation();
        conversation.setExceptionsThrownOnErrorStatus(false);
        WebResponse response = conversation.sendRequest(request);
        assertNotNull("POST response to " + resourceUrl + " is null", response);

        log.debug(getResponseHeaders(response));
        log.debug("Response code: " + response.getResponseCode());

        return response;
    }

    protected WebResponse post(DataNode node)
        throws IOException, SAXException
    {
        String resourceUrl = getServiceUrl() + node.getPath();
        log.debug("**************************************************");
        log.debug("HTTP POST: " + resourceUrl);
        WebRequest request = new PostMethodWebRequest(resourceUrl);
        

        StringBuilder sb = new StringBuilder();
        NodeWriter nodeWriter = new NodeWriter();
        nodeWriter.write(node, sb);
        request.setParameter(null, sb.toString());

        log.debug(getRequestParameters(request));

        WebConversation conversation = new WebConversation();
        conversation.setExceptionsThrownOnErrorStatus(false);
        WebResponse response = conversation.sendRequest(request);
        assertNotNull("POST response to " + resourceUrl + " is null", response);

        log.debug(getResponseHeaders(response));
        log.debug("Response code: " + response.getResponseCode());

        return response;
    }

    protected WebResponse put(ContainerNode node)
        throws IOException, SAXException
    {
        return put(node, new NodeWriter());
    }

    protected WebResponse put(ContainerNode node, NodeWriter writer)
        throws IOException, SAXException
    {
        String resourceUrl = getServiceUrl() + node.getPath();
        log.debug("**************************************************");
        log.debug("HTTP PUT: " + resourceUrl);

        StringBuilder sb = new StringBuilder();
        writer.write(node, sb);
        InputStream in = new ByteArrayInputStream(sb.toString().getBytes("UTF-8"));
        WebRequest request = new PutMethodWebRequest(resourceUrl, in, "text/xml");

        log.debug(getRequestParameters(request));

        WebConversation conversation = new WebConversation();
        conversation.setExceptionsThrownOnErrorStatus(false);
        WebResponse response = conversation.sendRequest(request);
        assertNotNull("POST response to " + resourceUrl + " is null", response);

        log.debug(getResponseHeaders(response));
        log.debug("Response code: " + response.getResponseCode());

        return response;
    }

    protected WebResponse put(DataNode node)
        throws IOException, SAXException
    {
        return put(node, new NodeWriter());
    }

    protected WebResponse put(DataNode node, NodeWriter writer)
        throws IOException, SAXException
    {
        String resourceUrl = getServiceUrl() + node.getPath();
        log.debug("**************************************************");
        log.debug("HTTP PUT: " + resourceUrl);
        
        StringBuilder sb = new StringBuilder();
        writer.write(node, sb);
        InputStream in = new ByteArrayInputStream(sb.toString().getBytes("UTF-8"));
        WebRequest request = new PutMethodWebRequest(resourceUrl, in, "text/xml");

        log.debug(getRequestParameters(request));

        WebConversation conversation = new WebConversation();
        conversation.setExceptionsThrownOnErrorStatus(false);
        WebResponse response = conversation.sendRequest(request);
        assertNotNull("POST response to " + resourceUrl + " is null", response);

        log.debug(getResponseHeaders(response));
        log.debug("Response code: " + response.getResponseCode());

        return response;
    }

    public static String getRequestParameters(WebRequest request)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Request parameters:");
        sb.append("\r\n");
        String[] headers = request.getRequestParameterNames();
        for (int i = 0; i < headers.length; i++)
        {
            sb.append("\t");
            sb.append(headers[i]);
            sb.append("=");
            sb.append(request.getParameter(headers[i]));
            sb.append("\r\n");
        }
        return sb.toString();
    }

    public static String getResponseHeaders(WebResponse response)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Response headers:");
        sb.append("\r\n");
        String[] headers = response.getHeaderFieldNames();
        for (int i = 0; i < headers.length; i++)
        {
            sb.append("\t");
            sb.append(headers[i]);
            sb.append("=");
            sb.append(response.getHeaderField(headers[i]));
            sb.append("\r\n");
        }
        return sb.toString();
    }


}
