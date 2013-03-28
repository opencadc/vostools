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
        final StringBuilder sessionIDBuilder = new StringBuilder();

        if (items.length == 3)
        {
            // olds style cookies. All we are interested in is the token
            // which has become the session id.
            // this if case exists for backwards compatibility
            // and it's to be removed when no old style cookies exist.
            // adriand - 13/03/2013
            for (final String item : items)
            {
                if (item.startsWith("token=")) 
                {
                    sessionIDBuilder.append(item.split("=")[1]);
                }
            }
        }
        else
        {
            sessionIDBuilder.append(value);
//            if (value.startsWith("sessionID="))
//            {
//                sessionIDBuilder.append(value.split("=")[1]);
//            }
//            else
//            {
//                throw new IllegalArgumentException(
//                        "Cannot parse SSO cookie with value:" + value);
//            }
        }

        return new CookiePrincipal(sessionIDBuilder.toString());
    }
}
