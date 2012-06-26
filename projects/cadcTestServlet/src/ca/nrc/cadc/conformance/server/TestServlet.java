
package ca.nrc.cadc.conformance.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;

/**
 *
 * @author pdowler
 */
public class TestServlet  extends HttpServlet
{
	private static final long serialVersionUID = 200909091014L;

	private static final Logger log = Logger.getLogger( TestServlet.class);

    private Map<String,Integer> attempts = new HashMap<String,Integer>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
    {
        try
        {
            log.debug("doGet - START");
            String path = req.getPathInfo();
            log.debug("path: " + path);
            path = "GET " + path;
            Integer count = attempts.get(path);
            if (count == null)
                count = new Integer(1);
            else
                count = new Integer(count.intValue() + 1);
            attempts.put(path, count);
            
            if (count.intValue() < 3)
            {
                log.debug("sending " + HttpServletResponse.SC_SERVICE_UNAVAILABLE + " with Retry-After: 5");
                resp.setHeader("Retry-After", "5");
                resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                resp.setContentType("text/plain");
                PrintWriter w = resp.getWriter();
                w.println("please try again later");
                w.close();
                return;
            }
            log.debug("sending " + HttpServletResponse.SC_OK + " and some bytes");
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("text/plain");
            PrintWriter w = resp.getWriter();
            w.println("hello world");
            w.close();
        }
        finally
        {
            log.debug("doGet - END");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
    {
        try
        {
            log.debug("doPut - START");
            String path = req.getPathInfo();
            log.debug("path: " + path);
            path = "PUT " + path;
            Integer count = attempts.get(path);
            if (count == null)
                count = new Integer(1);
            else
                count = new Integer(count.intValue() + 1);
            attempts.put(path, count);

            if (count.intValue() < 3)
            {
                //log.debug("getting and closing InputStream");
                //InputStream istream = req.getInputStream();
                //istream.close();
                log.debug("sending " + HttpServletResponse.SC_SERVICE_UNAVAILABLE + " with Retry-After: 5");
                resp.setHeader("Retry-After", "5");
                resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                resp.setContentType("text/plain");
                PrintWriter w = resp.getWriter();
                w.println("please try again later");
                w.close();
                return;
            }
            InputStream istream = req.getInputStream();
            byte[] buf = new byte[4096];
            //while ( istream.read(buf) != -1)
            //    log.debug("read(buf) ");

            log.debug("sending " + HttpServletResponse.SC_OK);
            resp.setStatus(HttpServletResponse.SC_OK);
        }
        finally
        {
            log.debug("doPut - END");
        }
    }


}
