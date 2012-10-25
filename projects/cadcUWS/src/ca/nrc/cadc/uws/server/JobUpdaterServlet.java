/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2011.                            (c) 2011.
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
*  $Revision: 5 $
*
************************************************************************
*/

package ca.nrc.cadc.uws.server;

import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.util.Base64;
import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.ErrorType;
import ca.nrc.cadc.uws.ExecutionPhase;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Date;
import javax.security.auth.Subject;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;

/**
 * Simple servlet that accepts a remote call (POST) to update the job phase using a
 * <code>JobUpdater</code> implementation.
 * </p><p>
 * This servlet requires 1 init param to be set to specify the class names that implement
 * the required interface. The <code>param-name</code> specifies the interface and
 * the <code>param-value</code> is the class name that implements the interface. This class
 * must have a public no-arg constructor.
 * For example:
 * </p><p>
 * <pre>
 *      <init-param>
 *          <param-name>ca.nrc.cadc.uws.JobUpdater</param-name>
 *          <param-value>com.example.uws.MyJobUpdater</param-value>
 *      </init-param>
 *
 * @author pdowler
 */
public class JobUpdaterServlet extends HttpServlet
{
    private static Logger log = Logger.getLogger(JobUpdaterServlet.class);
    private static final long serialVersionUID = 201206061200L;

    private JobUpdater jobUpdater;

    @Override
    public void init(ServletConfig config)
        throws ServletException
    {
        super.init(config);

        try
        {
            this.jobUpdater = createJobUpdater(config);
        }
        catch(Exception ex)
        {
            log.error("failed to init: " + ex);
        }
    }

    protected JobUpdater createJobUpdater(ServletConfig config)
    {
        String pname = JobUpdater.class.getName();
        try
        {
            String cname = config.getInitParameter(pname);
            //if (cname == null) // try a context param
            //    cname = config.getServletContext().getInitParameter(pname);
            if (cname != null && cname.trim().length() > 0)
            {
                Class c = Class.forName(cname);
                JobUpdater ret = (JobUpdater) c.newInstance();
                log.info("created JobUpdater: " + ret.getClass().getName());
                return ret;
            }
            else
                log.error("CONFIGURATION ERROR: required init-param not found: " + pname + " = <class name of JobUpdater implementation>");
        }
        catch(Exception ex)
        {
            log.error("failed to create JobUpdater", ex);
        }
        return null;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
        throws ServletException, IOException
    {
        log.debug("doGet - START");
        doit(true, request, response);
        log.debug("doGet - DONE");
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
        throws ServletException, IOException
    {
        log.debug("doPost - START");
        doit(false, request, response);
        log.debug("doPost - DONE");
    }

    private void doit(final boolean get, final HttpServletRequest request, final HttpServletResponse response)
        throws ServletException, IOException
    {
        if (jobUpdater == null)
        {
            // config error
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain");
            PrintWriter w = response.getWriter();
            w.println("servlet is not configured to accept requests");
            w.close();
            return;
        }

        Subject subject = AuthenticationUtil.getSubject(request);
        if (subject == null)
        {
            processRequest(get, request, response);
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
                            processRequest(get, request, response);
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

    private void processRequest(boolean get, HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        String jobID = null;
        try
        {
            // GET: <jobID>
            // POST:
            // <jobID>/<current phase>/<new phase>
            // <jobID>/<current phase>/ERROR/<base64 encoded message>/<errorType>
            jobID = getPathToken(request, 0);
            if (jobID == null)
                throw new IllegalArgumentException("jobID not specified");

            if (get)
            {
                ExecutionPhase ep = jobUpdater.getPhase(jobID);
                log.debug("GET: " + jobID + " " + ep.name());
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/plain");
                PrintWriter w = response.getWriter();
                w.println(ep.getValue());
                w.close();
                return;
            }

            String phase = getPathToken(request, 1);
            if (phase == null)
                throw new IllegalArgumentException("current phase not specified");
            ExecutionPhase cur = ExecutionPhase.valueOf(phase);

            phase = getPathToken(request, 2);
            if (phase == null)
                throw new IllegalArgumentException("new phase not specified");
            ExecutionPhase end = ExecutionPhase.valueOf(phase);

            ErrorSummary err = null;
            if (ExecutionPhase.ERROR.equals(end))
            {
                String base64 = getPathToken(request, 3);
                String type = getPathToken(request, 4);
                if (base64 != null && type != null)
                {
                    String msg = Base64.decodeString(base64);
                    ErrorType et = ErrorType.valueOf(type);
                    err = new ErrorSummary(msg, et);
                }
            }

            log.debug("changing phase of " + jobID + " to " + end);
            ExecutionPhase result = null;
            if (err != null)
                result = jobUpdater.setPhase(jobID, cur, end, err, new Date());
            else
                result = jobUpdater.setPhase(jobID, cur, end, new Date());

            if (result == null)
            {
                ExecutionPhase actual = jobUpdater.getPhase(jobID);
                log.debug("cannot change phase of " + jobID + " from " + cur + " to " + end + "(was: " + actual + ") [FAIL]");
                throw new IllegalArgumentException("cannot change phase of " + jobID + " from " + cur + " to " + end + "(was: " + actual + ")");
            }
            log.debug("changed phase of " + jobID + " to " + end + " [OK]");
            response.setStatus(HttpServletResponse.SC_OK);
        }
        catch(IllegalArgumentException ex)
        {
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
            // OutputStream not open, write an error response
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("text/plain");
            PrintWriter w = response.getWriter();
            w.println("failed to find job: " + jobID);
            w.close();
            return;
        }
        catch(TransientException ex)
        {
        	if (!response.isCommitted())
        	{  
	            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
	            response.addHeader("Retry-After", Integer.toString(ex.getRetryDelay()));
	            response.setContentType("text/plain");
	            PrintWriter w = response.getWriter();
	            w.println("failed to persist job: " + jobID);
	            w.println("   reason: " + ex.getMessage());
	            w.close();
	            return;
        	}
        	
        	log.error("response already committed", ex);
        	return;
        }
        catch(JobPersistenceException ex)
        {
            // OutputStream not open, write an error response
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain");
            PrintWriter w = response.getWriter();
            w.println("failed to persist job: " + jobID);
            w.println("   reason: " + ex.getMessage());
            w.close();
            return;
        }
    }

    private String getPathToken(HttpServletRequest request, int n)
    {
        String path = request.getPathInfo();
        if (path == null)
            return null;
        if (path.startsWith("/"))
            path = path.substring(1);
        String[] parts = path.split("/");
        log.debug("path: " + path + " " + parts.length + " parts, n=" + n);
        if (n > parts.length - 1)
            return null;
        return parts[n];
    }
}
