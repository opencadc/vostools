/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2011.                            (c) 2011.
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
*  $Revision: 5 $
*
************************************************************************
*/

package ca.nrc.cadc.net;

import ca.nrc.cadc.auth.SSOCookieCredential;
import ca.nrc.cadc.util.Log4jInit;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;
import sun.net.www.protocol.http.HttpURLConnection;

import javax.security.auth.Subject;
import java.net.URL;
import java.security.PrivilegedAction;


/**
 *
 * @author pdowler
 */
public class HttpTransferTest 
{
    private static Logger log = Logger.getLogger(HttpTransferTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.net", Level.INFO);
    }

    @Test
    public void testBufferSize() throws Exception
    {
        log.debug("TEST: testBufferSize");
        try
        {
            String cur = System.getProperty(HttpTransfer.class.getName() + ".bufferSize");
            Assert.assertNull("test setup", cur);

            HttpTransfer trans = new TestDummy();
            Assert.assertEquals("default buffer size", HttpTransfer.DEFAULT_BUFFER_SIZE, trans.getBufferSize());

            trans.setBufferSize(12345);
            Assert.assertEquals("set buffer size", 12345, trans.getBufferSize());

            System.setProperty(HttpTransfer.class.getName() + ".bufferSize", "16384");
            trans = new TestDummy();
            Assert.assertEquals("system prop buffer size (bytes)", 16384, trans.getBufferSize());

            System.setProperty(HttpTransfer.class.getName() + ".bufferSize", "32k");
            trans = new TestDummy();
            Assert.assertEquals("system prop buffer size KB", 32*1024, trans.getBufferSize());

            System.setProperty(HttpTransfer.class.getName() + ".bufferSize", "2m");
            trans = new TestDummy();
            Assert.assertEquals("system prop buffer size MB", 2*1024*1024, trans.getBufferSize());

            // bad syntax -> default
            System.setProperty(HttpTransfer.class.getName() + ".bufferSize", "123d");
            trans = new TestDummy();
            Assert.assertEquals("system prop buffer size (invalid)", HttpTransfer.DEFAULT_BUFFER_SIZE, trans.getBufferSize());

        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void setRequestSSOCookie() throws Exception
    {
        final HttpTransfer testSubject = new HttpTransfer(true)
        {
            @Override
            public void run()
            {

            }
        };

        final Subject subject = new Subject();
        subject.getPublicCredentials().add(
                new SSOCookieCredential("CADC_SSO=VALUE_1", "en.host.com"));
        subject.getPublicCredentials().add(
                new SSOCookieCredential("CADC_SSO=VALUE_2", "fr.host.com"));
        final URL testURL =
                new URL("http://www.fr.host.com/my/path/to/file.txt");
        final HttpURLConnection mockConnection =
                EasyMock.createMock(HttpURLConnection.class);

        EasyMock.expect(mockConnection.getURL()).andReturn(testURL).atLeastOnce();

        mockConnection.setRequestProperty("Cookie", "CADC_SSO=VALUE_2");
        EasyMock.expectLastCall().once();

        EasyMock.replay(mockConnection);

        Subject.doAs(subject, new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                testSubject.setRequestSSOCookie(mockConnection);
                return null;
            }
        });

        EasyMock.verify(mockConnection);
    }

    private class TestDummy extends HttpTransfer
    {
        TestDummy() { super(true); }
        
        public void run()
        {
            throw new UnsupportedOperationException();
        }
        
    }
}
