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
