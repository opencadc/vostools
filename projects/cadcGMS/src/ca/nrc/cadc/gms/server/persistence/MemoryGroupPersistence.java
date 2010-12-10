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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.security.auth.x500.X500Principal;

import ca.nrc.cadc.gms.ElemProperty;
import ca.nrc.cadc.gms.ElemPropertyImpl;
import ca.nrc.cadc.gms.GmsConsts;
import ca.nrc.cadc.gms.Group;
import ca.nrc.cadc.gms.GroupImpl;
import ca.nrc.cadc.gms.GroupNotExistsException;
import ca.nrc.cadc.gms.InvalidGroupException;
import ca.nrc.cadc.gms.UserImpl;

public class MemoryGroupPersistence implements GroupPersistence
{
    // The Database.
    private final ConcurrentMap<String, Group> GROUP_MAP = new ConcurrentHashMap<String, Group>();
    private String _groupName_;

    public MemoryGroupPersistence()
    {
        try
        {
            String dn2 = "CN=User2 ,OU=hia.nrc.ca,O=Grid,C=CA";
            final Group firstGroup = new GroupImpl(new URI("ivo://cadc.nrc.ca/gms/group#Test-Group1"));
            firstGroup.addMember(new UserImpl(new X500Principal("CN=User1 ,OU=hia.nrc.ca,O=Grid,C=CA")));

            final Group secondGroup = new GroupImpl(new URI("ivo://cadc.nrc.ca/gms/group#Test-Group2"));
            firstGroup.addMember(new UserImpl(new X500Principal(dn2)));

            final Group thirdGroup = new GroupImpl(new URI("ivo://cadc.nrc.ca/gms/group#Test-Group3"));
            firstGroup.addMember(new UserImpl(new X500Principal("CN=User3 ,OU=hia.nrc.ca,O=Grid,C=CA")));

            ElemProperty ep = new ElemPropertyImpl(GmsConsts.PROPERTY_OWNER_DN, dn2);
            List<ElemProperty> epList = new ArrayList<ElemProperty>();
            epList.add(ep);
            firstGroup.setProperties(epList);

            GROUP_MAP.put(firstGroup.getID().toString(), firstGroup);
            GROUP_MAP.put(Long.toString(99l), secondGroup);
            GROUP_MAP.put("MY TEST GROUP", thirdGroup);
        }
        catch (Exception e)
        {
            // Not worried about it here...
            e.printStackTrace();
        }
    }

    /**
     * Obtain a Group with the given Group ID.
     * 
     * @param groupID
     *            The Group unique ID.
     * @return A Group instance, or null if none found.
     * @throws InvalidGroupException
     *             if group not found
     */
    public Group getGroup(final URI groupID) throws InvalidGroupException
    {
        synchronized (GROUP_MAP)
        {
            if (GROUP_MAP.containsKey(groupID.toString()))
            {
                return GROUP_MAP.get(groupID.toString());
            }
            else
            {
                throw new InvalidGroupException("Group with ID " + groupID
                                                + " not found.");
            }
        }
    }

    /**
     * Creates a group.
     * 
     * @param group
     *            group to create
     * @return created group
     * @throws InvalidGroupException
     *             if group already exists
     */
    public Group putGroup(final Group group) throws InvalidGroupException
    {
        Group gr = group;
        if (group.getID() == null)
        {
            Random rand = new Random();
            // generate a random group ID
            int randomNo = rand.nextInt();

            try
            {
                gr = new GroupImpl(new URI("ivo://cadc.nrc.ca/gms/group#" + Integer.toString(randomNo)));
            }
            catch (URISyntaxException e)
            {
                throw new InvalidGroupException("Invalid group id");
            }
        }
        synchronized (GROUP_MAP)
        {

            if (GROUP_MAP.containsKey(gr.getID().toString()))
            {
                throw new InvalidGroupException("Group with ID " + gr.getID() + " already exists.");
            }
            else
            {
                GROUP_MAP.put(gr.getID().toString(), gr);
                return GROUP_MAP.get(gr.getID().toString());
            }
        }
    }

    /**
     * Deletes a group.
     * 
     * @param groupID
     *            of group to delete
     */
    public void deleteGroup(final URI groupID) throws InvalidGroupException
    {

        if (!GROUP_MAP.containsKey(groupID.toString()))
        {
            throw new InvalidGroupException("Group with ID " + groupID + " doeas not exists so it cannot be deleted.");
        }
        else
        {
            GROUP_MAP.remove(groupID.toString());
        }
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
     * @see ca.nrc.cadc.gms.GmsConsts
     */
    public Collection<Group> getGroups(Map<String, String> criteria)
    {
        Set<Group> groupRtn = new HashSet<Group>();
        Set<String> keys = criteria.keySet();
        if (keys.contains(GmsConsts.PROPERTY_OWNER_DN))
        {
            String dn = criteria.get(GmsConsts.PROPERTY_OWNER_DN);
            synchronized (GROUP_MAP)
            {
                Collection<Group> groups = getAllGroups();
                for (Group group : groups)
                {
                    ElemProperty ep = group.getProperty(GmsConsts.PROPERTY_OWNER_DN);
                    
                    if (ep != null && dn.equals(ep.getPropertyValue()))
                    {
                        groupRtn.add(group);
                    }
                }
            }
        }
        return groupRtn;
    }
    
    protected Collection<Group> getAllGroups()
    {
        return GROUP_MAP.values();
    }

    /* (non-Javadoc)
     * @see ca.nrc.cadc.gms.server.persistence.GroupPersistence#getGroupInfo(java.net.URI)
     */
    @Override
    public Group getGroupInfo(URI groupID) throws GroupNotExistsException
    {
        throw new RuntimeException("Not supported in MemoryGroupPersistence.");
    }

    /* (non-Javadoc)
     * @see ca.nrc.cadc.gms.server.persistence.GroupPersistence#isMember(java.net.URI, java.lang.String)
     */
    @Override
    public boolean isMember(URI groupID, String userDN)
    {
        throw new RuntimeException("Not supported in MemoryGroupPersistence.");
    }
}
