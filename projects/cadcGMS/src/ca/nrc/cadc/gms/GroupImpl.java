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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import ca.nrc.cadc.auth.AuthenticationUtil;

/**
 * Default implementation of a Group.
 */
public class GroupImpl implements Group
{
    // the group uri (unique ID)
    protected URI groupID;

    // group' s properties
    protected List<ElemProperty> properties;

    // group's members
    private Collection<User> members;

    // groups with read access to this group
    private Set<URI> groupsRead = new HashSet<URI>();

    // groups with read and write access to this group
    private Set<URI> groupsWrite = new HashSet<URI>();;

    /**
     * Default empty constructor.
     */
    public GroupImpl()
    {
        setMembers(new HashSet<User>());
        properties = new ArrayList<ElemProperty>();
    }

    /**
     * Full constructor.
     * 
     * @param groupID
     *            The new Group ID, if already generated/known.
     */
    public GroupImpl(final URI groupID)
    {
        this();
        this.groupID = groupID;
    }

    /**
     * Obtain this Group's unique id (URI).
     * 
     * @return String group URI.
     */
    public URI getID()
    {
        return groupID;
    }

    /**
     * 
     * @return properties associated with a group
     */
    public List<ElemProperty> getProperties()
    {
        return properties;
    }

    /**
     * Sets the properties associated with a group.
     * 
     * @param elemProperties
     *            new properties for the group
     */
    public void setProperties(List<ElemProperty> elemProperties)
    {
        properties = elemProperties;
    }

    /**
     * Add the given property to this Group's properties.
     * 
     * @param property
     *            The Property to add. Null is not acceptable.
     * @throws InvalidPropertyException
     *             If the given property is null or does not conform.
     */
    public void addProperty(final ElemProperty property)
            throws InvalidPropertyException
    {
        if (property == null)
        {
            throw new InvalidPropertyException(
                    "No null properties allowed.");
        }

        getProperties().add(property);
    }

    /**
     * Obtain a Property by its unique property URI.
     * 
     * @param propertyURI
     *            The URI key to search on. Null values will return null.
     * @return The ElemProperty found, or null if none found.
     */
    public ElemProperty getProperty(String propertyURI)
    {
        ElemProperty epRtn = null;
        if (propertyURI != null)
        {
            List<ElemProperty> epList = getProperties();
            if (epList != null)
            {
                for (ElemProperty ep : epList)
                {
                    if (propertyURI.equals(ep.getPropertyURI()))
                    {
                        epRtn = ep;
                        break;
                    }
                }
            }
        }
        return epRtn;
    }

    /**
     * Add the given property to this Group's properties.
     * 
     * @param property
     *            The Property to add. Null is not acceptable.
     * @throws ca.nrc.cadc.gms.InvalidPropertyException
     *             If the given property is null or does not exist in this
     *             Group.
     */
    public void removeProperty(final ElemProperty property)
            throws InvalidPropertyException
    {
        if ((property == null) || !getProperties().contains(property))
        {
            throw new InvalidPropertyException(
                    "No null properties allowed.");
        }

        getProperties().remove(property);
    }

    /**
     * Add the given property to this Group's properties.
     * 
     * @param propertyURI
     *            The URI (name) of the property to look for.
     * @return True if the property with the given URI exists, false
     *         otherwise.
     * @throws InvalidPropertyException
     *             If the given property URI is null or does not conform.
     */
    public boolean hasProperty(final String propertyURI)
            throws InvalidPropertyException
    {
        if (propertyURI == null)
        {
            throw new InvalidPropertyException(
                    "No null properties allowed.");
        }

        for (final ElemProperty property : getProperties())
        {
            if (property.getPropertyURI().equals(propertyURI))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Add the given User as a member of this Group.
     * 
     * @param newMember
     *            The new user. Null values and duplicates are not
     *            tolerated.
     * @throws ca.nrc.cadc.gms.InvalidMemberException
     *             If the given Member cannot be added.
     */
    public void addMember(final User newMember)
            throws InvalidMemberException
    {
        if (newMember == null)
        {
            throw new InvalidMemberException("Unable to add NULL Member");
        }
        else if (getMembers().contains((newMember)))
        {
            throw new InvalidMemberException(String.format(
                    "Member %s already exists.", newMember.getID()));
        }
        else
        {
            this.members.add(newMember);
        }
    }

    /**
     * Remove the given User member from this Group.
     * 
     * @param memberID
     *            The member to remove. Null values and non-existent
     *            members are not tolerated.
     * @throws InvalidMemberException
     *             If the given Member cannot be removed.
     */
    public void removeMember(final X500Principal memberID)
            throws InvalidMemberException
    {
        if (memberID == null)
        {
            throw new InvalidMemberException(
                    "Unable to remove NULL Member");
        }
        else
        {
            for (User member : getMembers())
            {
                if (AuthenticationUtil.equals(memberID, member.getID()))
                {
                    members.remove(member);
                    return;
                }
            }
            // member not found
            throw new InvalidMemberException(String.format(
                    "Member %s does not exist.", memberID));
        }
    }

    /**
     * Obtain whether the given user is a member of this Group.
     * 
     * @param userID
     *            The ID of User to check for. Null values will not be
     *            tolerated.
     * @return True if they are a member, False otherwise.
     * @throws ca.nrc.cadc.gms.InvalidMemberException
     *             If the given User cannot be used to check (i.e. null).
     */
    public boolean hasMember(final X500Principal userID)
            throws InvalidMemberException
    {
        if (userID == null)
        {
            throw new InvalidMemberException(
                    "Unable to check NULL Member");
        }
        else
        {
            for (User member : getMembers())
            {
                if (AuthenticationUtil.equals(userID, member.getID()))
                {
                    return true;
                }
            }
            // not found
            return false;
        }
    }

    /**
     * Obtain all of the Members of this Group.
     * 
     * @return Collection of User instances, or empty Collection. This
     *         will never return null.
     */
    public Collection<User> getMembers()
    {
        return Collections.unmodifiableCollection(members);
    }

    protected void setMembers(final Collection<User> members)
    {
        this.members = members;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }

        if ((o == null) || (getClass() != o.getClass()))
        {
            return false;
        }

        final GroupImpl group = (GroupImpl) o;

        return !(groupID != null ? !groupID.equals(group.groupID)
                : group.groupID != null);
    }

    @Override
    public int hashCode()
    {
        return groupID != null ? groupID.hashCode() : 0;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ca.nrc.cadc.gms.Group#getGroupsRead()
     */
    @Override
    public Set<URI> getGroupsRead()
    {
        return Collections.unmodifiableSet(groupsRead);
    }

    /*
     * (non-Javadoc)
     * 
     * @see ca.nrc.cadc.gms.Group#addGroupRead(java.net.URI)
     */
    @Override
    public void addGroupRead(URI groupID)
    {
        groupsRead.add(groupID);
    }

    /*
     * (non-Javadoc)
     * 
     * @see ca.nrc.cadc.gms.Group#removeGroupRead(java.net.URI)
     */
    @Override
    public void removeGroupRead(URI groupID)
    {
        groupsRead.remove(groupID);
    }

    /*
     * (non-Javadoc)
     * 
     * @see ca.nrc.cadc.gms.Group#clearGroupsRead()
     */
    @Override
    public void clearGroupsRead()
    {
        groupsRead.clear();
    }

    /*
     * (non-Javadoc)
     * 
     * @see ca.nrc.cadc.gms.Group#getGroupsWrite()
     */
    @Override
    public Set<URI> getGroupsWrite()
    {
        return Collections.unmodifiableSet(groupsWrite);
    }

    /*
     * (non-Javadoc)
     * 
     * @see ca.nrc.cadc.gms.Group#addGroupWrite(java.net.URI)
     */
    @Override
    public void addGroupWrite(URI groupID)
    {
        groupsWrite.add(groupID);
    }

    /*
     * (non-Javadoc)
     * 
     * @see ca.nrc.cadc.gms.Group#removeGroupWrite(java.net.URI)
     */
    @Override
    public void removeGroupWrite(URI groupID)
    {
        groupsWrite.remove(groupID);
    }

    /*
     * @see ca.nrc.cadc.gms.Group#clearGroupsWrite()
     */
    @Override
    public void clearGroupsWrite()
    {
        groupsWrite.clear();
    }
}
