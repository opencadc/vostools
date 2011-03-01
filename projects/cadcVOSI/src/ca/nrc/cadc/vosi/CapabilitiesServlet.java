package ca.nrc.cadc.vosi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;

import org.jdom.Document;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import ca.nrc.cadc.vosi.util.Util;

/**
 * Servlet implementation class CapabilityServlet
 */
public class CapabilitiesServlet extends HttpServlet
{
    private static Logger log = Logger.getLogger(CapabilitiesServlet.class);
    private static final long serialVersionUID = 201003131300L;
       
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
	{
        boolean started = false;
	    try
        {
	        List<Capability> caps = new ArrayList<Capability>();
	        String hostContext = Util.getStringPartBefore(request.getRequestURL().toString(), "/capabilities");
	        
	        String space = " ";
	        String resourceName, paramValue, standardID, role;
	        Capability capability;
	        Enumeration enumParam = this.getInitParameterNames();
	        while (enumParam.hasMoreElements())
	        {
	            resourceName = (String) enumParam.nextElement();
	            paramValue = this.getInitParameter(resourceName);
	            standardID = Util.getStringPartBefore(paramValue, space);
	            role = Util.getStringPartAfter(paramValue, space);
	            capability = new Capability(hostContext, standardID, resourceName, role);
	            caps.add(capability);
	        }
	        
	        Capabilities capabilities = new Capabilities(caps);
	        Document document = capabilities.toXmlDocument();
	        XMLOutputter xop = new XMLOutputter(Format.getPrettyFormat());
            started = true;
            response.setContentType("text/xml");
	        xop.output(document, response.getOutputStream());
	    }
        catch (Throwable t)
        {
            log.error("BUG", t);
            if (!started)
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, t.getMessage());
        }
        finally
        {

        }
	}
	
 
}
