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
 * 4/17/12 - 10:55 AM
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
import javax.servlet.http.HttpServletResponse;


public class SSOCookieManagerImpl implements SSOCookieManager
{
    private String username;
    private char[] token;
    private HttpServletResponse response;


    protected SSOCookieManagerImpl()
    {

    }

    public SSOCookieManagerImpl(final HttpServletRequest request,
                                final HttpServletResponse response)
    {
        this.response = response;

        parseCookieValue(request);
    }

    public SSOCookieManagerImpl(final String username, final char[] token)
    {
        this.username = username;
        this.token = token;
    }


    /**
     * Obtain the username from this manager's cookie.
     *
     * @return String username, or null if none found.
     */
    @Override
    public String getUsername()
    {
        return username;
    }

    protected void setUsername(final String uname)
    {
        this.username = uname;
    }


    /**
     * Obtain the unique token from the Cookie.
     *
     * @return The unqiue token.  Could be null if something wrong with the
     *         state of the cookie.
     */
    @Override
    public char[] getToken()
    {
        return token;
    }

    protected void setToken(final char[] tok)
    {
        this.token = tok;
    }


    public HttpServletResponse getResponse()
    {
        return response;
    }

    /**
     * Obtain the SSO CADC Cookie for this manager.
     *
     * @param path    The path for this cookie.
     * @param maxDays The maximum number of days until expiry.
     * @return Cookie instance, or null if unable to get the Cookie.
     */
    @Override
    public Cookie createSSOCookie(final String path, final int maxDays)
    {
        final Cookie cookie = new Cookie(SSOCookieManager.COOKIE_NAME,
                                         "username=" + getUsername() + "|"
                                         + "token="
                                         + String.valueOf(getToken()));

        cookie.setPath(path);
        cookie.setMaxAge(maxDays * 60 * 60 * 24);

        return cookie;
    }

    /**
     * Obtain the CookiePrincipal for this cookie manager.
     *
     * @return CookiePrincipal instance.
     */
    @Override
    public CookiePrincipal createCookiePrincipal()
    {
        return new CookiePrincipal(getUsername(), getToken());
    }

    /**
     * Obtain whether this has any cookie data.
     *
     * @return True if has data, false otherwise.
     */
    @Override
    public boolean hasData()
    {
        return StringUtil.hasText(getUsername())
               && !ArrayUtil.isEmpty(getToken());
    }

    /**
     * Expire this cookie manager's cookie.
     */
    @Override
    public void expire()
    {
        if ((getResponse() != null) && hasData())
        {
            getResponse().addCookie(createSSOCookie("/", 0));
        }
    }

    /**
     * Read in the pertinent cookie for this authentication.
     *
     * @param request       The HTTP Servlet request.
     * @return              Cookie, if present, or null if not.
     */
    protected Cookie getCookie(final HttpServletRequest request)
    {
        final Cookie[] cookies = request.getCookies();
        if (!ArrayUtil.isEmpty(cookies))
        {
            for (final Cookie cookie : cookies)
            {
                if (cookie.getName().equals(
                        SSOCookieManager.COOKIE_NAME))
                {
                    return cookie;
                }
            }
        }

        return null;
    }

    /**
     * Parse the username from the cookie value.
     *
     * @param request       The HTTP Servlet request.
     */
    private void parseCookieValue(final HttpServletRequest request)
    {
        final Cookie cookie = getCookie(request);
        parseCookieValue(cookie);
    }

    /**
     * Parse the username from the cookie value.
     *
     * @param cookie        The SSO CADC Cookie.
     */
    private void parseCookieValue(final Cookie cookie)
    {
        if (cookie != null)
        {
            parseValue(cookie.getValue());
        }
    }

    /**
     * Parse the value from a cookie.
     *
     * @param value     The String value.
     */
    protected void parseValue(final String value)
    {
        final String[] items = value.split("\\|");
        final StringBuilder username = new StringBuilder();
        final StringBuilder token = new StringBuilder();

        for (final String item : items)
        {
            if (item.startsWith("username="))
            {
                username.append(item.split("=")[1]);
            }
            else if (item.startsWith("token="))
            {
                token.append(item.split("=")[1]);
            }
        }

        setUsername(username.toString());
        setToken(token.toString().toCharArray());
    }
}
