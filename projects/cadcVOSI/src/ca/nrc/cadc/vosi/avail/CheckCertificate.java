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

package ca.nrc.cadc.vosi.avail;

import java.io.File;
import java.security.Principal;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.Date;
import java.util.Set;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import ca.nrc.cadc.auth.SSLUtil;
import ca.nrc.cadc.auth.X509CertificateChain;
import ca.nrc.cadc.date.DateUtil;

/**
 * @author zhangsa
 *
 */
public class CheckCertificate implements CheckResource
{
    private static Logger log = Logger.getLogger(CheckCertificate.class);
    
    private File cert;
    private File key;

    /**
     * Check a certficate. This certficate is assumed to hold a cert and key.
     * @param cert
     */
    public CheckCertificate(File cert)
    {
        this.cert = cert;
    }

    /**
     * Check a certficate. This certficate and key file are separate.
     *
     * @param cert
     * @param key
     */
    public CheckCertificate(File cert, File key)
    {
        this.cert = cert;
        this.key = key;
    }

    @Override
    public void check()
        throws CheckException
    {
        Subject s = null;
        try
        {
            if (key != null)
                s = SSLUtil.createSubject(cert, key);
            else
                s=  SSLUtil.createSubject(cert);
        }
        catch(Throwable t)
        {
            log.warn("test failed: " + cert + " " + key);
            throw new CheckException("internal certificate check failed");
        }

        try
        {
            Set<X509CertificateChain> certs = s.getPublicCredentials(X509CertificateChain.class);
            if (certs.isEmpty())
            {
                // subject without certs means something went wrong above
                throw new RuntimeException("failed to load X509 certficate from file(s)");
            }
            X509CertificateChain chain = certs.iterator().next(); // the first one
            checkValidity(chain);
        }
        catch(Throwable t)
        {
            log.warn("test failed: " + cert + " " + key);
            throw new CheckException("certificate check failed", t);
        }
        log.debug("test succeeded: " + cert + " " + key);
    }

    private void checkValidity(X509CertificateChain chain)
    {
        DateFormat df = DateUtil.getDateFormat(DateUtil.ISO_DATE_FORMAT, DateUtil.LOCAL);
        Date start = null;
        Date end = null;
        Principal principal = null;
        for (X509Certificate c : chain.getChain())
        {
            try
            {
                start = c.getNotBefore();
                end = c.getNotAfter();
                principal = c.getSubjectX500Principal();
                c.checkValidity();
            }
            catch(CertificateNotYetValidException exp)
            {
                log.error("certificate is not valid yet, DN: "
                        + principal + ", valid from " + df.format(start) + " to " + df.format(end));
                throw new RuntimeException("certificate is not valid yet, DN: "
                        + principal + ", valid from " + df.format(start) + " to " + df.format(end));
            }
            catch (CertificateExpiredException exp)
            {
                log.error("certificate has expired, DN: "
                        + principal + ", valid from " + df.format(start) + " to " + df.format(end));
                throw new RuntimeException("certificate has expired, DN: "
                        + principal + ", valid from " + df.format(start) + " to " + df.format(end));
            }
        }
    }
}