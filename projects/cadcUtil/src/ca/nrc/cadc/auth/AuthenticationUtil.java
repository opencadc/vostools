/*
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2009.                            (c) 2009.
 *  Government of Canada                 Gouvernement du Canada
 *  National Research Council            Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 *  All rights reserved                  Tous droits réservés
 *                                       
 *  NRC disclaims any warranties,        Le CNRC dénie toute garantie
 *  expressed, implied, or               énoncée, implicite ou légale,
 *  statutory, of any kind with          de quelque nature que ce
 *  respect to the software,             soit, concernant le logiciel,
 *  including without limitation         y compris sans restriction
 *  any warranty of merchantability      toute garantie de valeur
 *  or fitness for a particular          marchande ou de pertinence
 *  purpose. NRC shall not be            pour un usage particulier.
 *  liable in any event for any          Le CNRC ne pourra en aucun cas
 *  damages, whether direct or           être tenu responsable de tout
 *  indirect, special or general,        dommage, direct ou indirect,
 *  consequential or incidental,         particulier ou général,
 *  arising from the use of the          accessoire ou fortuit, résultant
 *  software.  Neither the name          de l'utilisation du logiciel. Ni
 *  of the National Research             le nom du Conseil National de
 *  Council of Canada nor the            Recherches du Canada ni les noms
 *  names of its contributors may        de ses  participants ne peuvent
 *  be used to endorse or promote        être utilisés pour approuver ou
 *  products derived from this           promouvoir les produits dérivés
 *  software without specific prior      de ce logiciel sans autorisation
 *  written permission.                  préalable et particulière
 *                                       par écrit.
 *                                       
 *  This file is part of the             Ce fichier fait partie du projet
 *  OpenCADC project.                    OpenCADC.
 *                                       
 *  OpenCADC is free software:           OpenCADC est un logiciel libre ;
 *  you can redistribute it and/or       vous pouvez le redistribuer ou le
 *  modify it under the terms of         modifier suivant les termes de
 *  the GNU Affero General Public        la “GNU Affero General Public
 *  License as published by the          License” telle que publiée
 *  Free Software Foundation,            par la Free Software Foundation
 *  either version 3 of the              : soit la version 3 de cette
 *  License, or (at your option)         licence, soit (à votre gré)
 *  any later version.                   toute version ultérieure.
 *                                       
 *  OpenCADC is distributed in the       OpenCADC est distribué
 *  hope that it will be useful,         dans l’espoir qu’il vous
 *  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
 *  without even the implied             GARANTIE : sans même la garantie
 *  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
 *  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
 *  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
 *  General Public License for           Générale Publique GNU Affero
 *  more details.                        pour plus de détails.
 *                                       
 *  You should have received             Vous devriez avoir reçu une
 *  a copy of the GNU Affero             copie de la Licence Générale
 *  General Public License along         Publique GNU Affero avec
 *  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
 *  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
 *                                       <http://www.gnu.org/licenses/>.
 *
 *  $Revision: 4 $
 *
 ************************************************************************
 */

package ca.nrc.cadc.auth;

import java.lang.reflect.Constructor;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.servlet.http.HttpServletRequest;

import ca.nrc.cadc.util.ArrayUtil;
import org.apache.log4j.Logger;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.net.NetUtil;

/**
 * Security utility.
 * 
 * @version $Version$
 * @author adriand
 */
public class AuthenticationUtil
{
    
    // Mandatory support list of RDN descriptors according to RFC 4512.
    private static final String[] ORDERED_RDN_KEYS = new String[]
        {"DC", "CN", "OU", "O", "STREET", "L", "ST", "C", "UID"};

    private static final String DEFAULT_AUTH = Authenticator.class.getName() + "Impl";
    
    private static Logger log = Logger.getLogger(AuthenticationUtil.class);

    private static Authenticator getAuthenticator()
    {
        String cname = System.getProperty(Authenticator.class.getName());
        if (cname == null)
            cname = DEFAULT_AUTH;
        try
        {
            Class c = Class.forName(cname);
            Object o = c.newInstance();
            Authenticator ret = (Authenticator) o;
            log.debug("Authenticator: " + cname);
            return ret;
        }
        catch(Throwable t)
        {
            if ( !DEFAULT_AUTH.equals(cname) )
                log.error("failed to load Authenticator: " + cname);
        }
        log.debug("Authenticator: null");
        return null;
    }

    private static Subject augmentSubject(Subject s)
    {
        final Authenticator auth = getAuthenticator();
        if (auth != null)
            return auth.getSubject(s);
        return s;
    }

    /**
     * Create a Subject from the given X509 Certificate Chain, and the given
     * Principal Extractor.  The Chain exists to provide Public Credentials.
     *
     * @param chain                 The X509 Certificate Chain for public
     *                              credentials.
     * @param principalExtractor    The PrincipalExtractor to provide
     *                              Principals.
     * @return                      A new Subject.
     */
    public static Subject getSubject(PrincipalExtractor principalExtractor)
    {
        if (principalExtractor == null)
            throw new IllegalArgumentException("principalExtractor cannot be null");
        
        final Set<Object> publicCred = new HashSet<Object>();
        final Set<Object> privateCred = new HashSet<Object>();

        X509CertificateChain chain = principalExtractor.getCertificateChain();
        if (chain != null)
        {
            publicCred.add(chain);
        }

        Subject subject =  new Subject(false, principalExtractor.getPrincipals(),
                           publicCred, privateCred);

       return augmentSubject(subject);
    }

    /**
     * Convenience method that uses a ServletPrincipalExtractor.
     *
     * @see #getSubject(PrincipalExtractor)
     * @param request       The HTTP Request.
     * @return a Subject with all available request content
     */
    public static Subject getSubject(final HttpServletRequest request)
    {
       return getSubject(new ServletPrincipalExtractor(request));
    }
    
    /**
     * Create a complete Subject with principal(s) and credentials (X509Certificate).
     * This method tries to detect the use of a proxy certificate and add the Principal
     * representing the real identity of the user by comparing the subject and issuer fields
     * of the certficicate and using the issuer principal when the certificate is self-signed.
     * If the user has connected anonymously, the returned Subject will have no
     * principals and no credentials, but should be safe to use with Subject.doAs(...).
     * </p><p>
     * This method will also try to load an implementation of the Authenticator interface
     * and use it to process the Subject before return. By default, it will try to load a
     * class named <code>ca.nrc.cadc.auth.AuthenticatorImpl</code>. Applications may override
     * this default class name by setting the <em>ca.nrc.cadc.auth.Authenticator</em> system
     * property to the class name of their implementation. Note that the default implementation
     * class does not exist in this library  so implementors can provide that exact class and
     * then not need the system property.
     * </p><p>
     * To get the collection of certficates in the servlet environment:
     * <pre>
     *   X509Certificate[] ca =
     *       (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");
     *   Collection<X509Certificate> certs = null;
     *   if (ca != null && ca.length > 0)
     *       certs = Arrays.asList(ca);
     * </pre>
     * In Restlet:
     * <pre>
     *   Request request = getRequest();
     *   Map<String, Object> requestAttributes = request.getAttributes();
     *   Collection<X509Certificate> certs = 
     *       (Collection<X509Certificate>) requestAttributes.get("org.restlet.https.clientCertificates");
     * </pre>
     *
     *
     *
     * @param remoteUser the remote user id (e.g. from http authentication)
     * @param certs certificates extracted from the calling context/session
     * @return a Subject
     * @deprecated      Use #getSubject(X509CertificateChain, PrincipalExtractor)
     */
    public static Subject getSubject(final String remoteUser,
                                     final Collection<X509Certificate> certs)
    {
        final X509CertificateChain chain;
        if ((certs != null) && !certs.isEmpty())
        {
            chain = new X509CertificateChain(certs);
        }
        else
        {
            chain = null;
        }

        return getSubject(remoteUser, chain);
    }

    /**
     * Create a subject with the specified certificate chain and private key.
     * 
     * @param certs a non-null and non-empty certficate chain
     * @param key optional private key
     * @return a Subject
     */
    public static Subject getSubject(X509Certificate[] certs, PrivateKey key)
    {
        final X509CertificateChain chain =
                new X509CertificateChain(certs, key);

        Set<Principal> principals = new HashSet<Principal>();
        Set<Object> publicCred = new HashSet<Object>();
        Set<Object> privateCred = new HashSet<Object>();

        principals.add(chain.getPrincipal());
        publicCred.add(chain);

        Subject subject =  new Subject(false, principals, publicCred, privateCred);
        return subject; // this method for client apps only
        //return augmentSubject(subject);
    }

    /**
     * Create a subject from the specified user name and certficate chain.
     * 
     *
     *
     * @param remoteUser            The HTTP Authenticated user, if any.
     * @param chain                 The X509Certificate chain of certificates,
     *                              if any.
     * @return                      An augmented Subject.
     * @deprecated      Use #getSubject(X509CertificateChain, PrincipalExtractor)
     */
    public static Subject getSubject(final String remoteUser,
                                     final X509CertificateChain chain)
    {
        Set<Principal> principals = new HashSet<Principal>();
        Set<Object> publicCred = new HashSet<Object>();
        Set privateCred = new HashSet();

        // basic authentication
        if (remoteUser != null)
        {
            // user logged in. Create corresponding Principal
            principals.add(new HttpPrincipal(remoteUser));
        }

        // SSL authentication
        if (chain != null)
        {
            principals.add(chain.getX500Principal());
            publicCred.add(chain);
            // note: we just leave the PrivateKey in the chain (eg public) rather
            // than extracting and putting it into the privateCred set... TBD
        }

        Subject subject = new Subject(false, principals, publicCred, privateCred);

        return augmentSubject(subject);
    }

    /**
     * Create a subject from the specified certficate chain. This method is
     * intended for use by applications that load a certificate and key pair
     * (probably from a file).
     *
     * @param chain                 The X509Certificate chain of certificates,
     *                              if any.
     * @return                      An augmented Subject.
     */
    public static Subject getSubject(X509CertificateChain chain)
    {
        Set<Principal> principals = new HashSet<Principal>();
        Set<Object> publicCred = new HashSet<Object>();
        Set privateCred = new HashSet();

        // SSL authentication
        if (chain != null)
        {
            principals.add(chain.getX500Principal());
            publicCred.add(chain);
            // note: we just leave the PrivateKey in the chain (eg public) rather
            // than extracting and putting it into the privateCred set... TBD
        }

        Subject subject = new Subject(false, principals, publicCred, privateCred);

        return augmentSubject(subject);
    }

    // Encode a Subject in the format:
    // Principal Class name[Principal name]
    public static String encodeSubject(Subject subject)
    {
        if (subject == null) return null;
        StringBuilder sb = new StringBuilder();

        for (final Principal principal : subject.getPrincipals())
        {
            sb.append(principal.getClass().getName());
            sb.append("[");
            sb.append(NetUtil.encode(principal.getName()));
            sb.append("]");
        }
        return sb.toString();
    }

    /**
     * Get corresponding user IDs from Subject's HttpPrincipals
     * @return set of user ids extracted from the HttpPrincipals
     */
    public static Set<String> getUseridsFromSubject()
    {
        AccessControlContext acc = AccessController.getContext();
        Subject subject = Subject.getSubject(acc);

        Set<String> userids = new HashSet<String>();
        if (subject != null)
        {
            final Set<HttpPrincipal> httpPrincipals =
                    subject.getPrincipals(HttpPrincipal.class);
            final Set<CookiePrincipal> cookiePrincipals =
                    subject.getPrincipals(CookiePrincipal.class);
            String userId;

            for (final HttpPrincipal principal : httpPrincipals)
            {
                userId = principal.getName();
                userids.add(userId);
            }
        }
        return userids;
    }

    // Build a Subject from the encoding.
    @SuppressWarnings("unchecked")
    public static Subject decodeSubject(String s)
    {
        if (s == null || s.length() == 0) return null;
        Subject subject = null;
        int pStart = 0;
        int nameStart = s.indexOf("[", pStart);
        try
        {
            while (nameStart != -1)
            {
                int nameEnd = s.indexOf("]", nameStart);
                if (nameEnd == -1)
                {
                    log.error("Invalid Principal encoding: " + s);
                    return null;
                }
                Class c = Class.forName(s.substring(pStart, nameStart));
                Class[] args = new Class[] { String.class };
                Constructor constructor = c.getDeclaredConstructor(args);
                String name = NetUtil.decode(s.substring(nameStart + 1, nameEnd));
                Principal principal = (Principal) constructor.newInstance(name);
                if (subject == null) subject = new Subject();
                subject.getPrincipals().add(principal);
                pStart = nameEnd + 1;
                nameStart = s.indexOf("[", pStart);
            }
        }
        catch (IndexOutOfBoundsException ioe)
        {
            log.error(ioe.getMessage(), ioe);
        }
        catch (Exception e)
        {
            log.error(e.getMessage(), e);
        }
        return subject;
    }

    /**
     * Given two principal objects, return true if they represent
     * the same identity.
     * 
     * The equality is defined by each principal type through the
     * equal method, with the exception of X500Principals: if the 
     * principals are instances of X500Principal, the
     * cannonical form of their names are compared.
     * 
     * Two null principals are considered equal.
     * 
     * @param p1 Principal object 1.
     * @param p2 Principal object 2.
     * 
     * @return True if they are equal, false otherwise.
     */
    public static boolean equals(Principal p1, Principal p2)
    {
        if (p1 == null && p2 == null)
        {
            return true;
        }

        if (p1 == null || p2 == null)
        {
            return false;
        }

        if (p1 instanceof X500Principal)
        {
            if (p2 instanceof X500Principal)
            {
                String converted1 = canonizeDistinguishedName(p1.getName());
                String converted2 = canonizeDistinguishedName(p2.getName());
                return converted1.equals(converted2);
            }
            return false;
        }

        return !(p2 instanceof X500Principal) && p1.equals(p2);
    }

    /**
     * Perform extended canonization operation on a distinguished name.
     * 
     * This method will convert the DN to a format that:
     * <ul>
     * <li>Is all lower case.</li>
     * <li>RDNs are separated by commas and no spaces.</li>
     * <li>RDNs are in the order specified by ORDERED_RDN_KEYS.  If more
     *     than one RDN of the same key exists, these are ordered
     *     among each other by their value by String.compareTo(String another).</li>
     * <li>If other RDNs exist in that are not in ORDERED_RDN_KEYS, an
     *     IllegalArgumentException is thrown.
     * </ul>
     * 
     * Please see RFC#4514 for more inforamation.
     * 
     * @param dnSrc
     * @return An extended canonized dn
     */
    public static String canonizeDistinguishedName(String dnSrc)
    {
        if (dnSrc == null)
        {
            throw new IllegalArgumentException("Null DN provided.");
        }
        
        log.debug("canonizeDistinguishedName: canonizing: " + dnSrc);
        
        // convert the entire DN to upper case
        String original = dnSrc.toUpperCase();
        
        // get a count of the number of RDN based on the number of
        // non-escaped equal signs.  use this count to compare the
        // results at the end
        int equalsIndex = original.indexOf("=");
        int rdnCount = 0;
        while (equalsIndex != -1)
        {
            // make sure it isn't an espaced equals sign
            if (equalsIndex == 0)
            {
                throw new IllegalArgumentException("Cannot start a DN with '=')");
            }
            if (equalsIndex == (original.length() - 1))
            {
                throw new IllegalArgumentException("Cannot end a DN with '=')");
            }
            if (original.charAt(equalsIndex - 1) != '\\')
            {
                rdnCount++;
            }
            equalsIndex = original.indexOf("=", equalsIndex + 1);
        }
        
        // Identify and collect the RDN (relative distinguished names).
        List<String> rdns = new ArrayList<String>();
        for (String rdnKey : ORDERED_RDN_KEYS)
        {
            // find the start of the RDN
            int startIndex = original.indexOf(rdnKey.toUpperCase() + "=");
            while (startIndex != -1)
            {
                // find the end of the RDN
                int endIndex = -1;
                for (String rdnKey2 : ORDERED_RDN_KEYS)
                {
                    int nextRdnStart = original.indexOf(rdnKey2.toUpperCase() + "=", startIndex + 1);
                    if (nextRdnStart != -1)
                    {
                        if (endIndex == -1 || (nextRdnStart < endIndex))
                        {
                            endIndex = nextRdnStart;
                        }
                    }
                }
                
                // check if this was the last RDN
                if (endIndex == -1)
                {
                    endIndex = original.length();
                }
                
                String rdn = original.substring(startIndex, endIndex);
                
                // remove any trailing spaces
                rdn = rdn.trim();
                
                // Remove the last character of the RDN if it is a comma or a forward
                // slash
                if (rdn.endsWith(",") || rdn.endsWith("/"))
                {
                    rdn = rdn.substring(0, rdn.length() - 1);
                }
                
                rdns.add(rdn);
                
                startIndex = original.indexOf(rdnKey.toUpperCase() + "=", endIndex);
            }
        }
        
        // ensure we have the right number of RDNs
        if (rdns.size() != rdnCount)
        {
            throw new IllegalArgumentException(
                    "Unexpected number of RDNs in DN.  At least one RDN is unrecognized.");
        }
        
        // put the RDNs back together separated by commas for a new DN
        StringBuilder newDN = new StringBuilder();
        List<String> rdnValues = null;
        for (String rdnKey : ORDERED_RDN_KEYS)
        {
            rdnValues = new ArrayList<String>();
            for (String rdn : rdns)
            {
                if (rdn.startsWith(rdnKey.toUpperCase() + "="))
                {
                    rdnValues.add(rdn);
                }
            }
            Collections.sort(rdnValues);
            for (String rdn : rdnValues)
            {
                if (newDN.length() != 0)
                {
                    newDN.append(",");
                }
                newDN.append(rdn);
            }
        }

        String ret = newDN.toString().toLowerCase();
        log.debug("canonizeDistinguishedName returning: " + ret);
        return ret;
    }

    /**
     * Object the X500Principal from a Subject.
     * 
     * @param subject
     * @return X500 Principal
     */
    public static X500Principal getX500Principal(Subject subject)
    {
        X500Principal x500Principal = null;
        Set<Principal> principals = subject.getPrincipals();
        for (Principal principal : principals) {
            if (principal instanceof X500Principal)
                x500Principal = (X500Principal) principal;
        }
        return x500Principal;
    }
    
    /**
     * This method checks the validity of X509Certificates associated with
     * a subject.
     * @param subject subject holding the certificates to be validated
     * @throws CertificateException Null subject or no certificates found
     * @throws CertificateNotYetValidException certificate not yet valid
     * @throws CertificateExpiredException certificate is expired
     */
    public static void checkCertificates(final Subject subject)
            throws CertificateException, CertificateNotYetValidException,
            CertificateExpiredException
    {
        // check validity
        if (subject != null)
        {
            Set<X509CertificateChain> certs = subject
                    .getPublicCredentials(X509CertificateChain.class);
            if (certs.size() == 0)
            {
                // subject without certs means something went wrong above
                throw new CertificateException(
                        "No certificates associated with the subject");
            }
            DateFormat df = DateUtil.getDateFormat(
                    DateUtil.ISO_DATE_FORMAT, DateUtil.LOCAL);
            X509CertificateChain chain = certs.iterator().next(); // the
                                                                    // first
                                                                    // one
            Date start = null;
            Date end = null;
            for (X509Certificate c : chain.getChain())
            {
                try
                {
                    start = c.getNotBefore();
                    end = c.getNotAfter();
                    c.checkValidity();

                }
                catch (CertificateExpiredException exp)
                {
                    // improve the message
                    String msg = "certificate has expired (valid from "
                            + df.format(start) + " to " + df.format(end)
                            + ")";
                    throw new CertificateExpiredException(msg);
                }
                catch (CertificateNotYetValidException exp)
                {
                    // improve the message
                    String msg = "certificate not yet valid (valid from "
                            + df.format(start) + " to " + df.format(end)
                            + ")";
                    throw new CertificateNotYetValidException(msg);
                }
            }
        }
        else
        {
            throw new CertificateException("No certificates (Null subject)");
        }
    }

}
