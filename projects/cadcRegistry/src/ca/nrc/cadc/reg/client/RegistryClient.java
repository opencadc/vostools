/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2010.                            (c) 2010.
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
*  $Revision: 5 $
*
************************************************************************
*/

package ca.nrc.cadc.reg.client;

import ca.nrc.cadc.util.MultiValuedProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;


/**
 * A very simple caching IVOA Registry client. All the lookups done by this client use a properties
 * file named RegistryClient.properties found via the classpath.
 * </p><p>
 * Note for developers: You can set a system property to force this class to replace the hostname
 * in the resuting URL with the canonical hostname of the local host. This is useful for testing:
 * </p>
 * <pre>
 * ca.nrc.cadc.reg.client.RegistryClient.local=true
 * </pre>
 *
 * @author pdowler
 */
public class RegistryClient
{
    private static Logger log = Logger.getLogger(RegistryClient.class);

    private static final String CACHE_FILENAME = RegistryClient.class.getSimpleName() + ".properties";
    private static final String LOCAL_PROPERTY = RegistryClient.class.getName() + ".local";


    private Map<URI,List<URL>> lookup;
    private MultiValuedProperties mvp;

    /**
     * Find the service URL for the service registered under the specified identifier. The
     * identifier must be an IVOA identifier (e.g. with URI scheme os "ivo"). If the service
     * has more than one service URL, one is chosen.
     *
     * @param serviceID
     * @throws MalformedURLException if underlying properties file contains malformed URL
     * @return base URL
     */
    public URL getServiceURL(URI serviceID)
        throws MalformedURLException
    {
        return getServiceURL(serviceID, null);
    }

    /**
     * Find the service URL for the service registered under the specified identifier and
     * using the specified protocol. The identifier must be an IVOA identifier
     * (e.g. with URI scheme os "ivo"). The protocol argument may be null, in which case this
     * method behaves exactly like getServiceURL(URI).
     *
     * @param serviceID the identifier of the service
     * @param protocol the desired protocol or null if any will do
     * @throws MalformedURLException if underlying properties file contains malformed URL
     * @return base URL or null if a matching service (and protocol) was not found
     */
    public URL getServiceURL(URI serviceID, String protocol)
        throws MalformedURLException
    {
        init();
        log.debug("getServiceURL: " + serviceID + "," + protocol);

        //List<URL> urls = lookup.get(serviceID);
        List<String> urls = mvp.getProperty(serviceID.toString());
        if (urls == null || urls.size() == 0)
        {
            //log.debug("no matching serviceID found");
            return null; // could not find matching serviceID
        }
        String url = urls.get(0);

        if (protocol != null)
        {
            for (String u : urls)
            {
                if ( u.startsWith(protocol + "://") )
                    url = u;
            }
            if ( !url.startsWith(protocol + "://") )
            {
                //log.debug("no matching protocol found");
                return null; // could not find matching protocol
            }
        }

        URL ret = new URL(url);
        if ( "true".equals(System.getProperty(LOCAL_PROPERTY)) )
        {
            try
            {
                log.debug(LOCAL_PROPERTY + " is set, assuming localhost runs the service");
                StringBuffer sb = new StringBuffer();
                sb.append(ret.getProtocol());
                sb.append("://");
                sb.append(InetAddress.getLocalHost().getCanonicalHostName());
                int p = ret.getPort();
                if (p > 0 && p != ret.getDefaultPort())
                {
                    sb.append(":");
                    sb.append(p);
                }
                sb.append(ret.getPath());
                String q = ret.getQuery();
                if (q != null && q.length() > 0)
                {
                    sb.append("?");
                    sb.append(q);
                }
                ret = new URL(sb.toString());
            }
            catch(UnknownHostException ex)
            {
                throw new RuntimeException("failed to determine canonical local host name", ex);
            }
            catch(MalformedURLException ex)
            {
                // we caused this ourselves, so don't blame prop file
                throw new RuntimeException("failed to change host name in URL to canonical local host name", ex);
            }
        }

        return ret;
    }

    private void init()
    {
        if (mvp != null)
            return;

        try
        {
            // find RegistryClient.properties in classpath
            URL url = RegistryClient.class.getClassLoader().getResource(CACHE_FILENAME);
            if (url == null)
                throw new RuntimeException("failed to find resource: " + CACHE_FILENAME);

            // read the properties
            InputStream istream = url.openStream();
            this.mvp = new MultiValuedProperties();
            mvp.load(istream);

            // load into the lookup map
            /*
            this.lookup = new HashMap<URI,List<URL>>();
            Iterator<String> iter = mvp.keySet().iterator();
            while ( iter.hasNext() )
            {
                String key = iter.next();
                List<String> values = mvp.getProperty(key);
                URI uri = new URI(key);
                List<URL> urls = new ArrayList<URL>(values.size());
                for (String s : values)
                {
                    URL u = new URL(s);
                    urls.add(u);
                    log.debug("init: " + uri + " -> " + u);
                }
            }
            */
        }
        catch(IOException ex)
        {
            throw new RuntimeException("failed to load resource: " + CACHE_FILENAME, ex);
        }
        //catch(URISyntaxException ex)
        //{
        //    throw new RuntimeException("failed to parse resource: " + CACHE_FILENAME, ex);
        //}
    }
}
