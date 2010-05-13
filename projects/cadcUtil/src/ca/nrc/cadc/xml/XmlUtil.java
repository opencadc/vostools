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

package ca.nrc.cadc.xml;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;

/**
 * @author zhangsa
 *
 */
public class XmlUtil
{
    private static Logger log = Logger.getLogger(XmlUtil.class);
    public static final String PARSER = "org.apache.xerces.parsers.SAXParser";

    public static Document validateXml(String xml, Map<String, String> schemaMap) throws IOException, JDOMException
    {
        log.debug("validateXml:\n" + xml);
        
        URL url;
        String schemaResource, serviceSchema;
        String space = " ";
        StringBuffer sbSchemaLocations = new StringBuffer();
        log.debug("schemaMap.size(): " + schemaMap.size());
        
        for (String schemaNSKey : schemaMap.keySet())
        {
            schemaResource = (String) schemaMap.get(schemaNSKey);
            url = XmlUtil.class.getClassLoader().getResource(schemaResource);
            if (url == null)
                throw new RuntimeException("failed to find resource: " + schemaResource);
            serviceSchema = url.toString();
            log.debug(schemaResource + " -> " + serviceSchema);
            sbSchemaLocations.append(schemaNSKey).append(space).append(serviceSchema).append(space);
        }
    
        SAXBuilder schemaValidator;
        schemaValidator = new SAXBuilder(PARSER, true);
        schemaValidator.setFeature("http://xml.org/sax/features/validation", true);
        schemaValidator.setFeature("http://apache.org/xml/features/validation/schema", true);
        schemaValidator.setFeature("http://apache.org/xml/features/validation/schema-full-checking", true);
        schemaValidator.setProperty("http://apache.org/xml/properties/schema/external-schemaLocation", sbSchemaLocations.toString());
    
        return schemaValidator.build(new StringReader(xml));
    }

    public static Document validateXml(String xml, String schemaNSKey, String schemaResourceFileName) throws IOException, JDOMException
    {
        Map<String, String> map = new HashMap<String, String>();
        map.put(schemaNSKey, schemaResourceFileName);
        return validateXml(xml, map);
    }

    /**
     * count how many nodes are represented by the xpath
     * 
     * @param doc
     * @param xpathStr
     * @return
     */
    public static int getXmlNodeCount(Document doc, String xpathStr)
    {
        int rtn = 0;
        XPath xpath;
        try
        {
            xpath = XPath.newInstance(xpathStr);
            List<?> rs = xpath.selectNodes(doc);
            rtn = rs.size();
        } catch (JDOMException e)
        {
            e.printStackTrace();
        }
        return rtn;
    }
    
    /**
     * Get an URL to the schema in the jar.
     * @return
     */
    public static String getResourceUrlString(String resourceFileName, Class runningClass)
    {
        String rtn = null;
        URL url = runningClass.getClassLoader().getResource(resourceFileName);
        if (url == null)
            throw new MissingResourceException("Resource not found: " + resourceFileName, runningClass.getName(), resourceFileName);
        rtn = url.toString();
        return rtn;
    }
}
