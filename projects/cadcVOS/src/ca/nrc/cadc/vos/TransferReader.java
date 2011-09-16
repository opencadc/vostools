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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import ca.nrc.cadc.vos.View.Parameter;
import ca.nrc.cadc.xml.XmlUtil;

/**
 * Constructs a Transfer from an XML source. This class is not thread safe but it is
 * re-usable  so it can safely be used to sequentially parse multiple XML transfer
 * documents.
 *
 * @author pdowler
 */
public class TransferReader
{
    private static final String VOSPACE_SCHEMA_URL = "http://www.ivoa.net/xml/VOSpace/v2.0";
    private static final String VOSPACE_SCHEMA_RESOURCE = "VOSpace-2.0.xsd";

    private static Logger log = Logger.getLogger(TransferReader.class);

    protected Map<String, String> schemaMap;

    /**
     * Constructor. XML Schema validation is enabled by default.
     */
    public TransferReader() { this(true); }

    /**
     * Constructor. XML schema validation may be disabled, in which case the client
     * is likely to fail in horrible ways (e.g. NullPointerException) if it receives
     * invalid documents. However, performance may be improved.
     *
     * @param enableSchemaValidation
     */
    public TransferReader(boolean enableSchemaValidation)
    {
        if (enableSchemaValidation)
        {
            String vospaceSchemaUrl = XmlUtil.getResourceUrlString(VOSPACE_SCHEMA_RESOURCE, TransferReader.class);
            if (vospaceSchemaUrl == null)
                throw new RuntimeException("failed to find " + VOSPACE_SCHEMA_RESOURCE + " in classpath");
            this.schemaMap = new HashMap<String, String>();
            schemaMap.put(VOSPACE_SCHEMA_URL, vospaceSchemaUrl);
            log.debug("schema validation enabled");
        }
        else
            log.debug("schema validation disabled");
    }

    public Transfer read(Reader reader)
        throws IOException, TransferParsingException
    {
        try
        {
            // TODO: investigate creating a SAXBuilder once and re-using it
            // as long as we can detect concurrent access (a la java collections)
            Document doc = XmlUtil.validateXml(reader, schemaMap);
            return parseTransfer(doc);
        }
        catch(JDOMException ex)
        {
            throw new TransferParsingException("failed to parse XML", ex);
        }
        catch(URISyntaxException ex)
        {
            throw new TransferParsingException("invalid URI in transfer document", ex);
        }
    }

    public Transfer read(InputStream in)
        throws IOException, TransferParsingException
    {
        InputStreamReader reader = new InputStreamReader(in);
        return read(reader);
    }

    public Transfer read(String string)
        throws IOException, TransferParsingException
    {
        StringReader reader = new StringReader(string);
        return read(reader);
    }

    private Transfer parseTransfer(Document document)
        throws URISyntaxException
    {
        Element root = document.getRootElement();

        Direction direction = parseDirection(root);
        // String serviceUrl; // not in XML yet
        Node target = new DataNode(new VOSURI(root.getChildText("target", VOS.NS)));

        // TODO: get view nodes and uri attribute
        View view = null;
        Parameter param = null;
        List views = root.getChildren("view", VOS.NS);
        if (views != null && views.size() > 0)
        {
            Element v = (Element) views.get(0);
            view = new View(new URI(v.getAttributeValue("uri")));
            List params = v.getChildren("param", VOS.NS);
            if (params != null)
            {
                for (Object o : params)
                {
                    Element p = (Element) o;
                    param = new Parameter(new URI(p.getAttributeValue("uri")), p.getText());
                    view.getParameters().add(param);
                }
            }
        }
        List<Protocol> protocols = parseProtocols(root);
        String keepBytesStr = root.getChildText("direction", VOS.NS);
        boolean keepBytes = keepBytesStr.equalsIgnoreCase("true");

        Transfer rtn = new Transfer();
        rtn.setDirection(direction);
        rtn.setProtocols(protocols);
        rtn.setTarget(target);
        rtn.setView(view);
        rtn.setKeepBytes(keepBytes);
        return rtn;
    }

    private Direction parseDirection(Element root)
    {
        Direction rtn = null;
        String strDirection = root.getChildText("direction", VOS.NS);
        
        if (strDirection == null)
            throw new RuntimeException("Did not find direction element in XML.");
        
        if (strDirection.equalsIgnoreCase(Direction.pullFromVoSpace.getValue()))
            rtn = Direction.pullFromVoSpace;
        else if (strDirection.equalsIgnoreCase(Direction.pullToVoSpace.getValue()))
            rtn = Direction.pullToVoSpace;
        else if (strDirection.equalsIgnoreCase(Direction.pushFromVoSpace.getValue()))
            rtn = Direction.pushFromVoSpace;
        else if (strDirection.equalsIgnoreCase(Direction.pushToVoSpace.getValue()))
            rtn = Direction.pushToVoSpace;
        else
            rtn = new Direction(strDirection);
        return rtn;
    }

    private List<Protocol> parseProtocols(Element root)
    {
        List<Protocol> rtn = new ArrayList<Protocol>();
        //Element e = root.getChild("protocols", VOS.NS);
        List prots = root.getChildren("protocol", VOS.NS);
        if (prots != null)
        {
            for (Object obj : prots)
            {
                Element eProtocol = (Element) obj;
                String uri = eProtocol.getAttributeValue("uri");
                String endpoint = eProtocol.getChildText("endpoint", VOS.NS);
                rtn.add(new Protocol(uri, endpoint, null));
            }
        }
        return rtn;
    }
}
