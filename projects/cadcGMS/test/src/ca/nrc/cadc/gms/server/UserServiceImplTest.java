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
package ca.nrc.cadc.gms.server;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import ca.nrc.cadc.gms.Group;
import ca.nrc.cadc.gms.User;
import ca.nrc.cadc.gms.UserImpl;
import ca.nrc.cadc.gms.server.persistence.GroupPersistence;
import ca.nrc.cadc.gms.server.persistence.UserPersistence;

public class UserServiceImplTest extends UserServiceTest
{
    protected UserPersistence mockUserPersistence = createMock(UserPersistence.class);
    protected GroupPersistence mockGroupPersistence = createMock(GroupPersistence.class);

    /**
     * Prepare the testSubject to be tested.
     * 
     * @throws Exception
     *             For anything that went wrong.
     */
    public void initializeTestSubject() throws Exception,
            URISyntaxException
    {
        setTestSubject(new UserServiceImpl(mockUserPersistence,
                mockGroupPersistence));
    }

    @Override
    @Test
    public void getMemberships() throws Exception
    {
        final Collection<Group> mockUserMemberships = new HashSet<Group>();
        final User mockUser = createMock(User.class);
        final Group mockGroup = createMock(Group.class);

        mockUserMemberships.add(mockGroup);
        expect(mockGroup.getID()).andReturn(GROUP_ID).once();
        expect(mockUserPersistence.getUser(NON_MEMBER_USER_ID, true))
                .andReturn(null).once();
        expect(mockUserPersistence.getUser(MEMBER_USER_ID, true))
                .andReturn(mockUser).once();
        expect(mockUser.getGMSMemberships()).andReturn(
                mockUserMemberships).once();

        replay(mockGroupPersistence, mockUserPersistence, mockUser,
                mockGroup);

        super.getMemberships();
    }

    @Override
    @Test
    public void getMember() throws Exception
    {
        final User user = new UserImpl(MEMBER_USER_ID);
        final Group mockGroup = createMock(Group.class);
        final Group mockNoMembershipGroup = createMock(Group.class);
        Set<User> members = new HashSet<User>();
        members.add(user);
        
        expect(mockUserPersistence.getUser(MEMBER_USER_ID, false)).andReturn(user).times(3);
        expect(mockUserPersistence.getUser(NON_MEMBER_USER_ID, false)).andReturn(user).times(1);

        expect(mockGroupPersistence.getGroup(NON_GROUP_ID)).andReturn(
                null).once();
        expect(mockGroupPersistence.getGroup(GROUP_ID)).andReturn(
                mockGroup).times(2);
        
        expect(mockGroupPersistence.getGroup(NO_MEMBERSHIP_GROUP_ID))
                .andReturn(mockNoMembershipGroup).once();
        expect(mockGroup.getMembers()).andReturn(members)
                .times(14);
        expect(mockNoMembershipGroup.getMembers()).andReturn(new HashSet<User>())
                .times(2);

        replay(mockUserPersistence, mockGroupPersistence, mockGroup,
                mockNoMembershipGroup);

        super.getMember();
    }

    @Override 
    @Test 
    public void getMemberNonCanonicalX500Principal() throws Exception
    {
        final User user = new UserImpl(MEMBER_USER_ID);
        final Group mockGroup = createMock(Group.class);
        final Group mockNoMembershipGroup = createMock(Group.class);
        Set<User> members = new HashSet<User>();
        members.add(user);
        
        expect(mockUserPersistence.getUser(MEMBER_USER_ID, false)).andReturn(user).times(3);
        expect(mockUserPersistence.getUser(NON_CANON_USER_ID, false)).andReturn(user).times(1);

        expect(mockGroupPersistence.getGroup(NON_GROUP_ID)).andReturn(
                null).once();
        expect(mockGroupPersistence.getGroup(GROUP_ID)).andReturn(
                mockGroup).times(2);
        
        expect(mockGroupPersistence.getGroup(NO_MEMBERSHIP_GROUP_ID))
                .andReturn(mockNoMembershipGroup).once();
        expect(mockGroup.getMembers()).andReturn(members)
                .times(14);
        expect(mockNoMembershipGroup.getMembers()).andReturn(new HashSet<User>())
                .times(2);

        replay(mockUserPersistence, mockGroupPersistence, mockGroup,
                mockNoMembershipGroup);
        
        super.getMemberNonCanonicalX500Principal();
    }
}
