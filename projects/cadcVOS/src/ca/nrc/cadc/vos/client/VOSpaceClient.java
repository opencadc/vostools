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

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import org.apache.log4j.Logger;

import ca.nrc.cadc.vos.*;

/**
 * @author zhangsa
 *
 */
public class VOSpaceClient
{
    private static Logger log = Logger.getLogger(VOSpaceClient.class);

    protected String _endpoint;

    /*
     * The service SHALL throw a HTTP 500 status code including an InternalFault fault in the entity body if the operation fails
     * The service SHALL throw a HTTP 409 status code including a DuplicateNode fault in the entity body if a Node already exists with the same URI
     * The service SHALL throw a HTTP 400 status code including an InvalidURI fault in the entity body if the requested URI is invalid
     * The service SHALL throw a HTTP 400 status code including a TypeNotSupported fault in the entity body if the type specified in xsi:type is not supported
     * The service SHALL throw a HTTP 401 status code including PermissionDenied fault in the entity body if the user does not have permissions to perform the operation
     * If a parent node in the URI path does not exist then the service MUST throw a HTTP 500 status code including a ContainerNotFound fault in the entity body.
           o For example, given the URI path /a/b/c, the service must throw a HTTP 500 status code including a ContainerNotFound fault in the entity body if either /a or /a/b do not exist. 
     * If a parent node in the URI path is a LinkNode, the service MUST throw a HTTP 500 status code including a LinkFound fault in the entity body.
           o For example, given the URI path /a/b/c, the service must throw a HTTP 500 status code including a LinkFound fault in the entity body if either /a or /a/b are LinkNodes. 
     */
    public Node createNode(ContainerNode node)
    {
        int responseCode;
        Node rtnNode = null;

        try
        {
            URL url = new URL(_endpoint);
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setDoOutput(true);
            httpCon.setRequestMethod("PUT");
            OutputStream out = httpCon.getOutputStream();
            NodeWriter nodeWriter = new NodeWriter();
            nodeWriter.write(node, out);
            out.close();

            responseCode = httpCon.getResponseCode();
            switch (responseCode)
            {
            case 201: // valid
                InputStream in = httpCon.getInputStream();
                NodeReader nodeReader = new NodeReader();
                rtnNode = nodeReader.read(in);
                in.close();
                break;
            case 500:
            case 409:
            case 400:
            case 401:
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

    /**
     * 
        *  The service SHALL throw a HTTP 500 status code including an InternalFault fault in the entity-body if the operation fails
        * The service SHALL throw a HTTP 401 status code including a PermissionDenied fault in the entity-body if the user does not have permissions to perform the operation
        * The service SHALL throw a HTTP 404 status code including a NodeNotFound fault in the entity-body if the target Node does not exist 
     * @param path
     * @return
     */
    public Node getNode(String path)
    {
        int responseCode;
        Node rtnNode = null;

        try
        {
            URL url = new URL(_endpoint + path);
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setDoOutput(true);
            httpCon.setRequestMethod("GET");

            responseCode = httpCon.getResponseCode();
            switch (responseCode)
            {
            case 201: // valid
                InputStream in = httpCon.getInputStream();
                // TODO generate returned node
                in.close();
                break;
            case 500:
            case 401:
            case 404:
                throw new IllegalArgumentException("Error returned.  HTTP Response Code: " + responseCode);
            default:
                break;
            }
        } catch (IOException ex)
        {
        }
        return rtnNode;
    }

    /**
    *  The service SHALL throw a HTTP 500 status code including an InternalFault fault in the entity-body if the operation fails
    * The service SHALL throw a HTTP 401 status code including a PermissionDenied fault in the entity-body if the request attempts to modify a readonly Property
    * The service SHALL throw a HTTP 401 status code including a PermissionDenied fault in the entity-body if the user does not have permissions to perform the operation
    * The service SHALL throw a HTTP 404 status code including a NodeNotFound fault in the entity-body if the target Node does not exist
    * The service SHALL throw a HTTP 400 status code including an InvalidArgument fault in the entity-body if a specified property value is invalid 
     * @param node
     * @return
     */
    public Node setNode(Node node)
    {
        int responseCode;
        Node rtnNode = null;
        try
        {
            URL url = new URL(_endpoint);
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setDoOutput(true);
            httpCon.setRequestMethod("POST");
            OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream());
            out.write(node.getName()); //TODO: send XML text
            out.close();

            responseCode = httpCon.getResponseCode();
            switch (responseCode)
            {
            case 200: // valid
                InputStream in = httpCon.getInputStream();
                // TODO generate returned node
                in.close();
                break;
            case 500:
            case 409:
            case 404:
            case 400:
            case 401:
                throw new IllegalArgumentException("Error returned.  HTTP Response Code: " + responseCode);
            default:
                break;
            }
        } catch (IOException ex)
        {
        }
        return rtnNode;
    }

    public Transfer createTransfer(Transfer transfer)
    {
        throw new UnsupportedOperationException("Feature under construction.");
    }

    public ServerTransfer createServerTransfer(ServerTransfer sTransfer)
    {
        throw new UnsupportedOperationException("Feature under construction.");
    }

    /**
     * For all faults, the service shall set the PHASE to "ERROR" in the Job representation. The <errorSummary> element in the Job representation shall be set to the appropriate value for the fault type and the appropriate fault representation (see section 5.5) provided at the error URI: http://rest-endpoint/transfers/{jobid}/error.
    Fault description   errorSummary    Fault representation
    Operation fails Internal Fault  InternalFault
    User does not have permissions to perform the operation Permission Denied   PermissionDenied
    Source node does not exist  Node Not Found  NodeNotFound
    Destination node already exists and it is not a ContainerNode   Duplicate Node  DuplicateNode
    A specified URI is invalid  Invalid URI InvalidURI
     * @param src
     * @param dest
     * @return
     */
    public ServerTransfer copyNode(Node src, Node dest)
    {
        throw new UnsupportedOperationException("Feature under construction.");
    }

    /**
     *For all faults, the service shall set the PHASE to "ERROR" in the Job representation. The <errorSummary> element in the Job representation shall be set to the appropriate value for the fault type and the appropriate fault representation (see section 5.5) provided at the error URI: http://rest-endpoint/transfers/{jobid}/error.
    Fault description   errorSummary    Fault representation
    Operation fails Internal Fault  InternalFault
    User does not have permissions to perform the operation Permission Denied   PermissionDenied
    Source node does not exist  Node Not Found  NodeNotFound
    Destination node already exists and it is not a ContainerNode   Duplicate Node  DuplicateNode
    A specified URI is invalid  Invalid URI InvalidURI 
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
}
