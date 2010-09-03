/**
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2010.                            (c) 2010.
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
 ************************************************************************
 */

package ca.nrc.cadc.gms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.XMLUnit;
import org.jdom.Element;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.util.Log4jInit;

/**
 * 
 * @author jburke
 */
public class GroupWriterTest
{
    private static Logger log = Logger.getLogger(GroupWriterTest.class);
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    static Group group;
    static String groupXML;

    public GroupWriterTest()
    {
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        XMLUnit.setIgnoreWhitespace(true);

        group = new GroupImpl("groupId", "groupName", new UserImpl(
                "ownerId", "owner name"), "This is just a test",
                GMSTestSuite.CADC_GROUP_URI);
        group.addMember(new UserImpl("memberId", "username"));

        StringBuilder sb = new StringBuilder();
        sb
                .append("<group id=\"groupId\" uriPrefix=\""
                        + GMSTestSuite.CADC_GROUP_URI
                        + "\" name=\"groupName\" description=\"This is just a test\" >");
        sb.append("<owner id=\"ownerId\" >");
        sb.append("<username>owner name</username>");
        sb.append("</owner>");
        sb.append("<members>");
        sb.append("<member id=\"memberId\">");
        sb.append("<username>username</username>");
        sb.append("</member>");
        sb.append("</members>");
        sb.append("</group>");
        groupXML = sb.toString();
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
    }

    @Before
    public void setUp()
    {
    }

    @After
    public void tearDown()
    {
    }

    /**
     * Test of write method, of class GroupWriter.
     */
    @Test
    public void testWrite_Group_StringBuilder() throws Exception
    {
        try
        {
            log.debug("testWrite_Group_StringBuilder");
            StringBuilder sb = new StringBuilder();
            GroupWriter.write(group, sb);
            log.debug(sb.toString());
            log.debug(groupXML);

            XMLAssert.assertXMLEqual(groupXML, sb.toString());

            log.info("testWrite_Group_StringBuilder passed");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    /**
     * Test of getGroupElement method, of class GroupWriter.
     */
    @Test
    public void testGetGroupElement()
    {
        try
        {
            log.debug("testGetGroupElement");
            Element groupElement = GroupWriter.getGroupElement(group);

            assertTrue(groupElement.getName().equals("group"));
            assertNotNull(groupElement.getAttribute("id"));
            assertTrue(groupElement.getAttributeValue("id").equals(
                    "groupId"));
            assertNotNull(groupElement.getAttribute("uriPrefix"));
            assertTrue(groupElement.getAttributeValue("uriPrefix").equals(
                    GMSTestSuite.CADC_GROUP_URI));
            assertNotNull(groupElement.getAttribute("name"));
            assertTrue(groupElement.getAttributeValue("name").equals(
                    "groupName"));

            Element owner = groupElement.getChild("owner");
            assertNotNull(owner);
            assertNotNull(owner.getAttribute("id"));
            assertTrue(owner.getAttributeValue("id").equals("ownerId"));
            Element ownerName = owner.getChild("username");
            assertNotNull(ownerName);
            assertEquals("owner name", ownerName.getText());

            Element userElements = groupElement.getChild("members");
            assertNotNull(userElements);

            Element userElement = userElements.getChild("member");
            assertNotNull(userElement);

            assertTrue(userElement.getName().equals("member"));
            assertNotNull(userElement.getAttribute("id"));
            assertTrue(userElement.getAttributeValue("id").equals(
                    "memberId"));

            Element usernameElement = userElement.getChild("username");
            assertNotNull(usernameElement);
            assertEquals("username", usernameElement.getText());

            log.info("testGetGroupElement passed");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

}