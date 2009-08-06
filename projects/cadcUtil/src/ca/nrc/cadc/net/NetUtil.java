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
 *  This file is part of cadcUtil.
 *  
 *  CadcUtil is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  CadcUtil is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with cadcUtil.  If not, see <http://www.gnu.org/licenses/>.			
 *  
 *****************************************************************************/

// Created on 25-Jul-07

package ca.nrc.cadc.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Miscellaneous network utility methods (static).
 *
 * @version $Version$
 * @author pdowler
 */
public class NetUtil
{
    /**
     * Find the server name. The server name is for use in constructing URLs to other services
     * on the same or a configured host. It is intended to support SchemeHandler implementations, 
     * but may have other uses. This method checks five (5) different places in order; the 
     * first one that produces a server name is returned.
     * </p>
     * <pre>
     * 1. system property constructed with the name of the specified class + .serverName
     * 2. system property constructed with the name of the package of the specified class + .serverName
     * 3. system property constructed with the name of the package of the NetUtil class + .serverName 
     *           (e.g. ca.nrc.cadc.net.serverName)
     * 4. the canonical FQHN from InetAddress.getInetAddress().getCanonicalHostname()
     * 5. localhost
     * </pre>
     * <p>
     * Thus, one can override the default (canonical host name in a properly configured network) with a global
     * (#3), package-specific (#2), or class specific (#1) setting as necessary.
     * 
     * @param c a class whose name is used to construct system properties (1 and 2 above), null allowed
     * @return a server name to use in constructing URLs
     */
    public static String getServerName(Class c)
    {
        // try class-specific setting
        if (c != null)
        {
            String s = System.getProperty(c.getName() + ".serverName");
            if (s != null)
                return s;
        }
        // try package-specific setting
        if (c != null)
        {
            String s = System.getProperty(c.getPackage().getName() + ".serverName");
            if (s != null)
                return s;
        }
        
        // try global serverName (this package)      
        String s = System.getProperty(NetUtil.class.getPackage().getName() + ".serverName");
        if (s != null)
            return s;
        
        // try FQHN from network
        try
        {   
            InetAddress inet = InetAddress.getLocalHost();
            return inet.getCanonicalHostName();
        }
        catch(UnknownHostException oops)
        {
            // NOTE: this does not use log4j because the net package can be used client-side
            // TODO: use JVM logging so we can turn this off by default?
            System.err.println("[" + NetUtil.class.getName() + "] network is poorly configured: " + oops);
        }
        
        // default: localhost
        return "localhost";
    }
}
