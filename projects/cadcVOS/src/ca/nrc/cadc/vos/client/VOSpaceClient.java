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
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import org.apache.log4j.Logger;
import org.jdom.JDOMException;

import ca.nrc.cadc.util.ArgumentMap;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobReader;
import ca.nrc.cadc.uws.JobWriter;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeParsingException;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.NodeReader;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.Protocol;
import ca.nrc.cadc.vos.Search;
import ca.nrc.cadc.vos.ServerTransfer;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.TransferReader;
import ca.nrc.cadc.vos.View;

/**
 * @author zhangsa
 *
 */
public class VOSpaceClient
{
    private static Logger log = Logger.getLogger(VOSpaceClient.class);
    public static final String CR = System.getProperty("line.separator"); // OS independant new line

    protected String baseUrl;

    public VOSpaceClient(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }

    public Node createNode(Node node)
    {
        int responseCode;
        Node rtnNode = null;

        String path = node.getPath();

        try
        {
            URL url = new URL(this.baseUrl + "/nodes/" + path);
            log.debug(url);
            HttpsURLConnection httpsCon = (HttpsURLConnection) url.openConnection();
            httpsCon.setDoOutput(true);
            httpsCon.setRequestMethod("PUT");
            //httpsCon.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            //httpsCon.setRequestProperty("Content-Language", "en-US");
            httpsCon.setUseCaches(false);
            httpsCon.setDoInput(true);
            httpsCon.setDoOutput(true);

            OutputStreamWriter out = new OutputStreamWriter(httpsCon.getOutputStream());
            NodeWriter nodeWriter = new NodeWriter();
            nodeWriter.write(node, out);
            out.close();

            StringWriter sw = new StringWriter();
            nodeWriter.write(node, sw);
            String xml = sw.toString();
            sw.close();
            log.debug(xml);



            String responseMessage = httpsCon.getResponseMessage();
            responseCode = httpsCon.getResponseCode();
            switch (responseCode)
            {
            case 201: // valid
                InputStream in = httpsCon.getInputStream();
                
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                StringBuffer sb = new StringBuffer();
                String line;
                while ((line = br.readLine()) != null)
                {
                    sb.append(line).append(CR);
                }
                in.close();

                log.debug("response from server: \n" + sb.toString());
                
                NodeReader nodeReader = new NodeReader();
                rtnNode = nodeReader.read(sb.toString());
                break;

            case 500:
                // The service SHALL throw a HTTP 500 status code including an InternalFault fault in the entity body 
                // if the operation fails

                // If a parent node in the URI path does not exist 
                // then the service MUST throw a HTTP 500 status code including a ContainerNotFound fault in the entity body.

                // If a parent node in the URI path is a LinkNode, 
                // the service MUST throw a HTTP 500 status code including a LinkFound fault in the entity body.
            case 409:
                // The service SHALL throw a HTTP 409 status code including a DuplicateNode fault in the entity body 
                // if a Node already exists with the same URI

            case 400:
                // The service SHALL throw a HTTP 400 status code including an InvalidURI fault in the entity body 
                // if the requested URI is invalid
                // The service SHALL throw a HTTP 400 status code including a TypeNotSupported fault in the entity body 
                // if the type specified in xsi:type is not supported
            case 401:
                // The service SHALL throw a HTTP 401 status code including PermissionDenied fault in the entity body
                // if the user does not have permissions to perform the operation
            default:
                log.error(responseMessage + ". HTTP Code: " + responseCode);
                InputStream errStrm = httpsCon.getErrorStream();
                br = new BufferedReader(new InputStreamReader(errStrm));
                while ((line = br.readLine()) != null)
                {
                    log.debug(line);
                }
                errStrm.close();
                throw new IllegalArgumentException("Error returned.  HTTP Response Code: " + responseCode);
            }
        } catch (IOException e)
        {
            e.printStackTrace(System.err);
            throw new IllegalStateException(e);
        } catch (NodeParsingException e)
        {
            throw new IllegalStateException(e);
        }
        return rtnNode;
    }

    /**
     * Get Node.
     *  
     * @param path
     * @return
     * @throws NodeParsingException 
     */
    public Node getNode(String path)
    {
        int responseCode;
        Node rtnNode = null;

        try
        {
            URL url = new URL(this.baseUrl + "/nodes/" + path);
            log.debug(url);
            HttpsURLConnection httpsCon = (HttpsURLConnection) url.openConnection();
            httpsCon.setDoOutput(true);
            httpsCon.setRequestMethod("GET");
            //httpsCon.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            //httpsCon.setRequestProperty("Content-Language", "en-US");
            httpsCon.setUseCaches(false);
            httpsCon.setDoInput(true);
            httpsCon.setDoOutput(false);



            String responseMessage = httpsCon.getResponseMessage();
            responseCode = httpsCon.getResponseCode();

            switch (responseCode)
            {
            case 200: // valid
                InputStream in = httpsCon.getInputStream();
                
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                StringBuffer sb = new StringBuffer();
                String line;
                while ((line = br.readLine()) != null)
                {
                    sb.append(line).append(CR);
                }
                in.close();

                log.debug("response from server: \n" + sb.toString());
                
                NodeReader nodeReader = new NodeReader();
                rtnNode = nodeReader.read(sb.toString());
                log.debug(rtnNode.getName());
                log.debug(rtnNode.getPath());
                break;
            case 500:
                // The service SHALL throw a HTTP 500 status code including an InternalFault fault in the entity-body if the operation fails
            case 401:
                // The service SHALL throw a HTTP 401 status code including a PermissionDenied fault in the entity-body if the user does not have permissions to perform the operation
            case 404:
                // The service SHALL throw a HTTP 404 status code including a NodeNotFound fault in the entity-body if the target Node does not exist 
            default:
                throw new IllegalArgumentException("Error returned.  HTTP Response Code: " + responseCode);
            }
        } catch (IOException ex)
        {
            throw new IllegalStateException(ex);
        } catch (NodeParsingException e)
        {
            throw new IllegalStateException(e);
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
            URL url = new URL(this.baseUrl);
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setDoOutput(true);
            httpCon.setRequestMethod("POST");
            OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream());
            NodeWriter nodeWriter = new NodeWriter();
            nodeWriter.write(node, out);
            out.close();

            responseCode = httpCon.getResponseCode();
            switch (responseCode)
            {
            case 200: // valid
                InputStream in = httpCon.getInputStream();
                NodeReader nodeReader = new NodeReader();
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
                throw new IllegalArgumentException("Error returned.  HTTP Response Code: " + responseCode);
            default:
                break;
            }
        } catch (IOException e)
        {
            throw new IllegalStateException(e);
        } catch (NodeParsingException e)
        {
            throw new IllegalStateException(e);
        }
        return rtnNode;
    }

    public ServerTransfer createServerTransfer(ServerTransfer sTransfer)
    {
        throw new UnsupportedOperationException("Feature under construction.");
    }

    private Transfer createTransfer(Transfer transfer, Transfer.Direction direction)
    {
        Transfer rtn = null;

        Job job = new Job();
        //TODO Transfer fields for values of parameter need to be confirmed.
        job.setExecutionDuration(0);
        //TODO is there a RUN for execution phase?
        job.setExecutionPhase(ExecutionPhase.EXECUTING);
        job.addParameter(new Parameter("target", transfer.getTarget().getUri().toString()));
        job.addParameter(new Parameter("view", transfer.getView().getUri().toString()));

        if (direction == Transfer.Direction.pushToVoSpace)
        {
            job.addParameter(new Parameter("direction", Transfer.Direction.pushToVoSpace.toString()));
            job.addParameter(new Parameter("protocol", transfer.getProtocols().get(0).getUri()));
        } else if (direction == Transfer.Direction.pullFromVoSpace)
        {
            job.addParameter(new Parameter("direction", Transfer.Direction.pullFromVoSpace.toString()));
            for (Protocol protocol : transfer.getProtocols())
            {
                job.addParameter(new Parameter("protocol", protocol.getUri()));
            }
        }

        try
        {
            URL url = new URL(this.baseUrl + "/transfers");
            log.debug(url);
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setDoOutput(true);
            httpCon.setRequestMethod("PUT");
            OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream());
            JobWriter jobWriter = new JobWriter(job);
            jobWriter.writeTo(out);
            out.close();

            StringWriter sw = new StringWriter();
            jobWriter.writeTo(sw);
            String xml = sw.toString();
            sw.close();
            log.debug(xml);

            String redirectLocation = getRedirectLocation(httpCon);

            URL urlRedirect = new URL(redirectLocation);
            JobReader jobReader = new JobReader();
            Job jobResult = jobReader.readFrom(urlRedirect);

            String strResultUrl = jobResult.getResultsList().get(0).getURL().toString();

            URL urlTransferDetail = new URL(strResultUrl);
            TransferReader txfReader = new TransferReader();
            rtn = txfReader.readFrom(urlTransferDetail);

        } catch (IOException e)
        {
            throw new IllegalStateException(e);
        } catch (JDOMException e)
        {
            e.printStackTrace();
            throw new IllegalStateException(e);
        }
        return rtn;
    }

    public Transfer pushToVoSpace(Transfer transfer)
    {
        return createTransfer(transfer, Transfer.Direction.pushToVoSpace);
    }

    public Transfer pullFromVoSpace(Transfer transfer)
    {
        return createTransfer(transfer, Transfer.Direction.pullFromVoSpace);
    }

    /**
     * @param httpCon
     * @return
     * @throws IOException 
     */
    private String getRedirectLocation(HttpURLConnection httpCon) throws IOException
    {
        // Check response code from tapServer, should be 303.
        int responseCode = httpCon.getResponseCode();
        log.debug("responseCode: " + responseCode);
        if (responseCode != HttpURLConnection.HTTP_SEE_OTHER)
        {
            String error = "Query request returned non 303 response code " + responseCode;
            throw new IllegalStateException(error);
        }

        // Get the redirect Location header.
        String location = httpCon.getHeaderField("Location");
        log.debug("Location: " + location);
        if (location == null || location.length() == 0)
        {
            String error = "Query response missing Location header";
            throw new IllegalStateException(error);
        }
        return location;
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

    public int deleteNode(String path)
    {
        int responseCode;
        try
        {
            URL url = new URL(this.baseUrl + path);
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setDoOutput(true);
            httpCon.setRequestMethod("DELETE");

            responseCode = httpCon.getResponseCode();
        } catch (IOException ex)
        {
            throw new IllegalStateException(ex);
        }
        return responseCode;
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }
}
