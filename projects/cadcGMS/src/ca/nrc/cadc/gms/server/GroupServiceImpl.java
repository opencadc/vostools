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
import java.util.Collection;
import java.util.Map;

import javax.security.auth.x500.X500Principal;

import ca.nrc.cadc.gms.AuthorizationException;
import ca.nrc.cadc.gms.Group;
import ca.nrc.cadc.gms.InvalidGroupException;
import ca.nrc.cadc.gms.InvalidMemberException;
import ca.nrc.cadc.gms.UserImpl;
import ca.nrc.cadc.gms.server.persistence.GroupPersistence;


public class GroupServiceImpl implements GroupService
{
    private GroupPersistence groupPersistence;
    private String uriGroupPrefix;

    /**
     * No-arg constructor.
     */
    public GroupServiceImpl()
    {
    }

    /**
     * Full constructor for members.
     * 
     * @param groupPersistence
     *            The GroupPersistence object.
     * @param uriGroupPrefix
     *            The GMS URI Prefix.
     */
    public GroupServiceImpl(final GroupPersistence groupPersistence,
            final String uriGroupPrefix)
    {
        this.groupPersistence = groupPersistence;
        this.uriGroupPrefix = uriGroupPrefix;
    }

    /**
     * Obtain the Group with the given Group ID.
     * 
     * @param groupID
     *            Unique Group identifier.
     * @return The Group object for the given ID.
     */
    public Group getGroup(final URI groupID)
            throws InvalidGroupException, AuthorizationException
    {
        Group tmp = getGroupPersistence().getGroup(groupID);
        return tmp;
    }

    /**
     * Create a new Group
     * 
     * @param group
     *            New group
     * @return The Group object for the given ID.
     */
    public Group putGroup(final Group group)
            throws InvalidGroupException, AuthorizationException
    {
        Group temp = getGroupPersistence().putGroup(group); // returned group
        return temp;
    }

    /**
     * Modify an existing Group
     * 
     * @param group
     *            Updated group
     * @return The Group object for the given ID.
     */
    public Group postGroup(final Group group)
            throws InvalidGroupException, AuthorizationException
    {
        if (getGroup(group.getID()) == null)
        {
            throw new InvalidGroupException(
                    "Cannot post group. Group with ID " + group.getID()
                            + " not found");
        }
        Group temp = getGroupPersistence().putGroup(group); // returned group
        return temp;
    }

    /**
     * Delete the Group with the given Group ID.
     * 
     * @param groupID
     *            Unique Group identifier.
     */
    public void deleteGroup(final URI groupID)
            throws InvalidGroupException, AuthorizationException
    {
        getGroupPersistence().deleteGroup(groupID);
    }

    /**
     * Add user to a Group.
     * 
     * @param groupID
     *            group to add the user to.
     * @param memberID
     *            member ID
     * 
     * @return The updated group.
     */
    public Group addUserToGroup(final URI groupID, X500Principal memberID)
            throws InvalidGroupException, InvalidMemberException,
            AuthorizationException
    {
    	Group group = getGroupPersistence().getGroup(groupID);
    	
        group.getMembers().add(new UserImpl(memberID));
        Group tmp = getGroupPersistence().putGroup(group);
        return tmp;
    }
    
    /**
     * Delete user from a Group.
     * 
     * @param groupID
     *            group to add the user to.
     * @param memberID
     *            member ID
     * 
     * @return The updated group.
     */
    public Group deleteUserFromGroup(final URI groupID, X500Principal memberID)
            throws InvalidGroupException, InvalidMemberException,
            AuthorizationException
    {
        Group group = getGroupPersistence().getGroup(groupID);

        group.getMembers().remove(new UserImpl(memberID));
        Group tmp = getGroupPersistence().putGroup(group);
        return tmp;
    }

    /**
     * Obtain a Collection of Groups that fit the given query.
     * <p/>
     * Example:
     * {[ivo://ivoa.net/gms#owner_dn] [CN=CADC OPS,OU=hia.nrc.ca,O=Grid,C=CA,CN=myCADCusername]}
     * <p/>
     * Where the IVOA GMS key is ivo://ivoa.net/gms#owner_dn,
     * and the value is CN=CADC OPS,OU=hia.nrc.ca,O=Grid,C=CA,CN=myCADCusername
     *
     * @param criteria The Criteria to search on.
     * @return Collection of Groups matching the query, or empty
     *         Collection.  Never null.
     * @throws InvalidGroupException 
     * @see ca.nrc.cadc.gms.GmsConsts
     */
    public Collection<Group> getGroups(Map<String, String> criteria) 
    throws InvalidGroupException, AuthorizationException
    {
        Collection<Group> tmp = getGroupPersistence().getGroups(criteria);
        return tmp;
    }

    public GroupPersistence getGroupPersistence()
    {
        return groupPersistence;
    }

    public void setGroupPersistence(GroupPersistence groupPersistence)
    {
        this.groupPersistence = groupPersistence;
    }

    /**
     * 
     * @return the group URI prefix associated with this service
     */
    public String getGroupUriPrefix()
    {
        return uriGroupPrefix;
    }

    /**
     * Set Group URI Prefix of the service
     * 
     * @param uriGroupPrefix    The GMS URI Prefix for Group IDs.
     */
    public void setGroupUriPrefix(String uriGroupPrefix)
    {
        this.uriGroupPrefix = uriGroupPrefix;
    }
}
