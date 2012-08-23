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


package ca.nrc.cadc.net;

import java.io.BufferedReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.log4j.Logger;

/**
 * Utility class to invoke the appropriate SchemeHandler to convert a URI to URL(s). 
 * If no SchemeHandler can be found, the URI.toURL() method is called as a fallback.
 * 
 * @author pdowler
 */
public class MultiSchemeHandler implements SchemeHandler
{
    private static final Logger log = Logger.getLogger(MultiSchemeHandler.class);

    private static final String CACHE_FILENAME = MultiSchemeHandler.class.getSimpleName() + ".properties";

    private final Map<String,SchemeHandler> handlers = new HashMap<String,SchemeHandler>();

    /**
     * Create a MultiSchemeHandler from the default config. By default, a resource named
     * MultiSchemeHandler.properties is found via the class loader that loaded this class.
     */
    public MultiSchemeHandler()
    {
        this(MultiSchemeHandler.class.getClassLoader().getResource(CACHE_FILENAME));
    }

    /**
     * Create a MultiSchemeHandler with configuration loaded from the specified URL.
     *
     * The config resource has contains URIs (one per line, comments start line with #, blank lines
     * are ignored) with a scheme and a class name of a class that implements the SchemeHandler
     * interface for that particular scheme.
     *
     * @param url
     */
    public MultiSchemeHandler(URL url)
    {
        if (url == null)
        {
            log.debug("config URL is null: no custom scheme support");
            return;
        }
        
        try
        {
            Properties props = new Properties();
            props.load(url.openStream());
            Iterator<String> i = props.stringPropertyNames().iterator();
            while ( i.hasNext() )
            {
                String scheme = i.next();
                String cname = props.getProperty(scheme);
                try
                {
                    log.debug("loading: " + cname);
                    Class c = Class.forName(cname);
                    log.debug("instantiating: " + c);
                    SchemeHandler handler = (SchemeHandler) c.newInstance();
                    log.debug("adding: " + scheme + "," + handler);
                    handlers.put(scheme, handler);
                    log.debug("success: " + scheme + " is supported");
                }
                catch(Exception fail)
                {
                    log.warn("failed to load " + cname + ", reason: " + fail);
                }
            }
        }
        catch(Exception ex)
        {
            log.error("failed to read config from " + url, ex);
        }
        finally
        {
            
        }
    }
    
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
            String frag = uri.getFragment();
            if (frag != null) // convert fragment to query string: this is horrendous :-(
            {
                String s = uri.toString().replace('#', '?');
                url = new URL(s);
                /*
                int i = s.indexOf('#');
                String suri = s.substring(0,i);
                String sfrag = s.substring(i+1);
                if (frag.equals(sfrag)) // check
                {
                    url = new URL(suri + "?" + frag);
                }
                */
            }
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
