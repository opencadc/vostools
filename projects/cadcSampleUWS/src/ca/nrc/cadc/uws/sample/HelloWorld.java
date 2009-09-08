/*****************************************************************************
 *  
 *  Copyright (C) 2009				Copyright (C) 2009
 *  National Research Council		Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6			Ottawa, Canada, K1A 0R6
 *  All rights reserved				Tous droits reserves
 *  					
 *  NRC disclaims any warranties,	Le CNRC denie toute garantie
 *  expressed, implied, or statu-	enoncee, implicite ou legale,
 *  tory, of any kind with respect	de quelque nature que se soit,
 *  to the software, including		concernant le logiciel, y com-
 *  without limitation any war-		pris sans restriction toute
 *  ranty of merchantability or		garantie de valeur marchande
 *  fitness for a particular pur-	ou de pertinence pour un usage
 *  pose.  NRC shall not be liable	particulier.  Le CNRC ne
 *  in any event for any damages,	pourra en aucun cas etre tenu
 *  whether direct or indirect,		responsable de tout dommage,
 *  special or general, consequen-	direct ou indirect, particul-
 *  tial or incidental, arising		ier ou general, accessoire ou
 *  from the use of the software.	fortuit, resultant de l'utili-
 *  								sation du logiciel.
 *  
 *  
 *  This file is part of cadcSampleUWS.
 *  
 *  CadcSampleUWS is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  CadcSampleUWS is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with cadcSampleUWS.  If not, see <http://www.gnu.org/licenses/>.			
 *  
 *****************************************************************************/


package ca.nrc.cadc.uws.sample;


import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import ca.nrc.cadc.net.NetUtil;
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
	private static final Logger log = Logger.getLogger(HelloWorld.class);
	
	public static String PASS = "PASS";
    public static String RUNFOR = "RUNFOR";
	
	private Job job;

    public HelloWorld()
    {
        try
        {
            //  Set up log4j.  For now hard-code the debug level.
        	//  Eventually it would be nice to be able to change
        	//  it while the program is running.
            //
            //  It should also be possible to use PropertyUtil in
        	//  a static block to get the log level from a Java -D
        	//  property set by tomcatEnv.
            //
            final String thisJavaPkg = HelloWorld.class.getPackage().getName();
            final String logLevel    = "debug";
            final String [] args     = { "-"+logLevel };
            //final String logFileBaseName = LoggerUtil.getTimestampLogName( "cadcSampleUWS" );
			//LoggerUtil.initialize( new String[] { thisJavaPkg, "ca.nrc.cadc.ad" }, args, logFileBaseName );
	        log.info( "Initialized logging to level="+logLevel+" for package: "+thisJavaPkg );
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
        log.debug("START");

        try
        {
            doit();
        }
        finally
        {
            log.debug("DONE");
        }
    }
    
    private void doit()
    {
        try
        {
            String server = NetUtil.getServerName( this.getClass() );
            log.debug( "server="+server );

            // parse params
            List<Parameter> params = job.getParameterList();
            if (params == null)
            {
				log.error("Missing param list");
				ErrorSummary error = new ErrorSummary("ERROR: No param list found in job", new URI("http://"+server+"/cadcSampleUWS/error.txt") );
				job.setErrorSummary(error);
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
                    log.debug("ignoring unexepcted param: " + p);
            }

            boolean pass = true;
            if ( passP != null )
                try 
                { 
                    pass = Boolean.parseBoolean(passP.getValue()); 
                    log.debug( "pass="+pass );
                }
                catch(Throwable oops)
                {
                    log.warn("failed to parse PASS parameter, found " + passP.getValue() 
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
                    log.warn("failed to parse RUNFOR parameter, found " + runforP.getValue() 
                            + ", expected an integer");
                }
            if (runfor < 0)
            {
				log.debug( "Negative run time: "+runfor );
            	ErrorSummary error = new ErrorSummary("ERROR: RUNFOR param < 0", new URI("http://"+server+"/cadcSampleUWS/error.txt") );
				job.setErrorSummary(error);
				return;
            }

            log.debug("sleeping for " + runfor + " seconds");
			Thread.sleep( runfor * 1000L );
			
            if (pass)
            {
                Result result = new Result( "RESULT", new URL("http://"+server+"/cadcSampleUWS/result.txt") );
                ArrayList<Result> resultList = new ArrayList<Result>();
                resultList.add( result );
                job.setResultsList( resultList );
                log.debug( "Having slept and being told to pass, invoke persistence here..." );
                // TODO: invoke JobPersistence somehow
                return;
            }
            else
			{
                log.debug( "Having slept and being told to fail, construct error" );
				ErrorSummary error = new ErrorSummary("error from PASS=false", new URI("http://"+server+"/cadcSampleUWS/error.txt") );
				job.setErrorSummary(error);
				return;
			}
		}
        catch (Throwable t)
        {
			ErrorSummary error = new ErrorSummary(t.getMessage(), null );
			job.setErrorSummary( error );
			return;
		}
    }

    public Job getJob()
    {
        return job;
    }
    
}
