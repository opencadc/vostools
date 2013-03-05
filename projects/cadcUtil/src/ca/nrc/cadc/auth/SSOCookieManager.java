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

/**
 * Manage authentication cookies.
 */
public class SSOCookieManager
{
    public final static String DEFAULT_SSO_COOKIE_NAME = "CADC_SSO";

    public SSOCookieManager() { }

    public CookiePrincipal createPrincipal(final Cookie cookie)
    {
        String value = cookie.getValue();
        final String[] items = value.split("\\|");
        final StringBuilder usernameBuilder = new StringBuilder();
        final StringBuilder tokenBuilder = new StringBuilder();
        final StringBuilder sessionIDBuilder = new StringBuilder();

        for (final String item : items)
        {
            if (item.startsWith("username="))
            {
                usernameBuilder.append(item.split("=")[1]);
            }
            else if (item.startsWith("token="))
            {
                tokenBuilder.append(item.split("=")[1]);
            }
            else if (item.startsWith("sessionID="))
            {
                sessionIDBuilder.append(item.split("=")[1]);
            }
        }

        String username = usernameBuilder.toString();
        char[] token = tokenBuilder.toString().toCharArray();
        long sessionID = Long.parseLong(sessionIDBuilder.toString());
        CookiePrincipal ret = new CookiePrincipal(username, token);
        ret.setSessionID(sessionID);
        return ret;
    }
}
