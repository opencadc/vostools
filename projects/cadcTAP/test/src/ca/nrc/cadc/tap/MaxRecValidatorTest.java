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
 * 6/18/12 - 2:43 PM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.tap;

import ca.nrc.cadc.uws.Parameter;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;


public class MaxRecValidatorTest
{
    @Test
    public void validate() throws Exception
    {
        final MaxRecValidator testSubject = new MaxRecValidator();

        // TEST 1
        //
        testSubject.setSynchronousMode(false);
        final List<Parameter> parameters1 = new ArrayList<Parameter>();

        final Integer result1 = testSubject.validate(parameters1);

        assertNull("Should be no limit set.", result1);


        // TEST 2
        //
        testSubject.setSynchronousMode(false);
        final List<Parameter> parameters2 = new ArrayList<Parameter>();

        parameters2.add(new Parameter("MAXREC", "88"));

        final int result2 = testSubject.validate(parameters2);

        assertEquals("Should be set to 88.", 88, result2);


        // TEST 2.a
        //
        testSubject.setSynchronousMode(true);
        final List<Parameter> parameters2a = new ArrayList<Parameter>();

        parameters2a.add(new Parameter("MAXREC", "88"));

        final int result2a = testSubject.validate(parameters2a);

        assertEquals("Should be set to 88.", 88, result2a);


        // TEST 3
        //
        testSubject.setSynchronousMode(false);
        final List<Parameter> parameters3 = new ArrayList<Parameter>();

        parameters3.add(new Parameter("MAXREC", "10000"));
        parameters3.add(
                new Parameter("DEST", "vos://cadc.nrc.ca!vospace/myvospace"));

        final Integer result3 = testSubject.validate(parameters3);

        assertNull("Should be no limit set.", result3);


        // TEST 4
        //
        testSubject.setSynchronousMode(true);
        final List<Parameter> parameters4 = new ArrayList<Parameter>();

        parameters4.add(new Parameter("MAXREC", "10000"));
        parameters4.add(
                new Parameter("DEST", "vos://cadc.nrc.ca!vospace/myvospace"));

        final int result4 = testSubject.validate(parameters4);

        assertEquals("Should be set to 10000 due to sync query.", 10000,
                     result4);
    }
}
