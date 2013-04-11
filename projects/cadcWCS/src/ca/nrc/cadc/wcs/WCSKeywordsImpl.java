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

package ca.nrc.cadc.wcs;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Wrapper class so that CAOM WCS classes can be easily used with the JSky
 * coordinate transformation library. The time treatment in here is probably
 * wrong as FITS WCS does not actually treat time this way.
 * 
 * @author pdowler
 */
public class WCSKeywordsImpl implements WCSKeywords, Serializable
{
    private static final long serialVersionUID = 200716131000L;
    
    /**
     * Map for keywords.
     */
    protected Map<String,Object> map = new TreeMap<String,Object>();
            
    /**
     * No argument constructor.
     *
     */
    public WCSKeywordsImpl() { }
    
    /**
     * Print the keywords.
     *
     */
    public void printString()
    {
        Iterator i = map.entrySet().iterator();
        while (i.hasNext())
        {
            Map.Entry me = (Map.Entry) i.next();
            System.out.println("[WCSKeywords] " + me.getKey() + " = " + me.getValue());
        }
    }
    
    /**
     * Add a String keyword to keywords.
     * 
     * @param key keyword name.
     * @param value keyword value.
     */
    public void put(String key, String value)
    {
        if (key != null && value != null && value.length() > 0)
            map.put(key, value);
    }
    
    /**
     * Add an int keyword to keywords.
     * 
     * @param key keyword name.
     * @param value keyword value.
     */
    public void put(String key, int value) { map.put(key, new Integer(value)); }
    
    /**
     * Add a double keyword to keywords.
     * 
     * @param key keyword name.
     * @param value keyword value.
     */
    public void put(String key, double value) { map.put(key, new Double(value)); }
    
    /**
     * Add an Integer keyword to keywords.
     * 
     * @param key keyword name.
     * @param value keyword value.
     */
    public void put(String key, Integer value)
    {
        if (key != null && value != null)
            map.put(key, value); 
    }
    
    /**
     * Add a Double keyword to keywords.
     * 
     * @param key keyword name.
     * @param value keyword value.
     */
    public void put(String key, Double value)
    {
        if (key != null && value != null)
            map.put(key, value); 
    }

    /**
     * Test whether a keyword exists in the keywords.
     * 
     * @param key keyword name.
     * @return boolean true if the map contains the key, false otherwise.
     */
    public boolean containsKey(String key) { return map.containsKey(key); }
    
    /**
     * Test whether a keyword exists in the keywords.
     * This is specific to the JSky WVSKeywordProvider API.
     * 
     * @param key keyword name.
     * @return boolean true if the map contains the key, false otherwise.
     */
    public boolean findKey(String key)
    {
        return map.containsKey(key);
    }

    /**
     * Retreive an int valued keyword from the keywords.
     * 
     * @param key keyword name.
     * @return int keyword value.
     */
    public int getIntValue(String key)
    {
        return getIntValue(key, 0);
    }

    /**
     * Retrieve an int valued keyword from the keywords.
     * If the keywords doesn't exists in the keywords,
     * return the default value.
     * 
     * @param key keyword name.
     * @param def default keyword value.
     * @return int keywork value.
     */
    public int getIntValue(String key, int def)
    {
        if (map.containsKey(key))
            return ((Number) map.get(key)).intValue();
        return def;
    }

    /**
     * Retrieve a float valued keyword from the keywords.
     * 
     * @param key keyword name.
     * @return float keyword value.
     */
    public float getFloatValue(String key)
    {
        return getFloatValue(key, 0.0f);
    }

    /**
     * Retrieve an float valued keyword from the keywords.
     * If the keywords doesn't exists in the keywords,
     * return the default value.
     * 
     * @param key keyword name.
     * @param def default keyword value.
     * @return float keywork value.
     */
    public float getFloatValue(String key, float def)
    {
        if (map.containsKey(key))
            return ((Number) map.get(key)).floatValue();
        return def;
    }

    /**
     * Retrieve a double valued keyword from the keywords.
     * 
     * @param key keyword name.
     * @return double keyword value.
     */
    public double getDoubleValue(String key)
    {
        return getDoubleValue(key, 0.0);
    }

    /**
     * Retrieve an double valued keyword from the keywords.
     * If the keywords doesn't exists in the keywords,
     * return the default value.
     * 
     * @param key keyword name.
     * @param def default keyword value.
     * @return double keywork value.
     */
    public double getDoubleValue(String key, double def)
    {
         if (map.containsKey(key))
            return ((Number) map.get(key)).doubleValue();
        return def;
    }

    /**
     * Retrieve a String valued keyword from the keywords.
     * 
     * @param key keyword name.
     * @return String keyword value.
     */
    public String getStringValue(String key)
    {
        return getStringValue(key, null);
    }

    /**
     * Retrieve a String valued keyword from the keywords.
     * If the keywords doesn't exists in the keywords,
     * return the default value.
     * 
     * @param key keyword name.
     * @param def default keyword value.
     * @return String keywork value.
     */
    public String getStringValue(String key, String def)
    {
        if (map.containsKey(key))
            return map.get(key).toString();
        return def;
    }
        
    /**
     * Get a Map.Entry Iterator to the keywords.
     * 
     * @return Iterator to the keywords.
     */
    public Iterator<Map.Entry<String,Object>> iterator()
    {
        return map.entrySet().iterator();
    }
    
    /**
     * Print String representation of the keywords.
     * 
     * @return String representation of the keywords.
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        Iterator<Map.Entry<String,Object>> iter = map.entrySet().iterator();
        while ( iter.hasNext() )
        {
            Map.Entry<String,Object> me = iter.next();
            sb.append(me.getKey()).append(" = ").append(me.getValue()).append("\n");
        }
        return sb.toString();
    }
}
