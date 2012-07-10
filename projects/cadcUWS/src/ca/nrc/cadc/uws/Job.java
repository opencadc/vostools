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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.security.auth.Subject;

/**
 * Default implementation of a Job.
 */
public class Job
{
    private String jobID;
    private ExecutionPhase executionPhase;
    private Long executionDuration;
    private Date destructionTime;
    private Date quote;
    private Date startTime;
    private Date endTime;    
    private ErrorSummary errorSummary;
    private String ownerID;
    private String runID;
    private List<Result> resultsList;
    private List<Parameter> parameterList;
    private String requestPath;
    private String remoteIP;
    private JobInfo jobInfo;

    private Date lastModified;

    // used on the server side only for authorization checks
    public transient Subject ownerSubject;

    // usable for hooking app-specific value here temporarily
    public transient Object appData;
    
    // so that protocols are not switched in the result
    public transient String protocol;

    public Job() { }

    // package access for use by JobReader
    Job(String jobID,
                ExecutionPhase executionPhase,
                Long executionDuration,
                Date destructionTime,
                Date quote,
                Date startTime,
                Date endTime,
                ErrorSummary errorSummary,
                String ownerID,
                String runID,
                String requestPath,
                String remoteIP,
                JobInfo jobInfo,
                List<Parameter> params,
                List<Result> results)
    {
        this(executionPhase, executionDuration, destructionTime, quote,
                startTime, endTime, errorSummary, ownerID, runID,
                requestPath, remoteIP, jobInfo, params, results);
        this.jobID = jobID;
    }
    
    /**
     * Complete constructor for a new job.
     * 
     * @param executionPhase
     * @param executionDuration
     * @param destructionTime
     * @param quote
     * @param startTime
     * @param endTime
     * @param errorSummary
     * @param ownerID
     * @param runID
     * @param requestPath
     * @param remoteIP
     * @param jobInfo
     * @param params
     * @param results
     */
    public Job(ExecutionPhase executionPhase,
                Long executionDuration,
                Date destructionTime,
                Date quote,
                Date startTime,
                Date endTime,
                ErrorSummary errorSummary,
                String ownerID,
                String runID,
                String requestPath,
                String remoteIP,
                JobInfo jobInfo,
                List<Parameter> params,
                List<Result> results)
    {
        this.executionPhase = executionPhase;
        this.executionDuration = executionDuration;
        this.destructionTime = destructionTime;
        this.quote = quote;
        this.startTime = startTime;
        this.endTime = endTime;
        this.errorSummary = errorSummary;
        this.ownerID = ownerID;
        this.runID = runID;
        this.requestPath = requestPath;
        this.remoteIP = remoteIP;
        this.jobInfo = jobInfo;
        this.parameterList = params;
        this.resultsList = results;
    }

    /**
     * Copy constructor. This makes a deep copy so that any changes to the created 
     * job will not effect the original job. The constructed job is a new job with
     * no jobID (until assigned by a JobPersistence implementation).
     * 
     * @param job
     */
    public Job(Job job)
    {
        this.executionPhase = job.getExecutionPhase();
        this.executionDuration = job.getExecutionDuration();
        this.destructionTime = job.getDestructionTime();
        this.quote = job.getQuote();
        this.startTime = job.getStartTime();
        this.endTime = job.getEndTime();

        this.errorSummary = job.getErrorSummary();
        this.ownerID = job.getOwnerID();
        this.runID = job.getRunID();
        this.requestPath = job.getRequestPath();
        this.remoteIP = job.getRemoteIP();

        // deep copy of the mutable fields
        if (job.getParameterList() != null)
        {
            this.parameterList = new ArrayList<Parameter>();
            for (Parameter p : job.getParameterList())
                parameterList.add(new Parameter(p.getName(), p.getValue()));
        }
        if (job.getResultsList() != null)
        {
            this.resultsList = new ArrayList<Result>();
            for (Result r : job.getResultsList())
                resultsList.add(new Result(r.getName(), r.getURI()));
        }
        if (job.getJobInfo() != null)
        {
            this.jobInfo = new JobInfo(
                    job.getJobInfo().getContent(),
                    job.getJobInfo().getContentType(),
                    job.getJobInfo().getValid() );
        }
    }

    @Override
    public String toString()
    {
        return "Job [jobInfo=" + jobInfo + " destructionTime=" + destructionTime + ", endTime=" + endTime + ", errorSummary="
                + errorSummary + ", executionDuration=" + executionDuration + ", executionPhase=" + executionPhase + ", jobID="
                + jobID + ", ownerID=" + ownerID + ", parameterList=" + parameterList + ", quote=" + quote + ", requestPath="
                + requestPath + ", remoteIP="
                + remoteIP + ", resultsList=" + resultsList + ", runID=" + runID + ", startTime=" + startTime + "]";
    }

    public Date getLastModified()
    {
        return lastModified;
    }

    /**
     * Obtain the unique Job ID.
     *
     * @return Long job ID.
     */
    public String getID()
    {
        return jobID;
    }

    /**
     * Get the string representation of the ownerID.
     *
     * @return
     */
    public String getOwnerID()
    {
        return ownerID;
    }

    /**
     * Set  the string representation of the ownerID.
     *
     * @param ownerID
     */
    public void setOwnerID(String ownerID)
    {
        this.ownerID = ownerID;
    }
    
    /**
     * Get the protocol.
     * 
     * @return
     */
    public String getProtocol()
    {
        return protocol;
    }
    
    /**
     * Set the protocol.
     * 
     * @param protocol
     */
    public void setProtocol(String protocol)
    {
        this.protocol = protocol;
    }

    /**
     * Obtain the Execution Phase.
     *
     * @return ExecutionPhase instance.
     */
    public ExecutionPhase getExecutionPhase()
    {
        return executionPhase;
    }

    /**
     * Set the new Phase.
     *
     * @param executionPhase The new Phase.
     */
    public void setExecutionPhase(final ExecutionPhase executionPhase)
    {
        this.executionPhase = executionPhase;
    }

    /**
     * An Execution Duration object defines the duration for which a job shall
     * run.  This represents the "computation time" that a job is to be
     * allowed, although because a specific measure of CPU time may not be
     * available in all environments, this duration is defined in real clock
     * seconds. An execution duration of 0 implies unlimited execution
     * duration.
     * <p/>
     * When the execution duration has been exceeded the service will
     * automatically abort the job, which has the same effect as when a manual
     * "Abort" is requested.
     * <p/>
     * When a job is created, the service sets the initial execution duration.
     * The client may write to an Execution Duration to try to change the
     * job's CPU time allocation.  The service may forbid changes, or may set
     * limits on the allowed execution duration.
     *
     * @return long execution duration.
     */
    public Long getExecutionDuration()
    {
        return executionDuration;
    }

    /**
     * Set the new value, in seconds.
     *
     * @param executionDuration New execution duration value, in seconds.
     */
    public void setExecutionDuration(Long executionDuration)
    {
        this.executionDuration = executionDuration;
    }

    /**
     * The Destruction Time object represents the instant when the job shall be
     * destroyed. The Destruction Time is an absolute time.
     * <p/>
     * The Destruction Time may be viewed as a measure of the amount of time
     * that a service is prepared to allocate storage for a job – typically
     * this will be a longer duration that the amount of CPU time that a
     * service would allocate.
     * <p/>
     * When a job is created the service sets the initial Destruction Time.
     * The client may write to the Destruction Time to try to change the life
     * expectancy of the job. The service may forbid changes, or may set limits
     * on the allowed destruction time.
     *
     * @return Date of destruction.
     */
    public Date getDestructionTime()
    {
        return destructionTime;
    }

    /**
     * Set the new date of Destruction.
     *
     * @param destructionTime Date of destruction.
     */
    public void setDestructionTime(Date destructionTime)
    {
        this.destructionTime = destructionTime;
    }

    /**
     * A Quote object predicts when the job is likely to complete. The
     * intention is that a client creates the same job on several services,
     * compares the quotes and then accepts the best quote.
     * <p/>
     * Quoting for a computational job is notoriously difficult. A UWS
     * implementation must always provide a quote object, in order that the
     * two-phase committal of jobs be uniform across all UWS, but it may supply
     * a "don't know" answer for the completion time.
     *
     * @return Date Quote.
     */
    public Date getQuote()
    {
        return quote;
    }

    public void setQuote(Date quote)
    {
        this.quote = quote;
    }

    public void setStartTime(Date startTime)
    {
        this.startTime = startTime;
    }

    public void setEndTime(Date endTime)
    {
        this.endTime = endTime;
    }

    public void setRunID(String runID)
    {
        this.runID = runID;
    }

    /**
     * Obtain the instant at which the job started execution.
     *
     * @return Date of start.
     */
    public Date getStartTime()
    {
        return startTime;
    }

    /**
     * Obtain the instant at which the job completed execution.
     *
     * @return Date at end of execution.
     */
    public Date getEndTime()
    {
        return endTime;
    }

    /**
     * The error object gives a human readable error message (if any) for the
     * underlying job. This object is intended to be a detailed error message, and
     * consequently might be a large piece of text such as a stack trace. When
     * there is an error running a job a summary of the error should be given using
     * the optional errorSummary element of the JobSummary type.
     *
     * @return String human readable message.
     */
    public ErrorSummary getErrorSummary()
    {
        return errorSummary;
    }

    public void setErrorSummary(ErrorSummary errorSummary)
    {
        this.errorSummary = errorSummary;
    }

    /**
     * The RunId object represents an identifier that the job creator uses to
     * identify the job. Note that this is distinct from the Job Identifier
     * that the UWS system itself assigns to each job. The UWS system should do
     * no parsing or processing of the RunId, but merely pass back the value
     * (if it exists) as it was passed to the UWS at job creation time. In
     * particular it may be the case that multiple jobs have the same RunId, as
     * this is a mechanism by which the calling process can identify jobs that
     * belong to a particular group. The exact mechanism of setting the RunId
     * is not specified here, but will be part of the specification of the
     * protocol using the UWS pattern.
     *
     * @return String run ID.
     */
    public String getRunID()
    {
        return runID;
    }

    /**
     * The Results List object is a container for formal results of the job.
     * Its children may be any objects resulting from the computation that may
     * be fetched from the service when the job has completed.
     * 
     * @return list of results
     */
    public List<Result> getResultsList()
    {
        if (resultsList == null)
        {
            setResultsList(new ArrayList<Result>());
        }

        return resultsList;
    }

    /**
     * Set the results of the underlying List of results.
     *
     * @param resultList List of Result instances, never null.
     */
    public void setResultsList(List<Result> resultList)
    {
        this.resultsList = resultList;
    }

    /**
     * Reading the Results List itself enumerates the available or expected
     * result objects.
     * <p/>
     * A particular implementation of UWS may choose to allow the parameters to
     * be updated after the initial job creation step, before the Phase is set
     * to the executing state. It is up to the individual implementation to
     * specify exactly how these parameters may be updated, but good practice
     * would be to choose one of the following options.
     * <p/>
     * 1.   HTTP POST an application/x-www-form-urlencoded parameter name,
     * value pair to either
     * 1.  /{jobs}/{job-id)
     * 2.  /{jobs}/{job-id)/parameters
     * <p/>
     * 2.   HTTP PUT the parameter value to
     * /{jobs}/{job-id)/parameters/(parameter-name)
     *
     * @return ParameterList instance.
     */
    public List<Parameter> getParameterList()
    {
        if (parameterList == null)
        {
            setParameterList(new ArrayList<Parameter>());
        }

        return parameterList;
    }

    public void setParameterList(List<Parameter> parameterList)
    {
        this.parameterList = parameterList;
    }

    /**
     * Path of the Request that created the Job.
     * 
     * @return The Request Path.
     */
    public String getRequestPath()
    {
        return requestPath;
    }

    /**
     * Path of the Request that created the Job.
     *
     * @param path The Request Path.
     */
    public void setRequestPath(final String path)
    {
        this.requestPath = path;
    }

    public String getRemoteIP()
    {
        return remoteIP;
    }

    public void setRemoteIP(String remoteIP)
    {
        this.remoteIP = remoteIP;
    }

    /**
     * The nebulous ANY object accommodates a domain object meaningful to
     * specific implementors of the UWS system.  The default implementation will
     * not make use of it at all.
     *
     * @return  An Object.  Anything in the world.
     */
    public JobInfo getJobInfo()
    {
        return jobInfo;
    }

    public void setJobInfo(final JobInfo jobInfo)
    {
        this.jobInfo = jobInfo;
    }

    
}
