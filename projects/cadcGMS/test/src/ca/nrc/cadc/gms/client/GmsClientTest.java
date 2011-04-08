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
package ca.nrc.cadc.gms.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.security.cert.Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.security.auth.x500.X500Principal;

import org.junit.Test;

import ca.nrc.cadc.gms.GMSTest;
import ca.nrc.cadc.gms.GMSTestSuite;
import ca.nrc.cadc.gms.Group;
import ca.nrc.cadc.gms.GroupImpl;
import ca.nrc.cadc.gms.User;
import ca.nrc.cadc.gms.UserImpl;

public class GmsClientTest extends GMSTest<GmsClient>
{
    // group IDs
    private final String getGrID = "getGroupID";
    private final String getMemberID = "getMemberID";
    private final String createPUTGrID = "createPUTGroupID";
    private final String createPOSTGrID = "createPOSTGroupID";
    private final String deleteGrID = "deleteGroupID";
    private final String setGrID = "setGroupID";

    // values for members in setGroup test
    private final String setGrMemberID = "CN=test user,OU=hia.nrc.ca,O=Grid,C=CA";

    /**
     * Prepare the testSubject to be tested.
     * 
     * @throws Exception
     *             For anything that went wrong.
     */
    public void initializeTestSubject() throws Exception
    {

        setTestSubject(new GmsClient()
        {
            protected HttpsURLConnection openConnection(final URL url)
                    throws IOException
            {
                return new HttpsURLConnection(url)
                {
                    InputStream inStream;

                    @Override
                    public int getResponseCode()
                    {
                        String path = getURL().getPath();
                        if (path.contains(createPUTGrID)
                                && getRequestMethod().equals("GET"))
                        {
                            // this is the call to verify if resource
                            // exists before creating it. Pretend it
                            // doesn't exist
                            return HttpURLConnection.HTTP_NOT_FOUND;
                        }

                        if (getRequestMethod().equals("POST")
                                || getRequestMethod().equals("PUT"))
                        {
                            return HttpURLConnection.HTTP_CREATED;
                        }
                        return HttpURLConnection.HTTP_OK;
                    }

                    @Override
                    public String getHeaderField(String headerField)
                    {
                        if (getRequestMethod().equals("PUT"))
                        {
                            return GMSTestSuite.CADC_GROUP_URI
                                    + createPOSTGrID;
                        }
                        return null;
                    }

                    @Override
                    public OutputStream getOutputStream()
                            throws IOException
                    {
                        connect();
                        return new FileOutputStream("/dev/null");
                    }

                    @Override
                    public void connect()
                    {
                        String path = getURL().getPath();
                        String groupID = null;
                        String memberID = null;
                        // there are two keywords in the path members and
                        // groups
                        if (path.contains("members"))
                        {
                            path = path
                                    .substring(path.indexOf("members"));
                            memberID = path
                                    .substring(path.indexOf("/") + 1);
                            if (memberID.contains("/"))
                            {
                                groupID = memberID.substring(memberID
                                        .lastIndexOf("/") + 1);
                                memberID = memberID.substring(0, memberID
                                        .lastIndexOf("/"));
                            }
                        }
                        if (path.contains("groups/"))
                        {
                            path = path.substring(path.indexOf("groups"));
                            groupID = path
                                    .substring(path.indexOf("/") + 1);
                            if (groupID.contains("/"))
                            {
                                memberID = groupID.substring(groupID
                                        .lastIndexOf("/") + 1);
                                groupID = groupID.substring(0, groupID
                                        .lastIndexOf("/"));

                            }
                        }
                        buildInputStream(groupID, memberID,
                                "TESTUSERNAME");

                    };

                    @Override
                    public void disconnect()
                    {
                    };

                    @Override
                    public String getCipherSuite()
                    {
                        return null;
                    }

                    @Override
                    public Certificate[] getServerCertificates()
                    {
                        return null;
                    }

                    @Override
                    public Certificate[] getLocalCertificates()
                    {
                        return null;
                    }

                    @Override
                    public boolean usingProxy()
                    {
                        return false;
                    };

                    @Override
                    public InputStream getInputStream()
                            throws IOException
                    {
                        return inStream;
                    }

                    private void buildInputStream(final String group,
                            final String memberID, final String name)
                    {
                        if (group != null)
                        {
                            if (group.equals(getMemberID))
                            {
                                final StringBuilder XML_INPUT = new StringBuilder(
                                        128);
                                XML_INPUT
                                        .append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
                                XML_INPUT.append("<member dn=\""
                                        + setGrMemberID + "\">\n");
                                XML_INPUT.append("<membershipGroups/>");
                                XML_INPUT.append("</member>");
                                inStream = new ByteArrayInputStream(
                                        XML_INPUT.toString().getBytes());
                                return;
                            }
                            if (group.equals(setGrID))
                            {
                                final StringBuilder XML_INPUT = new StringBuilder(
                                        128);
                                XML_INPUT
                                        .append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
                                XML_INPUT.append("<group uri=\""
                                        + GMSTestSuite.CADC_GROUP_URI
                                        + setGrID + "\" > \n");
                                XML_INPUT.append("<members>");
                                XML_INPUT.append("<member dn=\""
                                        + setGrMemberID + "\">\n");
                                XML_INPUT.append("<membershipGroups/>");
                                XML_INPUT.append("</member>");
                                XML_INPUT.append("</members>");
                                XML_INPUT.append("</group>");

                                inStream = new ByteArrayInputStream(
                                        XML_INPUT.toString().getBytes());
                                return;
                            }
                            if (group.equals(createPUTGrID)
                                    || group.equals(getGrID)
                                    || group.equals(createPOSTGrID)
                                    || group.equals(deleteGrID))
                            {
                                final StringBuilder XML_INPUT = new StringBuilder(
                                        128);
                                XML_INPUT
                                        .append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
                                XML_INPUT.append("<group uri=\""
                                        + GMSTestSuite.CADC_GROUP_URI
                                        + group + "\" />\n");
                                inStream = new ByteArrayInputStream(
                                        XML_INPUT.toString().getBytes());
                                return;
                            }
                        }
                        else
                        {
                            // this is post to create group
                            final StringBuilder XML_INPUT = new StringBuilder(
                                    128);
                            XML_INPUT
                                    .append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
                            XML_INPUT.append("<group uri=\""
                                    + GMSTestSuite.CADC_GROUP_URI
                                    + createPOSTGrID + "\" />\n");
                            inStream = new ByteArrayInputStream(XML_INPUT
                                    .toString().getBytes());
                            return;
                        }

                    }
                };
            }

        });
    }

    @Test
    public void getMember() throws Exception
    {
        final String groupID = GMSTestSuite.CADC_GROUP_URI + getMemberID;
        final String memberID = setGrMemberID;

        assertTrue("Member with ID " + setGrMemberID
                + " is a Member of Group getMember.", getTestSubject()
                .isMember(new URI(groupID), new X500Principal(memberID)));
    }

    @Test
    public void getGroup() throws Exception
    {
        final String groupID = GMSTestSuite.CADC_GROUP_URI + getGrID;
        final Group group = getTestSubject().getGroup(new URI(groupID));

        assertNotNull("Group " + groupID, group);
        assertEquals("Group's ID is " + getGrID, new URI(groupID), group
                .getID());
    }

    @Test
    public void createPOSTWithGIDGroup() throws Exception
    {
        final String groupID = GMSTestSuite.CADC_GROUP_URI
                + createPOSTGrID;
        Group group = new GroupImpl(new URI(groupID));
        final Group createdGroup = getTestSubject().createGroup(group);

        assertNotNull("Group createPOSTGrroup.", createdGroup);
        assertEquals("Group's ID is " + groupID, new URI(groupID),
                createdGroup.getID());
    }

    @Test
    public void createPOSTGroup() throws Exception
    {
        final Group group = getTestSubject().createGroup(
                new GroupImpl(new URI(GMSTestSuite.CADC_GROUP_URI)));

        assertNotNull("Group createPOSTGroup.", group);
        assertEquals("Group's ID is " + GMSTestSuite.CADC_GROUP_URI
                + createPOSTGrID, new URI(GMSTestSuite.CADC_GROUP_URI
                + createPOSTGrID), group.getID());
    }

    @Test
    public void setGroup() throws Exception
    {
        final String groupID = GMSTestSuite.CADC_GROUP_URI + setGrID;
        Group newGroup = new GroupImpl(new URI(groupID));
        String memberID = setGrMemberID;
        User newUser = new UserImpl(new X500Principal(memberID));
        newGroup.addMember(newUser);

        assert (getTestSubject().setGroup(newGroup));

    }

    @Test
    public void deleteGroup() throws Exception
    {
        final String groupID = GMSTestSuite.CADC_GROUP_URI
                + "deleteGroup";
        getTestSubject().deleteGroup(new URI(groupID));
    }

}
