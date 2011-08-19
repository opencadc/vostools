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

package ca.nrc.cadc.vos.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.AccessController;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import ca.nrc.cadc.auth.SSLUtil;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.Direction;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodeParsingException;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.NodeReader;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.Protocol;
import ca.nrc.cadc.vos.Search;
import ca.nrc.cadc.vos.ServerTransfer;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.TransferParsingException;
import ca.nrc.cadc.vos.TransferReader;
import ca.nrc.cadc.vos.TransferWriter;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.View;

/**
 * @author zhangsa
 *
 */
public class VOSpaceClient
{
    private static Logger log = Logger.getLogger(VOSpaceClient.class);
    
    public static final String CR = System.getProperty("line.separator"); // OS independant new line

    // TODO: get this from capabilites obtained via registry lookup
    public static final String VOSPACE_SYNC_TRANSFER_ENDPOINT = "/synctrans";

    protected String baseUrl;
    protected boolean schemaValidation;
    private SSLSocketFactory sslSocketFactory;

    /**
     * Constructor. XML Schema validation is enabled by default.
     * 
     * @param baseUrl
     */
    public VOSpaceClient(String baseUrl)
    {
        this(baseUrl, true);
    }

    /**
     * Constructor. XML schema validation may be disabled, in which case the client
     * is likely to fail in horrible ways (e.g. NullPointerException) if it receives
     * invalid VOSpace (node or transfer) or UWS (job) documents. However, performance
     * may be improved.
     * 
     * @param baseUrl
     * @param enableSchemaValidation
     */
    public VOSpaceClient(String baseUrl, boolean enableSchemaValidation)
    {
        this.baseUrl = baseUrl;
        this.schemaValidation = enableSchemaValidation;
    }

    public void setSSLSocketFactory(SSLSocketFactory sslSocketFactory)
    {
        this.sslSocketFactory = sslSocketFactory;
    }

    // temp hack to share SSL with ClientTransfer
    public SSLSocketFactory getSslSocketFactory()
    {
        initHTTPS(null);
        return sslSocketFactory;
    }

    /**
     * Create the specified node. If the parent (container) nodes do not exist, they
     * will also be created.
     * 
     * @param node
     * @return the created node
     */
    public Node createNode(Node node)
    {
        int responseCode;
        Node rtnNode = null;

        try
        {
            VOSURI parentURI = node.getUri().getParentURI();
            ContainerNode parent = null;
            if (parentURI == null)
                throw new RuntimeException("parent (root node) not found and cannot create: " + node.getUri());
            try
            {
                Node p = this.getNode(parentURI.getPath());
                log.debug("found parent: " + parentURI);
                if (p instanceof ContainerNode)
                    parent = (ContainerNode) p;
                else
                    throw new IllegalArgumentException("cannot create a child, parent is a " + p.getClass().getSimpleName());
            }
            catch(NodeNotFoundException ex)
            {
                // if parent does not exist, just create it!!
                log.info("creating parent: " + parentURI);
                ContainerNode cn = new ContainerNode(parentURI);
                parent = (ContainerNode) createNode(cn);
            }

            // check if already exists: also could fail like this below due to race condition
            for (Node n : parent.getNodes())
                if (n.getName().equals(node.getName()))
                    throw new IllegalArgumentException("DuplicateNode: " + node.getUri().getURIObject().toASCIIString());

            URL url = new URL(this.baseUrl + "/nodes" + node.getUri().getPath());
            log.debug("createNode(), URL=" + url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            if (connection instanceof HttpsURLConnection)
            {
                HttpsURLConnection sslConn = (HttpsURLConnection) connection;
                initHTTPS(sslConn);
            }
            connection.setDoOutput(true);
            connection.setRequestMethod("PUT");
            connection.setRequestProperty("Content-Type", "text/xml");
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);

            OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
            NodeWriter nodeWriter = new NodeWriter();
            nodeWriter.write(node, out);
            out.close();

            String responseMessage = connection.getResponseMessage();
            responseCode = connection.getResponseCode();
            log.debug("createNode(), response code: " + responseCode);
            log.debug("createNode(), response message: " + responseMessage);
            
            switch (responseCode)
            {
                case 201: // valid
                    InputStream in = connection.getInputStream();
                    NodeReader nodeReader = new NodeReader(schemaValidation);
                    rtnNode = nodeReader.read(in);
                    in.close();
                    log.debug("createNode, created node: " + rtnNode);
                    break;

                case 500:
                    // The service SHALL throw a HTTP 500 status code including an InternalFault fault in the entity body
                    // if the operation fails

                    // If a parent node in the URI path does not exist
                    // then the service MUST throw a HTTP 500 status code including a ContainerNotFound fault in the entity body.

                    // If a parent node in the URI path is a LinkNode,
                    // the service MUST throw a HTTP 500 status code including a LinkFound fault in the entity body.
                    throw new RuntimeException(responseMessage);
                case 409:
                    // The service SHALL throw a HTTP 409 status code including a DuplicateNode fault in the entity body
                    // if a Node already exists with the same URI
                    throw new IllegalArgumentException(responseMessage);
                case 400:
                    // The service SHALL throw a HTTP 400 status code including an InvalidURI fault in the entity body
                    // if the requested URI is invalid
                    // The service SHALL throw a HTTP 400 status code including a TypeNotSupported fault in the entity body
                    // if the type specified in xsi:type is not supported
                    throw new IllegalArgumentException(responseMessage);
                case 401:
                    // The service SHALL throw a HTTP 401 status code including PermissionDenied fault in the entity body
                    // if the user does not have permissions to perform the operation
                    String msg = responseMessage;
                    if (msg == null)
                        msg = "permission denied";
                    throw new AccessControlException(msg);

                case 404:
                    // handle server response when parent (container) does not exist
                    throw new IllegalArgumentException(responseMessage);
                default:

                    // TODO: does this actually capture something useful? has it ever happened?
                    // don't we just say that the service responded in a non-compliant way and give up?
                    InputStream errStrm = connection.getErrorStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(errStrm));
                    String line;
                    while ((line = br.readLine()) != null)
                    {
                        log.debug(line);
                    }
                    errStrm.close();

                    throw new RuntimeException("unexpected failure mode: " + responseMessage + "(" + responseCode + ")");
            }
        }
        catch (IOException e)
        {
            log.debug("failed to create node", e);
            throw new IllegalStateException("failed to create node", e);
        }
        catch (NodeParsingException e)
        {
            throw new IllegalStateException("failed to create node", e);
        }
        return rtnNode;
    }

    /**
     * Get Node.
     *  
     * @param path      The path to the Node.
     * @return          The Node instance.
     * @throws NodeNotFoundException when the requested node does not exist on the server
     */
    public Node getNode(String path)
        throws NodeNotFoundException
    {
        return getNode(path, null);
    }

    /**
     * Get Node.
     *
     * @param path      The path to the Node.
     * @param query     Optional query string
     * @return          The Node instance.
     * @throws NodeNotFoundException when the requested node does not exist on the server
     */
    public Node getNode(String path, String query)
        throws NodeNotFoundException
    {
        if (!path.startsWith("/"))
            path = "/" + path; // must be absolute
        if (query != null)
            path += "?" + query;

        int responseCode;
        final Node rtnNode;

        try
        {
            
            URL url = new URL(this.baseUrl + "/nodes" + path);
            log.debug("getNode(), URL=" + url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            if (connection instanceof HttpsURLConnection)
            {
                HttpsURLConnection sslConn = (HttpsURLConnection) connection;
                initHTTPS(sslConn);
            }
            connection.setDoOutput(true);
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(false);

            String responseMessage = connection.getResponseMessage();
            responseCode = connection.getResponseCode();
            

            switch (responseCode)
            {
                case 200: // TODO: check content-type for XML
                    // grab service response body
                    InputStream in = connection.getInputStream();
                    NodeReader nodeReader = new NodeReader(schemaValidation);
                    rtnNode = nodeReader.read(in);
                    in.close();
                    log.debug("getNode, returned node: " + rtnNode);
                    break;
                case 500:
                    // The service SHALL throw a HTTP 500 status code including an InternalFault fault in the entity-body if the operation fails
                    throw new RuntimeException("service failed: " + responseMessage);
                case 401:
                    // The service SHALL throw a HTTP 401 status code including a PermissionDenied fault in the entity-body if the user does not have permissions to perform the operation
                    String msg = responseMessage;
                    if (msg == null)
                        msg = "permission denied";
                    throw new AccessControlException(msg);
                case 404:
                    // The service SHALL throw a HTTP 404 status code including a NodeNotFound fault in the entity-body if the target Node does not exist
                    throw new NodeNotFoundException("not found: " + path);
                default:
                    log.error(responseMessage + ". HTTP Code: " + responseCode);
                    throw new IllegalArgumentException("Error returned.  HTTP Response Code: " + responseCode);
            }
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("failed to get node", ex);
        }
        catch (NodeParsingException e)
        {
            throw new IllegalStateException("failed to get node", e);
        }
        return rtnNode;
    }

    /**
     * @param node
     * @return
     */
    public Node setNode(Node node)
    {
        int responseCode;
        Node rtnNode = null;
        try
        {
            URL url = new URL(this.baseUrl + "/nodes" + node.getUri().getPath());
            log.debug("setNode: " + VOSClientUtil.xmlString(node));
            log.debug("setNode: " + url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            if (connection instanceof HttpsURLConnection)
            {
                HttpsURLConnection sslConn = (HttpsURLConnection) connection;
                initHTTPS(sslConn);
            }
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            //connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            //connection.setRequestProperty("Content-Language", "en-US");
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);

            OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
            NodeWriter nodeWriter = new NodeWriter();
            nodeWriter.write(node, out);
            out.close();

            String responseMessage = connection.getResponseMessage();
            responseCode = connection.getResponseCode();

            switch (responseCode)
            {
                case 200: // valid
                    InputStream in = connection.getInputStream();
                    NodeReader nodeReader = new NodeReader(schemaValidation);
                    rtnNode = nodeReader.read(in);
                    in.close();
                    break;
                case 500:
                    // The service SHALL throw a HTTP 500 status code including an InternalFault fault in the entity-body if the operation fails
                case 409:
                case 404:
                    // The service SHALL throw a HTTP 404 status code including a NodeNotFound fault in the entity-body if the target Node does not exist
                case 400:
                    // The service SHALL throw a HTTP 400 status code including an InvalidArgument fault in the entity-body if a specified property value is invalid
                case 401:
                    // The service SHALL throw a HTTP 401 status code including a PermissionDenied fault in the entity-body
                    // if the request attempts to modify a readonly Property

                    // The service SHALL throw a HTTP 401 status code including a PermissionDenied fault in the entity-body
                    // if the user does not have permissions to perform the operation
                    String msg = responseMessage;
                    if (msg == null)
                        msg = "permission denied";
                    throw new AccessControlException(msg);
                default:
                    log.error(responseMessage + ". HTTP Code: " + responseCode);
                    InputStream errStrm = connection.getErrorStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(errStrm));
                    String line;
                    while ((line = br.readLine()) != null)
                    {
                        log.debug(line);
                    }
                    errStrm.close();
                    throw new IllegalArgumentException("Error returned.  HTTP Response Code: " + responseCode);
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("failed to set node", e);
        }
        catch (NodeParsingException e)
        {
            throw new IllegalStateException("failed to set node", e);
        }
        return rtnNode;
    }

    public ServerTransfer createServerTransfer(ServerTransfer sTransfer)
    {
        throw new UnsupportedOperationException("Feature under construction.");
    }

    public Transfer pushToVoSpace(Transfer transfer)
    {
        transfer.setDirection(Direction.pushToVoSpace);
        return createTransfer(transfer);
    }

    public Transfer pullFromVoSpace(Transfer transfer)
    {
        transfer.setDirection(Direction.pullFromVoSpace);
        return createTransfer(transfer);
    }

    /**
     * Copy Node.
     * 
     * For all faults, the service shall set the PHASE to "ERROR" in the Job representation. 
     * The <errorSummary> element in the Job representation shall be set to the appropriate value 
     * for the fault type and the appropriate fault representation (see section 5.5) provided at 
     * the error URI: http://rest-endpoint/transfers/{jobid}/error.
    
    
    -Fault description   
    --errorSummary    
    ---Fault representation
    
    -Operation fails 
    --Internal Fault  
    ---InternalFault
    
    -User does not have permissions to perform the operation 
    --Permission Denied   
    ---PermissionDenied
    
    -Source node does not exist  
    --Node Not Found  
    ---NodeNotFound
    
    -Destination node already exists and it is not a ContainerNode   
    --Duplicate Node  
    ---DuplicateNode
    
    -A specified URI is invalid  
    --Invalid URI 
    ---InvalidURI
    
     * @param src
     * @param dest
     * @return
     */
    public ServerTransfer copyNode(Node src, Node dest)
    {
        throw new UnsupportedOperationException("Feature under construction.");
    }

    /**
     * Move Node.
     * 
     * For all faults, the service shall set the PHASE to "ERROR" in the Job representation. 
     * The <errorSummary> element in the Job representation shall be set to the appropriate value 
     * for the fault type and the appropriate fault representation (see section 5.5) provided at 
     * the error URI: http://rest-endpoint/transfers/{jobid}/error.
    
    
    -Fault description   
    --errorSummary    
    ---Fault representation
    
    -Operation fails 
    --Internal Fault  
    ---InternalFault
    
    -User does not have permissions to perform the operation 
    --Permission Denied   
    ---PermissionDenied
    
    -Source node does not exist  
    --Node Not Found  
    ---NodeNotFound
    
    -Destination node already exists and it is not a ContainerNode   
    --Duplicate Node  
    ---DuplicateNode
    
    -A specified URI is invalid  
    --Invalid URI 
    ---InvalidURI

     * @param src
     * @param dest
     * @return
     */
    public ServerTransfer moveNode(Node src, Node dest)
    {
        throw new UnsupportedOperationException("Feature under construction.");
    }

    public Search createSearch(Search search)
    {
        throw new UnsupportedOperationException("Feature under construction.");
    }

    public List<NodeProperty> getProperties()
    {
        throw new UnsupportedOperationException("Feature under construction.");
    }

    public List<Protocol> getProtocols()
    {
        throw new UnsupportedOperationException("Feature under construction.");
    }

    public List<View> getViews()
    {
        throw new UnsupportedOperationException("Feature under construction.");
    }

    public void deleteNode(String path)
    {
        if (!path.startsWith("/"))
            path = "/" + path; // must be absolute

        int responseCode;
        try
        {
            URL url = new URL(this.baseUrl + "/nodes" + path);
            log.debug(url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            if (connection instanceof HttpsURLConnection)
            {
                HttpsURLConnection sslConn = (HttpsURLConnection) connection;
                initHTTPS(sslConn);
            }
            connection.setDoOutput(true);
            connection.setRequestMethod("DELETE");
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(false);

            String responseMessage = connection.getResponseMessage();
            responseCode = connection.getResponseCode();
            switch (responseCode)
            {
            case 200: // successful
                break;

            case 500:
                // The service SHALL throw a HTTP 500 status code including an InternalFault fault in the entity-body 
                // if the operation fails
                //
                // If a parent node in the URI path does not exist then 
                // the service MUST throw a HTTP 500 status code including a ContainerNotFound fault in the entity-body
                //
                // If a parent node in the URI path is a LinkNode, 
                // the service MUST throw a HTTP 500 status code including a LinkFound fault in the entity-body.
                throw new RuntimeException(responseMessage);
            case 401:
                /* The service SHALL throw a HTTP 401 status code including a PermissionDenied fault in the entity-body 
                 * if the user does not have permissions to perform the operation
                 */
                String msg = responseMessage;
                if (msg == null)
                    msg = "permission denied";
                throw new AccessControlException(msg);
            case 404:
                /*
                 * The service SHALL throw a HTTP 404 status code including a NodeNotFound fault in the entity-body 
                 * if the target node does not exist
                 * 
                 * If the target node in the URI path does not exist, 
                 * the service MUST throw a HTTP 404 status code including a NodeNotFound fault in the entity-body. 
                 */
                throw new IllegalArgumentException(responseMessage);
            default:
                log.error(responseMessage + ". HTTP Code: " + responseCode);
                InputStream errStrm = connection.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(errStrm));
                String line;
                while ((line = br.readLine()) != null)
                {
                    log.debug(line);
                }
                errStrm.close();
                throw new RuntimeException("unexpected failure mode: " + responseMessage + "(" + responseCode + ")");
            }
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("failed to delete node", ex);
        }
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }

    private Transfer createTransfer(Transfer transfer)
    {
        Transfer rtn = null;
        try
        {                      
            // POST the Job and get the redirect location.
            TransferWriter writer = new TransferWriter();
            StringWriter sw = new StringWriter();
            writer.write(transfer, sw);
            log.debug("POST: " + sw.toString());
            String transStr = postJob(this.baseUrl + VOSPACE_SYNC_TRANSFER_ENDPOINT, sw.toString());
            log.debug("sync transfer url: " + transStr);
            URL transURL = new URL(transStr);
            
            TransferReader txfReader = new TransferReader(schemaValidation);
            HttpURLConnection conn = (HttpURLConnection) transURL.openConnection();
            if (conn instanceof HttpsURLConnection)
            {
                HttpsURLConnection sslConn = (HttpsURLConnection) conn;
                initHTTPS(sslConn);
            }
            int code = conn.getResponseCode();
            if (code != 200)
                throw new RuntimeException("failed to read transfer description (" + code + "): " + conn.getResponseMessage());

            // TODO: handle errors by parsing url, getting job and looking at phase/error summary

            rtn = txfReader.read(conn.getInputStream());
            log.debug(rtn.toXmlString());
            
        }
        catch (MalformedURLException e)
        {
            log.debug("failed to create transfer", e);
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            log.debug("failed to create transfer", e);
            throw new RuntimeException(e);
        }
        /*
        catch (JDOMException e) // from JobReader
        {
            log.debug("got bad job XML from service", e);
            throw new RuntimeException(e);
        }
        catch (ParseException e) // from JobReader
        {
            log.debug("got bad job XML from service", e);
            throw new RuntimeException(e);
        }
        */
        catch (TransferParsingException e)
        {
            log.debug("got invalid XML from service", e);
            throw new RuntimeException(e);
        }
        return rtn;
    }

    private String postJob(String strUrl, String strParam) throws MalformedURLException, IOException
    {
        // Connection to the tapServer.
        URL url = new URL(strUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (connection instanceof HttpsURLConnection)
        {
            HttpsURLConnection sslConn = (HttpsURLConnection) connection;
            initHTTPS(sslConn);
        }
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Length", "" + Integer.toString(strParam.getBytes().length));
//        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Content-Type", "text/xml");
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setDoOutput(true);
        connection.setDoInput(true);

        // POST the parameters to the tapServer.
        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(strParam.getBytes("UTF-8"));
        outputStream.flush();
        outputStream.close();
        log.debug("POST " + url.toString());// + " " + strParam);

        String redirectLocation = getRedirectLocation(connection);

        return redirectLocation;
    }

    /**
     * @param connection
     * @return
     * @throws IOException 
     */
    private String getRedirectLocation(HttpURLConnection connection) throws IOException
    {
        // Check response code from server, should be 303.
        String responseMessage = connection.getResponseMessage();
        int responseCode = connection.getResponseCode();
        log.debug("responseCode: " + responseCode);
        if (responseCode != HttpURLConnection.HTTP_SEE_OTHER)
        {
            log.error(responseMessage + ". HTTP Code: " + responseCode);
            InputStream inStrm = connection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(inStrm));
            String line;
            while ((line = br.readLine()) != null)
            {
                log.debug(line);
            }
            inStrm.close();

            String error = "Request returned non 303 response code " + responseCode;
            throw new IllegalStateException(error);
        }

        // Get the redirect Location header.
        String location = connection.getHeaderField("Location");
        log.debug("Location: " + location);
        if (location == null || location.length() == 0)
        {
            String error = "Response missing Location header";
            throw new IllegalStateException(error);
        }
        return location;
    }

    private void initHTTPS(HttpsURLConnection sslConn)
    {
        if (sslSocketFactory == null) // lazy init
        {
            log.debug("initHTTPS: lazy init");
            AccessControlContext ac = AccessController.getContext();
            Subject s = Subject.getSubject(ac);
            this.sslSocketFactory = SSLUtil.getSocketFactory(s);
        }
        if (sslSocketFactory != null && sslConn != null)
        {
            log.debug("setting SSLSocketFactory on " + sslConn.getClass().getName());
            sslConn.setSSLSocketFactory(sslSocketFactory);
        }
    }

    // copy permission properties, assuming replace rather than append
    private void copyPermissions(Node src, Node dest)
    {
        List<NodeProperty> srcProps = src.getProperties();
        List<NodeProperty> destProps = dest.getProperties();

        for (NodeProperty s : srcProps)
        {
            if ( isPermissionProperty(s) )
            {
                boolean found = false;
                for (NodeProperty d : destProps)
                {
                    if ( s.getPropertyURI().equals(d.getPropertyURI()) )
                    {
                        d.setValue(s.getPropertyValue());
                        found = true;
                    }
                }
                if (!found)
                    destProps.add(s);
                log.debug("set: " + s);
            }
        }
    }

    private boolean isPermissionProperty(NodeProperty p)
    {
        if ( VOS.PROPERTY_URI_ISPUBLIC.equals(p.getPropertyURI()) )
            return true;
        if ( VOS.PROPERTY_URI_GROUPREAD.equals(p.getPropertyURI()) )
            return true;
        if ( VOS.PROPERTY_URI_GROUPWRITE.equals(p.getPropertyURI()) )
            return true;
        return false;
    }
}
