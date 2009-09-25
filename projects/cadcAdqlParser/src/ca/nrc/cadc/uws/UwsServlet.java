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



package ca.nrc.cadc.uws;

import ca.nrc.cadc.db.DBUtil;
import ca.nrc.cadc.net.NetUtil;
// sz 2009-08-26 //import ca.nrc.cadc.net.URIConverter;
import ca.nrc.cadc.util.EnumToIterator;
import ca.nrc.cadc.util.LoggerUtil;
import ca.nrc.cadc.util.PropertyUtil;

import ca.nrc.cadc.util.threads.ConditionVar;
import java.io.IOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.naming.NamingException;
import javax.servlet.RequestDispatcher;
import org.apache.log4j.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.ServletException;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 *
 * @author pdowler
 */
public class UwsServlet extends HttpServlet
{
    private static Logger log;
    
    private static long MAX_BLOCK_TIME = 180*1000L; // 180 seconds
    
    protected String uwsDataSource;
    protected String uwsSchema;
    protected String jobDisplayPage;
    protected String serviceName;
    protected String resourceNamespacePrefix;
    protected String resourceNamespaceURI;
    
    protected boolean allowPostOnCreate = false;    // UWS default
    protected boolean allowPostToJob = false;       // UWS default
    protected boolean syncInvocation = false;       // UWS default
    
    private Map listeners = new HashMap();
    
    public UwsServlet()
    {
        this("UWS");
    }
    protected UwsServlet(String serviceName)
    {
        super();
        this.serviceName = serviceName;
    }
    
    static
    {
        try
        {
            String pkg = UwsServlet.class.getPackage().getName();
            String dbPkg = DBUtil.class.getPackage().getName();
            String[] logPackages = new String[] { pkg, dbPkg };
            String[] args = PropertyUtil.getPropertyList(pkg+".args",",");
            LoggerUtil.initialize(logPackages, args);
            log = Logger.getLogger(UwsServlet.class);
            log.info("initialized logging for package '" + pkg + "'");
        }
        catch(Throwable t) { t.printStackTrace(); }
    }
    
    public void init(ServletConfig config)
        throws ServletException
    {
        super.init(config);

        this.uwsDataSource = config.getInitParameter("uwsDataSource");
        this.uwsSchema = config.getInitParameter("uwsSchema");
        this.jobDisplayPage = config.getInitParameter("jobDisplayPage");
        this.serviceName += "/" + config.getServletName();
        
        log.info("serviceName: " + serviceName);
        log.debug("uwsDataSource: " + uwsDataSource);
        log.debug("uwsSchema: " + uwsSchema);
        log.info("jobDisplayPage: " + jobDisplayPage);
        
    }
    
    /**
     * Issue a redirect to the resource specified by the destination argument. The HTTP status code is
     * set to the specified code (typically 303 for UWS services). If the destination is relative, it is
     * assumed to be within the same context and servlet as the request. Thus, the normal use in UWS (redirect
     * to the created or modified resource) would just use the resource name. For example, a POST to the UWS
     * job list creates a new job; the POST causes a redirect to the job, which means destination is the jobId.
     * In most other cases (where an existing resource is modified) the redirect can be to the value returned
     * by request.getPathInfo().
     * 
     * @param request used to construct the absolute URL in the Location
     * @param response used to issue the redirect 
     * @param destination relative resource (within the jobList)
     * @param code HTTP redirect code (3xx)
     * @throws java.io.IOException
     */
    protected void doRedirect(HttpServletRequest request, HttpServletResponse response, String destination, int code)
        throws IOException
    {
        // response.sendRedirect() always uses 302
        try
        {
            if (destination == null)
                destination = "/tapServer"; // TODO: generalise this
            else
            {
                URI uri = new URI(destination);
                if ( !uri.isAbsolute())
                {
                    //URL url2 = new URL(request.getRequestURL().toString());
                    String s = getServletURL(request);
                    if (destination.charAt(0) == '/')
                        destination = s + destination;
                    else
                        destination = s + "/" + destination;
                }
            }
        }
        catch(URISyntaxException oops)
        {
            oops.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        log.debug("doRedirect: " + destination);
        response.setStatus(code);
        response.setHeader("Location", destination);
        response.setContentLength(0);
    }
    
    /**
     * Determine the resource to redirect to after a POST. If the return value is null, the default 
     * redirect location (the job resource) is used. Subclasses can provide an alternate location.
     * </p>
     * <p>
     * Note: The default implementation returns null.
     * @param job the UwsJob
     * @param request The HTTP request, which may effect the decision in subclasses
     * @return alternate location (relative to the servlet path) or null for default redirect
     */
    protected String getRedirectLocation(UwsJob job, HttpServletRequest request)
    {
        return null;
    }
    
    private String getServletURL(HttpServletRequest request)
        throws MalformedURLException
    {
        return getContextURL(request) + request.getServletPath();
    }
    private String getContextURL(HttpServletRequest request)
        throws MalformedURLException
    {
        return getBaseURL(request) + request.getContextPath();
    }
    private String getBaseURL(HttpServletRequest request)
        throws MalformedURLException
    {
        URL url2 = new URL(request.getRequestURL().toString());
        String s = url2.getProtocol() + "://" + url2.getHost();
        if (url2.getPort() > 0)
            s += ":" + url2.getPort();
        return s;
    }
    
    
    
    /**
     * @param request
     * @param response
     * @throws javax.servlet.ServletException
     * @throws IOException
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
     	throws ServletException, IOException
    {
        boolean html = HttpUtil.acceptsHTML(request);
        log.debug("GET: " + request.getPathInfo() + " HTML output: " + html);
        String id = null;
        String resource = null;
        try
        {
            id = findID(request);
            if (id == null)
            {
                if (syncInvocation)
                {
                    doPost(request, response);
                    return;
                }
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            JobORM orm = new JobORM(serviceName, uwsSchema);
            UwsJob job = orm.get(id, getJdbcTemplate());
            if (job == null)
            {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            resource = findResource(request, id);
            
            // special handling: access to the job error object
            if (UwsJob.ERROR.equals(resource))
            {
                // the implementation supplies the complete error response
                doErrorResponse(response, job.getError());
                return;
            }
            
            // synchronous mode: blocking GET of the result
            //if (syncInvocation && resource != null && resource.equals(UwsJob.SYNC_RESULT))
            if (resource != null && resource.equals(UwsJob.SYNC_RESULT))
            {
                StringBuffer sb = request.getRequestURL();
                int i = sb.lastIndexOf("/");
                sb.replace(i+1, sb.length(), UwsJob.SYNC_POKE);
                String listenerURL = sb.toString();
                log.debug("listenerURL = " + listenerURL);
                UwsResult ur = waitForResults(job.getID(), orm, listenerURL);
                if (ur != null)
                {
                	//sz temp 2009-08-26
//                    URIConverter conv = new URIConverter();
//                    URL url = conv.getURL(ur.uri);
//                    doRedirect(request, response, url.toExternalForm(), HttpServletResponse.SC_SEE_OTHER);
//                    return;
                }
                // error or aborted: redirect to the job where caller can figure it out
                doRedirect(request, response, job.getID(), HttpServletResponse.SC_SEE_OTHER);
                return;
            }
            
            // special handling: access to a specific result object
            if (resource != null && resource.startsWith(UwsJob.RESULTS) && !UwsJob.RESULTS.equals(resource))
            {
            	//sz temp 2009-08-26
//                List results = job.getResults();
//                // just in case, we should set the sys prop for server name
//                System.setProperty(NetUtil.class.getPackage().getName() + ".serverName", request.getServerName());
//                URIConverter conv = new URIConverter();
//                for (int i=0; i<results.size(); i++)
//                {
//                    UwsResult ur = (UwsResult) results.get(i);
//                    if (resource.equals(UwsJob.RESULTS + "/" + ur.name))
//                    {
//                        URL url = conv.getURL(ur.uri);
//                        doRedirect(request, response, url.toExternalForm(), HttpServletResponse.SC_SEE_OTHER);
//                        return;
//                    }
//                }
//                response.sendError(HttpServletResponse.SC_NOT_FOUND);
//                return;
            }
            
            // special handling: access to the job with HTML output
            if (html)
            {
                // put job in request scope and forward to a JSP for display
                request.setAttribute("job", job);
                request.setAttribute("baseAction", getServletURL(request));
                if (UwsJob.RESULTS.equals(resource))
                {
                    RequestDispatcher disp = request.getRequestDispatcher("/results.jsp");
                    disp.forward(request, response);
                }
                else
                {
                    RequestDispatcher disp = request.getRequestDispatcher("/" + jobDisplayPage);
                    disp.forward(request, response);
                }
                return;
            }
            
            // default handling: XML output
            XmlWriter w = new XmlWriter(response);
            w.setResourceNamespace(resourceNamespacePrefix, resourceNamespaceURI);
            if (resource == null)
            {
                w.doXmlResponse(job);
            }
            // GET a specific UWS resource
            else if (UwsJob.QUOTE.equals(resource))
            {
                w.doXmlResponse(UwsJob.QUOTE, job);
            }
            else if (UwsJob.TERMINATION.equals(resource))
            {
                 w.doXmlResponse(UwsJob.TERMINATION, job);
            }
            else if (UwsJob.DESTRUCTION.equals(resource))
            {
                 w.doXmlResponse(UwsJob.DESTRUCTION, job);
            }
            else if (UwsJob.PHASE.equals(resource))
            {
                 w.doXmlResponse(UwsJob.PHASE, job);
            }
            else if (UwsJob.RESULTS.equals(resource))
            {
                 w.doXmlResponse(UwsJob.RESULTS, job);
            }
            else if ( canHandleResource(resource) )
            {
                w.doXmlResponse(resource, job);
            }
            else
            {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        }
        // TODO: do not catch IOException or ServletException
        catch(Throwable t)
        {
            log.error("GET " + id + "," + resource, t);
        }
        finally
        {
            
        }
    }
    
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
     	throws ServletException, IOException
    {
        log.debug("POST: " + request.getPathInfo() + " " + this.getClass().getName());
        String id = null;
        String resource = null;
        try
        {
            JobORM orm = new JobORM(serviceName, uwsSchema);
            JdbcTemplate jdbc = getJdbcTemplate();
            id = findID(request);
            if (id == null)
            {
                // create a new job
                UwsJob job = new UwsJob();
                if (allowPostOnCreate)
                    copyParams(request, job);
                fireStateChangeEvent(job);
                
                if (syncInvocation) // experimental: implicit PHASE=RUN
                    job.setPhase(UwsJob.PHASE_QUEUED);
                    
                // persist the job
                orm.put(job, jdbc);

                // run or abort after persisting
                if (job.getPhase().equals(UwsJob.PHASE_QUEUED))
                    run(job);
                else if (job.getPhase().equals(UwsJob.PHASE_ABORTED))
                    abort(job);
                
                String dest = job.getID(); // UWS default
                if (syncInvocation) // experimental: custom redirects that don't obey UWS
                    dest += "/" + UwsJob.SYNC_RESULT;
                doRedirect(request, response, dest, 303);
                log.info("created " + job);
                return;
            }

            // find resource under the job
            resource = findResource(request, id);
            
            // synchronous mode: poke from the back-end job runner that result is ready
            if (UwsJob.SYNC_POKE.equals(resource))
            {
                notifyOfResults(id);
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }
            
            // intend to modify: get job from storage
            UwsJob job = orm.get(id, jdbc);
            if (job == null)
            {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            
            if (resource == null)
            {
                // POST to the job: look for the non-RESTy ACTION=DELETE
                String s = request.getParameter("ACTION");
                log.debug("POST: ACTION=" + s);
                if ("DELETE".equals(s))
                {
                    // TODO: if job is QUEUED or EXECUTING, ABORT it first
                    orm.delete(job.getID(), jdbc);
                    //response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    // non-standard: redirect back to welcome page
                    doRedirect(request, response, null, 303);
                    return;
                }
                /*
                if (allowPostToJob)
                {
                    // allow POST of params to the job to create/modify resources?
                    if (copyParams(request, job))
                    {
                        fireStateChangeEvent(job);
                        orm.put(job, jdbc);
                    }
                    // redirect to the job
                    doRedirect(request, response, job.getID(), 303);
                    return;
                }
                */
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            
            if (UwsJob.TERMINATION.equals(resource))
            {
                // negotiating: TODO or NONE?
                doRedirect(request, response, job.getID(), 303);
                return;
            }
            
            if (UwsJob.DESTRUCTION.equals(resource))
            {
                // negotiating: TODO or NONE?
                doRedirect(request, response, job.getID(), 303);
                return;
            }
            
            if (UwsJob.PHASE.equals(resource))
            {
                // changing phase
                try
                {
                    String phase = request.getParameter("PHASE");
                    log.debug("phase change: PHASE="+phase);
                    boolean changed = setPhase(job, phase);
                    if (changed)
                    {
                        fireStateChangeEvent(job);
                        orm.put(job, jdbc);
                    }
                    // run or abort after persisting
                    if (job.getPhase().equals(UwsJob.PHASE_QUEUED))
                        run(job);
                    else if (job.getPhase().equals(UwsJob.PHASE_ABORTED))
                        abort(job);
                    
                    doRedirect(request, response, job.getID(), 303);
                    return;
                }
                catch(IllegalArgumentException ex)
                {
                    log.error("doPost: " + ex);
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
            }
            
            if (UwsJob.QUOTE.equals(resource) || UwsJob.RESULTS.equals(resource) || UwsJob.ERROR.equals(resource))
            {
                // NOT ALLOWED
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            
            if ( canHandleResource(resource) )
            {
                // delegate
                boolean changed = doPost(resource, job, request, response);
                if (changed)
                {
                    fireStateChangeEvent(job);
                    orm.put(job, jdbc);
                }
                
                // redirect to this resource
                //doRedirect(request, response, request.getPathInfo(), 303);

                // redirect to the job?
                doRedirect(request, response, job.getID(), 303);
                return;
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        // TODO: do not catch IOException or ServletException
        catch(Throwable t)
        {
            log.error("POST " + id + "," + resource, t);
        }
        finally
        {
            
        }
    }
    
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
     	throws ServletException, IOException
    {
        String id = null;
        String resource = null;
        try
        {
            // TODO: PUT to termination, destruction
        
            // TODO: delegate
        }
        // TODO: do not catch IOException or ServletException
        catch(Throwable t)
        {
            log.error("POST " + id + "," + resource, t);
        }
        finally
        {
            
        }
    }
    
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
     	throws ServletException, IOException
    {
        String id = null;
        String resource = null;
        try
        {
            id = findID(request);
            if (id == null)
                return;

            resource = findResource(request, id);
            if (resource == null)
            {
                // delete job from storage
                JobORM orm = new JobORM(serviceName, uwsSchema);
                orm.delete(id, getJdbcTemplate());
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }
            // DELETE any other resource: NOT ALLOWED
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        // TODO: do not catch IOException or ServletException
        catch(Throwable t)
        {
            log.error("POST " + id + "," + resource, t);
        }
        finally
        {
            
        }
    }
    
    // get/create a JdbcTemplate connected to the UWS persistence dataSource
    protected JdbcTemplate getJdbcTemplate() 
        throws NamingException
    {
        log.debug("looking for DataSource: " + uwsDataSource);
        DataSource ds = DBUtil.getDataSource(uwsDataSource);
        return new JdbcTemplate(ds);
    }
    
    // find the jobID in the request URL
    private String findID(HttpServletRequest request)
    {
        String ret = request.getPathInfo();
        if (ret != null)
        {
            ret = ret.substring(1,ret.length()); // strip leading /
            if (ret.trim().length() == 0)
                ret = null;
            if (ret != null)
            {
                int i = ret.indexOf('/');
                if (i != -1)
                    ret = ret.substring(0,i); // strip trailing resource(s)
            }
        }
        log.debug("jobID: " + ret);
        return ret;
    }
    
    // find resource within the job in the request URL
    private String findResource(HttpServletRequest request, String id)
    {
        String ret = request.getPathInfo();
        if (id != null && ret != null)
        {
            ret = ret.substring(id.length() + 1, ret.length()); // strip /$id from front
            if (ret.trim().length() == 0)
                ret = null;
            if (ret != null)
            {
                ret = ret.substring(1,ret.length()); // string leading /
                if (ret.trim().length() == 0)
                    ret = null;
            }
            // NOTE: no URLs are generated with trailing /, so we
            // do not have to strip them... if a user puts one in and we
            // strip it off here, that will break some relative URLs in 
            // HTML output later on: leave them in, fail to find resource, and
            // fail immediately is better
        }
        
        log.debug("resource: " + ret);
        return ret;
    }
    
    // copy all request parameters into the job: these are POSTed to the job directly
    // or to the joblist during job creation
    private boolean copyParams(HttpServletRequest request, UwsJob job)
        throws URISyntaxException
    {
        log.debug("copyParams");
        boolean phaseChange = false;
        boolean resChange = false;
        Iterator i = new EnumToIterator(request.getParameterNames());
        while ( i.hasNext() )
        {
            String key = (String) i.next();
            String value = request.getParameter(key);
            key = key.toLowerCase();
            if (value != null)
            {
                log.debug("copyParams: key = " + key + " value: " + value);
                
                if (UwsJob.PHASE.equals(key))
                {
                    phaseChange = setPhase(job, value);
                }
                else if (UwsJob.TERMINATION.equals(key))
                {
                    // negotiating: TODO?
                }
                else if (UwsJob.DESTRUCTION.equals(key))
                {
                    // negotiating: TODO?
                } 
                else if (canHandleResource(key) && allowDirectPost(key))
                {
                    log.debug("mapping param " + key + " into resource");
                    boolean b = job.addResource(key, parseParam(key, value));
                    resChange = resChange || b;
                }
            }
        }
        return phaseChange || resChange;
    }
    
    private boolean setPhase(UwsJob job, String phase)
    {
        log.debug("setPhase: " + job.getID() + "\t" + phase);
        boolean changed = true;
        if ("RUN".equals(phase) 
                && job.getPhase().equals(UwsJob.PHASE_PENDING))
        {
            job.setPhase(UwsJob.PHASE_QUEUED);
        }
        else if ("ABORT".equals(phase) 
                && (job.getPhase().equals(UwsJob.PHASE_QUEUED) || job.getPhase().equals(UwsJob.PHASE_EXECUTING)) )
        {
            job.setPhase(UwsJob.PHASE_ABORTED);
        }
        else if ("ABORT".equals(phase) )
        {
            // harmless abort when job is done: redirect
            changed = false;
        }
        return changed;
    }
    
    /**
     * Subclasses should return true if the specified resource is something they
     * support. 
     * </p>
     * <p>
     * Note: The default implementation returns false.
     * 
     * @param resource
     * @return
     */
    protected boolean canHandleResource(String resource)
    {
        log.debug("UwsServlet.canHandleResource: " + resource);
        return false;
    }
    
    /**
     * Subclasses should return true if a POST to this resource is allowed to be
     * sent to the job directly (with param name equal to resource name).
     * </p>
     * <p>
     * Note: The default implementation returns false.
     * 
     * @param resource
     * @return
     */
    protected boolean allowDirectPost(String resource)
    {
        log.debug("UwsServlet.allowDirectPost: " + resource);
        return false;
    }
    
    /**
     * Parse the specified String value into another object before adding it to 
     * the job's param list. This method can be overridden by applications that 
     * want to parse parameter values into one of the parameter types supported 
     * by UwsParam (String, Number, Date, URI, or byte[]). If this method throws 
     * any exception, it will be caught and used to generate a message to go with
     * the HTTP 400 (Bad Request) response.
     * </p>
     * <p>
     * Note: The default implementation is a  no-op (returns the value argument),
     * so it just stores all param values as strings.
     * </p>
     * <p>
     * NOTE: This feature is currently not enabled and only String param values 
     * and resources are stored. 
     * 
     * @param key
     * @param value
     * @return the value, possibly converted to another supported type
     */
    protected Object parseParam(String key, String value)
        throws URISyntaxException
    {
        return value;
    }
    
    /**
     * Handle a GET request for the specified resource. This method must be implemented by a 
     * subclass of UwsServlet to handle custom resources within the job. A GET should never modify the job.
     * </p>
     * <p>
     * Note: The default implementation is a  no-op (does nothing).
     * 
     * @param html client accepts HTML output
     * @param resource the resource specified in the request
     * @param job an instance of UwsJob (or subclass) with the current state
     * @param request the complete request
     * @param response the response object to write to
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    protected void doGet(boolean html, String resource, UwsJob job, HttpServletRequest request, HttpServletResponse response)
     	throws ServletException, IOException
    {
        
    }
    
    /**
     * Handle a POST request for the specified resource. This method must be implemented by a 
     * subclass of UwsServlet to handle custom resources within the job. The implementation should return true if the
     * resource was modified and the new state should be saved. It should return false if the state 
     * did not change or if changes should be discarded.
     * </p>
     * <p>
     * Note: The default implementation isa  no-op (does nothing and returns false).
     * 
     * @param resource the resource specified in the request
     * @param job an instance of UwsJob (or subclass) with the current state
     * @param request
     * @param response
     * @return
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    protected boolean doPost(String resource, UwsJob job, HttpServletRequest request, HttpServletResponse response)
     	throws ServletException, IOException
    {
        return false;
    }
    
    /**
     * Handle a PUT request for the specified resource. This method must be implemented by a 
     * subclass of UwsServlet to handle custom resources within the job. The implementation should return true if the
     * resource was modified and the new state should be saved. It should return false if the state 
     * did not change or if changes should be discarded.
     * </p>
     * <p>
     * Note: The default implementation isa  no-op (does nothing and returns false).
     * 
     * @param resource the resource specified in the request
     * @param job an instance of UwsJob (or subclass) with the current state
     * @param request
     * @param response
     * @return
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    protected boolean doPut(String resource, UwsJob job, HttpServletRequest request, HttpServletResponse response)
     	throws ServletException, IOException
    {
        return false;
    }
    
    /**
     * Handle a DELETE request for the specified resource. This method must be implemented by a 
     * subclass of UwsServlet to handle custom resources within the job. The implementation should return true if the
     * resource was modified and the new state should be saved. It should return false if the state 
     * did not change or if changes should be discarded.
     * </p>
     * <p>
     * Note: The default implementation isa  no-op (does nothing and returns false).
     * 
     * @param resource the resource specified in the request
     * @param job an instance of UwsJob (or subclass) with the current state
     * @param request
     * @param response
     * @return
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    protected boolean doDelete(String resource, UwsJob job, HttpServletRequest request, HttpServletResponse response)
     	throws ServletException, IOException
    {
        return false;
    }
    
    /**
     * The state of this job just changed. Subclasses should override this method 
     * to react to state changes. For example, evaluate the state and possibly 
     * change the quote, termination, or destruction estimates. If the new
     * state is in error, then set the phase to UwsJob.PHASE_ERROR and create
     * the appropriate UwsError object. If the new state has cleared a previous error,
     * then remove the error and set the state back to UwsJob.PENDING.
     * </p>
     * <p>
     * Note: The default implementation logs the job at DEBUG level.
     * <b>You should call super.fireStateChangedEvent(job) at the end of
     * the overriding method in order to log the final state.</b>
     * 
     * @param job
     */
    protected void fireStateChangeEvent(UwsJob job)
    {
        log.debug("fireStateChangedEvent: " + job);
    }
    
    /**
     * Run the job. Subclasses MUST implement this method to either queue or execute the
     * job asynchronously and return as quickly as possible. The job phase will be set to
     * UwsJob.PHASE_QUEUED before this method is called. The fireStateChangedEvent(UwsJob) 
     * method will be called immediately after the run(UwsJob) method 
     * </p>
     * <p>
     * Note: The default implementation logs at info level and then it spawns a short-lived 
     * dummy job for testing purposes.
     * 
     * @param job
     */
    protected void run(UwsJob job)
    {
        log.info("run: " + job + "  " + this.getClass().getName());
        
        final UwsJob theJob = job;
            Thread t = new Thread(new Runnable()
                {
                    public void run()
                    {
                        log.info("run: sample job STARTED");
                        theJob.setStartTime(new Date());
                        try { Thread.sleep(10000); }
                        catch(InterruptedException ignore) { }
                        synchronized(theJob)
                        {
                            try
                            {
                                if ( !UwsJob.PHASE_ABORTED.equals(theJob.getPhase()) )
                                {
                                    theJob.setPhase(UwsJob.PHASE_COMPLETED);
                                    theJob.addResult("sample result", new URI("http://www.google.com/"));
                                    theJob.setEndTime(new Date());
                                }
                            }
                            catch(URISyntaxException ignore) { }
                            JobORM orm = new JobORM(serviceName, uwsSchema);
                            try { orm.put(theJob, UwsServlet.this.getJdbcTemplate()); }
                            catch(NamingException doh)
                            {
                                log.warn("failed to persist sample job state", doh);
                            }
                        }
                        log.info("run: sample job COMPLETE");
                    }
                }
            );
            t.start();
    }
    
    /**
     * Abort the job. Subclasses may implement this method to abort the job if that is feasible. 
     * The job phase will be set to UwsJob.PHASE_ABORTED before this method is called. The 
     * fireStateChangedEvent(UwsJob) method will be called immediately after the abort(UwsJob) 
     * method.
     * </p>
     * <p>
     * Note: The default implementation logs the abort and sets the endTime of the job.</b>
     * 
     * @param job
     */
    protected void abort(UwsJob job)
    {
        log.info("abort: " + job);
        job.setEndTime(new Date());
    }
    
    private void doErrorResponse(HttpServletResponse response, UwsError e)
        throws IOException
    {
        log.debug("doErrorResponse: " + e);
        log.debug(" error type=" + e.mimetype);
        log.debug(" error message=" + e.message);
        if (e == null || e.mimetype == null || e.message == null)
        {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(e.mimetype);
        Writer w = response.getWriter();
        w.write(e.message);
        w.write("\n");
        w.flush();
        w.close();
        log.debug("writing error DONE");
    }
    
    private void notifyOfResults(String jobID)
    {
        
        ConditionVar cv = (ConditionVar) listeners.get(jobID);
        if (cv != null)
        {
            log.debug("[notifyOfResults] " + jobID + ": notifying listener");
            cv.myNotify();
        }
        else
            log.debug("[notifyOfResults] " + jobID + ": no listener");
    }
    
    private UwsResult waitForResults(String jobID, JobORM orm, String listener)
        throws NamingException
    {
        // register and wait
        ConditionVar cv = null;
        UwsJob job = null;
        
        long t1 = System.currentTimeMillis();
        long dt = System.currentTimeMillis() - t1;
        while (dt < MAX_BLOCK_TIME)
        {
            log.debug("[waitForResults] checking " + jobID);
            job = orm.get(jobID, getJdbcTemplate());
            if (job.getPhase().equals(UwsJob.PHASE_COMPLETED))
            {
                log.debug("[waitForResults] completed: " + jobID);
                if (job.getResults().size() > 0)
                {
                    log.debug("[waitForResults] found result: " + jobID);
                    return (UwsResult) job.getResults().get(0);
                }
                log.debug("[waitForResults] no result: " + jobID);
                throw new IllegalStateException("phase=" + UwsJob.PHASE_COMPLETED + " with no results");
            }
            if (job.getPhase().equals(UwsJob.PHASE_ERROR))
            {
                log.debug("[waitForResults] error: " + jobID);
                return null;
            }
            if (job.getPhase().equals(UwsJob.PHASE_ABORTED))
            {
                log.debug("[waitForResults] aborted: " + jobID);
                return null;
            }
            
            
            boolean reg = false;
            if (cv == null)
            {
                // register a listener
                cv = new ConditionVar();
                cv.set(false);
                reg = true;
            }
            // TODO :safely set the CV false on each loop
            synchronized(cv)
            {
                
                if (reg)
                {
                    log.debug("[waitForResults] registering listener: " + listener);
                    listeners.put(jobID, cv);
                    orm.addListener(jobID, listener, getJdbcTemplate());
                }
                log.debug("[waitForResults] waiting: " + jobID + " ...");
                try { cv.myWait(10000L); } // 10 seconds
                catch(InterruptedException ex) { return null; }
            }
            dt = System.currentTimeMillis() - t1;
        }
        throw new RuntimeException("wait for results exceeded limit");
    }
}
