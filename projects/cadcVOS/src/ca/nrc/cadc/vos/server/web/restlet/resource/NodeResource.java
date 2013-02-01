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
import java.security.PrivilegedAction;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;

import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.util.StringUtil;
import ca.nrc.cadc.uws.util.RestletLogInfo;
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
 * @author majorb
 *
 */
public class NodeResource extends BaseResource implements PrivilegedAction<Representation>
{
    private static Logger LOGGER = Logger.getLogger(NodeResource.class);

    private NodeFault nodeFault;
    private VOSURI vosURI;
    private NodeAction action;
    private RestletLogInfo logInfo;
    
    /**
     * Called after object instantiation.
     */
    @Override
    public void doInit()
    {
        LOGGER.debug("Enter NodeResource.doInit(): " + getMethod());

        try
        {
            super.doInit();

            final String path = (String) getRequest().getAttributes().
                    get("nodePath");
            LOGGER.debug("path = " + path);
            
            LOGGER.debug("prefix = " + getVosUriPrefix());
            if (path != null)
                vosURI = new VOSURI(getVosUriPrefix() + "/" + path);
            else
                vosURI = new VOSURI(getVosUriPrefix()); // the root Container
            LOGGER.debug("vosURI = " + vosURI);
        }
        catch (URISyntaxException e)
        {
            String message = "URI not well formed: " + vosURI;
            LOGGER.debug(message, e);
            nodeFault = NodeFault.InvalidURI;
            nodeFault.setMessage(message);
        }
        catch (AccessControlException e)
        {
            String message = "Access Denied: " + e.getMessage();
            LOGGER.debug(message, e);
            nodeFault = NodeFault.PermissionDenied;
            nodeFault.setMessage(message);
        }
        catch (UnsupportedOperationException e)
        {
            String message = "Not supported: " + e.getMessage();
            LOGGER.debug(message, e);
            nodeFault = NodeFault.InvalidArgument;
            nodeFault.setMessage(message);
        }
        catch (IllegalArgumentException e)
        {
            String message = "Bad input: " + e.getMessage();
            LOGGER.debug(message, e);
            nodeFault = NodeFault.InvalidArgument;
            nodeFault.setMessage(message);
        }
        catch (Throwable t)
        {
            String message = "Internal Error:" + t.getMessage();
            LOGGER.debug(message, t);
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
    @Override
    public Representation run()
    {
        LOGGER.debug("Enter NodeResource.run()");
        
        long start = System.currentTimeMillis();
        long end = -1;
        String message = null;
        
        try
        {
            String startMessage = logInfo.start();
            LOGGER.info(startMessage);
            
            if (nodeFault != null)
            {
                setStatus(nodeFault.getStatus());
                logInfo.setSuccess(false);
                logInfo.setMessage(getErrorMessage(nodeFault));
                return new NodeErrorRepresentation(nodeFault);
            }
            
            // Two versions of authorizer: one that allows partial paths
            // for link resolution and one that doesn't.
            VOSpaceAuthorizer voSpaceAuthorizer = new VOSpaceAuthorizer(false);
            VOSpaceAuthorizer partialPathVOSpaceAuthorizer = new VOSpaceAuthorizer(true);
            
            voSpaceAuthorizer.setNodePersistence(getNodePersistence());
            partialPathVOSpaceAuthorizer.setNodePersistence(getNodePersistence());
            
            action.setVOSpaceAuthorizer(voSpaceAuthorizer);
            action.setPartialPathVOSpaceAuthorizer(partialPathVOSpaceAuthorizer);
            action.setNodePersistence(getNodePersistence());
            action.setVosURI(vosURI);
            action.setNodeXML(getRequestEntity());
            action.setRequest(getRequest());
            action.setQueryForm(getQueryForm());
            action.setStylesheetReference(getStylesheetReference());
            
            final NodeActionResult result = action.run();
            
            if (result != null)
            {
                if (result.getNodeFault() != null)
                {
                    message = getErrorMessage(result.getNodeFault());
                    logInfo.setSuccess(!result.getNodeFault().isServiceFailure());
                }
                
                setStatus(result.getStatus());
                
                if (result.getRedirectURL() != null)
                {
                    if (StringUtil.hasLength(message))
                    {
                        message = message + " ";
                    }
                    message = message + "Redirecting to: " + result.getRedirectURL().toExternalForm();
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
        catch (TransientException t)
        {
            LOGGER.debug(t);
            logInfo.setSuccess(false);
            message = "Transient exception: " + t.getMessage();
            setStatus(NodeFault.ServiceBusy.getStatus());
            
            Calendar retryTime = new GregorianCalendar();
            LOGGER.debug("After transient exception, setting retry-after to be "
                    + t.getRetryDelay() + " seconds.");
            retryTime.add(Calendar.SECOND, t.getRetryDelay());
            getResponse().setRetryAfter(retryTime.getTime());
            
            return new NodeErrorRepresentation(NodeFault.ServiceBusy);
        }
        catch (Throwable t)
        {
            LOGGER.debug(t);
            setStatus(NodeFault.InternalFault.getStatus());
            logInfo.setSuccess(false);
            message = getErrorMessage(NodeFault.InternalFault);
            if (StringUtil.hasText(t.getMessage()))
            {
                message = message
                    + ": " + t.getClass().getSimpleName()
                    + ": " + t.getMessage();
            }
            else
            {
                message = message
                    + ": "
                    + t.getClass().getSimpleName();
            }
            return new NodeErrorRepresentation(NodeFault.InternalFault);
        }
        finally
        {
            end = System.currentTimeMillis();
            logInfo.setElapsedTime(end - start);
            logInfo.setMessage(message);
            String endMessage = logInfo.end();
            LOGGER.info(endMessage);
        }
    }
    
    private Representation runPrivilegedAction(NodeAction action)
    {
        this.action = action;
        logInfo = new RestletLogInfo(getRequest());
        Subject subject = getSubject();
        
        if (subject == null)
            return run();
        else
        {
            logInfo.setSubject(subject);
            return Subject.doAs(getSubject(), this);
        }
    }
    
    @Get("xml")
    public Representation represent()
    {
        LOGGER.debug("Enter NodeResource.represent()");
        return runPrivilegedAction(new GetNodeAction());
    }

    @Put
    public Representation create(final Representation entity)
    {
        LOGGER.debug("Enter NodeResource.store()");
        return runPrivilegedAction(new CreateNodeAction());
    }

    @Post
    public Representation update(final Representation entity)
    {
        LOGGER.debug("Enter NodeResource.accept()");
        return runPrivilegedAction(new UpdatePropertiesAction());
    }

    @Delete
    public Representation remove()
    {
        LOGGER.debug("Enter NodeResource.remove()");
        return runPrivilegedAction(new DeleteNodeAction());
    }   
    
}
