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


import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;


/**
 *  Sets up log4j for whichever webapp contains this
 *  servlet.
 *   
 *  To make sure the logging level gets set before any
 *  logging gets done, set load-on-startup to a smaller
 *  whole number than is used for any other servlet
 *  in this webapp.  This assumes Tomcat 5.5 or later.
 *  
 *  For now get the level from a web.xml init-param.
 *  Eventually it would be nice to be able to change
 *  it while the program is running.
 *
 *  It should also be possible to use PropertyUtil in
 *  a static block to get the log level from a Java -D
 *  property set by tomcatEnv or from the WEB-INF/classes
 *  directory.
 *  
 *  The log level controls the logging level of those
 *  packages listed in an init-param.
 *  
 *  By default, log4j assigns the root logger to leve.DEBUG.
 */
public class LogControlServlet extends HttpServlet
{
	private static final long serialVersionUID = 200909091014L;

	private static final Logger logger = Logger.getLogger( LogControlServlet.class );;

	private static final Level DEFAULT_LEVEL = Level.DEBUG;
	
	private static final String LONG_FORMAT = "%d{ABSOLUTE} [%t] %-5p %c{1} %x - %m\n";
	
	private static final String LOG_LEVEL_PARAM = "logLevel";
	private static final String PACKAGES_PARAM  = "logLevelPackages";

	private static Level level = null;
	
    /**
     *  Initialize the logging.  This method should only get
     *  executed once and, if properly configured, it should
     *  be the first method to be executed.
     */
	public void init( final ServletConfig config ) throws ServletException
    {
    	super.init( config );

		//  Log all classes at this level except where a
    	//  different level is specified in the web.xml file.
    	Logger.getRootLogger().setLevel( Level.INFO );

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
    	
    	//  Set the specified packages to that level.
    	String packageParamValues = config.getInitParameter( PACKAGES_PARAM );
    	String[] packageNames = packageParamValues.split( "\\s" ); // whitespace
    	if ( packageNames != null )
    	{
    		for ( int i=0; i<packageNames.length; i++ )
    		{
        		String pkg = packageNames[i].trim();
        		if ( pkg.length() > 0 )
        		{
        			Logger.getLogger( pkg ).setLevel( level );
        		}
    		}
    	}

    	ConsoleAppender appender = new ConsoleAppender( new PatternLayout(LONG_FORMAT) );
		BasicConfigurator.configure( appender );
		
		logger.info( "Logging initialized at level="+level );
    }
    
}
