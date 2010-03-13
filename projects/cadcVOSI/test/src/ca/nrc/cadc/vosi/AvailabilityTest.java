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

import java.io.StringWriter;
import java.io.Writer;
import java.text.ParseException;
import java.util.Date;

import org.jdom.Document;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.util.Log4jInit;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * @author zhangsa
 *
 */
public class AvailabilityTest
{
    private static Logger log = Logger.getLogger(CapabilityTest.class);
    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.vosi", Level.INFO);
    }

    String schemaResource = "VOSIAvailability-v1.0.xsd"; // local xsd file name
    String schemaNSKey = VOSI.AVAILABILITY_NS_URI;

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterClass
    public static void tearDownAfterClass() throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception
    {
    }

    @Test
    public void testAvailability() throws Exception
    {
        AvailabilityStatus status = null;
        try
        {
            Date d1 = DateUtil.toDate("2009-04-12T11:22:33.444", DateUtil.IVOA_DATE_FORMAT); //yyyy-MM-dd'T'HH:mm:ss.SSS
            Date d2 = DateUtil.toDate("2009-05-12T11:22:33.444", DateUtil.IVOA_DATE_FORMAT); //yyyy-MM-dd'T'HH:mm:ss.SSS
            Date d3 = DateUtil.toDate("2009-06-12T11:22:33.444", DateUtil.IVOA_DATE_FORMAT); //yyyy-MM-dd'T'HH:mm:ss.SSS
            status = new AvailabilityStatus(true, d1, d2, d3, "noteA");
        }
        catch (ParseException e)
        {
            log.error("test code bug", e);
        }
        Availability availability = new Availability(status);
        Document doc = availability.toXmlDocument();
        XMLOutputter xop = new XMLOutputter(Format.getPrettyFormat());
        Writer stringWriter = new StringWriter();
        xop.output(doc, stringWriter);
        String xmlString = stringWriter.toString();
        
        TestUtil.validateXml(xmlString, schemaNSKey, schemaResource);

        TestUtil.assertXmlNode(doc, "/vosi:availability");
        TestUtil.assertXmlNode(doc, "/vosi:availability/vosi:available");
        TestUtil.assertXmlNode(doc, "/vosi:availability/vosi:upSince");
        TestUtil.assertXmlNode(doc, "/vosi:availability/vosi:downAt");
        TestUtil.assertXmlNode(doc, "/vosi:availability/vosi:backAt");
        TestUtil.assertXmlNode(doc, "/vosi:availability/vosi:note");

    }

    @Test
    public void testAvailabilityEmptyStatus() throws Exception
    {
        AvailabilityStatus status = null;
        status = new AvailabilityStatus(false, null, null, null, null);
        Availability availability = new Availability(status);
        Document doc = availability.toXmlDocument();
        XMLOutputter xop = new XMLOutputter(Format.getPrettyFormat());
        Writer stringWriter = new StringWriter();
        xop.output(doc, stringWriter);
        String xmlString = stringWriter.toString();

        TestUtil.validateXml(xmlString, schemaNSKey, schemaResource);
        
        TestUtil.assertXmlNode(doc, "/vosi:availability");
        TestUtil.assertXmlNode(doc, "/vosi:availability/vosi:available[.='false']");
        TestUtil.assertNoXmlNode(doc, "/vosi:availability/vosi:upSince");
        TestUtil.assertNoXmlNode(doc, "/vosi:availability/vosi:downAt");
        TestUtil.assertNoXmlNode(doc, "/vosi:availability/vosi:backAt");
        TestUtil.assertNoXmlNode(doc, "/vosi:availability/vosi:note");
    }

    @Test
    public void testAvailabilityNullStatus() throws Exception
    {
        try
        {
            new Availability(null);
        }
        catch (IllegalArgumentException e)
        {
            assert (true);
        }
    }

}
