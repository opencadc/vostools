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

package ca.nrc.cadc.vos;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Wrapper for a VOSpace URI around an URI.
 *
 * @author jburke
 */
public class VOSURI
{
    private URI vosURI;

    /**
     * Attempts to create a URI using the specified uri. The scheme for the uri
     * is expected to be vos, else a URISyntaxException will be thrown.
     *
     * @param uri The URI to use.
     * @throws IllegalArgumentException if the URI scheme is not vos
     * @throws NullPointerException if uri is null
     */
    public VOSURI(URI uri)
    {
        String path = uri.getPath();
        if (path != null && path.endsWith("/"))
        {
            path = path.substring(0, path.length() - 1);
        }
        
        try
        {
            vosURI = new URI(uri.getScheme(), uri.getAuthority(),
                    path, uri.getQuery(), uri.getFragment());
        }
        catch (URISyntaxException e)
        {
            throw new IllegalArgumentException("URI malformed: " + uri.toString());
        }

        // Check the scheme is vos
        if (vosURI.getScheme() == null || !vosURI.getScheme().equalsIgnoreCase("vos")) 
            throw new IllegalArgumentException("URI scheme must be vos: " + uri.toString());
    }

    /**
     * Attempts to create a URI using the specified uri string. The scheme for the uri
     * is expected to be vos, else a URISyntaxException will be thrown.
     *
     * @param uri String representation of a URI to decode.
     * @throws URISyntaxException if uri violates RFC 2396 
     * @throws IllegalArgumentException if the URI scheme is not vos
     * @throws NullPointerException if uri is null
     */
    public VOSURI(String uri)
        throws URISyntaxException
    {
        this(new URI(uri));
    }

    @Override
    public boolean equals(Object rhs)
    {
        if (rhs == null)
            return false;
        if (this == rhs)
            return true;
        if (rhs instanceof VOSURI)
        {
            VOSURI vu = (VOSURI) rhs;
            String a1 = this.getAuthority();
            String a2 = vu.getAuthority();
            if ( a1.equals(a2) ) // using same separator
                return vosURI.equals(vu.vosURI);

            a1 = a1.replace('~', '!');
            a2 = a2.replace('~', '!');
            if ( a1.equals(a2) )
            {
                // only separator diff in prefix, strip it off
                int n = a1.length() + 6; // vos://+<authority>
                String s1 = vosURI.toString().substring(n);
                String s2 = vu.toString().substring(n);
                return s1.equals(s2);
            }
        }
        return false;
    }

    /**
     * Returns the underlying URI object.
     * 
     * @return The URI object for this VOSURI.
     */
    public URI getURIObject()
    {
        return vosURI;
    }

    /**
     * Returns the decoded authority component of the URI.
     *
     * @return authority of the URI, or null if the authority is undefined.
     */
    public String getAuthority()
    {
        return vosURI.getAuthority();
    }

    /**
     * Returns the decoded fragment component of the URI.
     *
     * @return fragment of the URI, or null if the fragment is undefined.
     */
    public String getFragment()
    {
        return vosURI.getFragment();
    }

    /**
     * Returns the decoded path component of the URI.
     *
     * @return path of the URI, or null if the path is undefined.
     */
    public String getPath()
    {
        return vosURI.getPath();
    }

    /**
     * Returns the scheme, followed by a '://', and then the authority of the URI.
     *
     * @return [scheme]://[authority] of the URI.
     */
    public String getPrefix()
    {
        return vosURI.getScheme() + "://" + vosURI.getAuthority();
    }

    /**
     * Returns the decoded query component of the URI.
     *
     * @return query of the URI, or null if the query is undefined.
     */
    public String getQuery()
    {
        return vosURI.getQuery();
    }

    /**
     * Returns the scheme component of the URI, which must be vos.
     *
     * @return scheme of the URI.
     */
    public String getScheme()
    {
        return vosURI.getScheme();
    }

    /**
     * Attempts to parse out the path of the parent Node described
     * by this URI.
     *
     * @return path of the parent, or null if the URI path is null
     *         of if the Node has no parent.
     */
    public String getParent()
    {
        if ( isRoot() )
            return null; // there is no parent of the root
        String path = vosURI.getPath();
        int index = path.lastIndexOf('/');
        return path.substring(0, index);
    }

    public VOSURI getParentURI()
    {
        if ( isRoot() )
            return null;
        
        try
        {
            String path = getParent();
            URI uri = new URI("vos", getAuthority(), path, null, null);
            return new VOSURI(uri);
        }
        catch(URISyntaxException bug)
        {
            throw new RuntimeException("BUG: failed to get parent uri from " + vosURI.toASCIIString(), bug);
        }
    }

    @Override
    public String toString()
    {
        return vosURI.toString();
    }

    public String getName()
    {
        if ( isRoot() )
            return "";

        String path = vosURI.getPath();
        String[] comps = vosURI.getPath().split("/");
        return comps[comps.length - 1]; // last path component
    }

    public boolean isRoot()
    {
        String path = vosURI.getPath();
        if (path == null || path.length() == 0 || path.equals("/"))
            return true;
        return false;
    }

    /**
     * return the service URI which is with ivo:// scheme.
     * e.g.
     * for VOSURI("vos://cadc.nrc.ca!vospace/zhangsa/nodeWithPropertiesA"),
     * it returns:
     * URI("ivo://cadc.nrc.ca/vospace")
     * 
     * @throws URISyntaxException
     * @author Sailor Zhang, 2010-07-15
     */
    public URI getServiceURI()
    {
        String authority = getAuthority();
        authority = authority.replace('!', '/');
        authority = authority.replace('~', '/');
        String str = "ivo://" + authority;
        try
        {
            return new URI(str);
        }
        catch(URISyntaxException bug)
        {
            throw new RuntimeException("BUG: failed to create service URI from VOSURI: " + vosURI);
        }
    }
}
