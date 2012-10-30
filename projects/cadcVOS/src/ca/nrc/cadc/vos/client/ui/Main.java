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
 * 10/18/12 - 12:21 PM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.vos.client.ui;

import ca.nrc.cadc.auth.CookiePrincipal;
import ca.nrc.cadc.auth.SSOCookieManager;
import ca.nrc.cadc.auth.X509CertificateChain;
import ca.nrc.cadc.cred.AuthorizationException;
import ca.nrc.cadc.cred.client.priv.CredPrivateClient;
import ca.nrc.cadc.reg.client.RegistryClient;
import ca.nrc.cadc.util.ArgumentMap;
import ca.nrc.cadc.util.StringUtil;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.client.VOSpaceClient;
import ca.onfire.ak.ApplicationFrame;
import org.apache.log4j.Level;

import javax.security.auth.Subject;
import javax.swing.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.PrivilegedAction;


public class Main
{
    private static final String CRED_SERVICE_URI = "ivo://cadc.nrc.ca/cred";
    private static final String CRED_PROTOCOL = "https";

    public static void main(final String[] args)
    {
        final ArgumentMap argumentMap = new ArgumentMap(args);
        final VOSURI targetVOSpaceURI =
                new VOSURI(URI.create(argumentMap.getValue("dest")));
        final String vospaceWebServiceURL =
                argumentMap.getValue("vospaceWebServiceURL");
        final Subject subject = new Subject();

        System.setProperty("ca.nrc.cadc.auth.BasicX509TrustManager.trust",
                           "true");

        // Cookie based authentication?
        final String ssoCookieStr = fixNull(argumentMap.getValue("ssocookie"));
        if (ssoCookieStr != null)
        {
            final String ssoCookieDomain =
                    fixNull(argumentMap.getValue("ssocookiedomain"));
            if (ssoCookieDomain == null)
            {
                System.out.println("Missing ssocookiedomain argument...");
                Main.usage();
                System.exit(-1);
            }
            else
            {
                final SSOCookieManager ssoCookieManager =
                        new SSOCookieManager(ssoCookieStr);
                subject.getPrincipals().add(
                        new CookiePrincipal(ssoCookieManager.getUsername(),
                                            ssoCookieManager.getToken()));
            }
        }

        Subject.doAs(subject, new PrivilegedAction<Void>()
        {
            @Override
            public Void run()
            {
                final X509CertificateChain privateKeyChain;

                try
                {
                    final URL credBaseURL =
                            new RegistryClient().getServiceURL(
                                    URI.create(CRED_SERVICE_URI),
                                    CRED_PROTOCOL);
                    final CredPrivateClient cdp =
                            CredPrivateClient.getInstance(credBaseURL);
                    privateKeyChain = cdp.getCertificate(1.0f);
                    privateKeyChain.getChain()[0].checkValidity();

                    subject.getPublicCredentials().add(privateKeyChain);
                }
                catch (MalformedURLException ex)
                {
                    throw new RuntimeException(
                            "CredPrivateClient.getCertficate failed", ex);
                }
                catch (AuthorizationException ex)
                {
                    throw new RuntimeException(
                            "CredPrivateClient.getCertficate failed", ex);
                }
                catch (Exception ex)
                {
                    throw new RuntimeException(
                            "BUG: failed to instantiate CredPrivateClient impl",
                            ex);
                }

                return null;
            }
        });

        Subject.doAs(subject, new PrivilegedAction<Boolean>()
        {
            @Override
            public Boolean run()
            {
                try
                {
                    SwingUtilities.invokeAndWait(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            final GraphicUI graphicUploadUI =
                                    new GraphicUI(Level.DEBUG, targetVOSpaceURI,
                                                  new VOSpaceClient(vospaceWebServiceURL));
                            final ApplicationFrame frame =
                                    new ApplicationFrame(Constants.name, graphicUploadUI);
                            frame.getContentPane().add(graphicUploadUI);
                            frame.setVisible(true);

                            graphicUploadUI.start();
                        }
                    });

                    return Boolean.TRUE;
                }
                catch (Throwable t)
                {
                    t.printStackTrace();
                    return Boolean.FALSE;
                }
            }
        });
    }

    // convert string 'null' and empty string to a null, trim() and return
    private static String fixNull(final String s)
    {
        return (!StringUtil.hasLength(s) || "null".equals(s)) ? null : s;
    }

    private static void usage()
    {
        System.out.println("USAGE");
    }
}
