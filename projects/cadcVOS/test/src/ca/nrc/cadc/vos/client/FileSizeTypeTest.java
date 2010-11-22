/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2008.                            (c) 2008.
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
 * 21-Oct-2008 - 9:55:37 AM
 *
 * 
 * 
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.vos.client;

import static org.junit.Assert.*;
import org.junit.Test;


public class FileSizeTypeTest
{
    @Test
    public void getHumanReadableSize() throws Exception
    {
        final long TEST_SIZE_1 = 293l;
        final long TEST_SIZE_2 = 19771125l;
        final long TEST_SIZE_3 = 1977112588l;
        final long TEST_SIZE_4 = 19721127112588l;

        assertEquals("Test Size One (293 Bytes)",
                     "293B", FileSizeType.getHumanReadableSize(TEST_SIZE_1));
        assertEquals("Test Size Two (18.86 MegaBytes)", "18.86MB",
                     FileSizeType.getHumanReadableSize(TEST_SIZE_2));
        assertEquals("Test Size Two (1.84 GigaBytes)", "1.84GB",
                     FileSizeType.getHumanReadableSize(TEST_SIZE_3));
        assertEquals("Test Size Two (17.94 TerraBytes)", "17.94TB",
                     FileSizeType.getHumanReadableSize(TEST_SIZE_4));
    }
}
