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

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;

import ca.nrc.cadc.net.NetUtil;
import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.ErrorType;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobInfo;
import ca.nrc.cadc.uws.ParameterUtil;
import ca.nrc.cadc.uws.Result;
import ca.nrc.cadc.uws.server.JobRunner;
import ca.nrc.cadc.uws.server.JobUpdater;
import ca.nrc.cadc.uws.server.SyncOutput;
import ca.nrc.cadc.xml.XmlUtil;

/**
 * Basic Job Runner class. The sample job takes two params:</p>
 * <ul>
 * <li>RUNFOR=<number of seconds to run> to control how long it runs
 * <li>PASS=<true|false> to control whether it succeeds or fails
 * <li>SYNC=<true|false> to control whether
 * </ul>
 */
public class HelloWorld implements JobRunner
{
	private static final Logger log = Logger.getLogger(HelloWorld.class);

    public static long MIN_RUNFOR = 500L;   // milliseconds
    public static long MAX_RUNFOR = 10000L; // milliseconds
    
	public static String PASS   = "PASS";
    public static String RUNFOR = "RUNFOR";
    public static String STREAM = "STREAM";

    public static String RESULT = "HelloWorld -- OK";
    public static String ERROR  = "HelloWorld -- FAIL";

	private Job job;
    private JobUpdater jobUpdater;
    private SyncOutput syncOutput;

    public HelloWorld() { }

    public void setJob(Job job)
    {
        this.job = job;
    }

    public void setJobUpdater(JobUpdater ju)
    {
        this.jobUpdater = ju;
    }

    public void setSyncOutput(SyncOutput so)
    {
        this.syncOutput = so;
    }

    private ErrorSummary generateError()
    {
        try
        {
            URL url = new URL("http://" + NetUtil.getServerName(HelloWorld.class) + "/cadcSampleUWS/error.txt");
            return new ErrorSummary("job failed to say hello", ErrorType.FATAL, url);
        }
        catch (MalformedURLException ex)
        {
            throw new RuntimeException("BUG: failed to create error URL", ex);
        }
    }

    private List<Result> generateResults()
    {
        try
        {
            URI uri = new URI("http://" + NetUtil.getServerName(HelloWorld.class) + "/cadcSampleUWS/result.txt");
            List<Result> ret = new ArrayList<Result>();
            ret.add(new Result("result", uri));
            return ret;
        }
        catch (URISyntaxException ex)
        {
            throw new RuntimeException("BUG: failed to create result URI", ex);
        }
    }

    public void run()
    {
        log.debug("run: " + job.getID());
        try
        {
            ExecutionPhase ep = jobUpdater.setPhase(job.getID(), ExecutionPhase.QUEUED, ExecutionPhase.EXECUTING, new Date());
            if ( !ExecutionPhase.EXECUTING.equals(ep) )
            {
                ep = jobUpdater.getPhase(job.getID());
                log.debug(job.getID() + ": QUEUED -> EXECUTING [FAILED] -- DONE");
                return;
            }
            log.debug(job.getID() + ": QUEUED -> EXECUTING [OK]");

            String passValue = null;
            String streamValue = null;
            String runforValue = null;

            boolean pass = false;
            boolean stream = false;
            long runfor = 1;
            
            // JobInfo input (XML)
            if (job.getJobInfo() != null)
            {
                // TODO: content type/error handling here
                JobInfo ji = job.getJobInfo();
                if (ji.getValid() != null && ji.getValid())
                {
                    Document document = XmlUtil.buildDocument(ji.getContent());
                    Element root = document.getRootElement();
                    passValue = root.getChildText("pass");
                    streamValue = root.getChildText("stream");
                    runforValue = root.getChildText("runfor");
                }
            }
            else // parameter list input
            {
                passValue = ParameterUtil.findParameterValue(PASS, job.getParameterList());
                streamValue = ParameterUtil.findParameterValue(STREAM, job.getParameterList());
                runforValue = ParameterUtil.findParameterValue(RUNFOR, job.getParameterList());
            }
            
            if (passValue != null)
                try { pass = Boolean.parseBoolean(passValue); }
                catch(Exception ignore) { log.debug("invalid boolean value: " + PASS + "=" + passValue); }
            if (streamValue != null)
                try { stream = Boolean.parseBoolean(streamValue); }
                catch(Exception ignore) { log.debug("invalid boolean value: " + STREAM + "=" + streamValue); }
            if (runforValue != null)
                try { runfor = Long.parseLong(runforValue); }
                catch(Exception ignore) { log.debug("invalid long value: " + RUNFOR + "=" + runforValue); }

            // sanity check
            runfor *= 1000L; // convert to milliseconds
            if (runfor < MIN_RUNFOR)
                runfor = MIN_RUNFOR;
            else if (runfor > MAX_RUNFOR)
                runfor = MAX_RUNFOR;

            log.debug("pass: " + pass + ", stream: " + stream + ", duration: " + runfor);
            Thread.sleep(runfor);

            ExecutionPhase expected = null;
            ep = null;
            if (pass)
            {
                expected = ExecutionPhase.COMPLETED;
                List<Result> results = generateResults();
                if (syncOutput != null)
                {
                    if (stream)
                    {
                        syncOutput.setHeader("Content-Type", "text/plain");
                        PrintWriter w = new PrintWriter(syncOutput.getOutputStream());
                        w.println(RESULT);
                        w.close();
                        ep = jobUpdater.setPhase(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.COMPLETED, new Date());
                    }
                    else
                    {
                        syncOutput.setResponseCode(303);
                        syncOutput.setHeader("Location", results.get(0).getURI().toASCIIString());
                        ep = jobUpdater.setPhase(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.COMPLETED, new Date());
                    }
                }
                else // async
                {
                    ep = jobUpdater.setPhase(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.COMPLETED, results, new Date());
                }
            }
            else
            {
                expected = ExecutionPhase.ERROR;
                ErrorSummary error = generateError();
                if (syncOutput != null)
                {
                    if (stream)
                    {
                        syncOutput.setHeader("Content-Type", "text/plain");
                        PrintWriter w = new PrintWriter(syncOutput.getOutputStream());
                        w.println(ERROR);
                        w.close();
                        ep = jobUpdater.setPhase(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.COMPLETED, new Date());
                    }
                    else
                    {
                        syncOutput.setResponseCode(303);
                        syncOutput.setHeader("Location", error.getDocumentURL().toExternalForm());
                        ep = jobUpdater.setPhase(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.ERROR, error, new Date());
                    }
                }
                else // async
                {
                    ep = jobUpdater.setPhase(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.ERROR, error, new Date());
                }
            }
            if (expected.equals(ep))
                log.debug(job.getID() + ": EXECUTING -> "+ expected.name() + " [OK]");
            else
                log.debug(job.getID() + ": EXECUTING -> "+ expected.name() + " [FAILED]");
		}
        //catch(JobNotFoundException ex) { } // either a bug or someone deleted the job after executing it
        //catch(JobPersistenceException ex) { } // back end persistence is failing
        catch (Throwable t)
        {
            log.error("unexpected failure", t);
			ErrorSummary error = new ErrorSummary(t.toString(), ErrorType.FATAL);
            if (syncOutput != null)
            {
                try
                {
                    PrintWriter pw = new PrintWriter(syncOutput.getOutputStream());
                    pw.println(t.getMessage());
                    pw.close();
                }
                catch(IOException ex)
                {
                    log.error("failed to write unexpected failure message to output", ex);
                }
            }
            try
            {
                jobUpdater.setPhase(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.ERROR, error, new Date());
            }
            catch(Exception ex)
            {
                log.error("failed to set unexpected error state: " + ex);
            }
		}
    }
    
}
