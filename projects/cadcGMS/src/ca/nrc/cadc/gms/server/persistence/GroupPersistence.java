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
package ca.nrc.cadc.gms.server.persistence;

import java.net.URI;
import java.util.Collection;
import java.util.Map;

import ca.nrc.cadc.gms.Group;
import ca.nrc.cadc.gms.GroupNotExistsException;
import ca.nrc.cadc.gms.InvalidGroupException;

public interface GroupPersistence
{
    /**
     * Obtain a Group with the given Group ID.
     * 
     * @param groupID
     *            The Group unique ID.
     * @return A Group instance, or null if none found.
     */
    Group getGroup(final URI groupID) throws InvalidGroupException;

    /**
     * Creates a group.
     * 
     * @param group
     *            to create
     * @return created group
     */
    Group putGroup(final Group group) throws InvalidGroupException;

    /**
     * Deletes a group.
     * 
     * @param groupID
     *            Group ID to delete
     */
    void deleteGroup(final URI groupID) throws InvalidGroupException;

    /**
     * Obtain a Collection of Groups that fit the given query.
     *
     * Example:
     *   {[ivo://ivoa.net/gms#owner_dn] [CN=CADC OPS,OU=hia.nrc.ca,O=Grid,C=CA,CN=myCADCusername]}
     *
     * Where the IVOA GMS key is ivo://ivoa.net/gms#owner_dn,
     * and the value is CN=CADC OPS,OU=hia.nrc.ca,O=Grid,C=CA,CN=myCADCusername  
     *
     * @param criteria      The Criteria to search on.
     * @return      Collection of Groups matching the query, or empty
     *              Collection.  Never null.
     * @see ca.nrc.cadc.gms.GmsConsts
     */
    Collection<Group> getGroups(final Map<String, String> criteria) throws InvalidGroupException;
    
    /**
     * Obtain a GMS Group, through the basic GroupInfo from the persistence layer.  
     * GroupInfo does not contain member info or permission info. 
     * 
     * @param groupID ID of the group 
     * @return GMS group based on GroupInfo
     * @throws GroupNotExistsException, if group by this name does not exist
     */
    public Group getGroupInfo(final URI groupID) throws GroupNotExistsException;

    /**
     * Check whether the provided user is a member of the provided group
     * @param groupID  Archive Key of group
     * @param userDN Distinguished Name of user
     * @return true or false
     */
    public boolean isMember(final URI groupID, final String userDN);
    
}
