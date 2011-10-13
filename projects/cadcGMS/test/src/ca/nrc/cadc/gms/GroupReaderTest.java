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
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.net.URI;

import javax.security.auth.x500.X500Principal;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
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
public class GroupReaderTest
{
    private static Logger log = Logger.getLogger(GroupReaderTest.class);
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    static Group group;
    static String groupXML;
    static String groupID = "groupID";
    final static String groupURI = "ivo://cadc.nrc.ca/gms/group#" + groupID;
    final static String GROUP_DESCR = "ivo://cadc.nrc.ca/gms/group_description";
    final static String OWNER_ID_PROP = "ivo://cadc.nrc.ca/gms/group_owner_dn";
    final static String OWNER_NAME_PROP = "ivo://cadc.nrc.ca/gms/group_owner_name";
    final static String USER_NAME_PROP = "ivo://cadc.nrc.ca/gms#member_name";
    static String userName = "Test User";
    static String userId = "userId";
    static String ownerName = "Owner User";
    static String ownerId = "ownerId";
    final static String MEMBER_ID = "CN=test user,OU=hia.nrc.ca,O=Grid,C=CA";
    final static String OWNER_ID = "CN=owner user,OU=hia.nrc.ca,O=Grid,C=CA";
    static String description = "This is my test group";
    static final String groupRead1 = "ivo://cadc.nrc.ca/gms#test1";
    static final String groupRead2 = "ivo://cadc.nrc.ca/gms#test2";
    static final String groupWrite = "ivo://cadc.nrc.ca/gms#test3";

    public GroupReaderTest()
    {
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        group = new GroupImpl(new URI(groupURI));
        User user = new UserImpl(new X500Principal(OWNER_ID));
        ElemProperty eProp = new ElemPropertyImpl(OWNER_ID_PROP, OWNER_ID);
        group.getProperties().add(eProp);
        eProp = new ElemPropertyImpl(OWNER_NAME_PROP, ownerName);
        eProp.setReadOnly(true);
        group.getProperties().add(eProp);
        group.getProperties().add(new ElemPropertyImpl(GROUP_DESCR, description));
        user = new UserImpl(new X500Principal(MEMBER_ID));
        eProp = new ElemPropertyImpl(USER_NAME_PROP, userName);
        eProp.setReadOnly(true);
        user.getProperties().add(eProp);
        group.addMember(user);
        group.addGroupRead(new URI(groupRead1));
        group.addGroupRead(new URI(groupRead2));
        group.addGroupWrite(new URI(groupWrite));

        StringBuilder sb = new StringBuilder();
        sb.append("<group uri=\"" + groupURI + "\" >\n");
        sb.append("<property name=\"" + OWNER_ID_PROP + "\" value=\"" + OWNER_ID + "\" />\n");
        sb.append("<property name=\"" + OWNER_NAME_PROP + "\" value=\"" + ownerName + "\" readOnly=\"true\" />\n");
        sb.append("<property name=\"" + GROUP_DESCR + "\" value=\"" + description + "\" />\n");
        sb.append("<members>\n");
        sb.append("<member dn=\"" + MEMBER_ID + "\" >\n");
        sb.append("<property name=\"" + USER_NAME_PROP + "\" value=\"" + userName + "\" readOnly=\"true\" />\n");
        sb.append("<membershipGroups/>");
        sb.append("</member>\n");
        sb.append("</members>\n");
        sb.append("<groupsRead>\n");
        sb.append("<group uri=\"" + groupRead1 + "\" />\n");
        sb.append("<group uri=\"" + groupRead2 + "\" />\n");
        sb.append("</groupsRead>\n");
        sb.append("<groupsWrite>\n");
        sb.append("<group uri=\"" + groupWrite + "\" />\n");
        sb.append("</groupsWrite>\n");
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
     * Test of read method, of class GroupReader.
     */
    @Test
    public void testRead_String() throws Exception
    {
        try
        {
            log.debug("testRead_String");
            Group g = GroupReader.read(groupXML);

            assertEquals(group, g);
            assertEquals(group.getMembers().size(), g.getMembers().size());
            for (User user : group.getMembers())
            {
                assertTrue(g.getMembers().contains(user));
            }
            assertEquals(group.getGroupsRead().size(), g.getGroupsRead().size());
            for (URI gr : group.getGroupsRead())
            {
                assertTrue(g.getGroupsRead().contains(gr));
            }
            assertEquals(group.getGroupsWrite().size(), g.getGroupsWrite().size());
            for (URI gr : group.getGroupsWrite())
            {
                assertTrue(g.getGroupsWrite().contains(gr));
            }

            log.info("testRead_String passed");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

}