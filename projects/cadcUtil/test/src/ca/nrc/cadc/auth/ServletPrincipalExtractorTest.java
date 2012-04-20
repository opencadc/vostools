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
 * 4/20/12 - 1:33 PM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.auth;

import javax.security.auth.x500.X500Principal;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.junit.Test;

import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;


public class ServletPrincipalExtractorTest
        extends PrincipalExtractorTest<ServletPrincipalExtractor>
{
    private final HttpServletRequest mockRequest =
            createMock(HttpServletRequest.class);

    @Test
    public void addHTTPPrincipal() throws Exception
    {
        setTestSubject(new ServletPrincipalExtractor()
        {
            /**
             * Obtain this Principal Extractor's HTTP Request.
             *
             * @return HttpServletRequest instance.
             */
            @Override
            protected HttpServletRequest getRequest()
            {
                return getMockRequest();
            }
        });

        expect(getMockRequest().getRemoteUser()).andReturn(null).once();

        replay(getMockRequest());

        getTestSubject().addHTTPPrincipal();

        assertTrue("Should have no principals.",
                   getTestSubject().getPrincipals().isEmpty());

        verify(getMockRequest());


        //
        // TEST 2
        reset(getMockRequest());

        expect(getMockRequest().getRemoteUser()).andReturn("TESTUSER").once();

        replay(getMockRequest());

        getTestSubject().addHTTPPrincipal();

        assertEquals("Should have one HTTP principal.", 1,
                     getTestSubject().getPrincipals().size());

        verify(getMockRequest());
    }

    @Test
    public void addX500Principal() throws Exception
    {
        setTestSubject(new ServletPrincipalExtractor()
        {
            /**
             * Obtain this Principal Extractor's HTTP Request.
             *
             * @return HttpServletRequest instance.
             */
            @Override
            protected HttpServletRequest getRequest()
            {
                return getMockRequest();
            }
        });

        expect(getMockRequest().getAttribute(
                "javax.servlet.request.X509Certificate")).andReturn(null).
                once();

        replay(getMockRequest());

        getTestSubject().addX500Principal();

        assertTrue("Should have no principals.",
                   getTestSubject().getPrincipals().isEmpty());

        verify(getMockRequest());


        //
        // TEST 2
        reset(getMockRequest());

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


        expect(getMockRequest().getAttribute(
                "javax.servlet.request.X509Certificate")).andReturn(
                certificates1).once();

        expect(mockCertificate.getNotAfter()).andReturn(notAfterDate).once();
        expect(mockCertificate.getSubjectX500Principal()).
                andReturn(subjectX500Principal).once();
        expect(mockCertificate.getIssuerX500Principal()).andReturn(
                issuerX500Principal).once();

        replay(getMockRequest(), mockCertificate);

        getTestSubject().addX500Principal();

        assertEquals("Should have one HTTP principal.", 1,
                     getTestSubject().getPrincipals().size());

        verify(getMockRequest(), mockCertificate);
    }

    @Test
    public void addCookiePrincipal() throws Exception
    {
        setTestSubject(new ServletPrincipalExtractor()
        {
            /**
             * Obtain this Principal Extractor's HTTP Request.
             *
             * @return HttpServletRequest instance.
             */
            @Override
            protected HttpServletRequest getRequest()
            {
                return getMockRequest();
            }
        });

        expect(getMockRequest().getCookies()).andReturn(null).once();

        replay(getMockRequest());

        getTestSubject().addCookiePrincipal();

        assertTrue("Should have no principals.",
                   getTestSubject().getPrincipals().isEmpty());

        verify(getMockRequest());


        //
        // TEST 2
        reset(getMockRequest());

        expect(getMockRequest().getCookies()).andReturn(
                new Cookie[]
                {
                        new Cookie("CADC", "MYVALUE"),
                        new Cookie("CADC_SSO",
                                   "username=TESTUSER|sessionID=88|token=AAABBB")
                }).once();

        replay(getMockRequest());

        getTestSubject().addCookiePrincipal();

        assertEquals("Should have one cookie principal.", 1,
                     getTestSubject().getPrincipals().size());

        verify(getMockRequest());
    }


    public HttpServletRequest getMockRequest()
    {
        return mockRequest;
    }
}
