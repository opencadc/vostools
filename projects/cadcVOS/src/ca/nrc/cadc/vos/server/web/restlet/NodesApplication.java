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

package ca.nrc.cadc.vos.server.web.restlet;

import java.util.Map;

import org.apache.log4j.Logger;
import org.restlet.Application;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;
import org.restlet.routing.TemplateRoute;
import org.restlet.routing.Variable;

import ca.nrc.cadc.vos.InvalidServiceException;
import ca.nrc.cadc.vos.server.util.BeanUtil;
import ca.nrc.cadc.vos.server.web.restlet.resource.NodeResource;

/**
 * Application for handling Node routing and resources.
 * 
 * @author majorb
 *
 */
public class NodesApplication extends Application
{
    
    private static final Logger log = Logger.getLogger(NodesApplication.class);

    public NodesApplication()
    {
    }

    public NodesApplication(final Context context)
    {
        super(context);
        
    }

    private class NodesRouter extends Router
    {
        public NodesRouter(final Context context)
        {
            super(context);
            attach("", NodeResource.class);
            attach("/", NodeResource.class);

            log.debug("attaching /{nodePath} -> NodeResource");
            TemplateRoute nodeRoute = attach("/{nodePath}", NodeResource.class);
            Map<String, Variable> nodeRouteVariables = nodeRoute.getTemplate().getVariables();
            nodeRouteVariables.put("nodePath", new Variable(Variable.TYPE_ALL));
            log.debug("attaching /{nodePath} -> NodeResource - DONE");
        }
    }
    
    @Override
    public Restlet createInboundRoot()
    {
        
        Context context = getContext();
        
        // Get and save the vospace uri in the input representation
        // for later use
        final String vosURI = context.getParameters().
                getFirstValue(BeanUtil.IVOA_VOS_URI);
        if (vosURI == null || vosURI.trim().length() == 0)
        {
            final String message = "Context parameter not set: " + BeanUtil.IVOA_VOS_URI;
            log.error(message);
            throw new RuntimeException(message);
        }
        
        // save the vospace uri in the application context
        context.getAttributes().put(BeanUtil.IVOA_VOS_URI, vosURI);
        
        // stylesheet reference
        String stylesheetReference = context.getParameters().getFirstValue(BeanUtil.VOS_STYLESHEET_REFERENCE);
        context.getAttributes().put(BeanUtil.VOS_STYLESHEET_REFERENCE, stylesheetReference);
        
        // Create the configured NodePersistence bean
        createContextBean(context, ca.nrc.cadc.vos.server.NodePersistence.class, BeanUtil.VOS_NODE_PERSISTENCE);
        
        return new NodesRouter(context);
    }
    
    private void createContextBean(Context context, Class<?> beanInterface, String contextParam)
    {
        try
        {
            final String className = context.getParameters().
                    getFirstValue(contextParam);
            final BeanUtil beanUtil = new BeanUtil(className);
            Object bean = beanUtil.createBean();
            if ((beanInterface != null) && !beanInterface.isInstance(bean))
            {
                throw new InvalidServiceException("Bean does not implement interface: " + beanInterface.getName());
            }
            context.getAttributes().put(contextParam, bean);
            log.debug("Added " + contextParam + " bean to application context: " + className);
        }
        catch (InvalidServiceException e)
        {
            final String message = "Could not create bean: " + contextParam + ": " + e.getMessage();
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }
    
}