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
 * 4/17/12 - 10:42 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.auth;

import javax.servlet.http.Cookie;


/**
 * Manage cookies.
 */
public interface SSOCookieManager
{
    String COOKIE_NAME = "CADC_Login";


    /**
     * Obtain the username from this manager's cookie.
     *
     * @return  String username, or null if none found.
     */
    String getUsername();

    /**
     * Obtain the unique token from the Cookie.
     *
     * @return  The unqiue token.  Could be null if something wrong with the
     *          state of the cookie.
     */
    char[] getToken();

    /**
     * Obtain the SSO CADC Cookie for this manager.
     *
     * @param   path        The path for this cookie.
     * @param   maxDays     The maximum number of days until expiry.
     * @return  Cookie instance, or null if unable to get the Cookie.
     */
    Cookie createSSOCookie(final String path, final int maxDays);

    /**
     * Obtain the CookiePrincipal for this cookie manager.
     *
     * @return  CookiePrincipal instance.
     */
    CookiePrincipal createCookiePrincipal();

    /**
     * Obtain whether this has any cookie data.
     *
     * @return      True if has data, false otherwise.
     */
    boolean hasData();

    /**
     * Expire this cookie manager's cookie.
     */
    void expire();
}
