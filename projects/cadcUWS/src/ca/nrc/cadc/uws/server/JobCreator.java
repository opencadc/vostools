
package ca.nrc.cadc.uws.server;

import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobAttribute;
import ca.nrc.cadc.uws.JobInfo;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.xml.XmlUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.JDOMException;

/**
 * Simple class used to read job description from the request and create a new Job.

 * @author pdowler
 */
public class JobCreator 
{
    private static final Logger log = Logger.getLogger(JobCreator.class);

    private static final String TEXT_XML = "text/xml";
    private static final String FORM_URLENCODED = "application/x-www-form-urlencoded";

    public JobCreator() { }

    public Job create(HttpServletRequest request)
        throws IOException, JDOMException
    {
        // TODO: check content-type for params (www-urlencoded?) vs XML (text/xml)
        String contentType = request.getHeader("Content-Type");

        // Job from POSTed XML
        Job job = new Job();
        job.setExecutionPhase(ExecutionPhase.PENDING);
        
        if (contentType != null && contentType.equals(TEXT_XML))
        {
            JobInfo jobInfo = getJobInfo(request.getInputStream());
            job.setJobInfo(jobInfo);
        }
        else
        {
            Enumeration<String> paramNames = request.getParameterNames();
            List<Parameter> params = new ArrayList<Parameter>();
            while ( paramNames.hasMoreElements() )
            {
                String p = paramNames.nextElement();
                if ( JobAttribute.RUN_ID.getAttributeName().equalsIgnoreCase(p) )
                        job.setRunID(request.getParameter(p));
                else
                {
                    String[] vals = request.getParameterValues(p);
                    if (vals != null)
                        for (String v : vals)
                            params.add(new Parameter(p, v));
                }
            }
            job.setParameterList(params);
        }

        try
        {
            URL u = new URL(request.getRequestURL().toString());
            job.setRequestPath(u.getPath());
        }
        catch(MalformedURLException oops)
        {
            log.error("failed to get request path", oops);
        }
        
        job.setRemoteIP(request.getRemoteAddr());

        return job;
    }

    protected JobInfo getJobInfo(InputStream istream)
        throws IOException
    {
        // TODO: read into buffer (assume smallish)
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(istream));
        String line = r.readLine();
        while (line != null)
        {
            sb.append(line);
            line = r.readLine();
        }
        String content = sb.toString();
        log.debug("content: " + content);
        JobInfo ret = null;
        try
        {
            // Check that the XML is well-formed
            Document doc = XmlUtil.validateXml(content, null);
            //StringWriter sw = new StringWriter();
            //XMLOutputter outputter = new XMLOutputter();
            //outputter.setFormat(Format.getCompactFormat());
            //outputter.output(doc.detachRootElement(), sw);
            ret = new JobInfo(content, TEXT_XML, true);
        }
        catch(JDOMException ex)
        {
            // not well formed: how to detach root?
            ret = new JobInfo(content, TEXT_XML, false);

        }
        return ret;
    }
}
