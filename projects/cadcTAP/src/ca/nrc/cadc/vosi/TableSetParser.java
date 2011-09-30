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

package ca.nrc.cadc.vosi;

import ca.nrc.cadc.xml.XmlUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

/**
 * Simple class to parse a VOSI-tables document. When schema validation is enabled
 * (it is the default), this class finds all the necessary schema files in the
 * classpath (in cadcVOSI) and maps the current namespace URIs to those schema
 * locations as required by the XmlUtil class (cadcUtil).
 * 
 * @author pdowler
 */
public class TableSetParser 
{
    private static final Logger log = Logger.getLogger(TableSetParser.class);

    private Map<String,String> schemaMap;

    public TableSetParser()
    {
        this(true);
    }

    public TableSetParser(boolean enableSchemaValidation)
    {
        if (enableSchemaValidation)
        {
            this.schemaMap = new HashMap<String,String>();
            String url;

            url = XmlUtil.getResourceUrlString(VOSI.TABLES_SCHEMA, TableSetParser.class);
            if (url != null)
            {
                log.debug(VOSI.TABLES_NS_URI + " -> " + url);
                schemaMap.put(VOSI.TABLES_NS_URI, url);
            }
            else
                log.warn("failed to find resource: " + VOSI.TABLES_SCHEMA);

            url = XmlUtil.getResourceUrlString(VOSI.VORESOURCE_SCHEMA, TableSetParser.class);
            if (url != null)
            {
                log.debug(VOSI.VORESOURCE_NS_URI + " -> " + url);
                schemaMap.put(VOSI.VORESOURCE_NS_URI, url);
            }
            else
                log.warn("failed to find resource: " + VOSI.VORESOURCE_SCHEMA);

            url = XmlUtil.getResourceUrlString(VOSI.VODATASERVICE_SCHEMA, TableSetParser.class);
            if (url != null)
            {
                log.debug(VOSI.VODATASERVICE_NS_URI + " -> " + url);
                schemaMap.put(VOSI.VODATASERVICE_NS_URI, url);
            }
            else
                log.warn("failed to find resource: " + VOSI.VODATASERVICE_SCHEMA);

            url = XmlUtil.getResourceUrlString(VOSI.XSI_SCHEMA, TableSetParser.class);
            if (url != null)
            {
                log.debug(VOSI.XSI_NS_URI + " -> " + url);
                schemaMap.put(VOSI.XSI_NS_URI, url);
            }
            else
                log.warn("failed to find resource: " + VOSI.XSI_SCHEMA);
        }
    }

    public Document parse(Reader rdr)
        throws IOException, JDOMException
    {
        // and again with schema validation
        SAXBuilder sb = XmlUtil.createBuilder(schemaMap);
        return sb.build(rdr);
    }

    public Document parse(InputStream istream)
        throws IOException, JDOMException
    {
        // and again with schema validation
        SAXBuilder sb = XmlUtil.createBuilder(schemaMap);
        return sb.build(istream);
    }
}
