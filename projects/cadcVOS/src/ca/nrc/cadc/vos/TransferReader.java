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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

/**
 * @author zhangsa
 *
 */
public class TransferReader
{
    @SuppressWarnings("unused")
    private static Logger log = Logger.getLogger(TransferReader.class);

    private Document document;
    private SAXBuilder saxBuilder;

    public TransferReader()
    {
        this.saxBuilder = new SAXBuilder("org.apache.xerces.parsers.SAXParser", false);
        this.saxBuilder.setFeature("http://xml.org/sax/features/validation", true);
        this.saxBuilder.setFeature("http://apache.org/xml/features/validation/schema", true);
        this.saxBuilder.setFeature("http://apache.org/xml/features/validation/schema-full-checking", true);
        this.saxBuilder.setProperty("http://apache.org/xml/properties/schema/external-schemaLocation", VOS.EXT_SCHEMA_LOCATION);
    }

    public Transfer readFrom(Reader reader) throws JDOMException, IOException {
        this.document = this.saxBuilder.build(reader);
        return parseTransfer();
    }

    public Transfer readFrom(File file) throws JDOMException, IOException {
        this.document = this.saxBuilder.build(file);
        return parseTransfer();
    }

    public Transfer readFrom(InputStream in) throws JDOMException, IOException {
        this.document = this.saxBuilder.build(in);
        return parseTransfer();
    }

    public Transfer readFrom(URL url) throws JDOMException, IOException {
        this.document = this.saxBuilder.build(url);
        return parseTransfer();
    }

    public Transfer readFrom(String string) throws JDOMException, IOException {
        StringReader reader = new StringReader(string);
        this.document = this.saxBuilder.build(reader);
        return parseTransfer();
    }

    private Transfer parseTransfer() {
        Element root = this.document.getRootElement();

        Transfer.Direction direction = parseDirection();
        // String serviceUrl; // not in XML yet
        Node target = null;
        try
        {
            target = new DataNode(new VOSURI(root.getChildText("target", VOS.NS))); //TODO confirmation needed
        } catch (URISyntaxException e)
        { 
            log.debug(e.getMessage());
        }
        View view = new DataView(root.getChildText("view", VOS.NS), null); //TODO confirmation needed    
        List<Protocol> protocols = parseProtocols();
        // boolean keepBytes; // not in XML yet
        // String endpoint; // not in XML yet

        Transfer rtn = new Transfer();
        rtn.setDirection(direction);
        rtn.setProtocols(protocols);
        rtn.setTarget(target);
        rtn.setView(view);
        return rtn;
    }

    private Transfer.Direction parseDirection() {
        Transfer.Direction rtn = null;
        Element root = this.document.getRootElement();
        String strDirection = root.getChildText("direction", VOS.NS);
        if (strDirection.equalsIgnoreCase(Transfer.Direction.pullFromVoSpace.toString()))
            rtn = Transfer.Direction.pullFromVoSpace;
        else if (strDirection.equalsIgnoreCase(Transfer.Direction.pullToVoSpace.toString()))
            rtn = Transfer.Direction.pullToVoSpace;
        else if (strDirection.equalsIgnoreCase(Transfer.Direction.pushFromVoSpace.toString()))
            rtn = Transfer.Direction.pushFromVoSpace;
        else if (strDirection.equalsIgnoreCase(Transfer.Direction.pushToVoSpace.toString()))
            rtn = Transfer.Direction.pushToVoSpace;
        return rtn;
    }

    private List<Protocol> parseProtocols() {
        List<Protocol> rtn = null;
        Element root = this.document.getRootElement();
        Element e = root.getChild("protocols", VOS.NS);
        if (e != null)
        {
            rtn = new ArrayList<Protocol>();

            Protocol rs = null;
            List<?> listE = e.getChildren("protocol", VOS.NS);
            for (Object obj : listE)
            {
                Element eProtocol = (Element) obj;
                String uri = eProtocol.getAttributeValue("uri");
                String endpoint = eProtocol.getChildText("endpoint");
                rs = new Protocol(uri, endpoint, null);
                rtn.add(rs);
            }
        }
        return rtn;
    }
}
