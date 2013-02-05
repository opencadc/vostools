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


package ca.nrc.cadc.log;


import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import ca.nrc.cadc.util.Log4jInit;


/**
 * Sets up log4j for whichever webapp contains this
 * servlet. To make sure the logging level gets set before any
 * logging gets done, set load-on-startup to a smaller
 * whole number than is used for any other servlet
 * in the webapp.
 * </p><p>
 * The initial level is set with an init-param named 
 * <code>logLevel</code> and value equivalent to one of the
 * log4j levels (upper case, eg INFO).
 * </p><p>
 * The initially configered packages are set with an init-param
 * named <code>logLevelPackages</code> and value of whitespace-separated
 * package names.
 * </p><p>
 * The current configuration can be retrieved with an HTTP GET. 
 * </p><p>
 * The configuration can be modified with an HTTP PIOST to this servlet. 
 * The currently supported params are <code>level</code> (for example,
 * level=DEBUG) and <code>package</code> (for example, package=ca.nrc.cadc.log).
 * The level parameter is required. The package parameter is optional and 
 * may specify a new package to configure
 * or a change in level for an existing package; if no packages are specified, the
 * level is changed for all previously configured packages.
 */
public class LogControlServlet extends HttpServlet
{
	private static final long serialVersionUID = 200909091014L;

	private static final Logger logger = Logger.getLogger( LogControlServlet.class);

	private static final Level DEFAULT_LEVEL = Level.INFO;
	
	private static final String LOG_LEVEL_PARAM = "logLevel";
	private static final String PACKAGES_PARAM  = "logLevelPackages";

	private Level level = null;
	private List<String> packages;

    /**
     *  Initialize the logging.  This method should only get
     *  executed once and, if properly configured, it should
     *  be the first method to be executed.
     *
     * @param config
     * @throws ServletException
     */
    @Override
	public void init( final ServletConfig config ) throws ServletException
    {
    	super.init( config );
        this.packages = new ArrayList<String>();

    	//  Determine the desired logging level.
    	String levelVal = config.getInitParameter( LOG_LEVEL_PARAM );
    	if ( levelVal == null )
    		level = DEFAULT_LEVEL;
    	else if ( levelVal.equalsIgnoreCase(Level.TRACE.toString()) )
    		level = Level.TRACE;
    	else if ( levelVal.equalsIgnoreCase(Level.DEBUG.toString()) )
    		level = Level.DEBUG;
    	else if ( levelVal.equalsIgnoreCase(Level.INFO.toString()) )
    		level = Level.INFO;
    	else if ( levelVal.equalsIgnoreCase(Level.WARN.toString()) )
    		level = Level.WARN;
    	else if ( levelVal.equalsIgnoreCase(Level.ERROR.toString()) )
    		level = Level.ERROR;
    	else if ( levelVal.equalsIgnoreCase(Level.FATAL.toString()) )
    		level = Level.FATAL;
    	else
    		level = DEFAULT_LEVEL;
    	
    	String webapp = config.getServletContext().getServletContextName();
    	if (webapp == null) webapp = "[?]";
    	    
        String thisPkg = LogControlServlet.class.getPackage().getName();
        Log4jInit.setLevel(webapp, thisPkg, level);
        packages.add(thisPkg);
        logger.info("log level: " + thisPkg + " =  " + level);
        
        String packageParamValues = config.getInitParameter( PACKAGES_PARAM );
        if (packageParamValues != null)
        {
            StringTokenizer stringTokenizer = new StringTokenizer(packageParamValues, " \n\t\r", false);
            while (stringTokenizer.hasMoreTokens())
            {
                String pkg = stringTokenizer.nextToken();
                if ( pkg.length() > 0 )
                {
                    Log4jInit.setLevel(webapp, pkg, level);
                    logger.info("log level: " + pkg + " =  " + level);
                    if (!packages.contains(pkg))
                        packages.add(pkg);
                }
            }
        }

        // these are here to help detect problems with logging setup
        logger.info("init complete");
        logger.debug("init complete -- YOU SHOULD NEVER SEE THIS MESSAGE");
    }

    /**
     * In response to an HTTP GET, return the current logging level and the list
	 * of packages for which logging is enabled.
     * 
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
	{
        //Subject subject = AuthenticationUtil.getSubject(request);
        //logger.debug(subject.toString());

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/plain");
        PrintWriter writer = response.getWriter();

        //writer.println("Logging level " + level + " set on " + packageNames.length + " packages:");
        for (String pkg : packages)
        {
            Logger log = Logger.getLogger(pkg);
        	writer.println(pkg + " " + log.getLevel());
        }
        
        writer.close();
	}
	
    /**
     * Allows the caller to set the log level (e.g. with the level=DEBUG parameter).
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
	{
        //Subject subject = AuthenticationUtil.getSubject(request);
        //logger.debug(subject.toString());

        String[] params = request.getParameterValues("level");
        String levelVal = null;
        if (params != null && params.length > 0)
            levelVal = params[0];
        if (levelVal != null)
        {
            if ( levelVal == null )
                level = DEFAULT_LEVEL;
            else if ( levelVal.equalsIgnoreCase(Level.TRACE.toString()) )
                level = Level.TRACE;
            else if ( levelVal.equalsIgnoreCase(Level.DEBUG.toString()) )
                level = Level.DEBUG;
            else if ( levelVal.equalsIgnoreCase(Level.INFO.toString()) )
                level = Level.INFO;
            else if ( levelVal.equalsIgnoreCase(Level.WARN.toString()) )
                level = Level.WARN;
            else if ( levelVal.equalsIgnoreCase(Level.ERROR.toString()) )
                level = Level.ERROR;
            else if ( levelVal.equalsIgnoreCase(Level.FATAL.toString()) )
                level = Level.FATAL;
            else
            {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("text/plain");
                PrintWriter writer = response.getWriter();
                writer.println("unrecognised value for level: " + levelVal);
                writer.close();
            }
        }
        
        String[] pkgs = request.getParameterValues("package");
        if (pkgs != null)
        {
            String dnt = request.getParameter("notrack");
            boolean track = (dnt == null);
            for (String p : pkgs)
            {
                logger.info("setLevel: " + p + " -> " + level);
                Log4jInit.setLevel(p, level);
                if (!packages.contains(p) && track)
                    packages.add(p);
            }
        }
        else // all currently configured packages
        {
            for (String p : packages)
            {
                logger.info("setLevel: " + p + " -> " + level);
                Log4jInit.setLevel(p, level);
            }
        }
        
        // redirect the caller to the resulting settings
        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        String url = request.getRequestURI();
        response.setHeader("Location", url);
    }
}
