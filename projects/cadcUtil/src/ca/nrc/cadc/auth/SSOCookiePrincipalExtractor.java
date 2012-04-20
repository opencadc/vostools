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
 * 4/20/12 - 11:12 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.auth;

import ca.nrc.cadc.util.ArrayUtil;
import ca.nrc.cadc.util.StringUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


/**
 * Extract a CookiePrincipal for the SSO Cookie found for the given value.
 */
public class SSOCookiePrincipalExtractor implements PrincipalExtractor
{
    // Name for the cookie stored in the client.  Used by implementors when
    // extracting principals for cookies.
    public final static String COOKIE_NAME = "CADC_SSO";


    private String username;
    private long sessionID;
    private char[] token;


    /**
     * Cookie value to be parsed.
     *
     * @param cookieValue       The Cookie value.
     */
    public SSOCookiePrincipalExtractor(final String cookieValue)
    {
        if (StringUtil.hasText(cookieValue))
        {
            parseValue(cookieValue);
        }
    }


    /**
     * Obtain a Collection of Principals from this extractor.
     *
     * @return Collection of Principal instances, or empty Collection.
     *         Never null.
     */
    @Override
    public Set<CookiePrincipal> getPrincipals()
    {
        final Set<CookiePrincipal> principalSet = new HashSet<CookiePrincipal>();
        final CookiePrincipal cookiePrincipal = createCookiePrincipal();

        if (cookiePrincipal != null)
        {
            cookiePrincipal.setSessionID(getSessionID());
            principalSet.add(createCookiePrincipal());
        }

        return Collections.unmodifiableSet(principalSet);
    }

    /**
     * Obtain the CookiePrincipal for this cookie manager.
     *
     * @return CookiePrincipal instance.
     */
    public CookiePrincipal createCookiePrincipal()
    {
        final CookiePrincipal cookiePrincipal;

        if (StringUtil.hasText(getUsername())
            && !ArrayUtil.isEmpty(getToken()))
        {
            cookiePrincipal = new CookiePrincipal(getUsername(), getToken());
        }
        else
        {
            cookiePrincipal = null;
        }

        return cookiePrincipal;
    }


    public char[] getToken()
    {
        return token;
    }

    private void setToken(char[] token)
    {
        this.token = token;
    }

    public long getSessionID()
    {
        return sessionID;
    }

    private void setSessionID(long sessionID)
    {
        this.sessionID = sessionID;
    }

    public String getUsername()
    {
        return username;
    }

    private void setUsername(String username)
    {
        this.username = username;
    }

    protected void parseValue(final String value)
    {
        final SSOCookieManager ssoCookieManager =
                new SSOCookieManagerImpl(value);

        setUsername(ssoCookieManager.getUsername());
        setSessionID(ssoCookieManager.getSessionID());
        setToken(ssoCookieManager.getToken());
    }
}
