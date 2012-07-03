/*
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2009.                            (c) 2009.
 *  Government of Canada                 Gouvernement du Canada
 *  National Research Council            Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 *  All rights reserved                  Tous droits réservés
 *
 *  NRC disclaims any warranties,        Le CNRC dénie toute garantie
 *  expressed, implied, or               énoncée, implicite ou légale,
 *  statutory, of any kind with          de quelque nature que ce
 *  respect to the software,             soit, concernant le logiciel,
 *  including without limitation         y compris sans restriction
 *  any warranty of merchantability      toute garantie de valeur
 *  or fitness for a particular          marchande ou de pertinence
 *  purpose. NRC shall not be            pour un usage particulier.
 *  liable in any event for any          Le CNRC ne pourra en aucun cas
 *  damages, whether direct or           être tenu responsable de tout
 *  indirect, special or general,        dommage, direct ou indirect,
 *  consequential or incidental,         particulier ou général,
 *  arising from the use of the          accessoire ou fortuit, résultant
 *  software.  Neither the name          de l'utilisation du logiciel. Ni
 *  of the National Research             le nom du Conseil National de
 *  Council of Canada nor the            Recherches du Canada ni les noms
 *  names of its contributors may        de ses  participants ne peuvent
 *  be used to endorse or promote        être utilisés pour approuver ou
 *  products derived from this           promouvoir les produits dérivés
 *  software without specific prior      de ce logiciel sans autorisation
 *  written permission.                  préalable et particulière
 *                                       par écrit.
 *
 *  This file is part of the             Ce fichier fait partie du projet
 *  OpenCADC project.                    OpenCADC.
 *
 *  OpenCADC is free software:           OpenCADC est un logiciel libre ;
 *  you can redistribute it and/or       vous pouvez le redistribuer ou le
 *  modify it under the terms of         modifier suivant les termes de
 *  the GNU Affero General Public        la “GNU Affero General Public
 *  License as published by the          License” telle que publiée
 *  Free Software Foundation,            par la Free Software Foundation
 *  either version 3 of the              : soit la version 3 de cette
 *  License, or (at your option)         licence, soit (à votre gré)
 *  any later version.                   toute version ultérieure.
 *
 *  OpenCADC is distributed in the       OpenCADC est distribué
 *  hope that it will be useful,         dans l’espoir qu’il vous
 *  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
 *  without even the implied             GARANTIE : sans même la garantie
 *  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
 *  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
 *  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
 *  General Public License for           Générale Publique GNU Affero
 *  more details.                        pour plus de détails.
 *
 *  You should have received             Vous devriez avoir reçu une
 *  a copy of the GNU Affero             copie de la Licence Générale
 *  General Public License along         Publique GNU Affero avec
 *  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
 *  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
 *                                       <http://www.gnu.org/licenses/>.
 *
 *  $Revision: 4 $
 *
 ************************************************************************
 */

package ca.nrc.cadc.test.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

/**
 * Servlet used for testing client HTTP connections.
 *
 * @author pdowler, majorb
 */
public class TestServlet  extends HttpServlet
{
	private static final long serialVersionUID = 200909091014L;

	private static final Logger log = Logger.getLogger( TestServlet.class);

    private Map<String,Integer> getAttempts = new HashMap<String,Integer>();
    private Map<String,Integer> putAttempts = new HashMap<String,Integer>();
    private Map<String,Integer> postAttempts = new HashMap<String,Integer>();
    private String postData;

    /**
     * GET requests will return a 503 - Service Unavailable until some
     * number of requests has been made, at which time a 200 - OK is
     * returned.
     * 
     * If this is a redirect from a previous post (post=true), return
     * the postData.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
    {
        try
        {
            log.debug("doGet - START");
            String path = req.getPathInfo();
            log.debug("path: " + path);
            log.debug("query: " + req.getQueryString());
            path = "GET " + path;
            
            // write the post back if on the query string
            String post = req.getParameter("post");
            if (post != null && post.equalsIgnoreCase("true"))
            {
                ServletOutputStream ostream = resp.getOutputStream();
                ostream.write(postData.getBytes("UTF-8"));
                return;
            }
            
            Integer count = getAttempts.get(path);
            if (count == null)
                count = new Integer(1);
            else
                count = new Integer(count.intValue() + 1);
            getAttempts.put(path, count);
            
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
            // reset the count
            getAttempts.remove(path);
            
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

    /**
     * PUT requests will return a 503 - Service Unavailable until some
     * number of requests has been made, at which time a 200 - OK is
     * returned.
     */
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
            Integer count = putAttempts.get(path);
            if (count == null)
                count = new Integer(1);
            else
                count = new Integer(count.intValue() + 1);
            putAttempts.put(path, count);

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
            // reset the count
            putAttempts.remove(path);
            
            //InputStream istream = req.getInputStream();
            //byte[] buf = new byte[4096];
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
    
    /**
     * POST requests will return a 503 - Service Unavailable until some
     * number of requests has been made, at which time a 200 - OK is
     * returned.  When a 200 - OK is returned, the post data is written
     * back to the output stream.
     * 
     * If the query string contains 'redirect=true', then a redirect
     * is sent back to this servlet.  
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
    {
        try
        {
            log.debug("doPost - START");
            String path = req.getPathInfo();
            log.debug("path: " + path);
            log.debug("query: " + req.getQueryString());
            path = "POST " + path;
            Integer count = postAttempts.get(path);
            if (count == null)
                count = new Integer(1);
            else
                count = new Integer(count.intValue() + 1);
            postAttempts.put(path, count);

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
            
            // reset the count
            postAttempts.remove(path);
            
            // read the post
            InputStream istream = req.getInputStream();
            byte[] buf = new byte[4096];
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            
            int bytesRead = istream.read(buf);
            while (bytesRead != -1) {
                log.debug("read " + bytesRead + " bytes.");
                out.write(buf, 0, bytesRead);
                bytesRead = istream.read(buf);
            }
            postData = out.toString("UTF-8");
            log.debug("postData: " + postData);
            if (postData != null)
                log.debug("postData.length(): " + postData.length());
            
            // redirect the user if on the query string
            String redirect = req.getParameter("redirect");
            if (redirect != null && redirect.equalsIgnoreCase("true"))
            {
                String redirectURL =
                    req.getScheme() + "://" +
                    req.getServerName() +
                    req.getContextPath() +
                    "?post=true";
                resp.sendRedirect(redirectURL);
                log.debug("Issued redirect to: " + redirectURL);
                return;
            }
             
            // write the post back
            if (postData != null)
            {
                log.debug("writing postData back.");
                ServletOutputStream ostream = resp.getOutputStream();
                ostream.write(postData.getBytes("UTF-8"));
                ostream.flush();
            }
            
        }
        finally
        {
            log.debug("doPost - END");
        }
    }


}
