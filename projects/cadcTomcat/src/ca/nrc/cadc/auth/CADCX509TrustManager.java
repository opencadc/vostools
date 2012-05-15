/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2012.                            (c) 2012.
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
 * Custom trust manager implementation that will accept client proxy certificates.
 * 
 * @author majorb
 *
 */
public class CADCX509TrustManager implements X509TrustManager
{
    private static Logger log = Logger.getLogger(CADCX509TrustManager.class);
    
    private X509TrustManager defaultTrustManager; 
    public CADCX509TrustManager(X509TrustManager defaultTrustManager)
    {
        this.defaultTrustManager = defaultTrustManager;
    }
    
    /*
     * Remove any proxy entries and delegate to the default trust manager.
     */
    public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException
    {

        log.debug("Checking if client chain is trusted.");        
        if (chain == null || chain.length == 0)
        {
            log.error("No certificates in chain.");
            throw new CertificateException("No credentials provided.");
        }
        
        // remove all but the end entity from the chain so that the default
        // trust manager can authenticate proxy certificate chains as well as
        // original certificate chains.
        X509CertificateChain x509CertificateChain = new X509CertificateChain(chain, null);
        X509Certificate endEntity = x509CertificateChain.getEndEntity();
        if (endEntity == null)
        {
            log.error("Bug: Should always have an endEntity");
            throw new CertificateException("Error extracting certifcate chain end entity.");
        }
        X509Certificate[] endEntityChain = new X509Certificate[] { endEntity };
        
        // send the authentication to the default trust manager.
        defaultTrustManager.checkClientTrusted(endEntityChain, authType);
        log.debug("Client is trusted.");
    }

    /**
     * Delegate to the default trust manager.
     */
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException
    {
        log.debug("Checking if server trusted.");
        defaultTrustManager.checkServerTrusted(chain, authType);
    }

    /**
     * Delegate to the default trust manager.
     */
    @Override
    public X509Certificate[] getAcceptedIssuers()
    {
        X509Certificate[] acceptedIssuers = defaultTrustManager.getAcceptedIssuers();
        log.debug("Trusting " + acceptedIssuers.length + " issuers.");
        return acceptedIssuers;
    }
}
