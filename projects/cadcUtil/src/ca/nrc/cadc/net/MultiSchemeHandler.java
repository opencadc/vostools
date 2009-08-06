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

package ca.nrc.cadc.net;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class to invoke the appropriate SchemeHandler to convert a URI to URL(s). 
 * If no SchemeHandler can be found, the URI.toURL() method is called as a fallback.
 * 
 * @author pdowler
 */
public class MultiSchemeHandler implements SchemeHandler
{
    private Map<String,SchemeHandler> handlers = new HashMap<String,SchemeHandler>();
    
    public MultiSchemeHandler() { } 
    
    /**
     * Find and call a suitable SchemeHandler. This method gets the scheme from the 
     * URI and uses it to find a configured SchemeHandler. If that is successful, the
     * SchemeHandler is used to do the conversion. If no SchemeHandler can be found,
     * the URI.toURL() method is called as a fallback, which is sufficient to handle
     * URIs where the scheme is a known transport protocol (e.g. http).
     * 
     * @param uri
     * @return a URL to the identified resource; null if the uri was null
     * @throws IllegalArgumentException if a URL cannot be generated
     * @throws UnsupportedOperationException if there is no SchemeHandler for the URI scheme
     */
    public List<URL> toURL(URI uri)
        throws IllegalArgumentException
    {
        if (uri == null)
            return null;
        
        SchemeHandler sh = (SchemeHandler) handlers.get(uri.getScheme());
        if (sh != null)
            return sh.toURL(uri);
        
        // fallback: hope for the best
        try 
        {
            URL url = uri.toURL(); 
            List<URL> ret = new ArrayList<URL>();
            ret.add(url);
            return ret;
        }
        catch(MalformedURLException mex) 
        { 
            throw new IllegalArgumentException("unknown URI scheme: " + uri.getScheme(), mex); 
        }
    }
    
    /**
     * Add a new SchemeHandler to the converter. If this handler has the same scheme as an
     * existing handler, it will replace the previous one.
     * 
     * @param scheme
     * @param handler
     */
    public void addSchemeHandler(String scheme, SchemeHandler handler)
    {
        handlers.put(scheme, handler);
    }
}
