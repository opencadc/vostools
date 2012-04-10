package ca.nrc.cadc.vosi;

import ca.nrc.cadc.auth.AuthenticationUtil;
import java.io.IOException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedExceptionAction;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.servlet.ServletConfig;

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

    private String wsClassName;
    
    @Override
    public void init(ServletConfig config)
        throws ServletException
    {
        this.wsClassName = config.getInitParameter("ca.nrc.cadc.vosi.WebService");
        log.info("WebService class name: " + wsClassName);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
	{
        boolean started = false;
	    try
        {
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

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
	{
        boolean started = false;
	    try
        {
            Subject subject = AuthenticationUtil.getSubject(request);

	        Class wsClass = Class.forName(wsClassName);
	        WebService ws = (WebService) wsClass.newInstance();

            Subject.doAs(subject, new ChangeServiceState(ws, request));

            response.sendRedirect(request.getRequestURL().toString());
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

    private class ChangeServiceState implements PrivilegedExceptionAction
    {
        private WebService ws;
        private HttpServletRequest req;

        ChangeServiceState(WebService ws, HttpServletRequest req)
        {
            this.ws = ws;
            this.req = req;
        }

        public Object run() throws Exception
        {
            String state = req.getParameter("state");
            ws.setState(state);
            return null;
        }


    }
}
