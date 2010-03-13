package ca.nrc.cadc.vosi;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;

import org.jdom.Document;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/**
 * Servlet implementation class CapabilityServlet
 */
public class AvailabilityServlet extends HttpServlet
{
	private static Logger log = Logger.getLogger(AvailabilityServlet.class);
    private static final long serialVersionUID = 201003131300L;
       
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
	{
        boolean started = false;
	    try
        {
	        String wsClassName = getInitParameter("ca.nrc.cadc.vosi.WebService");
	        Class wsClass = Class.forName(wsClassName);
	        WebService ws = (WebService) wsClass.newInstance();
	        AvailabilityStatus status = ws.getStatus();
	        Availability availability = new Availability(status);

	        Document document = availability.toXmlDocument();
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
