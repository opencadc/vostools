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

import java.net.URI;

import javax.security.auth.x500.X500Principal;

import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.gms.AuthorizationException;
import ca.nrc.cadc.gms.Group;
import ca.nrc.cadc.gms.InvalidGroupException;
import ca.nrc.cadc.gms.InvalidMemberException;
import ca.nrc.cadc.gms.User;
import ca.nrc.cadc.gms.server.persistence.GroupPersistence;
import ca.nrc.cadc.gms.server.persistence.UserPersistence;

/**
 * Default implementation for a UserService.
 */
public class UserServiceImpl implements UserService
{
    private UserPersistence userPersistence;
    private GroupPersistence groupPersistence;

    /**
     * Hidden no-arg constructor for Javabean tools like Reflection.
     */
    UserServiceImpl()
    {
    }

    /**
     * Constructor to populate the fields.
     * 
     * @param userPersistence
     *            The Persistence for User instances.
     * @param groupPersistence
     *            The Persistence for Group instances.
     */
    public UserServiceImpl(final UserPersistence userPersistence,
            final GroupPersistence groupPersistence)
    {
        this.userPersistence = userPersistence;
        this.groupPersistence = groupPersistence;
    }

    /**
     * Obtain a User for the given ID.
     * 
     * @param userID User's X500Principal
     * @param withMembership Whether to include membership info.  It affects the performance.
     * @return User object if the user exists.
     * @throws InvalidMemberException if the user does not exist.
     * @throws AuthorizationException
     */
    public User getUser(final X500Principal userID, boolean withMembership) 
            throws InvalidMemberException, AuthorizationException
    {
        User user = getUserPersistence().getUser(userID, withMembership);
        if (user == null)
        {
            throw new InvalidMemberException(userID + " not found");
        }
        return user;
    }

    /**
     * Obtain the Member with the given Member's User ID of the Group with
     * the given Group ID.
     * 
     * @param memberUserID
     *            The Member's User ID.
     * @param groupID
     *            The Group's ID.
     * @return The User member of the given Group, or null if they are not
     *         a member.
     * @throws ca.nrc.cadc.gms.InvalidMemberException
     *             If the given User ID does not exist.
     * @throws ca.nrc.cadc.gms.InvalidGroupException
     *             If the given Group does not exist.
     * @throws IllegalArgumentException
     *             If the given member is not a member of the given Group.
     * @throws AuthorizationException
     *             If the executing User is not authorized to do so.
     */
    public User getMember(final X500Principal memberUserID,
            final URI groupID) throws InvalidMemberException,
            InvalidGroupException, AuthorizationException
    { 
        final Group group = getGroupPersistence().getGroup(groupID);
        if (group == null)
        {
            throw new InvalidGroupException(String.format(
                    "No such Group with ID %s", groupID));
        }

        // compare member IDs in cannonical format!

        for (User member : group.getMembers())
        {
            if (AuthenticationUtil.equals(member.getID(), memberUserID))
            {
                return member;
            }
        }
        // User is not a member of the group.  return null.
        return null;
    }

    public UserPersistence getUserPersistence()
    {
        return userPersistence;
    }

    public void setUserPersistence(UserPersistence userPersistence)
    {
        this.userPersistence = userPersistence;
    }

    public GroupPersistence getGroupPersistence()
    {
        return groupPersistence;
    }

    public void setGroupPersistence(GroupPersistence groupPersistence)
    {
        this.groupPersistence = groupPersistence;
    }

    /* (non-Javadoc)
     * @see ca.nrc.cadc.gms.server.UserService#isMember(javax.security.auth.x500.X500Principal, java.net.URI)
     */
    @Override
    public boolean isMember(X500Principal memberUserID, URI groupID) 
    throws AuthorizationException
    {
        throw new RuntimeException("isMember(X500Principal memberUserID, URI groupID) is not implemented in UserServiceImpl. It's in CADCUserServiceImpl.");
    }
}
