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
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import org.apache.log4j.Logger;


/**
 * Implementation of a Principal Extractor from an HttpServletRequest.
 */
public class ServletPrincipalExtractor implements PrincipalExtractor
{
    private static final Logger log = Logger.getLogger(ServletPrincipalExtractor.class);
    
    public static final String CERT_REQUEST_ATTRIBUTE = "javax.servlet.request.X509Certificate";
    private final HttpServletRequest request;

    private final X509CertificateChain chain;

    /**
     * Hidden no-arg constructor.
     */
    ServletPrincipalExtractor()
    {
        this.request = null;
        this.chain = null;
    }

    /**
     * Constructor to create Principals from the given Servlet Request.
     *
     * @param req       The HTTP Request.
     */
    public ServletPrincipalExtractor(final HttpServletRequest req)
    {
        this.request = req;
        X509Certificate[] ca = (X509Certificate[])
            request.getAttribute(CERT_REQUEST_ATTRIBUTE);
        if (!ArrayUtil.isEmpty(ca))
            chain = new X509CertificateChain(Arrays.asList(ca));
        else
            chain = null;
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
        Set<Principal> principals = new HashSet<Principal>();
        addPrincipals(principals);
        return principals;
    }

    public X509CertificateChain getCertificateChain()
    {
        return chain;
    }

    /**
     * Add known principals.
     */
    protected void addPrincipals(Set<Principal> principals)
    {
        addCookiePrincipal(principals);
        addHTTPPrincipal(principals);
        addX500Principal(principals);
    }

    /**
     * Add the cookie principal, if it exists.
     */
    protected void addCookiePrincipal(Set<Principal> principals)
    {
        Cookie[] cookies = getRequest().getCookies();
        if (cookies == null || ArrayUtil.isEmpty(cookies))
            return;
        
        for (Cookie cookie : cookies)
        {
            SSOCookieManager ssoCookieManager = new SSOCookieManager();
            if (SSOCookieManager.DEFAULT_SSO_COOKIE_NAME.equals(cookie.getName()))
            {
                try
                {
                    CookiePrincipal cp = ssoCookieManager.createPrincipal(cookie);
                    principals.add(cp);
                    return; // only pick up one SSO cookie
                }
                catch(Exception oops)
                {
                    log.error("failed to create CookiePrincipal: " + cookie.getValue(), oops);
                }
            }
        }
    }

    /**
     * Add the HTTP Principal, if it exists.
     */
    protected void addHTTPPrincipal(Set<Principal> principals)
    {
        final String httpUser = getRequest().getRemoteUser();
        if (StringUtil.hasText(httpUser))
            principals.add(new HttpPrincipal(httpUser));
    }

    /**
     * Add the X500 Principal, if it exists.
     */
    protected void addX500Principal(Set<Principal> principals)
    {
        if (chain != null)
            principals.add(chain.getPrincipal());
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
                if (SSOCookieManager.DEFAULT_SSO_COOKIE_NAME.equals(cookie.getName()))
                {
                    return cookie;
                }
            }
        }
        return null;
    }
}
