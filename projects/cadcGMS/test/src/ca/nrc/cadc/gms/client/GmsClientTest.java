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

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;

import org.junit.Test;

import ca.nrc.cadc.gms.GMSTest;
import ca.nrc.cadc.gms.Group;
import ca.nrc.cadc.gms.GroupImpl;
import ca.nrc.cadc.gms.User;
import ca.nrc.cadc.gms.UserImpl;

public class GmsClientTest extends GMSTest<GmsClient>
{
    // group IDs
    private final String getGrID = "getGroup";
    private final String getMemberID = "getMember";
    private final String createPUTGrID = "createPUTGroup";
    private final String createPOSTGrID = "createPOSTGroup";
    private final String deleteGrID = "deleteGroup";
    private final String setGrID = "setGroup";
    
    // values for members in setGroup test
    private final String setGrMemberID = "auser";
    private final String setGrMemberName = "A User";

    /**
     * Prepare the testSubject to be tested.
     * 
     * @throws Exception
     *             For anything that went wrong.
     */
    public void initializeTestSubject() throws Exception
    {

        setTestSubject(new GmsClient(
                new URL("http://localhost/myservice"))
        {
            protected URLConnection openConnection(final URL url)
                    throws IOException
            {
                return new HttpURLConnection(url)
                {
                    InputStream inStream;

                    @Override
                    public int getResponseCode()
                    {
                        String path = getURL().getPath();
                        if (path.contains(createPUTGrID) && 
                                getRequestMethod().equals("GET"))
                        {
                            // this is the call to verify if resource
                            // exists before creating it. Pretend it
                            // doesn't exist
                            return HttpURLConnection.HTTP_NOT_FOUND;
                        }
                        
                        if( getRequestMethod().equals("PUT"))
                        {
                            return HttpURLConnection.HTTP_CREATED;
                        }
                        return HttpURLConnection.HTTP_OK;
                    }
                    
                    @Override
                    public OutputStream getOutputStream() throws IOException
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
                        if (path.contains("groups"))
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
                                XML_INPUT.append("<member id=\""
                                        + memberID + "\">\n");
                                XML_INPUT.append("  <username>" + name
                                        + "</username>\n");
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
                                XML_INPUT.append("<group id=\""
                                        + setGrID + "\">\n");
                                XML_INPUT.append("<member id=\""
                                        + setGrMemberID + "\">\n");
                                XML_INPUT.append("  <username>" + setGrMemberName
                                        + "</username>\n");
                                XML_INPUT.append("</member>");
                                XML_INPUT.append("</group>");
                                
                                inStream = new ByteArrayInputStream(
                                        XML_INPUT.toString().getBytes());
                                return;
                            }
                            if (group.equals(createPUTGrID)
                                    || group.equals(getGrID)
                                    || group.equals(deleteGrID))
                            {
                                final StringBuilder XML_INPUT = new StringBuilder(
                                        128);
                                XML_INPUT
                                        .append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
                                XML_INPUT.append("<group id=\"" + group
                                        + "\"/>\n");
                                inStream = new ByteArrayInputStream(
                                        XML_INPUT.toString().getBytes());
                                return;
                            }
                            if (group.length() == 0)
                            {
                                // this is post to create group
                                final StringBuilder XML_INPUT = new StringBuilder(
                                        128);
                                XML_INPUT
                                        .append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
                                XML_INPUT
                                        .append("<group id=\"" + createPOSTGrID + "\"/>\n");
                                inStream = new ByteArrayInputStream(
                                        XML_INPUT.toString().getBytes());
                                return;
                            }
                        }

                    }
                };
            }

        });
    }

    @Test
    public void getMember() throws Exception
    {
        final String groupID = getMemberID;
        final String memberID = Long.toString(88l);
        final User member = getTestSubject().getMember(groupID, memberID);

        assertNotNull(
                "Member with ID 88 is a Member of Group getMember.",
                member);
        assertEquals("Member's ID is 88.", Long.toString(88l), member
                .getUserID());
        assertEquals("Member's Username is TESTUSERNAME.",
                "TESTUSERNAME", member.getUsername());
    }

    @Test
    public void getGroup() throws Exception
    {
        final String groupID = getGrID;
        final Group group = getTestSubject().getGroup(groupID);

        assertNotNull("Group 88.", group);
        assertEquals("Group's ID is getGroup.", "getGroup", group
                .getGMSGroupID());
    }

    @Test
    public void createPUTGroup() throws Exception
    {
        final String groupID = createPUTGrID;
        Group group = new GroupImpl(createPUTGrID);
        final Group createdGroup = getTestSubject().createGroup(group);

        assertNotNull("Group createPUTGrroup.", createdGroup);
        assertEquals("Group's ID is " + groupID, groupID, createdGroup
                .getGMSGroupID());
    }

    @Test
    public void createPOSTGroup() throws Exception
    {
        final Group group = getTestSubject().createGroup(null);

        assertNotNull("Group createPOSTGroup.", group);
        assertEquals("Group's ID is " + createPOSTGrID, createPOSTGrID,
                group.getGMSGroupID());
    }

    @Test
    public void setGroup() throws Exception
    {
        final String groupID = setGrID;
        Group newGroup = new GroupImpl(groupID);
        String memberID = setGrMemberID;
        String memberName = setGrMemberName;
        User newUser = new UserImpl(memberID, memberName);
        newGroup.addMember(newUser);
        final Group group = getTestSubject().setGroup(newGroup);

        assertNotNull("Group SetNewTestGroup.", group);
        assertEquals("Group's ID is " + groupID, groupID, group
                .getGMSGroupID());

        assertNotNull(
                "Member with ID auser is a Member of Group " + setGrID,
                group.getMembers());
        Collection<User> members = group.getMembers();
        assertEquals("Number of members is 1", 1, members.size());
        
        for(User member : members)
        {
            assertEquals("Member's ID is " + setGrMemberID, setGrMemberID, member
                    .getUserID());
            assertEquals("Member's Username is " + setGrMemberName, setGrMemberName,
                    member.getUsername());
        }

    }

    @Test
    public void deleteGroup() throws Exception
    {
        final String groupID = "deleteGroup";
        getTestSubject().deleteGroup(groupID);
    }

}
