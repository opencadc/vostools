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
 *  $Revision: $
 *
 ************************************************************************
 */

package ca.nrc.cadc.auth;

import java.io.Serializable;
import java.security.Principal;

/**
 * This class encapsulates an Http Principal
 */
public class HttpPrincipal implements Principal, Serializable
{
    private static final long serialVersionUID = 20090816143750l;

    private String remoteUser;

    
    /**
     * Ctor
     * @param remoteUser Http remote user. Cannot be null.
     */
    public HttpPrincipal(String remoteUser)
    {
        if (remoteUser == null)
        {
            throw new IllegalArgumentException("Provided null remoteUser");
        }
        this.remoteUser = remoteUser;
    }

    
    /* (non-Javadoc)
     * @see java.security.Principal#getName()
     */
    public String getName()
    {
        return remoteUser;
    }
    

    /**
     * Returns a hashcode for this principal. The hash code is defined to
     * be the result of the following calculation:
     * 
     * <pre><code>
     * hashCode = (this.getClass().getName() + getName()).hashCode();
     * </code></pre>
     * 
     * @return a hashCode() for the <code>HttpPrincipal</code>
     */
    public int hashCode()
    {
        return (this.getClass().getName() + getName()).hashCode();
    }

    /**
     * Compares the specified Object with this Principal for equality.
     * Returns true if the given object is also a
     * <code>HttpPrincipal</code> and the two <code>HttpPrincipal</code>
     * instances are equivalent. More formally two
     * <code>HttpPrincipal</code> instances are equal if the values
     * returned by <code>getName()</code> are equal.
     * 
     * @param other
     *            the Object to compare to
     * @return true if the Object passed in represents the same principal
     *         as this one, false otherwise.
     */
    public boolean equals(Object other)
    {

        if (other == this)
            return true;

        if (!(other instanceof HttpPrincipal))
        {
            return false;
        }
        return this.hashCode() == other.hashCode();
    }


    public String toString()
    {
        return getName();
    }

}
