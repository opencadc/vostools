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

package ca.nrc.cadc.vos.server;

import ca.nrc.cadc.auth.SSOCookieManager;
import ca.nrc.cadc.net.NetUtil;
import ca.nrc.cadc.reg.client.RegistryClient;
import ca.nrc.cadc.util.ArrayUtil;
import ca.nrc.cadc.vos.VOSURI;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Download pre-processor for Java WebStart download method.
 * @author adriand
 */
public class JavaWebStartServlet extends HttpServlet
{
    private static final long serialVersionUID = 201210190745L;
    
    private static final Logger log = Logger.getLogger(JavaWebStartServlet.class);
    
    /**
     * 
     * @param config
     * @throws javax.servlet.ServletException
     */
    @Override
    public void init(ServletConfig config)
        throws ServletException
    {
        super.init(config);
        log.setLevel(Level.INFO);
    }
    
    /**
     * Handle POSTed download request directed to the Java WebStart download method.
     * 
     * @param request
     * @param response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
                    
        // codebase for applet and webstart deployments
        String codebase = getCodebase(request);
        request.setAttribute("codebase", codebase);
        log.debug("codebase attribute: " + codebase);

        // from the target vos uri find the vospace service url
        String dest = request.getParameter("dest");
        if (dest == null)
        {
            throw new ServletException("Required dest parameter not found");
        }
        request.setAttribute("dest", dest);
        log.debug("dest: " + dest);

        try
        {
            VOSURI destUri = new VOSURI(dest);
            RegistryClient regClient = new RegistryClient();
            URL endpoint = regClient.getServiceURL(destUri.getServiceURI(), "https");
            request.setAttribute("vospaceWebServiceURL", endpoint.toExternalForm());
            log.debug("vospaceWebServiceURL: " + endpoint);
        }
        catch (URISyntaxException e)
        {
            throw new ServletException("Unable to parse dest parameter to uri because " + e.getMessage());
        }

        log.debug("looking for ssocookie attribute...");
        final Cookie[] cookies = request.getCookies();
        if (!ArrayUtil.isEmpty(cookies))
        {
            for (final Cookie cookie : cookies)
            {
                if (cookie.getName().equals(
                        SSOCookieManager.DEFAULT_SSO_COOKIE_NAME))
                {
                    request.setAttribute("ssocookie", cookie.getValue());
                    log.debug("ssocookie attribute: " + cookie.getValue());
                    request.setAttribute("ssocookiedomain", NetUtil
                            .getDomainName(request.getRequestURL()
                                    .toString()));
                    log.debug("ssocookie domain: "
                            + NetUtil.getDomainName(request
                                    .getRequestURL().toString()));
                }
            }
        }

        RequestDispatcher disp = request.getRequestDispatcher("VOSpaceUpload.jsp");
        disp.forward(request, response);
    }
    
    public static String getCodebase(HttpServletRequest request)
    {
        try
        {
            URL req = new URL(request.getRequestURL().toString());
            String ret = req.getProtocol() + "://" + req.getHost();
            ret += request.getContextPath();
            return ret;
        }
        catch(Throwable oops)
        {
            log.error("failed to generate codebase URL", oops);
        }
        return null;
    }
    
}
