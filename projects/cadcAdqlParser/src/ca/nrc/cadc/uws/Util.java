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


/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.nrc.cadc.uws;

import ca.nrc.cadc.util.ConversionUtil;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;
import org.apache.log4j.Logger;

/**
 *
 * @author pdowler
 */
public class Util 
{
    private static Logger log = Logger.getLogger(Util.class);
    
    private static final int MAGIC_STRING = 1;
    private static final int MAGIC_URI    = 2;
    private static final int MAGIC_DATE   = 3;
    private static final int MAGIC_NUMBER = 4;
    
    public static void pingListeners(List listeners)
    {
        if (listeners != null)
        {
            log.debug("pingListeners: " + listeners.size());
            Iterator iter = listeners.iterator();
            while (iter.hasNext())
            {
                String s = (String) iter.next();
                log.debug("pingListeners: " + s);
                try
                {
                    URL url = new URL(s);
                    HttpURLConnection uc = (HttpURLConnection) url.openConnection();
                    uc.setRequestMethod("POST");
                    uc.getResponseCode();
                }
                catch(Throwable oops) 
                { 
                    log.debug("pingListeners failed: " + oops);
                }
            }
        }
    }
    
    public static String encode(Object obj)
    {
        String ret = null;
        if (obj == null)
            return null;
        log.debug("encoding a " + obj.getClass().getName());
        try
        {
            if (obj instanceof String)
            {
                log.debug("using URLEncoder to encode String with magic=" + MAGIC_STRING);
                ret = URLEncoder.encode(Integer.toString(MAGIC_STRING) + obj, "UTF-8");
            }
            if (obj instanceof URI)
            {
                log.debug("using vanilla URI with magic=" + MAGIC_URI);
                ret = Integer.toString(MAGIC_URI) + obj.toString();
            }
            if (ret == null)
                throw new UnsupportedOperationException("encode: " + obj.getClass().getName());
        }
        catch(UnsupportedEncodingException never) { }
        return ret;
    }
    
    public static Object decode(String encoded)
    {
        Object ret = null;
        try
        {
            if (encoded == null)
                return null;
            int magic = Integer.parseInt(encoded.substring(0,1));
            String eval = encoded.substring(1);
            
            switch(magic)
            {
                case MAGIC_STRING:
                    ret = URLDecoder.decode(eval, "UTF-8");
                    break;
                case MAGIC_URI:
                    ret = new URI(eval);
                    break;
                default:
                    throw new UnsupportedOperationException("decode: magic=" + magic);
            }
            
        }
        catch(UnsupportedEncodingException never) { }
        catch(URISyntaxException bug) 
        { 
            throw new IllegalArgumentException("failed to decode an encoded URI", bug);
        }
        return ret;
    }
    
    
    
    
/**
     * Encode a String into a byte array. If the specified string is null or empty, only
     * the length (0) is returned via an array of length 4. Otherwise, the returned array length
     * is 4 bytes plus 2 bytes per character in the string.
     * 
     * @param s string to encode
     * @return encoded string
     */
    public static byte[] encodeString(String s)
    {
        if (s == null || s.length() == 0)
            return ConversionUtil.intToHex(0);
        
        byte[] ret = new byte[8 + 2*s.length()];
        byte[] b = ConversionUtil.intToHex(MAGIC_STRING);
        System.arraycopy(b, 0, ret, 0, 4);
        b = ConversionUtil.intToHex(s.length());
        System.arraycopy(b, 0, ret, 4, 4);
        for (int i=0; i<s.length(); i++)
        {
            b = ConversionUtil.shortToHex((short) s.charAt(i));
            System.arraycopy(b, 0, ret, 8 + i*2, 2);
        }
        return ret;
    }
    
    /**
     * Decode an encoded string. If the previously encoded string was null or empty, 
     * this method returns null.
     * 
     * @param encoded an array with bytes encoded by encode(String)
     * @param offset offset in the encoded array where encoded bytes start
     * @return the original string
     */
    public static String decodeString(byte[] encoded, int offset)
    {
        int magic = ConversionUtil.hexToInt(encoded, offset);
        if (magic != MAGIC_STRING)
            throw new IllegalArgumentException("wrong magic number - not an encoded string");
        int length = ConversionUtil.hexToInt(encoded, 4 + offset);
        if (length == 0)
            return null; 
        
        StringBuffer sb = new StringBuffer();
        for (int i=0; i<length; i++)
        {
            short s = ConversionUtil.hexToShort(encoded, 8 + offset + i*2);
            char c = (char) s;
            sb.append(c);
        }
        return sb.toString();
    }
    
    /**
     * Decode an encoded byte array into a String with offset 0.
     * 
     * @param encoded string
     * @return the original string
     */
    public static String decodeString(byte[] encoded)
    {
        return decodeString(encoded, 0);
    }
    
}
