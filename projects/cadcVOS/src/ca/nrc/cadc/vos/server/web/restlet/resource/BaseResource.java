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

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;
import org.restlet.Request;
import org.restlet.data.Method;
import org.restlet.resource.ServerResource;

import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.vos.server.NodePersistence;
import ca.nrc.cadc.vos.server.util.BeanUtil;
import java.security.Principal;
import org.restlet.data.ChallengeResponse;

public abstract class BaseResource extends ServerResource
{
    private static Logger log = Logger.getLogger(BaseResource.class);
    
    private static final String CERTIFICATE_REQUEST_ATTRIBUTE_NAME =
            "org.restlet.https.clientCertificates";
    
    private String vosUriPrefix;
    private NodePersistence nodePersistence;
    private String stylesheetReference;
    private Subject subject;
    
    protected BaseResource()
    {
        super();

        final Map<String, Object> attributes =
                getApplication().getContext().getAttributes();

        vosUriPrefix = (String) attributes.get(BeanUtil.IVOA_VOS_URI);
        
        nodePersistence = (NodePersistence) attributes.get(
                BeanUtil.VOS_NODE_PERSISTENCE);
        
        stylesheetReference = (String) attributes.get(
                BeanUtil.VOS_STYLESHEET_REFERENCE);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void doInit()
    {
        final Set<Method> allowedMethods = new CopyOnWriteArraySet<Method>();
        allowedMethods.add(Method.GET);
        allowedMethods.add(Method.PUT);
        allowedMethods.add(Method.DELETE);
        allowedMethods.add(Method.POST);

        setAllowedMethods(allowedMethods);
        
        // Create a subject for authentication
        Request request = getRequest();
        String user  = null;
        ChallengeResponse cr = request.getChallengeResponse();
        if (cr != null)
        {
            Principal p = cr.getPrincipal();
            if (p != null)
                user = p.getName();
        }
        Map<String, Object> requestAttributes = request.getAttributes();
        Collection<X509Certificate> certs =
                (Collection<X509Certificate>) requestAttributes.get(
                        CERTIFICATE_REQUEST_ATTRIBUTE_NAME);
        this.subject = AuthenticationUtil.getSubject(user, certs);
        log.debug(subject);
    }
    
    public final String getVosUriPrefix()
    {
        return vosUriPrefix;
    }
    
    public final NodePersistence getNodePersistence()
    {
        return nodePersistence;
    }
    
    public final String getStylesheetReference()
    {
        return stylesheetReference;
    }
    
    public final Subject getSubject()
    {
        return subject;
    }
}
