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

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * A VOSpace property representing metadata for a node.
 * 
 * @author majorb
 *
 */
public class NodeProperty
{
    
    // Maximum number of groups allowed
    static final int MAX_GROUPS = 4;
    
    // The property identifier
    private String propertyURI;
    
    // The value of the property
    private String propertyValue;
    
    // true if the property cannot be modified.
    private boolean readOnly;
    
    // true if this property is marked for deletion
    private boolean markedForDeletion;

    /**
     * Property constructor.
     * 
     * @param uri The property identifier.
     * @param value The property value.
     */
    public NodeProperty(String uri, String value)
    {
        this.propertyURI = uri;
        this.propertyValue = value;
        this.markedForDeletion = false;
        validateProperty();
    }
    
    /**
     * Property constructor.
     * 
     * @param uri The property identifier.
     * @param values The list of values for the property.
     */
    public NodeProperty(String uri, List<String> values)
    {
        this(uri, serializePropertyValueList(uri, values));
    }
    
    /**
     * Return true iff the property URIs are equal.
     */
    public boolean equals(Object o)
    {
        if (o instanceof NodeProperty)
        {
            final NodeProperty np = (NodeProperty) o;

            if (getPropertyURI() != null)
            {
                return getPropertyURI().equals(np.getPropertyURI());
            }
        }
        return false;
    }
    
    public String toString()
    {
        return propertyURI + ": " + propertyValue;
    }

    /**
     * @return The property identifier.
     */
    public String getPropertyURI()
    {
        return propertyURI;
    }

    /**
     * @return The property value.
     */
    public String getPropertyValue()
    {
        return propertyValue;
    }

    public void setValue(String value)
    {
        this.propertyValue = value;
    }

    /**
     * @return True if the property cannot be modified.
     */
    public boolean isReadOnly()
    {
        return readOnly;
    }

    /**
     * @param readOnly
     */
    public void setReadOnly(boolean readOnly)
    {
        this.readOnly = readOnly;
    }

    public boolean isMarkedForDeletion()
    {
        return markedForDeletion;
    }

    public void setMarkedForDeletion(boolean markedForDeletion)
    {
        this.markedForDeletion = markedForDeletion;
    }
    
    /**
     * Perform validation on the property.  
     * 
     * @throws IllegalArgumentException If validation fails.
     */
    private void validateProperty() throws IllegalArgumentException
    {
        // group read
        if (VOS.PROPERTY_URI_GROUPREAD.equalsIgnoreCase(propertyURI))
        {
            List<String> values = extractPropertyValueList();
            if (values != null && values.size() > MAX_GROUPS)
            {
                throw new IllegalArgumentException(
                    "No more than " + MAX_GROUPS
                    + " groups allowed for property " + propertyURI);
            }
        }
        
        // group write
        if (VOS.PROPERTY_URI_GROUPWRITE.equalsIgnoreCase(propertyURI))
        {
            List<String> values = extractPropertyValueList();
            if (values != null && values.size() > MAX_GROUPS)
            {
                throw new IllegalArgumentException(
                    "No more than " + MAX_GROUPS
                    + " groups allowed for property " + propertyURI);
            }
        }
        
        // is public
        if (VOS.PROPERTY_URI_ISPUBLIC.equalsIgnoreCase(propertyURI))
        {
            List<String> values = extractPropertyValueList();
            if (values != null && values.size() > 1)
            {
                throw new IllegalArgumentException(
                    "Only one values allowed for property " + propertyURI);
            }
        }
        
    }
    
    /**
     * Given multiple values for a certain property specified by uri, return
     * the string representing the list of values with the correct delimiter(s)
     * in place.
     * 
     * @param uri
     * @param values
     * @return
     */
    public static String serializePropertyValueList(String uri, List<String> values)
    {
        if (uri == null || values == null)
            return null;
        
        String delim = getPropertyValueDelimiter(uri);
        StringBuilder sb = new StringBuilder();
        for (int index=0; index<values.size(); index++)
        {
            sb.append(values.get(index));
            if ((index + 1) < values.size())
                sb.append(delim);
        }
        return sb.toString();
    }
    
    /**
     * Given a string representing multiple values of a property specified by uri,
     * return the values as an array.
     * 
     * @param uri
     * @param values
     * @return
     */
    public List<String> extractPropertyValueList()
    {
        if (propertyURI == null || propertyValue == null)
            return null;
        
        String delim = getPropertyValueDelimiter(propertyURI);
        StringTokenizer st = new StringTokenizer(propertyValue, delim);
        List<String> ret = new ArrayList<String>(st.countTokens());
        while (st.hasMoreElements())
        {
            ret.add(st.nextToken());
        }
        return ret;
    }
    
    /**
     * Given a property URI, return the delimiter used for separating multiple
     * values.
     * 
     * @param uri
     * @return
     */
    private static String getPropertyValueDelimiter(String uri)
    {
        // For now, new delimiters for properties must be added manually to this method.
        if (VOS.PROPERTY_URI_GROUPREAD.equalsIgnoreCase(uri))
            return VOS.PROPERTY_DELIM_GROUPREAD;
        
        if (VOS.PROPERTY_URI_GROUPWRITE.equalsIgnoreCase(uri))
            return VOS.PROPERTY_DELIM_GROUPWRITE;
        
        return VOS.DEFAULT_PROPERTY_VALUE_DELIM;
    }
    
}
