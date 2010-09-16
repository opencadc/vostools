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

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

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
public class UserReaderTest
{
    private static Logger log = Logger.getLogger(UserReaderTest.class);
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    static String MEMBER_ID = "CN=test user,OU=hia.nrc.ca,O=Grid,C=CA";
    static String USER_NAME_PROP = "ivo://cadc.nrc.ca/gms#member_name";
    static String USER_ID_PROP = "ivo://cadc.nrc.ca/gms#member_id";
    static String groupID = "groupID";
    final static String groupURI = "ivo://cadc.nrc.ca/gms/group#" + groupID;
    final static String GROUP_DESCR = "ivo://cadc.nrc.ca/gms/group_description";
    static String userName = "Test User";
    static String userId = "userId";
    static UserImpl user;
    static String userXML;
    static String description = "This is my test group";
    
    public UserReaderTest() {}

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        user = new UserImpl(new X500Principal(MEMBER_ID));
        user.getProperties().add(new ElemPropertyImpl(USER_NAME_PROP, userName));
        ElemProperty eProp = new ElemPropertyImpl(USER_ID_PROP, userId);
        eProp.setReadOnly(true);
        user.getProperties().add(eProp);
        GroupImpl group = new GroupImpl(new URI(groupURI));
        group.getProperties().add(new ElemPropertyImpl(GROUP_DESCR, description));
        Set<Group> memberships = new HashSet<Group>();
        memberships.add(group);
        user.setMemberships(memberships);
        
        StringBuilder sb = new StringBuilder();
        sb.append("<member dn=\"" + MEMBER_ID + "\">\n");
        sb.append("<property name=\"" + USER_NAME_PROP + "\" value=\"" + userName + "\" />\n");
        sb.append("<property name=\"" + USER_ID_PROP + "\" value=\"" + userId + "\" readOnly=\"true\" />\n");
        sb.append("<membershipGroups>\n");
        sb.append("<group uri=\"" + groupURI + "\" >");
        sb.append("<property name=\"" + GROUP_DESCR + "\" value=\"" + description +"\" />\n");
        sb.append("<members/>\n");
        sb.append("</group>\n");
        sb.append("</membershipGroups>\n");
        sb.append("</member>\n");
        userXML = sb.toString();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {}

    @Before
    public void setUp() {}

    @After
    public void tearDown() {}

    /**
     * Test of read method, of class UserReader.
     */
    @Test
    public void testRead_String() throws Exception
    {
        try
        {
            log.debug("testRead_String");
            User u = UserReader.read(userXML);

            assertEquals(user, u);

            log.info("testRead_String passed");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

}
