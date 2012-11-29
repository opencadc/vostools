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

package ca.nrc.cadc.uws.util;

import junit.framework.Assert;

import org.easymock.EasyMock;
import org.junit.Test;

import ca.nrc.cadc.log.WebServiceLogInfo;
import ca.nrc.cadc.uws.Job;

public class JobLogInfoTest
{
    
    @Test
    public void testJobLogInfo()
    {
        Job job = EasyMock.createMock(Job.class);
        EasyMock.expect(job.getRemoteIP()).andReturn("192.168.0.0").once();
        EasyMock.expect(job.getRequestPath()).andReturn("/path/of/request").once();
        EasyMock.expect(job.getID()).andReturn("jobid").once();
        job.ownerSubject = null;
        
        EasyMock.replay(job);
        
        WebServiceLogInfo logInfo = new JobLogInfo(job);
        String start = logInfo.start();
        Assert.assertEquals("Wrong start", "START: {\"method\":\"UWS\",\"path\":\"/path/of/request\",\"user\":\"anonUser\",\"from\":\"192.168.0.0\",\"jobID\":\"jobid\"}", start);
        logInfo.setElapsedTime(1234L);
        logInfo.setMessage("the message");
        String end = logInfo.end();
        Assert.assertEquals("Wrong end", "END: {\"method\":\"UWS\",\"path\":\"/path/of/request\",\"success\":true,\"user\":\"anonUser\",\"from\":\"192.168.0.0\",\"time\":1234,\"message\":\"the message\",\"jobID\":\"jobid\"}", end);
        
        EasyMock.verify(job);
    }

}
