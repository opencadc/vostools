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
 * 4/10/12 - 3:10 PM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.auth;


import ca.nrc.cadc.util.StringUtil;

import java.io.Serializable;
import java.security.Principal;


/**
 * Represents the value of a Cookie as part of the Single Sign-On Cookie
 * based authentication.
 */
public class CookiePrincipal implements Principal, Serializable
{
    private static final long serialVersionUID = 20130313151134l;

    private final String sessionID;


    public CookiePrincipal(final String sessionID)
    {
        if (!StringUtil.hasText(sessionID))
        {
            throw new IllegalArgumentException("Null or emplty sessionID");
        }

        this.sessionID = sessionID;
    }

    @Override
    public String toString()
    {
        return getName();
    }

    /**
     * Returns the name of this principal.
     *
     * @return the name of this principal.
     */
    @Override
    public String getName()
    {
        return sessionID;
    }

    public String getSessionId()
    {
        return sessionID;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }

        if ((o == null) || (getClass() != o.getClass()))
        {
            return false;
        }

        final CookiePrincipal that = (CookiePrincipal) o;

        return sessionID.equals(that.sessionID);
    }

    @Override
    public int hashCode()
    {
        return sessionID.hashCode();
    }
}
