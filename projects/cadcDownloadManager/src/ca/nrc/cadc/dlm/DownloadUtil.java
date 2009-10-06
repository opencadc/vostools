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


package ca.nrc.cadc.dlm;

import ca.nrc.cadc.net.MultiSchemeHandler;
import ca.nrc.cadc.net.SchemeHandler;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Miscellanneous methods for use in JSP pages.
 *
 * @author pdowler
 */
public class DownloadUtil
{
    private static MultiSchemeHandler schemeHandler;
    
    private DownloadUtil() { }
    
    // static classes for return values so we can put list operations in here 
    // and keep fine-grained error handling in app
    
    public static class ParsedURI
    {
        public String str;
        public URI uri;
        public Throwable error;
    }
    public static class GeneratedURL
    {
        public String str;
        public URL url;
        public Throwable error;
    }

    public static synchronized MultiSchemeHandler getSchemeHandler()
    {
        if (schemeHandler == null)
        {        
            schemeHandler = new MultiSchemeHandler();
            // TODO: read class name(s) from somewhere
            String[] uris = new String[] 
            {
                "ad:ca.nrc.cadc.ad.AdSchemeHandler",
                "plane:ca.nrc.cadc.caom.util.PlaneSchemeHandler"
            };

            for (int i=0; i<uris.length; i++)
            {
                try
                {
                    System.out.println("[CoreUI] configuring: " + uris[i]);
                    URI u = new URI(uris[i]);
                    String scheme = u.getScheme();
                    String cname = u.getSchemeSpecificPart();
                    System.out.println("[CoreUI] loading: " + cname);
                    Class c = Class.forName(cname);
                    System.out.println("[CoreUI] instantiating: " + c);
                    SchemeHandler handler = (SchemeHandler) c.newInstance();
                    System.out.println("[CoreUI] adding: " + scheme + "," + handler);
                    schemeHandler.addSchemeHandler(scheme, handler);
                    System.out.println("[CoreUI] success: " + scheme + " is supported");
                }
                catch(Throwable oops)
                {
                    System.out.println("[CoreUI] failed to create SchemeHandler: " + uris[i] + ", " + oops);
                }
            }
        }
        return schemeHandler;
    }
    
    /**
     * Parse a comma-separated list of URIs into a single list of unique URIs and
     * convert URIs to URLs via URIConverter. The resulting list may contain URLs 
     * or URIs; the latter occurs when the URI scheme is not recognized by the 
     * URIConverter.
     * 
     * @param uris
     * @param commonFragment
     * @return mixed List of GeneratedURL objects
     * @throws MalformedURLException
     * @throws URISyntaxException
     */
    public static List<GeneratedURL> generateURLs(String uris, String commonFragment)
    {
        return generateURLs(uris.split(","), commonFragment);
    }
    
    /**
     * Parse an array of (possibly comma separated) URIs into a single list of unique URIs and
     * convert URIs to URLs via URIConverter.
     * 
     * @param uris
     * @param commonFragment
     * @return List of GeneratedURL objects
     * @throws URISyntaxException
     */
    public static List<GeneratedURL> generateURLs(String[] uris, String commonFragment)
    {
        List<ParsedURI> uriList = parseURIs(uris, commonFragment);
        return generateURLs(uriList, commonFragment);
    }
    
    public static List<GeneratedURL> generateURLs(List<ParsedURI> uris, String commonFragment)
    {
        System.out.println("[DownloadUtil] generateURLs: START");
        List ret = new ArrayList<GeneratedURL>();
        
        Iterator<ParsedURI> i = uris.iterator();
        while ( i.hasNext() )
        {
            ParsedURI pu = i.next();
            
            if (pu.error != null)
            {
                GeneratedURL gu = new GeneratedURL();
                gu.str = pu.str;
                gu.error = pu.error;
                ret.add(gu);
            }
            else
            {
                try
                {
                    List<URL> urls = getSchemeHandler().toURL(pu.uri);
                    Iterator<URL> j = urls.iterator();
                    while ( j.hasNext() )
                    {
                        GeneratedURL gu = new GeneratedURL();
                        gu.url = j.next();
                        //gu.str = pu.str;
                        gu.str = gu.url.toString();
                        ret.add(gu);
                    }
                }
                catch(IllegalArgumentException iex)
                {
                    GeneratedURL gu = new GeneratedURL();
                    gu.str = pu.str;
                    gu.error = iex;
                    ret.add(gu);
                }
            }
        }
        System.out.println("[DownloadUtil] generateURLs: " + ret.size() + " URLs");
        return ret;
    }
    
    /**
     * Parse a comma separated list URIs into a single list of unique URI(s)
     *
     * @return unique List of URI
     */
    public static List<ParsedURI> parseURIs(String uris, String commonFragment)
    {
        ArrayList ret = new ArrayList<URI>();
        parseURIs(uris, commonFragment, ret);
        return ret;
    }
    
    public static List<ParsedURI> parseURIs(String[] uris, String commonFragment)
    {
        ArrayList ret = new ArrayList<URI>();
        parseURIs(uris, commonFragment, ret);
        return ret;
    }
    
    /**
     * Flatten an array of URIs into a comma separated list.
     *
     * @return comma separated list of URI strings
     */
    public static String flattenURIs(String[] uris)
    {
        String ret = "";
        if (uris != null && uris.length > 0)
        {
            StringBuffer sb = new StringBuffer();
            for (int i=0; i<uris.length; i++)
            {
                if (uris[i] != null)
                    sb.append(uris[i].trim() + ",");
            }
            ret = sb.substring(0, sb.length() - 1); // omit trailing comma
        }
        return ret;
    }
    
    // generate a List of URIs from the array of (comma-separated list of) URIs
    private static void parseURIs(String[] uris, String commonFragment, List<ParsedURI> ret)
    {
        if (uris != null && uris.length > 0)
        {
            for (int i=0; i<uris.length; i++)
                parseURIs(uris[i], commonFragment, ret);
        }
    }
    
    // generate a List of URIs from the comma-separated list of URIs
    private static void parseURIs(String uris, String commonFragment, List<ParsedURI> ret)
    {
        System.out.println("[DownloadUtil] parseURIs: " + uris + " commonFragment: " + commonFragment);
        if (uris != null)
        {
            if (commonFragment != null)
            {
                commonFragment = commonFragment.trim();
                if (commonFragment.length() == 0)
                    commonFragment = null;
            }
            String[] sa = uris.split(",");
            for (int i=0; i<sa.length; i++)
            {
                String s = sa[i].trim();
                if (s.length() > 0) // guard against original list having extra commas aka empty URIs
                {
                    ParsedURI pu = new ParsedURI();
                    pu.str = s;
                    try
                    {
                        pu.uri = new URI(s);
                        if ( !s.startsWith("http:") )
                            pu.uri = appendFragment(pu.uri, commonFragment);
                    }
                    catch(URISyntaxException uex)
                    {
                        pu.error = uex;
                    }
                    ret.add(pu);
                    // TODO: duplicate check?
                }
            }
        }
    }
    
    private static URI appendFragment(URI uri, String fragment)
        throws URISyntaxException
    {
        if (fragment == null)
            return uri;
        
        String orig = uri.getFragment(); // fragment was encoded in string
        if (orig == null)
            orig = fragment;
        else
            orig += "&" + fragment;
        return new URI(uri.getScheme(), uri.getSchemeSpecificPart(), orig);
    }

    public static void debug(String key, String value)
    {
        System.out.println("[DownloadUtil] " + key + " = " + value);
    }
    public static void debug(String key, String[] value)
    {
        try
        {
            System.out.println("[DownloadUtil] " + key + " START");
    
            if (value == null)
                return;
            System.out.println("[DownloadUtil] " + key + " = " + value.length);
            for (int i=0; i<value.length; i++)
                debug("\t"+key, value[i]);
        }
        finally
        {
            System.out.println("[DownloadUtil] " + key + " DONE");
        }
    }
}
