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
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;

import ca.nrc.cadc.net.NetUtil;

/**
 * Security utility.
 * 
 * @version $Version$
 * @author adriand
 */
public class AuthenticationUtil
{
    private static Logger log = Logger.getLogger(AuthenticationUtil.class);

    /**
     * Method to extract Principals from a request. Two types of
     * principals are currently supported: X500Principal and
     * HttpPrincipal.
     * 
     * @param request
     *            request that contains use authentication information
     * @return Set of Principals extracted from the request. The Set is
     *         empty if no Principals have been extracted.
     * @deprecated use getSubject(HttpServletRequest) instead
     */
    public static Set<Principal> getPrincipals(HttpServletRequest request)
    {
        Set<Principal> principals = new HashSet<Principal>();

        // look for basic authentication
        String userId = request.getRemoteUser();
        if (userId != null)
        {
            // user logged in. Create corresponding Principal
            principals.add(new HttpPrincipal(userId));
        }

        // look for X509 certificates
        X509Certificate[] certificates = (X509Certificate[]) request
                .getAttribute("javax.servlet.request.X509Certificate");
        if (certificates != null)
        {
            for (X509Certificate cert : certificates)
            {
                principals.add(cert.getSubjectX500Principal());
            }
        }

        return principals;
    }

    /**
     * Create a complete Subject with principal(s) and credentials (X509Certificate) from an
     * HttpServletRequest. 
     *
     * @see #getSubject(String, Collection<X509Certificate>)
     * @param request
     * @return a Subject with all available request content
     */
    public static Subject getSubject(HttpServletRequest request)
    {
        String remoteUser = request.getRemoteUser();
        X509Certificate[] ca = (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");
        Collection<X509Certificate> certs = null;
        if (ca != null && ca.length > 0)
            certs = Arrays.asList(ca);
        return getSubject(remoteUser, certs);
    }

    /**
     * Create a complete Subject with principal(s) and credentials (X509Certificate).
     * This method tries to detect the use ofa proxy certificate and add the Principal
     * representing the real identity of the user by comparing the subject and issuer fields
     * of the certficicate and using the issuer principal when the certificate is self-signed.
     * If the user has connected anonymously, the returned Subject will have no
     * principals and no credentials, but should be safe to use with Subject.doAs(...).
     *
     * @param remoteUser the remote user id (e.g. from http authentication)
     * @param certificates certificates extracted from the calling context/session
     * @return a Subject with all available request content
     */
    public static Subject getSubject(String remoteUser, Collection<X509Certificate> certificates)
    {
        Set<Principal> principals = new HashSet<Principal>();
        Set<X509Certificate> publicCred = new HashSet<X509Certificate>();
        Set privateCreds = new HashSet();

        // look for basic authentication
        if (remoteUser != null)
        {
            // user logged in. Create corresponding Principal
            principals.add(new HttpPrincipal(remoteUser));
        }

        // look for X509 certificates
        if (certificates != null)
        {
            for (X509Certificate c : certificates)
            {
                // we add the certificate to public credentials and try to add the real
                // principal to principals, eg detect proxy certificate usage here
                Principal p = null;
                X500Principal sp = c.getSubjectX500Principal();
                String sdn = sp.getName(X500Principal.RFC1779);
                X500Principal ip = c.getIssuerX500Principal();
                String idn = ip.getName(X500Principal.RFC1779);
                if ( sdn.endsWith(idn) )
                {
                    log.debug("detected self-issued proxy certificate by " + idn);
                    p = ip;
                }
                else
                {
                    log.debug("sp.getName(RFC1779) = " + sdn);
                    log.debug("sp.getName(RFC2253) = " + idn);
                    p = sp;
                }
                principals.add(p);
                publicCred.add(c);
            }
        }
        // put the certficates into pubCredentials?
        return new Subject(false, principals, publicCred, privateCreds);
    }
    
    // Encode a Subject in the format:
    // Principal Class name[Principal name]
    public static String encodeSubject(Subject subject)
    {
        if (subject == null)
            return null;
        StringBuilder sb = new StringBuilder();
        Iterator it = subject.getPrincipals().iterator();
        while (it.hasNext())
        {
            Principal principal = (Principal) it.next();
            sb.append(principal.getClass().getName());
            sb.append("[");
            sb.append(NetUtil.encode(principal.getName()));
            sb.append("]");
        }
        return sb.toString();
    }

    // Build a Subject from the encoding.
    public static Subject decodeSubject(String s)
    {
        if (s == null || s.length() == 0)
            return null;
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
                Class[] args = new Class[]{String.class};
                Constructor constructor = c.getDeclaredConstructor(args);
                String name = NetUtil.decode(s.substring(nameStart + 1, nameEnd));
                Principal principal = (Principal) constructor.newInstance(name);
                if (subject == null)
                    subject = new Subject();
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
     * If the principals are instances of X500Principal, the
     * cannonical form of their names are compared.  Otherwise,
     * their names are compared directly.
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
        
        if (p1 == null)
        {
            return false;
        }
        
        if (p2 == null)
        {
            return false;
        }
        
        if (p1 instanceof X500Principal)
        {
            if (p2 instanceof X500Principal)
            {
                X500Principal x1 = (X500Principal) p1;
                X500Principal x2 = (X500Principal) p2;
                return x1.getName(X500Principal.CANONICAL).equals(
                        x2.getName(X500Principal.CANONICAL));
            }
            return false;
        }
        else
        {
            if (p2 instanceof X500Principal)
            {
                return false;
            }
            return p1.getName().equals(p2.getName());
        }
    }

}
