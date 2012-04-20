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

import ca.nrc.cadc.auth.HTTPPrincipalExtractor;
import ca.nrc.cadc.auth.PrincipalExtractor;
import ca.nrc.cadc.auth.SSOCookiePrincipalExtractor;
import ca.nrc.cadc.auth.X500PrincipalExtractor;
import ca.nrc.cadc.util.ArrayUtil;

import ca.nrc.cadc.util.StringUtil;
import org.restlet.Request;
import org.restlet.data.Cookie;

import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


/**
 * Principal Extractor implementation using a Restlet Request.
 */
public class RestletPrincipalExtractor implements PrincipalExtractor
{
    private final Request request;
    private final Set<Principal> principals;


    /**
     * Hidden no-arg constructor for testing.
     */
    RestletPrincipalExtractor()
    {
        this.request = null;
        this.principals = new HashSet<Principal>();
    }

    /**
     * Create this extractor from the given Restlet Request.
     *
     * @param req       The Restlet Request.
     */
    public RestletPrincipalExtractor(final Request req)
    {
        this.request = req;
        this.principals = new HashSet<Principal>();

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
        final String httpUser = getAuthenticatedUsername();

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
                (X509Certificate[]) getRequest().getAttributes().get(
                        "org.restlet.https.clientCertificates");

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
     * Read in the pertinent cookie for this authentication.
     *
     * @return              Cookie, if present, or null if not.
     */
    protected Cookie getCookie()
    {
        for (final Cookie cookie : getRequest().getCookies())
        {
            if (cookie.getName().equals(
                    SSOCookiePrincipalExtractor.COOKIE_NAME))
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
