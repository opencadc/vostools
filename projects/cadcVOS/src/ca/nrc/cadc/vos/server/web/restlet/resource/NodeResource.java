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

package ca.nrc.cadc.vos.server.web.restlet.resource;

import java.net.URISyntaxException;
import java.security.AccessControlException;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;
import org.restlet.data.Form;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;

import ca.nrc.cadc.vos.NodeFault;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.server.auth.VOSpaceAuthorizer;
import ca.nrc.cadc.vos.server.web.representation.NodeErrorRepresentation;
import ca.nrc.cadc.vos.server.web.restlet.action.CreateNodeAction;
import ca.nrc.cadc.vos.server.web.restlet.action.DeleteNodeAction;
import ca.nrc.cadc.vos.server.web.restlet.action.GetNodeAction;
import ca.nrc.cadc.vos.server.web.restlet.action.NodeAction;
import ca.nrc.cadc.vos.server.web.restlet.action.NodeActionResult;
import ca.nrc.cadc.vos.server.web.restlet.action.UpdatePropertiesAction;

/**
 * Handles HTTP requests for Node resources.
 * 
 * @author majorbNodeNotFoundException
 *
 */
public class NodeResource extends BaseResource
{
    private static Logger log = Logger.getLogger(NodeResource.class);
    
    private String path;
    private NodeFault nodeFault;
    private VOSURI vosURI;
    private String viewReference;
    
    /**
     * Called after object instantiation.
     */
    public void doInit()
    {
        log.debug("Enter NodeResource.doInit(): " + getMethod());

        try
        {
            super.doInit();
            
            path = (String) getRequest().getAttributes().get("nodePath");  
            log.debug("path = " + path);
            
            if (path == null || path.trim().length() == 0)
            {
                throw new IllegalArgumentException("No node path information provided.");
            }
            
            vosURI = new VOSURI(getVosUriPrefix() + "/" + path);
            
            Form form = getRequest().getResourceRef().getQueryAsForm();
            viewReference = form.getFirstValue("view");
            log.debug("viewReference = " + viewReference);
        }
        catch (URISyntaxException e)
        {
            String message = "URI not well formed: " + vosURI;
            log.debug(message, e);
            nodeFault = NodeFault.InvalidURI;
            nodeFault.setMessage(message);
        }
        catch (AccessControlException e)
        {
            String message = "Access Denied: " + e.getMessage();
            log.debug(message, e);
            nodeFault = NodeFault.PermissionDenied;
            nodeFault.setMessage(message);
        }
        catch (UnsupportedOperationException e)
        {
            String message = "Not supported: " + e.getMessage();
            log.debug(message, e);
            nodeFault = NodeFault.NotSupported;
            nodeFault.setMessage(message);
        }
        catch (IllegalArgumentException e)
        {
            String message = "Bad input: " + e.getMessage();
            log.debug(message, e);
            nodeFault = NodeFault.BadRequest;
            nodeFault.setMessage(message);
        }
        catch (Throwable t)
        {
            String message = "Internal Error:" + t.getMessage();
            log.debug(message, t);
            nodeFault = NodeFault.InternalFault;
            nodeFault.setMessage(message);
        }
    }
    
    /**
     * Performs the specified node action, first checking for errors, and
     * catching any errors that may happen.
     * @param action The action to perform.
     * @return A representation of the output or of any error that happens.
     */
    private Representation performNodeAction(NodeAction action)
    {
        log.debug("Enter NodeResource.performNodeAction()");
        try
        {
            if (nodeFault != null)
            {
                setStatus(nodeFault.getStatus());
                return new NodeErrorRepresentation(nodeFault);
            }
            
            VOSpaceAuthorizer voSpaceAuthorizer = new VOSpaceAuthorizer();
            voSpaceAuthorizer.setNodePersistence(getNodePersistence());
            
            action.setVOSpaceAuthorizer(voSpaceAuthorizer);
            action.setNodePersistence(getNodePersistence());
            action.setVosURI(vosURI);
            action.setNodeXML(getRequestEntity());
            action.setRequest(getRequest());
            action.setViewReference(viewReference);

            NodeActionResult result = null;
            if (getSubject() == null)
            {
                result = (NodeActionResult) action.run();
            }
            else
            {
                result = (NodeActionResult) Subject.doAs(getSubject(), action);
            }
            
            if (result != null)
            {
                setStatus(result.getStatus());
                if (result.getRedirectURL() != null)
                {
                    getResponse().redirectSeeOther(
                            result.getRedirectURL().toString());
                }
                else
                {
                    return result.getRepresentation();
                }
            }
            return null;
            
            
            
        }
        catch (Throwable t)
        {
            log.debug(t);
            setStatus(NodeFault.InternalFault.getStatus());
            return new NodeErrorRepresentation(NodeFault.InternalFault);
        }
    }
    
    /**
     * HTTP GET
     */
    @Get("xml")
    public Representation represent()
    {
        log.debug("Enter NodeResource.represent()");
        return performNodeAction(new GetNodeAction());
    }
    
    /**
     * HTTP PUT
     */
    @Put("xml")
    public Representation store()
    {   
        log.debug("Enter NodeResource.store()");
        return performNodeAction(new CreateNodeAction());
    }
    
    /**
     * HTTP POST
     */
    @Post("xml")
    public Representation accept()
    {
        log.debug("Enter NodeResource.accept()");
        return performNodeAction(new UpdatePropertiesAction());
    }
    
    /**
     * HTTP DELETE
     */
    @Delete
    public Representation remove()
    {
        log.debug("Enter NodeResource.remove()");
        return performNodeAction(new DeleteNodeAction());
    }
    

    
}
