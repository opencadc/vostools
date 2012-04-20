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
 * 4/20/12 - 11:07 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.auth;

import ca.nrc.cadc.util.ArrayUtil;
import ca.nrc.cadc.util.StringUtil;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


/**
 * Implementation of a Principal Extractor from an HttpServletRequest.
 */
public class ServletPrincipalExtractor implements PrincipalExtractor
{
    private final HttpServletRequest request;
    private final Set<Principal> principals;


    /**
     * Hidden no-arg constructor.
     */
    ServletPrincipalExtractor()
    {
        this.request = null;
        this.principals = new HashSet<Principal>();
    }

    /**
     * Constructor to create Principals from the given Servlet Request.
     *
     * @param req       The HTTP Request.
     */
    public ServletPrincipalExtractor(final HttpServletRequest req)
    {
        this.principals = new HashSet<Principal>();
        this.request = req;

        addPrincipals();
    }


    /**
     * Add known principals.
     */
    protected void addPrincipals()
    {
        addCookiePrincipal();
        addHTTPPrincipal();
        addX500Principal();
    }

    /**
     * Add the cookie principal, if it exists.
     */
    protected void addCookiePrincipal()
    {
        final Cookie cookie = getCookie();

        if (cookie != null)
        {
            final SSOCookiePrincipalExtractor ssoCookiePrincipalExtractor =
                    new SSOCookiePrincipalExtractor(cookie.getValue());

            principals.addAll(ssoCookiePrincipalExtractor.getPrincipals());
        }
    }

    /**
     * Add the HTTP Principal, if it exists.
     */
    protected void addHTTPPrincipal()
    {
        final String httpUser = getRequest().getRemoteUser();

        if (StringUtil.hasText(httpUser))
        {
            final HTTPPrincipalExtractor httpPrincipalExtractor =
                    new HTTPPrincipalExtractor(httpUser);

            principals.addAll(httpPrincipalExtractor.getPrincipals());
        }
    }

    /**
     * Add the X500 Principal, if it exists.
     */
    protected void addX500Principal()
    {
        final X509Certificate[] requestCertificates =
                (X509Certificate[]) getRequest().getAttribute(
                        "javax.servlet.request.X509Certificate");

        if (!ArrayUtil.isEmpty(requestCertificates))
        {
            final X500PrincipalExtractor x500PrincipalExtractor =
                    new X500PrincipalExtractor(requestCertificates);

            principals.addAll(x500PrincipalExtractor.getPrincipals());
        }
    }

    /**
     * Obtain a Collection of Principals from this extractor.  This should be
     * immutable.
     *
     * @return Collection of Principal instances, or empty Collection.
     *         Never null.
     */
    @Override
    public Set<Principal> getPrincipals()
    {
        return Collections.unmodifiableSet(principals);
    }

    /**
     * Obtain this Principal Extractor's HTTP Request.
     *
     * @return      HttpServletRequest instance.
     */
    protected HttpServletRequest getRequest()
    {
        return request;
    }

    /**
     * Read in the pertinent cookie for this authentication.
     *
     * @return              Cookie, if present, or null if not.
     */
    protected Cookie getCookie()
    {
        final Cookie[] cookies = getRequest().getCookies();
        if (!ArrayUtil.isEmpty(cookies))
        {
            for (final Cookie cookie : cookies)
            {
                if (cookie.getName().equals(
                        SSOCookiePrincipalExtractor.COOKIE_NAME))
                {
                    return cookie;
                }
            }
        }

        return null;
    }
}
