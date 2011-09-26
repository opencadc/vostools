
package ca.nrc.cadc.vosi;

import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.xml.XmlUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;

/**
 * Simple VOSI capabilities servlet that takes an input XML document and simply
 * modifies all the hostnames in accessURL content to have the hostname from
 * the request. This makes the input file+code work correctly in development, test, and
 * operational deployment on different servers without change.
 * </p>
 * <p>
 * To use, simply configure an init-param with param name of <code>input</code>
 * and param value of the input resource (with the preceeding / so it is findable
 * via <code>ServletContext.getResource(String></code>.
 *
 * @author pdowler
 */
public class TapCapabilitiesServlet extends HttpServlet
{
    private static Logger log = Logger.getLogger(TapCapabilitiesServlet.class);
    private static final long serialVersionUID = 201109261300L;

    private String staticCapabilities;

    @Override
    public void init(ServletConfig config)
        throws ServletException
    {
        super.init(config);

        String str = config.getInitParameter("input");
        log.info("static capabilities: " + str);
        try
        {
            URL resURL = config.getServletContext().getResource(str);
            TAPRegExtParser tp = new TAPRegExtParser(true);
            Document doc = tp.parse(resURL.openStream());
            StringWriter sw = new StringWriter();
            XMLOutputter out = new XMLOutputter();
            out.output(doc, sw);
            this.staticCapabilities = sw.toString();
        }
        catch(Throwable t)
        {
            log.error("CONFIGURATION ERROR: failed to read static capabilities file: " + str, t);
        }
            

    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
	{
        if (staticCapabilities == null)
        {
            response.setContentType("text/plain");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            PrintWriter pw = response.getWriter();
            pw.println("resource incorrectly configured and not functional");
            pw.close();
            return;
        }
	    try
        {
            URL rurl = new URL(request.getRequestURL().toString());
            String hostname = rurl.getHost();
            StringReader sr = new StringReader(staticCapabilities);
            TAPRegExtParser tp = new TAPRegExtParser(false); // schema validation done in init
            Document doc = tp.parse(sr);
            String xpath = "/vosi:capabilities/capability/interface/accessURL";
            XPath xp = XPath.newInstance(xpath);
            List accessURLs = xp.selectNodes(doc);
            log.debug("xpath[" + xpath + "] found: " + accessURLs.size());
            Iterator i = accessURLs.iterator();
            while ( i.hasNext() )
            {
                Element e = (Element) i.next();
                String surl = e.getTextTrim();
                log.debug("accessURL: " + surl);
                URL url = new URL(surl);
                URL nurl = new URL(url.getProtocol(), hostname, url.getPath());
                log.debug("accessURL: " + surl + " -> " + nurl);
                e.setText(nurl.toExternalForm());
            }
            XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
            response.setContentType("text/xml");
            out.output(doc, response.getOutputStream());
        }
        catch(Throwable t)
        {
            log.error("BUG: failed to rewrite hostname in accessURL elements", t);
        }
    }
}
