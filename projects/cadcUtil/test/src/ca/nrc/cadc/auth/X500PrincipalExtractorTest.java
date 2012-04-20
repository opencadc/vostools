/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2012.                         (c) 2012.
 * National Research Council            Conseil national de recherches
 * Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 * All rights reserved                  Tous droits reserves
 *
 * NRC disclaims any warranties         Le CNRC denie toute garantie
 * expressed, implied, or statu-        enoncee, implicite ou legale,
 * tory, of any kind with respect       de quelque nature que se soit,
 * to the software, including           concernant le logiciel, y com-
 * without limitation any war-          pris sans restriction toute
 * ranty of merchantability or          garantie de valeur marchande
 * fitness for a particular pur-        ou de pertinence pour un usage
 * pose.  NRC shall not be liable       particulier.  Le CNRC ne
 * in any event for any damages,        pourra en aucun cas etre tenu
 * whether direct or indirect,          responsable de tout dommage,
 * special or general, consequen-       direct ou indirect, particul-
 * tial or incidental, arising          ier ou general, accessoire ou
 * from the use of the software.        fortuit, resultant de l'utili-
 *                                      sation du logiciel.
 *
 *
 * @author jenkinsd
 * 4/20/12 - 12:07 PM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.auth;


import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.easymock.EasyMock.*;


public class X500PrincipalExtractorTest
        extends PrincipalExtractorTest<X500PrincipalExtractor>
{
    @Test
    public void createNullX500Principal() throws Exception
    {
        final X509Certificate[] certificates1 = new X509Certificate[0];

        setTestSubject(new X500PrincipalExtractor(certificates1));

        assertNull("Should be null.", getTestSubject().createX500Principal());
    }

    @Test
    public void createGoodX500Principal() throws Exception
    {
        final Calendar notAfterCal = Calendar.getInstance();
        notAfterCal.set(1977, Calendar.NOVEMBER, 25, 3, 21, 0);
        notAfterCal.set(Calendar.MILLISECOND, 0);

        final X500Principal subjectX500Principal =
                new X500Principal("CN=CN1,O=O1");
        final X500Principal issuerX500Principal =
                new X500Principal("CN=CN2,O=O2");
        final Date notAfterDate = notAfterCal.getTime();
        final X509Certificate mockCertificate =
                createMock(X509Certificate.class);

        final X509Certificate[] certificates1 =
                new X509Certificate[]
                        {
                                mockCertificate
                        };

        setTestSubject(new X500PrincipalExtractor(certificates1));

        expect(mockCertificate.getNotAfter()).andReturn(notAfterDate).once();
        expect(mockCertificate.getSubjectX500Principal()).
                andReturn(subjectX500Principal).once();
        expect(mockCertificate.getIssuerX500Principal()).andReturn(
                issuerX500Principal).once();

        replay(mockCertificate);

        final X500Principal resultX500Principal =
                getTestSubject().createX500Principal();

        assertNotNull("Should not be null.", resultX500Principal);
        assertEquals("Name should match subject.",
                     "CN=cn1,O=o1", resultX500Principal.getName());

        verify(mockCertificate);
    }
}
