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


// Created on 25-Jul-07

package ca.nrc.cadc.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.*;


/**
* Miscellaneous network utility methods (static).
*
* @version $Version$
* @author pdowler
*/
public class NetUtil
{
    
    private static final int ERROR_BUFFER_SIZE = 512;
    private static final int MAX_ERROR_LENGTH  = 16384;
    
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
            {
                return s;
            }
        }
        // try package-specific setting
        if (c != null)
        {
            String s = System.getProperty(c.getPackage().getName() + ".serverName");
            if (s != null)
            {
                return s;
            }
        }
        
        // try global serverName (this package)      
        String s = System.getProperty(NetUtil.class.getPackage().getName() + ".serverName");
        if (s != null)
        {
            return s;
        }
        
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

    // URLEncode a string.
    public static String encode(String s)
    {
        if (s == null)
        {
            return null;
        }
        try
        {
            return URLEncoder.encode(s, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("Unsupported encoding used", e);
        }
    }

    // URLDecode a string.
    public static String decode(String s)
    {
        if (s == null)
        {
            return null;
        }
        try
        {
            return URLDecoder.decode(s, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("Unsupported decoding used", e);
        }
    }
    
    /**
     * Return the error body of the response in the connection.
     * @param conn              The HTTP URL Connection.
     * @return                  String error body text, if any.
     * @throws IOException      If the stream cannot be obtained or read from.
     */
    public static String getErrorBody(HttpURLConnection conn)
            throws IOException
    {
        // get the error message from the body
        ByteArrayOutputStream out = null;
        InputStream in = null;
        try
        {
            out = new ByteArrayOutputStream();
            in = conn.getErrorStream();
            if (in == null)
            {
                return "";
            }
            byte[] buffer = new byte[ERROR_BUFFER_SIZE];
            int bytesRead;

            // read until finished
            while ((bytesRead = in.read(buffer)) > 0)
            {
                // throw bytes away if at the maximum
                if (out.size() < MAX_ERROR_LENGTH)
                {
                    out.write(buffer, 0, bytesRead);
                }
            }
            return out.toString("UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            if (out.size() > 0)
            {
                return out.toString();
            }
            return "";
        }
        finally
        {
            if (out != null)
            {
                out.close();
            }
            if (in != null)
            {
                in.close();
            }
        }
    }

    /**
     * Obtain the Domain Name from the given URL.  An inappropriate URL will
     * throw an Exception.
     *
     * @param stringURL           The URL string to parse from.
     * @return              String domain name.
     * @throws IOException  If anything goes awry during URL handling.
     */
    public static String getDomainName(final URL url)
            throws IOException
    {
        final InetAddress address = InetAddress.getByName(url.getHost());
        final String fqdn = address.getHostName();
        return getDomainName(fqdn);
    }

    public static String getDomainName(final String serverName)
            throws IOException
    {
        final String[] splitName = serverName.split("\\.");
        final String domainName;

        // If the result ends up being something similar to myhost.com, or
        // localhost.
        if (splitName.length < 3)
        {
            domainName = serverName;
        }
        else
        {
            domainName = serverName.substring(serverName.indexOf(".") + 1);
        }

        return domainName;
    }
}
