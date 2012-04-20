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
 * 4/20/12 - 11:30 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.auth;

import ca.nrc.cadc.util.StringUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


/**
 * Extract an HttpPrincipal for this extractor's HTTP User.
 */
public class HTTPPrincipalExtractor implements PrincipalExtractor
{
    private final String httpUser;


    public HTTPPrincipalExtractor(final String httpUser)
    {
        this.httpUser = httpUser;
    }


    /**
     * Obtain a Collection of Principals from this extractor.
     *
     * @return Collection of Principal instances, or empty Collection.
     *         Never null.
     */
    @Override
    public Set<HttpPrincipal> getPrincipals()
    {
        final Set<HttpPrincipal> principalSet = new HashSet<HttpPrincipal>();
        final HttpPrincipal httpPrincipal = createHTTPPrincipal();

        if (httpPrincipal != null)
        {
            principalSet.add(httpPrincipal);
        }

        return Collections.unmodifiableSet(principalSet);
    }

    /**
     * Create a new HTTP Principal.
     *
     * @return  HttpPrincipal instance.
     */
    protected HttpPrincipal createHTTPPrincipal()
    {
        final HttpPrincipal httpPrincipal;

        if (StringUtil.hasText(getHttpUser()))
        {
            httpPrincipal = new HttpPrincipal(getHttpUser());
        }
        else
        {
            httpPrincipal = null;
        }

        return httpPrincipal;
    }

    public String getHttpUser()
    {
        return httpUser;
    }
}
