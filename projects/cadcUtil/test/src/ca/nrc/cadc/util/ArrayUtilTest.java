/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2011.                         (c) 2011.
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
 * 12/8/11 - 1:32 PM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.util;

import org.junit.Test;

import static org.junit.Assert.*;


public class ArrayUtilTest
{
    @Test
    public void match() throws Exception
    {
        assertEquals("Should not match.", -1,
                     ArrayUtil.matches("um", new String[] {"", ""}, true));

        assertEquals("Should not match.", -1,
                     ArrayUtil.matches("um", new String[] {"", null}, true));

        assertEquals("Should match.", 1,
                     ArrayUtil.matches("um", new String[] {"", "um"}, true));

        assertEquals("Should match.", 0,
                     ArrayUtil.matches("um", new String[] {"um", "µm"}, true));

        assertEquals("Should match.", 1,
                     ArrayUtil.matches("^[uU][mM]",
                                       new String[] {null, "um", null}, true));

        assertEquals("Should match.", 2,
                     ArrayUtil.matches("nm", new String[] {null, "um", "nm"},
                                       true));

        assertEquals("Should match.", 2,
                     ArrayUtil.matches("Nm", new String[] {null, "um", "nm"},
                                       true));
    }
}
