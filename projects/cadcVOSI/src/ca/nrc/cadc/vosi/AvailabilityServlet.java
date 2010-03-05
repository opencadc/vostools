package ca.nrc.cadc.vosi;

import java.io.IOException;

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
public class AvailabilityServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public AvailabilityServlet() {
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
	        String wsClassName = getInitParameter("ca.nrc.cadc.vosi.WebService");
	        Class wsClass = Class.forName(wsClassName);
	        WebService ws = (WebService) wsClass.newInstance();
	        AvailabilityStatus status = ws.getStatus();
	        Availability availability = new Availability(status);

	        Document document = availability.toXmlDocument();
	        XMLOutputter xop = new XMLOutputter(Format.getPrettyFormat());
	        xop.output(document, response.getOutputStream());
	    } catch (Throwable t) {
	        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t.getMessage());
	    }
	}
}
