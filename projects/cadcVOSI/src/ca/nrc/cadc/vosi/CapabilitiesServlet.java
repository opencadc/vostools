package ca.nrc.cadc.vosi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jdom.Document;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/**
 * Servlet implementation class CapabilityServlet
 */
public class CapabilitiesServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public CapabilitiesServlet() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#service(HttpServletRequest request, HttpServletResponse response)
	 */
	@SuppressWarnings("unchecked")
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException 
	{
	    try {
	        List<Capability> caps = new ArrayList<Capability>();
	        String hostContext = Util.getStringPartBefore(request.getRequestURL().toString(), "/capabilities");
	        
	        String space = " ";
	        String resourceName, paramValue, standardID, role;
	        Capability capability;
	        Enumeration<String> enumParam = this.getInitParameterNames();
	        while (enumParam.hasMoreElements())
	        {
	            resourceName = enumParam.nextElement();
	            paramValue = this.getInitParameter(resourceName);
	            standardID = Util.getStringPartBefore(paramValue, space);
	            role = Util.getStringPartAfter(paramValue, space);
	            capability = new Capability(hostContext, standardID, resourceName, role);
	            caps.add(capability);
	        }
	        
	        Capabilities capabilities = new Capabilities(caps);
	        Document document = capabilities.toXmlDocument();
	        XMLOutputter xop = new XMLOutputter(Format.getPrettyFormat());
	        xop.output(document, response.getOutputStream());
	    } catch (Throwable t) {
	        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t.getMessage());
	    }
	}
	
 
}
