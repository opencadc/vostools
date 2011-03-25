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

package ca.nrc.cadc.util;

import java.lang.reflect.Array;

/**
 * Simple utility to help in overriding Object.hashCode method.
 */
public class HashUtil
{

    /**
    * An initial value for a <code>hashCode</code>, to which is added contributions
    * from fields. Using a non-zero value decreases collisions of <code>hashCode</code>
    * values.
    */
    public static final int SEED = 23;

    /**
    * booleans.
    */
    public static int hash( int aSeed, boolean aBoolean )
    {
        return firstTerm( aSeed ) + ( aBoolean ? 1 : 0 );
    }

    /**
    * chars.
    */
    public static int hash( int aSeed, char aChar )
    {
        return firstTerm( aSeed ) + (int)aChar;
    }

    /**
    * ints.
    */
    public static int hash( int aSeed , int aInt )
    {
        return firstTerm( aSeed ) + aInt;
    }

    /**
    * longs.
    */
    public static int hash( int aSeed , long aLong )
    {
        return firstTerm(aSeed)  + (int)( aLong ^ (aLong >>> 32) );
    }

    /**
    * floats.
    */
    public static int hash( int aSeed , float aFloat )
    {
        return hash( aSeed, Float.floatToIntBits(aFloat) );
    }

    /**
    * doubles.
    */
    public static int hash( int aSeed , double aDouble )
    {
        return hash( aSeed, Double.doubleToLongBits(aDouble) );
    }

    /**
    * The <code>obj</code> is a possibly-null object field, and possibly an array.
    *
    * If <code>obj</code> is an array, then each element may be a primitive
    * or a possibly-null object.
    */
    public static int hash(int seed , Object obj)
    {
        int result = seed;
        if ( obj == null)
            result = hash(result, 0);
        else if ( ! isArray(obj) )
            result = hash(result, obj.hashCode());
        else
        {
            int length = Array.getLength(obj);
            for ( int idx = 0; idx < length; ++idx )
            {
                Object item = Array.get(obj, idx);
                //recursive call!
                result = hash(result, item);
            }
        }
        return result;
    }


    /// PRIVATE ///
    private static final int fODD_PRIME_NUMBER = 37;

    private static int firstTerm( int aSeed )
    {
        return fODD_PRIME_NUMBER * aSeed;
    }

    private static boolean isArray(Object aObject)
    {
        return aObject.getClass().isArray();
    }
}

