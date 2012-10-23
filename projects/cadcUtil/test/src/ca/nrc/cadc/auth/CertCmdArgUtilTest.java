/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2012.                            (c) 2012.
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
 * @author adriand
 * 
 * @version $Revision: $
 * 
 * 
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */



package ca.nrc.cadc.auth;

import java.io.File;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Arrays;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.util.FileUtil;
import ca.nrc.cadc.util.Log4jInit;

public class CertCmdArgUtilTest
{
    private static Logger log = Logger.getLogger(CertCmdArgUtilTest.class);
    
    
    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc.auth", Level.INFO);
    }
    
    @Test
    public void testLoadCertificate() throws Exception
    {
        log.debug("testLoadCertificate - START");
        
        byte[] file1 = FileUtil.readFile(new File("test/data/test.pem"));
        String key = "-----BEGIN RSA PRIVATE KEY-----\n" +
        "MIIBOgIBAAJBAOvm3yk/tr7/8ZaT584T54tOviYIpoWWRfwDgd176c0kTfTj43+C\n" +
        "BgxFcequf5mY51mgD7v38krRA3+xXi/igfsCAwEAAQJBAMqVrQXGcpDaScVPZV1j\n" +
        "WJAY4lDVUvQb1iQTev4SwPjqUy8H/f0Zt+Bezwf1LaxcHcCFA6QnDxHw6l99/5zw\n" +
        "p7kCIQD+4rfjcZyYUKwF0C2deKEgvZUjpiLYVyh/G4qKfT2sPwIhAOzu598CHLLn\n" +
        "LSZoBRJtjuhAr1zUrfkoBsNHQwTKi6tFAiBOpKtyXPKhOHrrTEFWzgqBLJ2gozkr\n" +
        "ITFYjqnfcycdRwIgbMW1L31hvYRCBxrEEVS4wclIeJ6vC+6jRC1ICEAQZN0CIFe+\n" +
        "Az22zN/URBRVBK32tI2axHy/j80Asysh+hxalp1F\n" +
        "-----END RSA PRIVATE KEY-----\n";

        
        byte[] file2 = key.getBytes();
        
        byte[] privateKey = SSLUtil.getPrivateKey(file2);
        try
        {
            RSAPrivateCrtKeySpec spec = SSLUtil.parseKeySpec(privateKey);
        }
        catch(Exception e)
        {
            System.out.println(e.getLocalizedMessage());
        }
        
        boolean result = Arrays.equals(file1, file2);
        System.out.println(result);
        
    }
    
}
