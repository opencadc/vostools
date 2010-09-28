/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2009.                            (c) 2009.
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
*  $Revision: 4 $
*
************************************************************************
*/

package ca.nrc.cadc.gms.server.persistence;

import static org.junit.Assert.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import junit.framework.Assert;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.gms.ElemProperty;
import ca.nrc.cadc.gms.ElemPropertyImpl;
import ca.nrc.cadc.gms.GmsConsts;
import ca.nrc.cadc.gms.Group;
import ca.nrc.cadc.gms.GroupImpl;
import ca.nrc.cadc.gms.InvalidMemberException;
import ca.nrc.cadc.gms.UserImpl;

/**
 * @author zhangsa
 *
 */
public class MemoryGroupPersistenceTest
{

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterClass
    public static void tearDownAfterClass() throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception
    {
    }

    /**
     * Test method for {@link ca.nrc.cadc.gms.server.persistence.MemoryGroupPersistence#getGroups(java.util.Map)}.
     */
    @Test
    public void testGetGroups()
    {
        final Set<Group> ALL_GROUPS = new HashSet<Group>();
        String dn2 = "CN=User2 ,OU=hia.nrc.ca,O=Grid,C=CA";
        try {
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

        ALL_GROUPS.add(firstGroup);
        ALL_GROUPS.add(secondGroup);
        ALL_GROUPS.add(thirdGroup);
        } catch (Exception ex) {
            throw new RuntimeException("Error in preparing test env.");
        }
        
        boolean included = false;
        Map<String, String> map = new HashMap<String, String>();
        map.put(GmsConsts.PROPERTY_OWNER_DN, dn2);
        MemoryGroupPersistence mgp = new MemoryGroupPersistence()
        {
            @Override
            protected Collection<Group> getAllGroups()
            {
                return ALL_GROUPS;
            }
        };
        Collection<Group> groups = mgp.getGroups(map);
        if (groups != null)
        for (Group group : groups)
        {
            ElemProperty epRtn = group.getProperty(GmsConsts.PROPERTY_OWNER_DN);
            if (epRtn != null && dn2.equals(epRtn.getPropertyValue())) {
                included = true;
                break;
            }
        }
        Assert.assertTrue(included);
    }

}
