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

import java.net.URI;
import java.net.URL;
import java.security.PrivilegedAction;

import javax.security.auth.Subject;

import org.apache.log4j.Level;

import ca.nrc.cadc.auth.SSOCookieCredential;
import ca.nrc.cadc.auth.SSOCookieManager;
import ca.nrc.cadc.reg.client.RegistryClient;
import ca.nrc.cadc.util.ArgumentMap;
import ca.nrc.cadc.util.StringUtil;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.client.VOSpaceClient;
import ca.onfire.ak.ApplicationFrame;


public class Main
{
    protected static final URI VOSPACE_SERVICE_URI =
            URI.create("ivo://cadc.nrc.ca/vospace");


    public static void main(final String[] args)
    {
        final ArgumentMap argumentMap = new ArgumentMap(args);

        final ArgumentMap am = new ArgumentMap(args);

        if (am.isSet("h") || am.isSet("help"))
        {
            usage();
            System.exit(0);
        }

        final Level logLevel;
        if (am.isSet("d") || am.isSet("debug"))
        {
            logLevel = Level.DEBUG;
        }
        else if (am.isSet("v") || am.isSet("verbose"))
        {
            logLevel = Level.INFO;
        }
        else if (am.isSet("q") || am.isSet("quiet"))
        {
            logLevel = Level.OFF;
        }
        else
        {
            logLevel = Level.WARN;
        }

        final VOSURI targetVOSpaceURI =
                new VOSURI(URI.create(argumentMap.getValue("dest")));
        final Subject subject = new Subject();

        // Cookie based authentication?
        final String ssoCookieStr = fixNull(argumentMap.getValue("ssocookie"));

        if (ssoCookieStr != null)
        {

              String ssoCookieDomain =
                  fixNull(am.getValue("ssocookiedomain"));
              if (ssoCookieDomain == null)
              {   
                  System.out.
                  println("Missing ssocookiedomain argument...");
                  Main.usage();
                  System.exit(-1);
              }
              final String[] domains = ssoCookieDomain.split(",");
              if (domains.length < 1)
              {   
                  System.out.
                  println("Invalid ssocookiedomain argument: " + ssoCookieDomain);
                  Main.usage();
                  System.exit(-1);
              }
              for (String domain : domains)
              {   
                  SSOCookieCredential cred = new SSOCookieCredential(
                      SSOCookieManager.DEFAULT_SSO_COOKIE_NAME + "=" +
                              ssoCookieStr, domain.trim());
                  subject.getPublicCredentials().add(cred);
              }

        }

        final Boolean successfulStart =
                Subject.doAs(subject, new PrivilegedAction<Boolean>()
        {
            @Override
            public Boolean run()
            {
                try
                {
                    final RegistryClient registryClient = new RegistryClient();
                    final URL vospaceServiceURL =
                            registryClient.getServiceURL(VOSPACE_SERVICE_URI,
                                                         "http");

                    final GraphicUI graphicUploadUI =
                            new GraphicUI(logLevel,
                                          targetVOSpaceURI,
                                          new VOSpaceClient(
                                                  vospaceServiceURL.
                                                          toString()),
                                          subject);
                    final ApplicationFrame frame =
                            new ApplicationFrame(Constants.name,
                                                 graphicUploadUI);
                    frame.getContentPane().add(graphicUploadUI);
                    frame.setVisible(true);

                    return Boolean.TRUE;
                }
                catch (Throwable t)
                {
                    t.printStackTrace();
                    return Boolean.FALSE;
                }
            }
        });

        if (!successfulStart)
        {
            System.out.println("Unable to start application.");
            System.exit(-1);
        }
    }

    // convert string 'null' and empty string to a null, trim() and return
    private static String fixNull(final String s)
    {
        return (!StringUtil.hasLength(s) || "null".equals(s)) ? null : s;
    }

    private static void usage()
    {
        System.out.println("java -jar cadcVOSClient.jar -h || --help");
        System.out.println("java -jar cadcVOSClient.jar [-v|--verbose | -d|--debug | -q|--quiet ]");
        System.out.println("          --dest=<VOSpace URI to upload the directory to>");
        System.out.println("          --ssocookie=<cookie value to use in sso authentication>");
        System.out.println("          --ssocookiedomain=<domain cookie is valid in (required with ssocookie arg)>");
        System.out.println();
    }
}
