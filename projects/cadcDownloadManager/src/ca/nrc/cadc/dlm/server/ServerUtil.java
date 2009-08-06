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

import java.io.IOException;
import java.net.URL;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * TODO.
 *
 * @author pdowler
 */
public class ServerUtil
{
    private ServerUtil() { }
 
    public static String APPLET = "Java Applet";
    public static String URLS = "URL List";
    public static String WEBSTART = "Java Webstart";
    
    private static int ONE_YEAR = 365*24*3600;
    
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
            oops.printStackTrace();
        }
        return null;
    }
    
    /**
     * Checks cookie and request param for download method preference; tries to set a cookie
     * to save setting for future use.
     *
     * @return name of page to forward to, null if caller should offer choices to user
     */
    public static String getDownloadMethod(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException
    {
        String method = request.getParameter("method");
        Cookie ck = null;
        
        // get cookie
        Cookie[] cookies = request.getCookies();
        if (cookies != null)
        {
            for (int i=0; i<cookies.length; i++)
            {
                if ( cookies[i].getName().equals("DownloadMethod") )
                    ck = cookies[i];
            }
        }
        
        if (method == null && ck != null)
            method = ck.getValue();
        
        String target = null;
        if (APPLET.equals(method))
            target = "/applet.jsp";
        else if (URLS.equals(method))
            target = "/wget.jsp";
        else if (WEBSTART.equals(method))
            target = "/DownloadManager.jnlp";
        else
        {
            // invalid method, tell page we did not forward
            if (ck != null)
            {
                // delete cookie on client
                ck.setValue(null);
                ck.setMaxAge(0); // delete
                response.addCookie(ck);
            }
            return null;
        }
        
        // set/edit cookie
        if (ck == null) // new
        {
            if ( request.getParameter("remember") != null )
            {
                ck = new Cookie("DownloadMethod", method);
                ck.setPath(request.getContextPath());
                ck.setMaxAge(ONE_YEAR);
                response.addCookie(ck);
            }
        }
        else if ( !method.equals(ck.getValue()) ) // changed
        {
            ck.setValue(method);
            ck.setPath(request.getContextPath());
            ck.setMaxAge(ONE_YEAR);
            response.addCookie(ck);
        }
        return target;
    }
}
