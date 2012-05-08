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
 * 4/20/12 - 12:49 PM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.uws.web.restlet;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;
import org.restlet.Request;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.ClientInfo;
import org.restlet.data.Cookie;
import org.restlet.engine.util.CookieSeries;
import org.restlet.util.Series;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.Test;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;


public class RestletPrincipalExtractorTest
{
    private RestletPrincipalExtractor testSubject;
    private final Request mockRequest = createMock(Request.class);


    @Test
    public void addCookiePrincipal() throws Exception
    {
        final Series<Cookie> requestCookies = new CookieSeries();

        setTestSubject(new RestletPrincipalExtractor()
        {
            @Override
            public Request getRequest()
            {
                return getMockRequest();
            }
        });

        expect(getMockRequest().getCookies()).andReturn(requestCookies).once();

        replay(getMockRequest());

        Set<Principal> ps = new HashSet<Principal>();
        getTestSubject().addCookiePrincipal(ps);

        assertTrue("Should have no principals.", ps.isEmpty());

        verify(getMockRequest());


        //
        // TEST 2
        reset(getMockRequest());

        requestCookies.add("CADC_SSO",
                           "username=TESTUSER|sessionID=88|token=TOKEN");

        expect(getMockRequest().getCookies()).andReturn(requestCookies).once();

        replay(getMockRequest());

        getTestSubject().addCookiePrincipal(ps);

        assertEquals("Should have one cookie principal.", 1,
                     ps.size());

        verify(getMockRequest());
    }

    @Test
    public void addHTTPPrincipal() throws Exception
    {
        setTestSubject(new RestletPrincipalExtractor()
        {
            @Override
            public Request getRequest()
            {
                return getMockRequest();
            }
        });

        final ClientInfo clientInfo = new ClientInfo();

        expect(getMockRequest().getChallengeResponse()).andReturn(null).once();
        expect(getMockRequest().getClientInfo()).andReturn(clientInfo).once();

        replay(getMockRequest());

        Set<Principal> ps = new HashSet<Principal>();

        getTestSubject().addHTTPPrincipal(ps);

        assertTrue("Should have no principals.", ps.isEmpty());

        verify(getMockRequest());


        //
        // TEST 2
        reset(getMockRequest());

        final ChallengeResponse challengeResponse =
                new ChallengeResponse(ChallengeScheme.HTTP_BASIC, "TESTUSER",
                                      "TESTPASS".toCharArray());
        expect(getMockRequest().getChallengeResponse()).andReturn(
                challengeResponse).times(2);

        replay(getMockRequest());

        getTestSubject().addHTTPPrincipal(ps);

        assertEquals("Should have one HTTP principal.", 1, ps.size());

        verify(getMockRequest());
    }

    @Test
    public void addX500Principal() throws Exception
    {
        setTestSubject(new RestletPrincipalExtractor()
        {
            @Override
            public Request getRequest()
            {
                return getMockRequest();
            }
        });

        final ConcurrentMap<String, Object> attributes =
                new ConcurrentHashMap<String, java.lang.Object>();

        expect(getMockRequest().getAttributes()).andReturn(attributes).once();

        replay(getMockRequest());

        Set<Principal> ps = new HashSet<Principal>();

        getTestSubject().addX500Principal(ps);

        assertTrue("Should have no principals.", ps.isEmpty());

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

        final Collection<X509Certificate> certificates1 =
                new ArrayList<X509Certificate>();

        certificates1.add(mockCertificate);

        attributes.put("org.restlet.https.clientCertificates", certificates1);
        expect(getMockRequest().getAttributes()).andReturn(attributes).once();

        expect(mockCertificate.getNotAfter()).andReturn(notAfterDate).once();
        expect(mockCertificate.getSubjectX500Principal()).
                andReturn(subjectX500Principal).once();
        expect(mockCertificate.getIssuerX500Principal()).andReturn(
                issuerX500Principal).once();

        replay(getMockRequest(), mockCertificate);

        getTestSubject().addX500Principal(ps);

        assertEquals("Should have one HTTP principal.", 1, ps.size());

        verify(getMockRequest(), mockCertificate);
    }


    protected RestletPrincipalExtractor getTestSubject()
    {
        return testSubject;
    }

    protected void setTestSubject(final RestletPrincipalExtractor testSubject)
    {
        this.testSubject = testSubject;
    }

    public Request getMockRequest()
    {
        return mockRequest;
    }
}
