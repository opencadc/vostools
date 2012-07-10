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

package ca.nrc.cadc.uws.server;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.security.auth.Subject;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.io.ByteLimitExceededException;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobWriter;
import ca.nrc.cadc.uws.web.InlineContentHandler;
import ca.nrc.cadc.uws.web.JobCreator;

/**
 * Servlet that runs a SyncJobRunner for each request. This servlet supports both
 * GET and POST, creates and persists a job and issues a redirect to cause execution.
 * </p><p>
 * This servlet requires 1 init params to be set to specify the class names that implements
 * the JobManager interface. The <code>param-name</code> specifies the interface and
 * the <code>param-value</code> is the class name that implements the interface. This class
 * must have a public no-arg constructor.
 * For example:
 * </p><p>
 * <pre>
 *      <init-param>
 *          <param-name>ca.nrc.cadc.uws.JobManager</param-name>
 *          <param-value>ca.nrc.cadc.uws.SimpleJobManager</param-value>
 *      </init-param>
 *
 * </pre>
 *
 * @author pdowler
 */
public class SyncServlet extends HttpServlet
{
    private static Logger log = Logger.getLogger(SyncServlet.class);
    private static final long serialVersionUID = 201009291100L;
    private static final String JOB_EXEC = "run";
    
    private JobManager jobManager;
    private Class inlineContentHandlerClass;
    private boolean execOnGET = false;
    private boolean execOnPOST = false;
    
    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);

        try
        {
            String str = config.getInitParameter(SyncServlet.class.getName() + ".execOnGET");
            if (str !=null)
                try { execOnGET = Boolean.parseBoolean(str); }
                catch(Exception ignore) { }
            str = config.getInitParameter(SyncServlet.class.getName() + ".execOnPOST");
            if (str !=null)
                try { execOnPOST = Boolean.parseBoolean(str); }
                catch(Exception ignore) { }
            log.info("execOnGET: " + execOnGET);
            log.info("execOnPOST: " + execOnPOST);

            jobManager = createJobManager(config);
            
            String cname = InlineContentHandler.class.getName();
            String pname = config.getInitParameter(cname);
            if (pname == null)
                log.info("CONFIGURATION INFO: init-param not found: " + cname + " = <class name of InlineContentHandler implementation>");
            else
            {
                try
                {
                    inlineContentHandlerClass = Class.forName(pname);
                    InlineContentHandler ret = (InlineContentHandler) inlineContentHandlerClass.newInstance();
                    log.info("created InlineContentHandler: " + ret.getClass().getName());
                }
                catch (Exception e)
                {
                    log.error("CONFIGURATION ERROR: error loading class: " + cname + " = <class name of InlineContentHandler implementation>", e);
                }
            }
        }
        catch(Exception ex)
        {
            log.error("failed to init: " + ex);
        }
    }

    /**
     * Instantiate a JobManager. The default implementation simply looks for a
     * servlet init-param with name <code>ca.nrc.cadc.uws.server.JobManager</code>
     * and value equal to the class name of the implementation, loads the class
     * with <code>Class.forName</code> (assuming no-arg constructor), and uses it
     * as-is.
     * </p>
     * <p>
     * To create the JobManager in some other fashion (e.g. dependency injection or
     * some pre-configured IoC container) simply override this method to return a
     * ready-to-use JobManager.
     *
     * @param config
     * @return
     */
    protected JobManager createJobManager(ServletConfig config)
    {
        String pname = JobManager.class.getName();
        try
        {
            String cname = config.getInitParameter(pname);
            //if (cname == null) // try a context param
            //    cname = config.getServletContext().getInitParameter(pname);
            if (cname != null && cname.trim().length() > 0)
            {
                Class c = Class.forName(cname);
                JobManager ret = (JobManager) c.newInstance();
                log.info("created JobManager: " + ret.getClass().getName());
                return ret;
            }
            else
                log.error("CONFIGURATION ERROR: required init-param not found: " + pname + " = <class name of JobManager implementation>");
        }
        catch(Exception ex)
        {
            log.error("failed to create JobManager", ex);
        }
        return null;
    }

    protected JobCreator getJobCreator()
    {
        return new JobCreator(getInlineContentHandler());
    }

    protected InlineContentHandler getInlineContentHandler()
    {
        InlineContentHandler handler = null;
        if (inlineContentHandlerClass != null)
        {
            try
            {
                handler = (InlineContentHandler) inlineContentHandlerClass.newInstance();
            }
            catch (Throwable t)
            {
                log.error("Unable to create inline content handler ", t);
            }
        }
        return handler;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        log.debug("doGet - START");
        doit(execOnGET, request, response);
        log.debug("doGet - DONE");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        log.debug("doPost - START");
        doit(execOnPOST, request, response);
        log.debug("doPost - DONE");
    }

    private void doit(final boolean execOnCreate, final HttpServletRequest request, final HttpServletResponse response)
        throws ServletException, IOException
    {
        log.debug("doit: execOnCreate=" + execOnCreate);
        if (jobManager == null)
        {
            // config error
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain");
            PrintWriter w = response.getWriter();
            w.println("servlet is not configured to accept jobs");
            w.close();
            return;
        }

        Subject subject = AuthenticationUtil.getSubject(request);
        if (subject == null)
        {
            processRequest(execOnCreate, request, response);
        }
        else
        {
            try
            {
                Subject.doAs(subject, new PrivilegedExceptionAction<Object>()
                {
                    public Object run()
                        throws PrivilegedActionException
                    {
                        try
                        {
                            processRequest(execOnCreate, request, response);
                            return null;
                        }
                        catch(Exception ex)
                        {
                            throw new PrivilegedActionException(ex);
                        }
                    }
                } );
            }
            catch(PrivilegedActionException pex)
            {
                if (pex.getCause() instanceof ServletException)
                    throw (ServletException) pex.getCause();
                else if (pex.getCause() instanceof IOException)
                    throw (IOException) pex.getCause();
                else if (pex.getCause() instanceof RuntimeException)
                    throw (RuntimeException) pex.getCause();
                else
                    throw new RuntimeException(pex.getCause());
            }
        }
    }

    private void processRequest(boolean execOnCreate, HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        log.debug("doit: execOnCreate=" + execOnCreate);
        SyncOutputImpl syncOutput = null;
        
        String jobID = null;
        Job job = null;
        String action = null;
        try
        {
            jobID = getJobID(request);
            if (jobID == null)
            {
                // create
                job = getJobCreator().create(request);
                job = jobManager.create(job);
                log.debug("persisted job: " + job);
                jobID = job.getID();

                
                log.info("created job: " + jobID);
                if (execOnCreate)
                {
                    log.debug("no redirect, action = " + JOB_EXEC);
                    action = JOB_EXEC;
                }
                else // redirect
                {
                    String jobURL = getJobURL(request, job.getID());
                    String execURL = jobURL + "/" + JOB_EXEC;
                    log.debug("redirect: " + execURL);
                    response.setHeader("Location", execURL);
                    response.setStatus(HttpServletResponse.SC_SEE_OTHER);
                    return;
                }
            }
            else
                // get job from persistence
                job = jobManager.get(jobID);
                
            // set the protocol of the request
            job.setProtocol(request.getScheme());

            log.debug("found: " + jobID);

            if (action == null)
                action = getJobAction(request);

            if (action == null)
            {
                log.info("dumping job: " + jobID);
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/xml");
                JobWriter w = new JobWriter();
                w.write(job, new SafeOutputStream(response.getOutputStream()));
                return;
            }

            if ( !JOB_EXEC.equals(action) ) // this is the only valid action
            {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.setContentType("text/plain");
                PrintWriter w = response.getWriter();
                w.println("not found: " + jobID + "/" + action);
                w.close();
                return;
            }

            log.info("executing job: " + jobID);
            syncOutput = new SyncOutputImpl(response);
            jobManager.execute(job, syncOutput);
        }
        catch(JobPhaseException ex)
        {
            if (syncOutput != null && syncOutput.isOpen())
            {
                log.error("failure after OutputStream opened, cannot report error to user");
                return;
            }
            // OutputStream not open, write an error response
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain");
            PrintWriter w = response.getWriter();
            w.println(ex.getMessage());
            w.close();
            return;
        }
        catch(JobNotFoundException ex)
        {
            if (syncOutput != null && syncOutput.isOpen())
            {
                log.error("failure after OutputStream opened, cannot report error to user");
                return;
            }
            // OutputStream not open, write an error response
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("text/plain");
            PrintWriter w = response.getWriter();
            w.println("failed to find " + jobID);
            w.close();
            return;
        }
        catch(JobPersistenceException ex)
        {
            if (syncOutput != null && syncOutput.isOpen())
            {
                log.error("failure after OutputStream opened, cannot report error to user");
                return;
            }
            // OutputStream not open, write an error response
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain");
            PrintWriter w = response.getWriter();
            w.println("failed to get or persist job state: " + jobID);
            w.println("   reason: " + ex.getMessage());
            w.close();
            return;
        }
        catch(ByteLimitExceededException ex)
        {
            if (syncOutput != null && syncOutput.isOpen())
            {
                log.error("failure after OutputStream opened, cannot report error to user");
                return;
            }
            // OutputStream not open, write an error response
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("text/plain");
            PrintWriter w = response.getWriter();
            w.println("failed to execute job " + jobID);
            w.println("   reason: XML document exceeds " + ex.getLimit() + " bytes");
            w.close();
            return;
        }
        catch(IllegalArgumentException ex)
        {
            if (syncOutput != null && syncOutput.isOpen())
            {
                log.error("failure after OutputStream opened, cannot report error to user");
                return;
            }
            // OutputStream not open, write an error response
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain");
            PrintWriter w = response.getWriter();
            w.println("failed to execute job " + jobID);
            w.println("   reason: " + ex.getMessage());
            w.close();
            return;
        }
        catch(Throwable t)
        {
            if (jobID == null)
                log.error("create job failed", t);
            else
                log.error("execute job failed", t);
            if (syncOutput != null && syncOutput.isOpen() )
            {
                log.error("unexpected failure after OutputStream opened", t);
                return;
            }
            // OutputStream not open, write an error response
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain");
            PrintWriter w = response.getWriter();
            w.println("job " + jobID + " failed unexpectedly: ");
            // show user the stack trace? no
            //t.printStackTrace(w);
            w.close();
            return;
        }
        finally
        {
            if (syncOutput != null && syncOutput.isOpen())
                try
                {
                    OutputStream ostream = syncOutput.getOutputStream();
                    ostream.flush();
                }
                catch(Throwable ignore) { }
        }
    }

    private class SyncOutputImpl implements SyncOutput
    {
        OutputStream ostream;
        HttpServletResponse response;

        SyncOutputImpl(HttpServletResponse response)
        {
            this.response = response;
        }

        public boolean isOpen() { return ostream != null; }
        
        public OutputStream getOutputStream()
            throws IOException
        {
            if (ostream == null)
            {
                log.debug("opening OutputStream");
                ostream = new SafeOutputStream(response.getOutputStream());
            }
            return ostream;
        }

        public void setResponseCode(int code)
        {
            if (ostream == null) // header not committed
                response.setStatus(code);
            else
                log.warn("setResponseCode: " + code + " AFTER OutputStream opened, ignoring");
        }
        public void setHeader(String key, String value)
        {
            if (ostream == null) // header not committed
                response.setHeader(key, value);
            else
                log.warn("setHeader: " + key + " = " + value + " AFTER OutputStream opened, ignoring");
        }
    }

    private String getJobID(HttpServletRequest request)
    {
        String path = request.getPathInfo();
        log.debug("path: " + path);
        // path can be null, <jobID> or <jobID>/exec
        if (path == null)
            return null;
        if (path.startsWith("/"))
            path = path.substring(1);
        String[] parts = path.split("/");
        log.debug("path: " + path + " jobID: " + parts[0]);
        return parts[0];
    }

    private String getJobAction(HttpServletRequest request)
    {
        String path = request.getPathInfo();
        log.debug("path: " + path);
        // path can be null, <jobID> or <jobID>/<token>
        if (path == null)
            return null;
        if (path.startsWith("/"))
            path = path.substring(1);
        String[] parts = path.split("/");
        String ret = null;
        if (parts.length == 2)
            ret = parts[1];
        log.debug("path: " + path + " jobAction: " + ret);
        return ret;
    }

    private String getJobURL(HttpServletRequest request, String jobID)
    {
        StringBuffer sb = request.getRequestURL();
        log.debug("request URL: " + sb);
        if ( sb.charAt(sb.length()-1) != '/' )
            sb.append("/");
        sb.append(jobID);
        return sb.toString();
    }

    private class SafeOutputStream extends FilterOutputStream
    {
        SafeOutputStream(OutputStream ostream) { super(ostream); }

        @Override
        public void close()
            throws IOException
        {
            // must not let the JobRunner call close on the OutputStream!!!
        }
    }
}
