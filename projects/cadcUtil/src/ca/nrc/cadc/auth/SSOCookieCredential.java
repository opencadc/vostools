/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2012.                            (c) 2012.
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
 * @author adriand
 * 
 * @version $Revision: $
 * 
 * 
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */



package ca.nrc.cadc.auth;


/**
 * Class that stores a Single-Sign-On cookie credential. The crential can
 * be used to authenticate the user against a given domain name.
 */
public class SSOCookieCredential
{
    private String ssoCookieValue;
    private String domain; 
    /**
     * Ctor
     * @param cookieValue value of the cookie to be used in the header request.
     * This cookie value should be in the form: cookie name = value
     * @param domain the domain that this cookie applies to. SSO cookies
     * should only be send to URLs within this domain.
     */
    public SSOCookieCredential(final String cookieValue, final String domain)
    {
        this.ssoCookieValue = cookieValue;
        this.domain = domain;
    }
    
    public String getSsoCookieValue()
    {
        return ssoCookieValue; 
    }
    
    public String getDomain()
    {
        return domain;
    }
}
