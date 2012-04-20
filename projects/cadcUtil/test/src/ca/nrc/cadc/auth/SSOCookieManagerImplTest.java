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
 * 4/17/12 - 11:21 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.auth;


import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.junit.Test;

import java.util.Arrays;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;


public class SSOCookieManagerImplTest
{
    private SSOCookieManagerImpl testSubject;
    private HttpServletRequest mockRequest =
            createMock(HttpServletRequest.class);


    @Test
    public void readCookie() throws Exception
    {
        setTestSubject(new SSOCookieManagerImpl("TESTUSER", 88l));

        expect(getMockRequest().getCookies()).andReturn(null).once();

        replay(getMockRequest());
        final Cookie cookie1 = getTestSubject().getCookie(getMockRequest());

        assertNull("Cookie should be null.", cookie1);

        verify(getMockRequest());


        //
        // TEST 2
        reset(getMockRequest());
        expect(getMockRequest().getCookies()).andReturn(
                new Cookie[]
                {
                        new Cookie("CADC", "MYVALUE")
                }).times(2);

        replay(getMockRequest());
        final Cookie cookie2 = getTestSubject().getCookie(getMockRequest());

        assertNull("Cookie should be null.", cookie2);

        verify(getMockRequest());


        //
        // TEST 3
        reset(getMockRequest());
        expect(getMockRequest().getCookies()).andReturn(
                new Cookie[]
                {
                        new Cookie("CADC", "MYVALUE"),
                        new Cookie("CADC_Login",
                                   "username=TESTUSER|sessionID=88|token=AAABBB")
                }).times(2);

        replay(getMockRequest());
        final Cookie cookie3 = getTestSubject().getCookie(getMockRequest());

        assertNotNull("Cookie should not be null.", cookie3);

        verify(getMockRequest());
    }

    @Test
    public void parseCookieValue() throws Exception
    {
        final Cookie mockCookie = createMock(Cookie.class);

        expect(mockCookie.getValue()).andReturn(
                "username=TESTUSER|sessionID=88|token=AAABBB").once();

        replay(getMockRequest(), mockCookie);

        setTestSubject(new SSOCookieManagerImpl(getMockRequest(), null)
        {
            /**
             * Read in the pertinent cookie for this authentication.
             *
             * @param request The HTTP Servlet request.
             * @return Cookie, if present, or null if not.
             */
            @Override
            protected Cookie getCookie(final HttpServletRequest request)
            {
                return mockCookie;
            }
        });

        assertEquals("Username should be TESTUSER", "TESTUSER",
                     getTestSubject().getUsername());
        assertTrue("Token should be AAABBB",
                   Arrays.equals("AAABBB".toCharArray(),
                                 getTestSubject().getToken()));
        assertEquals("Session ID should be 88.", 88l,
                     getTestSubject().getSessionID());

        verify(getMockRequest(), mockCookie);
    }


    public SSOCookieManagerImpl getTestSubject()
    {
        return testSubject;
    }

    public void setTestSubject(final SSOCookieManagerImpl testSubject)
    {
        this.testSubject = testSubject;
    }

    public HttpServletRequest getMockRequest()
    {
        return mockRequest;
    }
}
