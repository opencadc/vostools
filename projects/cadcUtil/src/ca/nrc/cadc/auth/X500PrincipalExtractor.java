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
 * 4/20/12 - 11:40 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.auth;

import ca.nrc.cadc.util.ArrayUtil;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.util.*;


public class X500PrincipalExtractor implements PrincipalExtractor
{
    private final X509Certificate[] certificates;


    public X500PrincipalExtractor(final X509Certificate[] certs)
    {
        this.certificates = certs;
    }


    /**
     * Obtain a Collection of Principals from this extractor.  This should be
     * immutable.
     *
     * @return Collection of Principal instances, or empty Collection.
     *         Never null.
     */
    @Override
    public Set<X500Principal> getPrincipals()
    {
        final Set<X500Principal> principalSet = new HashSet<X500Principal>();
        final X500Principal x500Principal = createX500Principal();

        if (x500Principal != null)
        {
            principalSet.add(x500Principal);
        }

        return Collections.unmodifiableSet(principalSet);
    }

    /**
     * Pull the X500 Principal from this extractor's certificates.
     *
     * @return      The X500 Principal.  Careful, it could be null.
     */
    protected X500Principal createX500Principal()
    {
        final X500Principal x500Principal;

        if (!ArrayUtil.isEmpty(getCertificates()))
        {
            final X509CertificateChain x509CertificateChain =
                    new X509CertificateChain(Arrays.asList(getCertificates()));
            x500Principal = x509CertificateChain.getX500Principal();
        }
        else
        {
            x500Principal = null;
        }

        return x500Principal;
    }


    public X509Certificate[] getCertificates()
    {
        return certificates;
    }
}
