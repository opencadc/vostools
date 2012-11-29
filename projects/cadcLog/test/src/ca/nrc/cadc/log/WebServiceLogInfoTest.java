/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2011.                            (c) 2011.
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
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */

package ca.nrc.cadc.log;

import javax.security.auth.Subject;
import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

import org.easymock.EasyMock;
import org.junit.Test;

import ca.nrc.cadc.auth.HttpPrincipal;

public class WebServiceLogInfoTest
{
    
    @Test
    public void testMinimalContentServlet()
    {
        HttpServletRequest request = EasyMock.createMock(HttpServletRequest.class);
        EasyMock.expect(request.getMethod()).andReturn("Get").once();
        EasyMock.expect(request.getPathInfo()).andReturn("/path/of/request").once();
        EasyMock.expect(request.getRemoteAddr()).andReturn("192.168.0.0").once();
        
        EasyMock.replay(request);
        
        WebServiceLogInfo logInfo = new ServletLogInfo(request);
        String start = logInfo.start();
        String end = logInfo.end();
        Assert.assertEquals("Wrong start", "START: {\"method\":\"GET\",\"path\":\"/path/of/request\",\"from\":\"192.168.0.0\"}", start);
        Assert.assertEquals("Wrong end", "END: {\"method\":\"GET\",\"path\":\"/path/of/request\",\"success\":true,\"from\":\"192.168.0.0\"}", end);
        
        EasyMock.verify(request);
    }
    
    @Test
    public void testMaximalContentServlet()
    {
        HttpServletRequest request = EasyMock.createMock(HttpServletRequest.class);
        EasyMock.expect(request.getMethod()).andReturn("Get").once();
        EasyMock.expect(request.getPathInfo()).andReturn("/path/of/request").once();
        EasyMock.expect(request.getRemoteAddr()).andReturn("192.168.0.0").once();
        
        EasyMock.replay(request);
        
        WebServiceLogInfo logInfo = new ServletLogInfo(request);
        String start = logInfo.start();
        Assert.assertEquals("Wrong start", "START: {\"method\":\"GET\",\"path\":\"/path/of/request\",\"from\":\"192.168.0.0\"}", start);
        logInfo.setSuccess(false);
        logInfo.setSubject(createSubject("the user"));
        logInfo.setElapsedTime(1234L);
        logInfo.setBytes(10L);
        logInfo.setMessage("the message");
        String end = logInfo.end();
        Assert.assertEquals("Wrong end", "END: {\"method\":\"GET\",\"path\":\"/path/of/request\",\"success\":false,\"user\":\"the user\",\"from\":\"192.168.0.0\",\"time\":1234,\"bytes\":10,\"message\":\"the message\"}", end);
        EasyMock.verify(request);
    }
    
    private Subject createSubject(String userid)
    {
        Subject s = new Subject();
        HttpPrincipal p = new HttpPrincipal(userid);
        s.getPrincipals().add(p);
        return s;
    }

}
