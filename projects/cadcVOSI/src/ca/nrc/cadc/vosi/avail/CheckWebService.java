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

package ca.nrc.cadc.vosi.avail;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;

import ca.nrc.cadc.vosi.VOSI;
import ca.nrc.cadc.vosi.util.WebGet;
import ca.nrc.cadc.xml.XmlUtil;

/**
 * Check the /availability resource of another web service.
 * 
 * @author zhangsa
 *
 */
public class CheckWebService implements CheckResource
{
    private static Logger log = Logger.getLogger(CheckWebService.class);

    private Map<String,String> schemaMap = new HashMap<String,String>();
    private String wsURL;

    /**
     * @param url the URL of availability checking, e.g. http://www.sample.com/myservice/availability
     */
    public CheckWebService(String url)
    {
        wsURL = url;
        this.schemaMap.put( VOSI.AVAILABILITY_NS_URI, XmlUtil.getResourceUrlString(VOSI.AVAILABILITY_SCHEMA, CheckWebService.class));
    }

    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    @Override
    public void check()
        throws CheckException
    {
        String wgReturn = null;
        try
        {
            WebGet webGet = new WebGet(wsURL);
            wgReturn = webGet.submit();
            checkReturnedXml(wgReturn);
            log.debug("test succeeded: " + wsURL);
        }
        catch (MalformedURLException e)
        {
            log.warn("test failed: " + wsURL);
            throw new RuntimeException("test URL is malformed: " + wsURL, e);
        }
        catch(IOException e)
        {
            log.warn("test failed: " + wsURL);
            throw new CheckException("service not responding: " + wsURL, e);
        }
    }

    void checkReturnedXml(String strXml)
        throws CheckException
    {
        Document doc = null;
        String xpathStr;
        XPath xpath;

        try
        {
            doc = XmlUtil.validateXml(strXml, schemaMap);
            //get namespace and/or prefix from Document, then create xpath based on the prefix
            String nsp = doc.getRootElement().getNamespacePrefix(); //Namespace Prefix
            if (nsp != null && nsp.length() > 0)
                nsp = nsp + ":";
            else
                nsp = "";
            xpathStr = "/" + nsp + "availability/" + nsp + "available";
            xpath = XPath.newInstance(xpathStr);
            Element eleAvail = (Element) xpath.selectSingleNode(doc);
            log.debug(eleAvail);
            String textAvail = eleAvail.getText();

            // TODO: is this is actually valid? is the content not constrained by the schema?
            if (textAvail == null)
                throw new CheckException(wsURL + " output is invalid: no content in <available> element", null);

            if ( !textAvail.equalsIgnoreCase("true") )
            {
                xpathStr = "/" + nsp + "availability/" + nsp + "note";
                xpath = XPath.newInstance(xpathStr);
                Element eleNotes = (Element) xpath.selectSingleNode(doc);
                String textNotes = eleNotes.getText();
                throw new CheckException("service " + wsURL + " is not available, reported reason: " + textNotes, null);
            }
        }
        catch(IOException e)
        {
            // probably an incorrect setup or bug in the checks
            throw new RuntimeException("failed to test " + wsURL, e);
        }
        catch(JDOMException e)
        {
            throw new CheckException(wsURL + " output is invalid", e);
        }
    }
}
