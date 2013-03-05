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
 * 4/20/12 - 12:44 PM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.uws.web.restlet;

import ca.nrc.cadc.auth.CookiePrincipal;
import ca.nrc.cadc.auth.HttpPrincipal;
import ca.nrc.cadc.auth.PrincipalExtractor;
import ca.nrc.cadc.auth.SSOCookieManager;
import ca.nrc.cadc.auth.X509CertificateChain;
import ca.nrc.cadc.util.StringUtil;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.apache.log4j.Logger;
import org.restlet.Request;
import org.restlet.data.Cookie;
import org.restlet.util.Series;


/**
 * Principal Extractor implementation using a Restlet Request.
 */
public class RestletPrincipalExtractor implements PrincipalExtractor
{
    private static final Logger log = Logger.getLogger(RestletPrincipalExtractor.class);
    
    private final Request request;
    private X509CertificateChain chain;

    /**
     * Hidden no-arg constructor for testing.
     */
    RestletPrincipalExtractor()
    {
        this.request = null;
    }

    /**
     * Create this extractor from the given Restlet Request.
     *
     * @param req       The Restlet Request.
     */
    public RestletPrincipalExtractor(final Request req)
    {
        this.request = req;
    }

    private void init()
    {
        if (chain == null)
        {
            final Collection<X509Certificate> requestCertificates =
                (Collection<X509Certificate>) getRequest().getAttributes().get(
                        "org.restlet.https.clientCertificates");
            if ((requestCertificates != null) && (!requestCertificates.isEmpty()))
                chain = new X509CertificateChain(requestCertificates);
        }
    }
    public X509CertificateChain getCertificateChain()
    {
        init();
        return chain;
    }

    public Set<Principal> getPrincipals()
    {
        init();
        Set<Principal> principals = new HashSet<Principal>();
        addPrincipals(principals);
        return principals;
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
        Series<Cookie> cookies = getRequest().getCookies();
        if (cookies == null || cookies.isEmpty())
            return;
        
        for (Cookie cookie : cookies)
        {
            SSOCookieManager ssoCookieManager = new SSOCookieManager();
            if (SSOCookieManager.DEFAULT_SSO_COOKIE_NAME.equals(cookie.getName()))
            {
                try
                {
                    javax.servlet.http.Cookie tmp = new javax.servlet.http.Cookie(cookie.getName(), cookie.getValue());
                    CookiePrincipal cp = ssoCookieManager.createPrincipal(tmp);
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
        final String httpUser = getAuthenticatedUsername();
        if (StringUtil.hasText(httpUser))
            principals.add(new HttpPrincipal(httpUser));
    }

    /**
     * Add the X500 Principal, if it exists.
     */
    protected void addX500Principal(Set<Principal> principals)
    {
        init();
        if (chain != null)
            principals.add(chain.getPrincipal());
    }


    /**
     * Read in the pertinent cookie for this authentication.
     *
     * @return              Cookie, if present, or null if not.
     */
    protected Cookie getCookie()
    {
        for (final Cookie cookie : getRequest().getCookies())
        {
            if (SSOCookieManager.DEFAULT_SSO_COOKIE_NAME.equals(cookie.getName()))
            {
                return cookie;
            }
        }

        return null;
    }

    /**
     * Obtain the Username submitted with the Request.
     *
     * @return      String username, or null if none found.
     */
    protected String getAuthenticatedUsername()
    {
        final String username;

        if (getRequest().getChallengeResponse() != null)
        {
            username = getRequest().getChallengeResponse().getIdentifier();
        }
        else if (!getRequest().getClientInfo().getPrincipals().isEmpty())
        {
            // Put in to support Safari not injecting a Challenge Response.
            // Grab the first principal's name as the username.
            username = getRequest().getClientInfo().getPrincipals().
                    get(0).getName();
        }
        else
        {
            username = null;
        }

        return username;
    }


    public Request getRequest()
    {
        return request;
    }
}
