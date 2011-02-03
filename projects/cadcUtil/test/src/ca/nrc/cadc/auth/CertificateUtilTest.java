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

package ca.nrc.cadc.auth;


import java.security.Principal;

import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;

import org.apache.log4j.Level;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.util.ArgumentMap;
import ca.nrc.cadc.util.Log4jInit;

/**
 * @author zhangsa
 *
 */
public class CertificateUtilTest
{

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc.auth", Level.INFO);
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

    @Test
    public void testInitSubject1() 
    {
        String[] args = {
                "--cert=/usr/cadc/dev/admin/test-certificates/x509_CADCAnontest1.crt",
                "--key=/usr/cadc/dev/admin/test-certificates/x509_CADCAnontest1.key"
                };
                
        ArgumentMap aMap = new ArgumentMap(args);
        Subject subject = CertCmdArgUtil.initSubject(aMap);
        
        boolean xpLoaded = false;
        for (Principal p : subject.getPrincipals() )
        {
            if (p instanceof X500Principal)
                xpLoaded = true;
        }
        
        Assert.assertTrue(xpLoaded);
    }

    @Test
    public void testInitSubject2() 
    {
        String[] args = {
                "--cert=/usr/cadc/dev/admin/test-certificates/x509_CADCAnontest1.pem"
                };
                
        ArgumentMap aMap = new ArgumentMap(args);
        Subject subject = CertCmdArgUtil.initSubject(aMap);
        
        boolean xpLoaded = false;
        for (Principal p : subject.getPrincipals() )
        {
            if (p instanceof X500Principal)
                xpLoaded = true;
        }
        
        Assert.assertTrue(xpLoaded);
    }

    @Test
    public void testInitSubject3() 
    {
        String[] args = {
                "--cert=/home/cadc/zhangsa/.globus/myproxy.pem.not.exist"
                };
                
        ArgumentMap aMap = new ArgumentMap(args);
        boolean exThrown = false;
        try {
            Subject subject = CertCmdArgUtil.initSubject(aMap);
        } catch (IllegalArgumentException ex)
        {
            exThrown = true;
        }
        
        Assert.assertTrue(exThrown);
    }
}
