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
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

/**
 * A Group that can hold members.
 */
public interface Group
{
    /**
     * Obtain this Group's unique identifier.
     * 
     * @return String group ID.
     */
    URI getID();

    /**
     * Add the given User as a member of this Group.
     * 
     * @param newMember
     *            The new user. Null values and duplicates are not
     *            tolerated.
     * @throws InvalidMemberException
     *             If the given Member cannot be added.
     */
    void addMember(final User newMember) throws InvalidMemberException;

    /**
     * Remove the given User member from this Group.
     * 
     * @param memberID
     *            The ID of member to remove. Null values and non-existent
     *            members are not tolerated.
     * @throws InvalidMemberException
     *             If the given Member cannot be removed.
     */
    void removeMember(final X500Principal memberID)
            throws InvalidMemberException;

    /**
     * Obtain whether the given user is a member of this Group.
     * 
     * @param userID
     *            The ID of User to check for. Null values will not be
     *            tolerated.
     * @return True if they are a member, False otherwise.
     * @throws InvalidMemberException
     *             If the given User cannot be used to check (i.e. null).
     */
    boolean hasMember(final X500Principal userID)
            throws InvalidMemberException;

    /**
     * Obtain all of the Members of this Group.
     * 
     * @return Collection of User instances, or empty Collection. This
     *         will never return null.
     */
    Collection<User> getMembers();
    
    /**
     * Obtain all of the Groups with read permission to this Group.
     * 
     * @return Set of group URIs or empty Collection. This
     *         will never return null.
     */
    Set<URI> getGroupsRead();
    
    /**
     * Add a new group to the groups with read permissions
     * @param groupID
     */
    void addGroupRead(URI groupID);
    
    /**
     * Remove a group from the groups with read permissions
     * @param groupID
     */
    void removeGroupRead(URI groupID);
    
    /**
     * Clear the groups read list
     */    
    void clearGroupsRead();
    
    
    /**
     * Obtain all of the Groups with read and write permissions to this Group.
     * 
     * @return Set of group URIs or empty Collection. This
     *         will never return null.
     */
    Set<URI> getGroupsWrite();
    
    /**
     * Add a new group to the groups with write permissions
     * @param groupID
     */
    void addGroupWrite(URI groupID);
    
    /**
     * Remove a group from the groups with write permissions
     * @param groupID
     */
    void removeGroupWrite(URI groupID);
    
    /**
     * Clear the groups write list
     */    
    void clearGroupsWrite();
    
    /**
     * 
     * @return properties associated with a group such as description or
     *         other optional properties.
     */
    List<ElemProperty> getProperties();


    /**
     * Obtain a Property by its unique property URI.
     *
     * @param propertyURI       The URI key to search on.  Null values will
     *                          return null.
     * @return              The ElemProperty found, or null if none found.
     */
    ElemProperty getProperty(final String propertyURI);

    /**
     * Add the given property to this Group's properties.
     *
     * @param property  The Property to add.  Null is not acceptable.
     * @throws InvalidPropertyException  If the given property is null or does
     *                                  not conform.
     */
    void addProperty(final ElemProperty property)
            throws InvalidPropertyException;

    /**
     * Sets the properties associated with a group.
     * 
     * @param elemProperties
     *            new properties for the group
     */
    void setProperties(List<ElemProperty> elemProperties);

    /**
     * Add the given property to this Group's properties.
     *
     * @param property  The Property to add.  Null is not acceptable.
     * @throws InvalidPropertyException  If the given property is null or does
     *                                  not exist in this Group.
     */
    void removeProperty(final ElemProperty property)
            throws InvalidPropertyException;

    /**
     * Add the given property to this Group's properties.
     *
     * @param propertyURI  The URI (name) of the property to look for.
     * @return      True if the property with the given URI exists, false
     *              otherwise.
     * @throws InvalidPropertyException  If the given property URI is null or
     *                                   does not conform.
     */
    boolean hasProperty(final String propertyURI)
            throws InvalidPropertyException;
}
