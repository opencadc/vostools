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


public class SSOCookieManagerImpl implements SSOCookieManager
{
    private String username;
    private char[] token;
    private long sessionID;


    protected SSOCookieManagerImpl()
    {

    }

    public SSOCookieManagerImpl(final String value)
    {
        parseValue(value);
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
     * Obtain the unique session ID.
     *
     * @return long Session ID.
     */
    @Override
    public long getSessionID()
    {
        return sessionID;
    }

    protected void setSessionID(final long sID)
    {
        this.sessionID = sID;
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

    /**
     * Obtain whether this has any cookie data.
     *
     * @return True if has data, false otherwise.
     */
    @Override
    public boolean hasData()
    {
        return StringUtil.hasText(getUsername())
               && !ArrayUtil.isEmpty(getToken())
               && (getSessionID() > 0);
    }

    /**
     * Parse the value from a cookie.
     *
     * @param value     The String value.
     */
    protected void parseValue(final String value)
    {
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

        setUsername(usernameBuilder.toString());
        setToken(tokenBuilder.toString().toCharArray());
        setSessionID(Long.parseLong(sessionIDBuilder.toString()));
    }
}
