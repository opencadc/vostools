/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2009.                            (c) 2009.
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
 * Dec 15, 2009 - 11:41:45 AM
 *
 * 
 * 
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.uws.web.restlet.resources;

import static junit.framework.TestCase.*;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.restlet.representation.Representation;
import ca.nrc.cadc.uws.Result;

import java.net.URL;


public class ResultResourceTest
{
    protected Result result;
    protected ResultResource testSubject;

    @Before
    public void setup() throws Exception
    {
        result = new Result("TEST_RESULT", new URL("http://www.mysite.ca"));
        testSubject = new ResultResource()
        {
            /**
             * Obtain the current requested Result.
             *
             * @return Result instance, or null if none found.
             */
            @Override
            protected Result getResult()
            {
                return result;
            }
        };
    }

    @After
    public void tearDown()
    {
        testSubject = null;
    }


    @Test
    public void represent() throws Exception
    {
        final Representation representation = testSubject.represent();

        assertNull("Null representation.", representation);
    }
}
