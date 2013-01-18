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

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;
import org.apache.log4j.Logger;

/**
 * Simple X509TrustManager that delegates all calls to an underlying X509TrustManager.
 * This class currently only adds some debug-level logging of method calls and supports
 * a way to bypass checking server certificates for testing (e.g. against a server without
 * a valid certificate). To bypass server validation entirely, simply set the system property
 * <code>ca.nrc.cadc.auth.BasicX509TrustManager.trust=true</code>. This feature
 * should only be used for running test code with self-signed certificates.
 *
 * @author pdowler
 */
public class BasicX509TrustManager implements X509TrustManager
{
    private static Logger log = Logger.getLogger(BasicX509TrustManager.class);
    private static final String TRUST_ALL_PROPERTY = BasicX509TrustManager.class.getName() + ".trust";

    private X509TrustManager delegate;

    public BasicX509TrustManager(X509TrustManager delegate)
    {
        this.delegate = delegate;
    }

    public void checkClientTrusted(X509Certificate[] xcs, String str) throws CertificateException
    {
        if (xcs != null)
            for (int i=0; i<xcs.length; i++)
                log.debug("checkClientTrusted: " + xcs[i].getSubjectDN() + "," + str);
        delegate.checkClientTrusted(xcs, str);
        log.debug("delegate.checkClientTrusted: OK");
    }

    public void checkServerTrusted(X509Certificate[] xcs, String str) throws CertificateException
    {
        if (xcs != null)
            for (int i=0; i<xcs.length; i++)
                log.debug("checkServerTrusted: " + xcs[i].getSubjectDN() + "," + str);
        if ( System.getProperty(TRUST_ALL_PROPERTY) != null )
        {
            log.debug(TRUST_ALL_PROPERTY + " is set, trusting all server certificates");
            return;
        }
        delegate.checkServerTrusted(xcs, str);
        log.debug("delegate.checkServerTrusted: OK");
    }

    public X509Certificate[] getAcceptedIssuers()
    {
        X509Certificate[] ret = delegate.getAcceptedIssuers();
        log.debug("deletage X509TrustManager knows " + ret.length + " accepted issuers");
        return ret;
    }


}
