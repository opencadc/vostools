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


package ca.nrc.cadc.uws.sample;


import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import ca.nrc.cadc.net.NetUtil;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.JobRunner;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.Result;


/**
 * Basic Job Runner class. The sample job takes two params:</p>
 * <ul>
 * <li>RUNFOR=<number of seconds to run> to control how long it runs
 * <li>PASS=<true|false> to control whether it succeeds or fails
 * </ul>
 */
public class HelloWorld implements JobRunner
{
	private static final Logger logger = Logger.getLogger(HelloWorld.class);
	
	public static String PASS   = "PASS";
    public static String RUNFOR = "RUNFOR";
	
	private Job job;

    public HelloWorld()
    {
        try
        {
        	logger.info( "Initializing sample JobRunner" );
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            return;
        }
    }

    /**
     * Set the Job that this Runner is currently responsible for.
     *
     * @param job The Job to run.
     */
    public void setJob( final Job job )
    {
        this.job = job;
    }

    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p/>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    public void run()
    {
        logger.debug("START");

        try
        {
        	doit();
        }
        finally
        {
            logger.debug("DONE");
        }
    }
    
    private void doit()
    {
        try
        {
            job.setExecutionPhase( ExecutionPhase.EXECUTING );

            String server = NetUtil.getServerName( this.getClass() );
            logger.debug( "server="+server );

            // parse params
            List<Parameter> params = job.getParameterList();
            if (params == null)
            {
				logger.error("Missing param list");
				ErrorSummary error = new ErrorSummary("ERROR: No param list found in job", new URI("http://"+server+"/cadcSampleUWS/error.txt") );
				job.setErrorSummary(error);
	            job.setExecutionPhase( ExecutionPhase.ERROR );
				return;
            }
            Parameter passP = null;
            Parameter runforP = null;
            Iterator<Parameter> i = params.iterator();
            while ( i.hasNext() )
            {
                Parameter p = i.next();
                String s = p.getName().toUpperCase();
                if (s.equals(PASS))
                    passP = p;
                else if ( s.equals(RUNFOR))
                    runforP = p;
                else
                    logger.debug("ignoring unexepcted param: " + p);
            }

            boolean pass = true;
            if ( passP != null )
                try 
                { 
                    pass = Boolean.parseBoolean(passP.getValue()); 
                    logger.debug( "pass="+pass );
                }
                catch(Throwable oops)
                {
                    logger.warn("failed to parse PASS parameter, found " + passP.getValue() 
                            + ", expected " + Boolean.TRUE + " or " + Boolean.FALSE);
                }
            int runfor = -1;
            if (runforP != null)
                try 
                { 
                    runfor = Integer.parseInt(runforP.getValue()); 
                }
                catch(Throwable oops)
                {
                    logger.warn("failed to parse RUNFOR parameter, found " + runforP.getValue() 
                            + ", expected an integer");
                }
            if (runfor < 0)
            {
				logger.debug( "Negative run time: "+runfor );
            	ErrorSummary error = new ErrorSummary("ERROR: RUNFOR param less than 0", new URI("http://"+server+"/cadcSampleUWS/error.txt") );
				job.setErrorSummary(error);
	            job.setExecutionPhase( ExecutionPhase.ERROR );
				return;
            }

            logger.debug("sleeping for " + runfor + " seconds");
			Thread.sleep( runfor * 1000L );
			
            if (pass)
            {
                Result result = new Result( "RESULT", new URL("http://"+server+"/cadcSampleUWS/result.txt") );
                ArrayList<Result> resultList = new ArrayList<Result>();
                resultList.add( result );
                job.setResultsList( resultList );
                logger.debug( "Having slept and being told to pass, invoke persistence here..." );
                // TODO: invoke JobPersistence somehow
	            job.setExecutionPhase( ExecutionPhase.COMPLETED );
                return;
            }
            else
			{
                logger.debug( "Having slept and being told to fail, construct error" );
				ErrorSummary error = new ErrorSummary("error from PASS=false", new URI("http://"+server+"/cadcSampleUWS/error.txt") );
				job.setErrorSummary(error);
	            job.setExecutionPhase( ExecutionPhase.ERROR );
				return;
			}
		}
        catch (Throwable t)
        {
			ErrorSummary error = new ErrorSummary(t.getMessage(), null );
			job.setErrorSummary( error );
            job.setExecutionPhase( ExecutionPhase.ERROR );
			return;
		}
    }

    public Job getJob()
    {
        return job;
    }
    
}
