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


import ca.nrc.cadc.util.Base64;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.cert.Certificate;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import org.apache.log4j.Logger;

/**
 * Utility class to setup SSL before trying to use HTTPS.
 *
 * @author pdowler
 */
public class SSLUtil 
{
    private static Logger log = Logger.getLogger(SSLUtil.class);

    // SSL, SSLv2mm SSLv3, TLS, TLSv1, TLSv1.1
    private static String SSL_PROTOCOL = "TLS";

    // jceks, jks, pkcs12
    private static String KEYSTORE_TYPE = "JKS";

    // SunX509
    private static String KEYMANAGER_ALGORITHM = "SunX509";
    
    private static String CERT_ALIAS = "opencadc_x509";
    
    private static char[] THE_PASSWORD = CERT_ALIAS.toCharArray();

    /**
     * Initialise the default SSL socket factory so that all HTTPS connections use the
     * provided key store to authenticate (when the server requies client authentication).
     *
     * @see HttpsURLConnection#setDefaultSSLSocketFactory(javax.net.ssl.SSLSocketFactory)
     * @param certFile proxy certificate
     * @param private key file in DER format
     */
    public static void initSSL(File certFile, File keyFile)
    {
        SSLSocketFactory sf = getSocketFactory(certFile, keyFile);
        HttpsURLConnection.setDefaultSSLSocketFactory(sf);
    }

    /**
     * Initialise the default SSL socket factory so that all HTTPS connections use the
     * provided key store to authenticate (when the server requies client authentication).
     *
     * @param certFile proxy certificate
     * @param private key file in DER format
     * @return configured SSL socket factory
     */
    public static SSLSocketFactory getSocketFactory(File certFile, File keyFile)
    {
        KeyStore ks = readCertificates(certFile, keyFile);
        KeyStore ts = null;
        KeyManagerFactory kmf = getKeyManagerFactory(ks);
        TrustManagerFactory tmf = getTrustManagerFactory(ts);
        SSLContext ctx = getContext(kmf, tmf, ks);
        SSLSocketFactory sf = ctx.getSocketFactory();
        return sf;
    }

    static byte[] readFile(File f)
        throws IOException
    {
        DataInputStream dis = new DataInputStream(new FileInputStream(f));
        byte[] ret = new byte[(int)f.length()];
        dis.readFully(ret);
        dis.close();
        log.debug("readFile: " + ret.length);
        return ret;
    }

    // not working due to Base64 decoding to byte array not producing valid DER format key
    static byte[] getPrivateKey(byte[] certBuf)
        throws IOException
    {
        BufferedReader rdr = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(certBuf)));
        String line = rdr.readLine();
        StringBuffer base64 = new StringBuffer();
        while (line != null)
        {
            if (line.startsWith("-----BEGIN RSA PRIVATE KEY-"))
            {
                log.debug(line);
                line = rdr.readLine();
                while ( line != null && !line.startsWith("-----END RSA PRIVATE KEY-") )
                {
                    log.debug(line + " (" + line.length() + ")");
                    base64.append(line);
                    line = rdr.readLine();
                }
                log.debug(line);
                line = null; // break from outer loop
            }
            else
                line = rdr.readLine();
        }
        rdr.close();
        String encoded = base64.toString();
        log.debug("RSA PRIVATE KEY: " + encoded);
        log.debug("RSA private key: " + encoded.length() + " chars");
        // now: base64 -> byte[]
        byte[] ret = Base64.decode(encoded);
        log.debug("RSA private key: " + ret.length + " bytes");

        return ret;
    }

    static KeyStore readCertificates(File certFile, File keyFile)
    {
        try
        {
            byte[] certBuf = readFile(certFile);

            KeyFactory kf = KeyFactory.getInstance("RSA");
            //byte[] priv = getPrivateKey(certBuf);
            byte[] priv = readFile(keyFile);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(priv);
            PrivateKey pk = kf.generatePrivate(spec);

            BufferedInputStream istream = new BufferedInputStream(new ByteArrayInputStream(certBuf));
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            ArrayList certs = new ArrayList();
            while ( istream.available() > 0 )
            {
                Certificate cert = cf.generateCertificate(istream);
                log.debug("found: " + cert);
                certs.add(cert);
            }
            istream.close();
            
            Certificate[] chain = new Certificate[certs.size()];
            Iterator i = certs.iterator();
            int c = 0;
            while ( i.hasNext() )
            {
                X509Certificate x509 = (X509Certificate) i.next();
                chain[c++] = x509;
                try { x509.checkValidity(); }
                catch(CertificateException ex)
                {
                    throw new RuntimeException("certificate from file " + certFile + " is not valid", ex);
                }
                log.debug("X509 certificate is valid");
            }
            
            KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
            ks.load(null,null); // empty
            KeyStore.Entry ke = new KeyStore.PrivateKeyEntry(pk, chain);
            ks.setKeyEntry(CERT_ALIAS, pk, THE_PASSWORD, chain);
            return ks;
        }
        catch(InvalidKeySpecException ex)
        {
            throw new RuntimeException("failed to read RSA private key from " + keyFile, ex);
        }
        catch(KeyStoreException ex)
        {
            throw new RuntimeException("failed to find/load KeyStore of type " + KEYSTORE_TYPE, ex);
        }
        catch(FileNotFoundException ex)
        {
            throw new RuntimeException("failed to find certificate file " + certFile, ex);
        }
        catch(IOException ex)
        {
            throw new RuntimeException("failed to read certificate file " + certFile, ex);
        }
        catch(NoSuchAlgorithmException ex)
        {
            throw new RuntimeException("BUG: failed to create empty KeyStore", ex);
        }
        catch(CertificateException ex)
        {
            throw new RuntimeException("failed to load certificate from file " + certFile, ex);
        }
        finally
        {
            
        }
    }

    // currently broken trying to parse the openssl-generated pkcs12 file
    static KeyStore readPKCS12(File f)
    {
        InputStream istream = null;
        try
        {
            istream = new FileInputStream(f);
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(istream, THE_PASSWORD); // assume a non-password-protected proxy cert
            return ks;
        }
        catch(KeyStoreException ex)
        {
            throw new RuntimeException("failed to find KeyStore for " + KEYSTORE_TYPE, ex);
        }
        catch(FileNotFoundException ex)
        {
            throw new RuntimeException("failed to find key store file " + f, ex);
        }
        catch(IOException ex)
        {
            throw new RuntimeException("failed to read key store file " + f, ex);
        }
        catch(NoSuchAlgorithmException ex)
        {
            throw new RuntimeException("failed to check integtrity of key store file " + f, ex);
        }
        catch(CertificateException ex)
        {
            throw new RuntimeException("failed to load proxy certificate(s) from key store file " + f, ex);
        }
        finally
        {
            try { istream.close(); }
            catch(Throwable ignore) { }
        }
    }

    static KeyManagerFactory getKeyManagerFactory(KeyStore keyStore)
    {
        String da = KEYMANAGER_ALGORITHM;
        try
        {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(da);
            kmf.init(keyStore, THE_PASSWORD); // assume a non-password-protected proxy cert
            return kmf;
        }
        catch(NoSuchAlgorithmException ex)
        {
            throw new RuntimeException("failed to find KeyManagerFactory for " + da, ex);
        }
        catch(KeyStoreException ex)
        {
            throw new RuntimeException("failed to init KeyManagerFactory", ex);
        }
        catch(UnrecoverableKeyException ex)
        {
            throw new RuntimeException("failed to init KeyManagerFactory", ex);
        }
    }

    static TrustManagerFactory getTrustManagerFactory(KeyStore trustStore)
    {
        try
        {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX", "SunJSSE");
            tmf.init(trustStore);
            return tmf;
        }
        catch(NoSuchAlgorithmException ex)
        {
            throw new RuntimeException("BUG: failed to create TrustManagerFactory for algorithm=PKIX", ex);
        }
        catch(NoSuchProviderException ex)
        {
            throw new RuntimeException("BUG: failed to create TrustManagerFactory for provider=SunJSSE", ex);
        }
        catch(KeyStoreException ex)
        {
            throw new RuntimeException("failed to init trustManagerFactory", ex);
        }
    }

    static SSLContext getContext(KeyManagerFactory kmf, TrustManagerFactory tmf, KeyStore ks)
    {
        try
        {
            KeyManager[] kms = kmf.getKeyManagers();
            for (int i=0; i<kms.length; i++)
            {
                // cast is safe since we used KEYMANAGER_ALGORITHM=SunX509 above
                BasicX509KeyManager wrapper = new BasicX509KeyManager( (X509KeyManager) kms[i], CERT_ALIAS);
                kms[i] = wrapper;
            }
            TrustManager[] tms = tmf.getTrustManagers();
            for (int i=0; i<tms.length; i++)
            {
                // safe cast since we used PKIX, SunJSSE above
                BasicX509TrustManager wrapper = new BasicX509TrustManager((X509TrustManager) tms[i]);
                tms[i] = wrapper;
            }
            SSLContext ctx = SSLContext.getInstance(SSL_PROTOCOL);
            log.debug("KMF returned " + kms.length + " KeyManagers");
            log.debug("TMF returned " + tms.length + " TrustManagers");
            ctx.init(kms, tms, null);
            return ctx;
        }
        catch(NoSuchAlgorithmException ex)
        {
            throw new RuntimeException("failed to find SSLContext for " + SSL_PROTOCOL, ex);
        }
        catch(KeyManagementException ex)
        {
            throw new RuntimeException("failed to init SSLContext", ex);
        }
    }

    

    static void printKeyStoreInfo(KeyStore keystore)
        throws KeyStoreException
    {
        log.debug("Provider : " + keystore.getProvider().getName());
        log.debug("Type : " + keystore.getType());
        log.debug("Size : " + keystore.size());

        Enumeration en = keystore.aliases();
        while (en.hasMoreElements()) {
            System.out.println("Alias: " + en.nextElement());
        }
    }
}
