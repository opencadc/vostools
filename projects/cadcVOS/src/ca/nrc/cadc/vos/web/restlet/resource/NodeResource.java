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

package ca.nrc.cadc.vos.web.restlet.resource;

import java.io.IOException;
import java.util.HashSet;

import org.apache.log4j.Logger;
import org.restlet.data.Method;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Put;

import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeAlreadyExistsException;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodeParsingException;
import ca.nrc.cadc.vos.NodeReader;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.dao.SearchNode;
import ca.nrc.cadc.vos.web.representation.NodeOutputRepresentation;

/**
 * Handles HTTP requests for Node resources.
 * 
 * @author majorb
 *
 */
public class NodeResource extends BaseResource
{
    private static Logger log = Logger.getLogger(NodeResource.class);
    
    private String path;
    private Node node;
    
    public void doInit()
    {

        HashSet<Method> allowedMethods = new HashSet<Method>();
        allowedMethods.add(Method.GET);
        allowedMethods.add(Method.PUT);
        allowedMethods.add(Method.DELETE);
        setAllowedMethods(allowedMethods);

        path = (String) getRequest().getAttributes().get("nodePath");  
        if (path == null || path.trim().length() == 0)
        {
            final String message = "No path information provided.";
            log.debug(message);
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, message);
        }
        
        if (!getMethod().equals(Method.PUT))
        {
            try
            {
                Node searchNode = new SearchNode(path);
                node = getNodePersistence().get(searchNode);   
            }
            catch (NodeNotFoundException e)
            {
                final String message = "Could not find node with path: " + path;
                log.debug(message, e);
                setStatus(Status.CLIENT_ERROR_NOT_FOUND, message);
            }
        }
    }
    
    /**
     * Obtain the XML Representation of this Resource.
     *
     * @return  The XML Representation.
     */
    @Get("xml")
    public Representation represent()
    {
        NodeWriter nodeWriter = new NodeWriter();
        return new NodeOutputRepresentation(node, nodeWriter);
    }
    
    @Put("xml")
    public Representation store(Representation value)
    {   
        try
        {
            Node nodeToPut = new NodeReader().read(value.getStream());
            Node returnNode = getNodePersistence().put(nodeToPut);
            NodeWriter nodeWriter = new NodeWriter();
            return new NodeOutputRepresentation(returnNode, nodeWriter);
        }
        catch (NodeParsingException e)
        {
            String message = "Bad node xml format.";
            log.debug(message, e);
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, message);
        }
        catch (NodeAlreadyExistsException e)
        {
            final String message = "Node already exists: " + path;
            log.debug(message, e);
            setStatus(Status.CLIENT_ERROR_CONFLICT, message);
        }
        catch (NodeNotFoundException e)
        {
            final String message = "Could not resolve part of path for node: " + path;
            log.debug(message, e);
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, message);
        }
        catch (IOException e)
        {
            final String message = "Unexception IOException";
            log.debug(message, e);
            setStatus(Status.SERVER_ERROR_INTERNAL, message);
        }
        return null;
    }
    
    @Delete
    public void remove()
    {
        try
        {
            getNodePersistence().delete(node);
        }
        catch (NodeNotFoundException e)
        {
            final String message = "Could not find node with path: " + path;
            log.debug(message, e);
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, message);
        }
    }

}
