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

package ca.nrc.cadc.vos;

import java.net.URI;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import ca.nrc.cadc.util.Log4jInit;

/**
 *
 * @author pdowler
 */
public class VOSURITest 
{
    private static Logger log = Logger.getLogger(VOSURITest.class);

    String AUTHORITY = "cadc.nrc.ca!vospace";
    String SERVICE = "ivo://cadc.nrc.ca/vospace";

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.vos", Level.INFO);
    }

    @Test
    public void testInvalidScheme()
    {
        // VOSURI(URI)
        try
        {
            URI abc = new URI("abc://" + AUTHORITY + "container/data");
            VOSURI vos = new VOSURI(abc);
            Assert.fail("expected IllegalArgumentException, created: " + vos);
        }
        catch(IllegalArgumentException expected) { }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }

        // VOSURI(String) convenience method
        try
        {
            VOSURI vos = new VOSURI("abc://" + AUTHORITY + "container/data");
            Assert.fail("expected IllegalArgumentException, created: " + vos);
        }
        catch(IllegalArgumentException expected) { }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testAuthority()
    {
        try
        {
            String base = "vos://" + AUTHORITY;
            URI uri = new URI(base);
            VOSURI vos = new VOSURI(uri);
            Assert.assertEquals(AUTHORITY, vos.getAuthority());

            uri = new URI(base + "/foo");
            vos = new VOSURI(uri);
            Assert.assertEquals(AUTHORITY, vos.getAuthority());

            uri = new URI(base + "/");
            vos = new VOSURI(uri);
            Assert.assertEquals(AUTHORITY, vos.getAuthority());

            uri = new URI(base + "/foo#bar");
            vos = new VOSURI(uri);
            Assert.assertEquals(AUTHORITY, vos.getAuthority());

            uri = new URI(base + "/foo?bar");
            vos = new VOSURI(uri);
            Assert.assertEquals(AUTHORITY, vos.getAuthority());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testServiceURI()
    {
        try
        {
            URI srvURI = new URI(SERVICE);
            String base = "vos://" + AUTHORITY;
            
            URI uri = new URI(base);
            VOSURI vos = new VOSURI(uri);
            Assert.assertEquals(srvURI, vos.getServiceURI());

            uri = new URI(base + "/foo");
            vos = new VOSURI(uri);
            Assert.assertEquals(srvURI, vos.getServiceURI());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testPath()
    {
        try
        {
            String base = "vos://" + AUTHORITY;
            String path = "/container/data";

            URI uri = new URI(base + path);
            VOSURI vos = new VOSURI(uri);
            Assert.assertEquals(path, vos.getPath());

            uri = new URI(base);
            vos = new VOSURI(uri);
            Assert.assertNotNull(vos.getPath());
            Assert.assertEquals(vos.getPath().length(), 0);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testTildeSeparator()
    {
        try
        {
            URI srvURI = new URI(SERVICE);
            String base = "vos://cadc.nrc.ca~vospace";
            String path = "/container/data";
            URI uri = new URI(base + path);
            VOSURI vos = new VOSURI(uri);

            Assert.assertEquals(srvURI, vos.getServiceURI());
            Assert.assertEquals(path, vos.getPath());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testMultipleSeparators()
    {
        try
        {
            URI srvURI = new URI("ivo://cadc.nrc.ca/vospace/666");

            String base;
            String path = "/container/data";

            base = "vos://cadc.nrc.ca!vospace!666";
            VOSURI vos = new VOSURI(new URI(base + path));
            Assert.assertEquals(srvURI, vos.getServiceURI());
            Assert.assertEquals(path, vos.getPath());

            base = "vos://cadc.nrc.ca~vospace~666";
            vos = vos = new VOSURI(new URI(base + path));
            Assert.assertEquals(srvURI, vos.getServiceURI());
            Assert.assertEquals(path, vos.getPath());

            base = "vos://cadc.nrc.ca!vospace~666";
            vos = vos = new VOSURI(new URI(base + path));
            Assert.assertEquals(srvURI, vos.getServiceURI());
            Assert.assertEquals(path, vos.getPath());

            base = "vos://cadc.nrc.ca~vospace!666";
            vos = vos = new VOSURI(new URI(base + path));
            Assert.assertEquals(srvURI, vos.getServiceURI());
            Assert.assertEquals(path, vos.getPath());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testEquals()
    {
        try
        {
            String[] base = { "vos://cadc.nrc.ca!foo", "vos://ca.nrc.cadc!foo", "vos://cadc.nrc.ca!bar" };
            String[] path = { "/foo", "/bar" };

            for (String b1 : base)
               for (String b2 : base)
                   for (String p1 : path)
                       for (String p2 : path)
                       {
                            VOSURI vos1 = new VOSURI(new URI(b1 + p1));
                            VOSURI vos2 = new VOSURI(new URI(b2 + p2));
                            if (b1 == b2 && p1 == p2)
                               Assert.assertEquals(vos1, vos2);
                            else
                                junit.framework.Assert.assertNotSame(vos1, vos2);
                       }
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    @Test
    public void testTrailingSlash()
    {
        try
        {
            VOSURI uri1 = new VOSURI("vos://cadc.nrc.ca!foo/bar");
            VOSURI uri2 = new VOSURI("vos://cadc.nrc.ca!foo/bar/");
            Assert.assertEquals("URIs should be equal", uri1, uri2);
            Assert.assertEquals("Paths should be equal", uri1.getPath(), uri2.getPath());
            Assert.assertEquals("Names should be equal", uri1.getName(), uri2.getName());
            Assert.assertEquals("Parents should be equal", uri1.getParent(), uri2.getParent());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    

    @Test
    public void testBangTildeEquivalent()
    {
        try
        {
            URI srvURI = new URI(SERVICE);

            String bang  = "vos://cadc.nrc.ca!vospace";
            String tilde = "vos://cadc.nrc.ca~vospace";
            String path = "/container/data";

            VOSURI vosBang = new VOSURI(new URI(bang + path));
            VOSURI vosTilde = new VOSURI(new URI(tilde + path));
            Assert.assertNotSame(bang, tilde);
            Assert.assertTrue("! and ~ are equivalent in uri", vosBang.equals(vosTilde));

            vosBang = new VOSURI(new URI(bang + path + "?foo"));
            vosTilde = new VOSURI(new URI(tilde + path + "?foo"));
            Assert.assertNotSame(bang, tilde);
            Assert.assertTrue("! and ~ are equivalent in uri", vosBang.equals(vosTilde));

            vosBang = new VOSURI(new URI(bang + path + "#foo"));
            vosTilde = new VOSURI(new URI(tilde + path + "#foo"));
            Assert.assertNotSame(bang, tilde);
            Assert.assertTrue("! and ~ are equivalent in uri", vosBang.equals(vosTilde));
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testGetParent()
    {
        try
        {
            String bang  = "vos://cadc.nrc.ca!vospace";
            String tilde = "vos://cadc.nrc.ca~vospace";
            String path = "/container/data";

            VOSURI vosBang = new VOSURI(new URI(bang + path));
            VOSURI vosTilde = new VOSURI(new URI(tilde + path));
            Assert.assertNotSame(bang, tilde);

            VOSURI parentBang = vosBang.getParentURI();
            Assert.assertNotNull(parentBang);
            Assert.assertEquals(bang + "/container", parentBang.getURIObject().toASCIIString());

            VOSURI parentTilde = vosTilde.getParentURI();
            Assert.assertNotNull(parentTilde);
            Assert.assertEquals(tilde + "/container", parentTilde.getURIObject().toASCIIString());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testRoot()
    {
        try
        {
            String auth  = "cadc.nrc.ca!vospace";
            URI uri;
            VOSURI vos;

            uri = new URI("vos", auth, null, null, null);
            vos = new VOSURI(uri);
            Assert.assertTrue("null path in URI is root", vos.isRoot());

            uri = new URI("vos", auth, "", null, null);
            vos = new VOSURI(uri);
            Assert.assertTrue("0-length path in URI is root", vos.isRoot());

            uri = new URI("vos", auth, "/", null, null);
            vos = new VOSURI(uri);
            Assert.assertTrue("/ path in URI is root", vos.isRoot());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testGetParentRoot()
    {
        try
        {
            String bang  = "vos://cadc.nrc.ca!vospace";
            String tilde = "vos://cadc.nrc.ca~vospace";
            String path = "/path";

            VOSURI vosBang = new VOSURI(new URI(bang + path));
            VOSURI vosTilde = new VOSURI(new URI(tilde + path));
            Assert.assertNotSame(bang, tilde);

            VOSURI parentBang = vosBang.getParentURI();
            Assert.assertNotNull(parentBang);
            Assert.assertEquals(bang, parentBang.getURIObject().toASCIIString());

            VOSURI parentTilde = vosTilde.getParentURI();
            Assert.assertNotNull(parentTilde);
            Assert.assertEquals(tilde, parentTilde.getURIObject().toASCIIString());

            VOSURI root = new VOSURI(new URI(tilde));
            VOSURI noParent = root.getParentURI();
            Assert.assertNull(noParent);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
}
