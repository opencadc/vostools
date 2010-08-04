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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;


public abstract class GroupTest extends GMSTest<Group>
{
    @Test
    public void getMembers() throws Exception
    {
        assertNotNull("Should never return null.",
                      getTestSubject().getMembers());
        assertTrue("Should initially be empty.",
                   getTestSubject().getMembers().isEmpty());
    }

    @Test
    public void addMember() throws Exception
    {
        try
        {
            getTestSubject().addMember(null);
            fail("Cannot add a null member.");
        }
        catch (InvalidMemberException iae)
        {
            // Good!
        }

        final User mockUserOne = createMock(User.class);
        final User mockUserTwo = createMock(User.class);
        expect(mockUserOne.getUsername()).andReturn("ONE").once();

        mockUserOne.addMembership(getTestSubject());
        expectLastCall().times(2);

        mockUserTwo.addMembership(getTestSubject());
        expectLastCall().once();

        replay(mockUserOne);

        getTestSubject().addMember(mockUserOne);

        assertEquals("Group should have one member.", 1,
                     getTestSubject().getMembers().size());

        getTestSubject().addMember(mockUserTwo);

        assertEquals("Group should have two members.", 2,
                     getTestSubject().getMembers().size());

        try
        {
            getTestSubject().addMember(mockUserOne);
            fail("Cannot add a member twice.");
        }
        catch (InvalidMemberException iae)
        {
            // Good!
        }

        final User mockUserThree = createMock(User.class);
        try
        {
            getTestSubject().getMembers().add(mockUserThree);
            fail("Members should not be able to be added using the "
                 + "Collections API.");
        }
        catch (UnsupportedOperationException uoe)
        {
            // Good!
        }
    }

    @Test
    public void removeMember() throws Exception
    {
        try
        {
            getTestSubject().removeMember(null);
            fail("Cannot remove a null member.");
        }
        catch (InvalidMemberException iae)
        {
            // Good!
        }

        final User mockUserOne = createMock(User.class);
        
        final User mockUserTwo = createMock(User.class);
        final User mockUserThree = createMock(User.class);

        mockUserOne.addMembership(getTestSubject());
        expectLastCall().times(2);
        
        expect(mockUserOne.getUserID()).andReturn("ONE_ID").times(4);
        
        expect(mockUserTwo.getUserID()).andReturn("TWO_ID").times(2);
        
        expect(mockUserThree.getUserID()).andReturn("THREE_ID").times(1);

        mockUserOne.removeMembership(getTestSubject());
        expectLastCall().once();

        mockUserTwo.addMembership(getTestSubject());
        expectLastCall().once();

        mockUserThree.addMembership(getTestSubject());
        expectLastCall().once();

        mockUserTwo.removeMembership(getTestSubject());
        expectLastCall().once();

        replay(mockUserOne, mockUserTwo, mockUserThree);

        try
        {
            getTestSubject().removeMember(mockUserOne.getUserID());
            fail("Cannot remove a non-existent member.");
        }
        catch (InvalidMemberException iae)
        {
            // Good!
        }

        getTestSubject().addMember(mockUserOne);
        assertTrue("Member was not added for removal.",
                   getTestSubject().getMembers().contains(mockUserOne));

        getTestSubject().removeMember(mockUserOne.getUserID());
        assertFalse("Member One was not removed.",
                   getTestSubject().getMembers().contains(mockUserOne));

        getTestSubject().addMember(mockUserOne);
        getTestSubject().addMember(mockUserTwo);
        getTestSubject().addMember(mockUserThree);
        assertEquals("Three members should have been added.", 3,
                     getTestSubject().getMembers().size());

        getTestSubject().removeMember(mockUserTwo.getUserID());
        assertTrue("Member One was removed but should not have been.",
                   getTestSubject().getMembers().contains(mockUserOne));
        assertTrue("Member Three was removed but should not have been.",
                   getTestSubject().getMembers().contains(mockUserThree));
        assertFalse("Member Two was not removed but should have been.",
                    getTestSubject().getMembers().contains(mockUserTwo));
    }    

    @Test
    public void hasMember() throws Exception
    {
        try
        {
            getTestSubject().hasMember(null);
            fail("Cannot check null values.");
        }
        catch (InvalidMemberException iae)
        {
            // Good!
        }


        final User mockUserOne = createMock(User.class);
        
        final User mockUserTwo = createMock(User.class);
        
        expect(mockUserOne.getUserID()).andReturn("ONE_ID").atLeastOnce();
        expect(mockUserTwo.getUserID()).andReturn("TWO_ID").atLeastOnce();

        mockUserOne.addMembership(getTestSubject());
        expectLastCall().times(1);
        
        mockUserTwo.addMembership(getTestSubject());
        expectLastCall().times(1);
        
        replay(mockUserOne, mockUserTwo);

        assertFalse("No members exist yet.",
                    getTestSubject().hasMember(mockUserOne.getUserID()));

        getTestSubject().addMember(mockUserOne);
        assertTrue("User One is a member.",
                   getTestSubject().hasMember(mockUserOne.getUserID()));

        getTestSubject().addMember(mockUserTwo);
        assertTrue("User Two is a member.",
                   getTestSubject().hasMember(mockUserTwo.getUserID()));
        assertTrue("User One is still a member.",
                   getTestSubject().hasMember(mockUserOne.getUserID()));

        getTestSubject().removeMember(mockUserTwo.getUserID());
        assertFalse("User Two should no longer be a member.",
                   getTestSubject().hasMember(mockUserTwo.getUserID()));        
    }
}
