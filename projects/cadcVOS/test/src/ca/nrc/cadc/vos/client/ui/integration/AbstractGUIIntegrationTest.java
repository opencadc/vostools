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
 * 10/29/12 - 9:20 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.vos.client.ui.integration;

import ca.nrc.cadc.auth.SSOCookieCredential;
import ca.nrc.cadc.auth.SSOCookieManager;
import ca.nrc.cadc.net.HttpPost;
import ca.nrc.cadc.uws.server.RandomStringGenerator;
import org.uispec4j.UISpecTestCase;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;


public class AbstractGUIIntegrationTest extends UISpecTestCase
{
    private static final char[] SEED_CHARS;

    static
    {
        final StringBuilder chars = new StringBuilder(128);

        for (char c = 'a'; c <= 'z'; c++)
        {
            chars.append(c);
        }

        for (char c = 'A'; c <= 'Z'; c++)
        {
            chars.append(c);
        }

        for (char c = '0'; c <= '9'; c++)
        {
            chars.append(c);
        }

        chars.append("_-()=+!,;:@&*$.");

        SEED_CHARS = chars.toString().toCharArray();
    }


    /**
     * Generate an ASCII string, replacing the '\' and '+' characters with
     * underscores to keep them URL friendly.
     *
     * @param length        The desired length of the generated string.
     * @return              An ASCII string of the given length.
     */
    protected String generateAlphaNumeric(final int length)
    {
        return new RandomStringGenerator(length,
                                         String.copyValueOf(
                                                 SEED_CHARS)).getID();
    }

    protected String generateTestDirectoryName()
    {
        final StringBuilder testDirNameBuilder = new StringBuilder(24);

        testDirNameBuilder.append("TEST_");
        testDirNameBuilder.append(generateAlphaNumeric(16));

        return testDirNameBuilder.toString();
    }

    protected SSOCookieCredential login(final String host,
                                        final String username,
                                        final char[] password) throws Exception
    {
        final StringBuilder loginURLString = new StringBuilder();

        loginURLString.append("http://");
        loginURLString.append(host);
        loginURLString.append("/access/login");

        final StringBuilder payload = new StringBuilder();

        payload.append("username=");
        payload.append(username);
        payload.append("&password=");
        payload.append(password);

        final String content = payload.toString();
        final String contentType = "application/x-www-form-urlencoded";

        final StringBuilder cookieValue = new StringBuilder();
        final StringBuilder cookieDomain = new StringBuilder();

        final HttpPost httpPost =
                new HttpPost(new URL(loginURLString.toString()),
                             content, contentType, false)
                {
                    /**
                     * Retry on TransientExceptions
                     */
                    @Override
                    public void run()
                    {
                        try
                        {
                            final HttpURLConnection conn =
                                    (HttpURLConnection) new URL(
                                            loginURLString.toString()).
                                            openConnection();

                            conn.setRequestMethod("POST");
                            conn.setRequestProperty("Content-Length",
                                                    "" + Integer.toString(
                                                            content.getBytes(
                                                                    "UTF-8").length));

                            conn.setRequestProperty("Content-Type",
                                                    contentType);

                            conn.setInstanceFollowRedirects(false);
                            conn.setUseCaches(false);
                            conn.setDoOutput(true);
                            conn.setDoInput(true);

                            OutputStream ostream = conn.getOutputStream();
                            ostream.write(content.getBytes("UTF-8"));
                            ostream.flush();
                            ostream.close();

                            final String cookieHeaderValue =
                                    conn.getHeaderField("Set-Cookie");
                            final String[] cookieItems =
                                    cookieHeaderValue.split(";");

                            for (final String cookieItem : cookieItems)
                            {
                                if (cookieItem.trim().startsWith(
                                        SSOCookieManager.DEFAULT_SSO_COOKIE_NAME))
                                {
                                    final String sanitizedCookieItem =
                                            cookieItem.replaceAll("\"", "");
                                    cookieValue.append(
                                            sanitizedCookieItem.split(
                                                    SSOCookieManager.DEFAULT_SSO_COOKIE_NAME
                                                    + "=")[1]);
                                }
                                else if (cookieItem.trim().startsWith("Domain"))
                                {
                                    cookieDomain.append(
                                            cookieItem.split("=")[1]);
                                }
                            }
                        }
                        catch (Exception e)
                        {
                            throw new RuntimeException("Can't login.", e);
                        }
                    }
                };

        httpPost.run();

        return new SSOCookieCredential(cookieValue.toString(),
                                       cookieDomain.toString());
    }
}
