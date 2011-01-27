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

import org.jdom.JDOMException;
import java.util.HashMap;
import java.util.Map;
import ca.nrc.cadc.util.Log4jInit;
import java.io.File;
import org.apache.log4j.Logger;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jburke
 */
public class XmlUtilTest
{
    private static Logger log = Logger.getLogger(XmlUtilTest.class);
    static
    {
        Log4jInit.setLevel("ca.nrc.cadc", org.apache.log4j.Level.INFO);
    }

    String fooSchemaURL;
    String barSchemaURL;

    public XmlUtilTest() 
    {
        fooSchemaURL = XmlUtil.getResourceUrlString("foo.xsd", XmlUtilTest.class);
        barSchemaURL = XmlUtil.getResourceUrlString("bar.xsd", XmlUtilTest.class);
    }

    @Test
    public void testValidXml_ValidParser() throws Exception
    {
        log.debug("testValidXml_ValidParser");
        try
        {
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" ?>");
            xml.append("<foons:foo xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
            xml.append("         xmlns:foons=\"http://localhost/foo.xsd\">");
            xml.append("</foons:foo>");

            Map<String, String> map = new HashMap<String, String>();
            map.put("http://localhost/foo.xsd", fooSchemaURL);
            XmlUtil.validateXml(xml.toString(), map);
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
            log.debug("testValidXml_ValidParser passed.");
    }

    @Test
    public void testInvalidXml_ValidParser() throws Exception
    {
        log.debug("testInvalidXml_ValidParser");
        try
        {
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" ?>");
            xml.append("<foons:bar xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
            xml.append("         xmlns:foons=\"http://localhost/foo.xsd\">");
            xml.append("</foons:bar>");

            Map<String, String> map = new HashMap<String, String>();
            map.put("http://localhost/foo.xsd", fooSchemaURL);

            try
            {
                XmlUtil.validateXml(xml.toString(), map);
                fail("JDOM parsing exception should have been thrown");
            }
            catch (JDOMException ifnore) {}
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
        log.debug("testInvalidXml_ValidParser passed.");
    }

    @Test
    public void testValidXml_InvalidParser() throws Exception
    {
        log.debug("testValidXml_InvalidParser");
        try
        {
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" ?>");
            xml.append("<foons:bar xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
            xml.append("         xmlns:foons=\"http://localhost/bar.xsd\">");
            xml.append("</foons:bar>");

            Map<String, String> map = new HashMap<String, String>();
            map.put("http://localhost/bar.xsd", fooSchemaURL);
            
            try
            {
                XmlUtil.validateXml(xml.toString(), map);
                fail("JDOM parsing exception should have been thrown");
            }
            catch (JDOMException ifnore) {}
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
        log.debug("testValidXml_InvalidParser passed.");
    }

    @Test
    public void testValidXml_NoSchemaLocation() throws Exception
    {
        log.debug("testValidXml_NoSchemaLocation");
        try
        {
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" ?>");
            xml.append("<foons:bar xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
            xml.append("         xmlns:foons=\"http://localhost/bar.xsd\">");
            xml.append("</foons:bar>");

            // MUST pass in an empty map of validation is turned off
            Map<String, String> map = new HashMap<String, String>();
            //map.put("http://localhost/bar.xsd", fooSchemaURL);

            try
            {
                XmlUtil.validateXml(xml.toString(), map);
                fail("JDOM parsing exception should have been thrown");
            }
            catch (JDOMException ifnore) {}
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
        log.debug("testValidXml_NoSchemaLocation passed.");
    }

    @Test
    public void testParseWithoutSchemaValidation()
    {
        log.debug("testParseWithoutSchemaValidation");
        try
        {
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" ?>");
            xml.append("<foons:bar xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
            xml.append("         xmlns:foons=\"http://localhost/bar.xsd\">");
            xml.append("</foons:bar>");

            // MUST pass null map to disable validation
            Map<String, String> map = null;
            try
            {
                XmlUtil.validateXml(xml.toString(), map);
            }
            catch (JDOMException ex)
            {
                fail("unexpected exception: " + ex);
            }
        }
        catch (Throwable t)
        {
            log.error("test failure", t);
            fail(t.getMessage());
        }
    }
}
