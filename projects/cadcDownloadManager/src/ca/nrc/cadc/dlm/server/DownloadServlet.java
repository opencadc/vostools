/*****************************************************************************
 *  
 *  Copyright (C) 2009				Copyright (C) 2009
 *  National Research Council		Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6			Ottawa, Canada, K1A 0R6
 *  All rights reserved				Tous droits reserves
 *  					
 *  NRC disclaims any warranties,	Le CNRC denie toute garantie
 *  expressed, implied, or statu-	enoncee, implicite ou legale,
 *  tory, of any kind with respect	de quelque nature que se soit,
 *  to the software, including		concernant le logiciel, y com-
 *  without limitation any war-		pris sans restriction toute
 *  ranty of merchantability or		garantie de valeur marchande
 *  fitness for a particular pur-	ou de pertinence pour un usage
 *  pose.  NRC shall not be liable	particulier.  Le CNRC ne
 *  in any event for any damages,	pourra en aucun cas etre tenu
 *  whether direct or indirect,		responsable de tout dommage,
 *  special or general, consequen-	direct ou indirect, particul-
 *  tial or incidental, arising		ier ou general, accessoire ou
 *  from the use of the software.	fortuit, resultant de l'utili-
 *  								sation du logiciel.
 *  
 *  
 *  This file is part of cadcDownloadManager.
 *  
 *  CadcDownloadManager is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  CadcDownloadManager is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with cadcDownloadManager.  If not, see <http://www.gnu.org/licenses/>.			
 *  
 *****************************************************************************/

package ca.nrc.cadc.dlm.server;

import ca.nrc.cadc.dlm.DownloadUtil;
import java.io.IOException;
import java.util.Date;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Download pre-processor. This servlet accepts either direct POSTs from clients or
 * requerst scope attributes and passes the request on to the approproate JSP page.
 * </p><p>
 * For direct POST, the client must use the multi-valued <em>uri</em> parameter to pass 
 * in one or more URIs. These are flattened into a single comma-separated list and 
 * passed along as attributes. The client may also set the single-valued <em>fragment</em>
 * parameter; the fragment is appended to each URI before SchemeHandler(s) are used to
 * convert them to URLs.
 * </p><p>
 * When forwarding from another servlet
 * @author pdowler
 */
public class DownloadServlet extends HttpServlet
{
    private static HackLogger log;
    private static class HackLogger
    {
        boolean dbg = true;
        Class c;
        String prefix;
        HackLogger(Class c) 
        { 
            this.c = c; 
            this.prefix = " " + c.getSimpleName() + " ";
        }
        
        void error(String s)
        {
            System.out.println(new Date() + " [ERROR]" + prefix + s);
        }
        void warn(String s)
        {
            System.out.println(new Date() + " [WARN]" + prefix + s);
        }
        void info(String s)
        {
            System.out.println(new Date() + " [INFO]" + prefix + s);
        }
        void debug(String s)
        {
            if (dbg) System.out.println(new Date() + " [DEBUG]" + prefix + s);
        }
    }
    static
    {
        // TODO: pick a logging system and configure it properly
        log = new HackLogger(DownloadServlet.class);
        log.dbg = true;
    }
    
    /**
     * 
     * @param config
     * @throws javax.servlet.ServletException
     */
    public void init(ServletConfig config)
        throws ServletException
    {
        super.init(config);
    }
    
    /**
     * Handle POSTed download request from an external page.
     * 
     * @param request
     * @param response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        boolean empty = true;

        // check for forwarded attributes
        String fragment = (String) request.getAttribute("fragment");
        String uris = (String) request.getAttribute("uris");
        log.debug("fragment attribute: " + fragment);
        log.debug("uris attribute: " + uris);
        
        // check for direct POST
        if (uris == null)
        {
            log.debug("checking for uris parameter...");
            uris = request.getParameter("uris"); // forward/render of JSP page
            
            if (uris == null)
            {
                log.debug("checking for uri parameter...");
                String[] uri = request.getParameterValues("uri");
                if (uri != null)
                {

                    uris = DownloadUtil.flattenURIs(uri);
                    log.debug("found " + uri.length + " uri parameters: " + uris);
                }
            }
            if (uris != null)
            {
                log.debug("adding request attribute: uris=" + uris);
                request.setAttribute("uris", uris);
                empty = false;
            }
        }
        else
            empty = false;
        
        if (fragment == null)
        {
            log.debug("checking for fragment parameter...");
            fragment = request.getParameter("fragment");
            if (fragment != null)
            {
                log.debug("adding request attribute: fragment=" + fragment);
                request.setAttribute("fragment", fragment);
            }
        }
        
        if (empty)
        {
             request.getRequestDispatcher("/emptySubmit.jsp").forward(request, response);
             return;
        }
        
        // check for preferred/selected download method
        String target = ServerUtil.getDownloadMethod(request, response);
        if (target == null)
            target = "/chooser.jsp";
                    
        // codebase for applet and webstart deployments
        String codebase = ServerUtil.getCodebase(request);
        request.setAttribute("codebase", codebase);

        RequestDispatcher disp = request.getRequestDispatcher(target);
        disp.forward(request, response);
    }
    
}
