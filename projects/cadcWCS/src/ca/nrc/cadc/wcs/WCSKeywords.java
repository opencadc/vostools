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

import java.util.Iterator;
import java.util.Map;

/**
 * Provides an interface to access WCS keywords.
 * Inlcudes a method to check if a keyword exists in a set of keywords,
 * and methods to retrieve the value of a specified keyword. The keyword
 * value can be returned as a double, float, int or String. Optionally
 * a default value can be specified and returned if the keyword does
 * not exist in the set of keywords.
 * 
 * @author jburke
 *
 */
public interface WCSKeywords
{
    /**
     * Returns true if the specified key exists in the keywords.
     * 
     * @param key key whose presence in keywords is to be tested.
     * @return True if keywords contains key, False otherwise.
     */
    boolean containsKey(String key);
    
    /**
     * Returns a double value for the specified key. It is expected that
     * a default value, typically 0.0, will be returned if the key
     * does not exist in the keywords.
     * 
     * @param key key whose double value is to be returned.
     * @return double value corresponding to the specified key.
     */
    double getDoubleValue(String key);
    
    /**
     * Returns a double value for the specified key. If the key does not
     * exist in the keywords, the default value is returned.
     * 
     * @param key key whose double value is to be returned.
     * @param value default value returned if the specified key does not exist in keywords.
     * @return double value corresponding to the specified key.
     */
    double getDoubleValue(String key, double value);
    
    /**
     * Returns a float value for the specified key. It is expected that
     * a default value, typically 0.0, will be returned if the key
     * does not exist in the keywords.
     * 
     * @param key key whose float value is to be returned.
     * @return float value corresponding to the specified key.
     */
    float getFloatValue(String key);
    
    /**
     * Returns a float value for the specified key. If the key does not
     * exist in the keywords, the default value is returned.
     * 
     * @param key key whose float value is to be returned.
     * @param value default value returned if the specified key does not exist in keywords.
     * @return float value corresponding to the specified key.
     */
    float getFloatValue(String key, float value);
    
    /**
     * Returns an int value for the specified key. It is expected that
     * a default value, typically 0, will be returned if the key
     * does not exist in the keywords.
     * 
     * @param key key whose int value is to be returned.
     * @return int value corresponding to the specified key.
     */
    int getIntValue(String key);
    
    /**
     * Returns an int value for the specified key. If the key does not
     * exist in the keywords, the default value is returned.
     * 
     * @param key key whose int value is to be returned.
     * @param value default value returned if the specified key does not exist in keywords.
     * @return int value corresponding to the specified key.
     */
    int getIntValue(String key, int value);
    
    /**
     * Returns a String value for the specified key. It is expected that
     * a default value, typically an empty String, will be returned if 
     * the key does not exist in the keywords.
     * 
     * @param key key whose String value is to be returned.
     * @return String value corresponding to the specified key.
     */
    String getStringValue(String key);
    
    /**
     * Returns a String value for the specified key. If the key does not
     * exist in the keywords, the default value is returned.
     * 
     * @param key key whose String value is to be returned.
     * @param value default value returned if the specified key does not exist in keywords.
     * @return String value corresponding to the specified key.
     */
    String getStringValue(String key, String value);
    
    /**
     * Add the key and String value to the keywords.
     * 
     * @param key keywords name.
     * @param value keywords value.
     */
    void put(String key, String value);
    
    /**
     * Add the key and int value to the keywords.
     * 
     * @param key key keywords name.
     * @param value value keywords value.
     */
    void put(String key, int value);
    
    /**
     * Add the key and double value to the keywords.
     * 
     * @param key key keywords name.
     * @param value value keywords value.
     */
    void put(String key, double value);
    
    /**
     * Add the key and Integer value to the keywords.
     * 
     * @param key key keywords name.
     * @param value value keywords value.
     */
    void put(String key, Integer value);
    
    /**
     * Add the key and Double value to the keywords.
     * 
     * @param key key keywords name.
     * @param value value keywords value.
     */
    void put(String key, Double value);
    
    /**
     * Returns an iterator over the keyword key value pairs.
     * It is expected the iterator will not remove keywords.
     * 
     * @return iterator over the keywords.
     */
    Iterator<Map.Entry<String,Object>> iterator();
}
