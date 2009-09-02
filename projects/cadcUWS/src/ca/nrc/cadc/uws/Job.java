/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2009.                            (c) 2009.
 * National Research Council            Conseil national de recherches
 * Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 * All rights reserved                  Tous droits reserves
 *
 * NRC disclaims any warranties         Le CNRC denie toute garantie
 * expressed, implied, or statu-        enoncee, implicite ou legale,
 * tory, of any kind with respect       de quelque nature que se soit,
 * to the software, including           concernant le logiciel, y com-
 * without limitation any war-          pris sans restriction toute
 * ranty of merchantability or          garantie de valeur marchande
 * fitness for a particular pur-        ou de pertinence pour un usage
 * pose.  NRC shall not be liable       particulier.  Le CNRC ne
 * in any event for any damages,        pourra en aucun cas etre tenu
 * whether direct or indirect,          responsable de tout dommage,
 * special or general, consequen-       direct ou indirect, particul-
 * tial or incidental, arising          ier ou general, accessoire ou
 * from the use of the software.        fortuit, resultant de l'utili-
 *                                      sation du logiciel.
 *
 *
 * @author jenkinsd
 * Jul 14, 2009 - 11:14:58 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.uws;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;


/**
 * Default implementation of a Job.
 */
public class Job
{
    private Long jobId;
    private ExecutionPhase executionPhase;
    private long executionDuration;
    private Date destructionTime;
    private Date quote;
    private Date startTime;
    private Date endTime;    
    private Error error;
    private String owner;
    private String runId;
    private List<Result> resultsList;
    private List<Parameter> parameterList;
    private Object any;


    /**
     * Constructor.
     *
     * @param jobId                 The unique Job ID.
     * @param executionPhase        The Execution Phase.
     * @param executionDuration     The Duration in clock seconds.
     * @param destructionTime       The date and time of destruction.
     * @param quote                 The quoted date of completion.
     * @param startTime             The start date of execution.
     * @param endTime               The end date of execution.
     * @param error                 The error, if any.
     * @param owner                 The Owner of this Job.
     * @param runId                 The specific running ID.
     * @param resultsList           The List of Results.
     * @param parameterList         The List of Parameters.
     */
    public Job(final Long jobId, final ExecutionPhase executionPhase,
               final long executionDuration, final Date destructionTime,
               final Date quote, final Date startTime, final Date endTime,
               final Error error, final String owner,
               final String runId, final List<Result> resultsList,
               final List<Parameter> parameterList)
    {
        this.jobId = jobId;
        this.executionPhase = executionPhase;
        this.executionDuration = executionDuration;
        this.destructionTime = destructionTime;
        this.quote = quote;
        this.startTime = startTime;
        this.endTime = endTime;
        this.error = error;
        this.owner = owner;
        this.runId = runId;
        this.resultsList = resultsList;
        this.parameterList = parameterList;
    }


    /**
     * Obtain the unique Job ID.
     *
     * @return Long job ID.
     */
    public Long getJobId()
    {
        return jobId;
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
     * job's cpu time allocation.  The service may forbid changes, or may set
     * limits on the allowed execution duration.
     *
     * @return long execution duration.
     */
    public long getExecutionDuration()
    {
        return executionDuration;
    }

    /**
     * Set the new value, in seconds.
     *
     * @param executionDuration New execution duration value, in seconds.
     */
    public void setExecutionDuration(final long executionDuration)
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
    public void setDestructionTime(final Date destructionTime)
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
    public Error getError()
    {
        return error;
    }

    public void setError(final Error error)
    {
        this.error = error;
    }

    /**
     * The owner object represents the identifier for the creator of the job.
     * This object will not exist for all invocations of a UWS conformant
     * protocol, but only in cases where the access to the service is
     * authenticated.
     *
     * @return String Owner Name.
     */
    public String getOwner()
    {
        return owner;
    }

    /**
     * The RunId object represents an identifier that the job creator uses to
     * identify the job. Note that this is distinct from the Job Identifier
     * that the UWS system itself assigns to each job. The UWS system should do
     * no parsing or processing of the RunId, but merely pass back the value
     * (if it exists) as it was passed to the UWS at job creation time. In
     * particular it may be the case that multiple jobs have the same RunId, as
     * this is a mechanism by which the calling process can identifiy jobs that
     * belong to a particular group. The exact mechanism of setting the RunId
     * is not specified here, but will be part of the specification of the
     * protocol using the UWS pattern.
     *
     * @return String run ID.
     */
    public String getRunId()
    {
        return runId;
    }

    /**
     * The Results List object is a container for formal results of the job.
     * Its children may be any objects resulting from the computation that may
     * be fetched from the service when the job has completed.
     * <p/>
     * Reading the Results List itself enumerates the available or expected
     * result objects.
     * <p/>
     * The children of the Results List may be read but not updated or deleted.
     * The client may not add anything to the Results List.
     *
     * @return ResultList instance.
     */
    public List<Result> getResultsList()
    {
        if (resultsList == null)
        {
            setResultsList(new ArrayList<Result>());
        }

        return Collections.unmodifiableList(resultsList);
    }

    /**
     * Set the results of the underlying List of results.
     *
     * @param resultList List of Result instances, never null.
     */
    public void setResultsList(final List<Result> resultList)
    {
        this.resultsList = resultList;
    }

    /**
     * Reading the Results List itself enumerates the available or expected
     * result objects.
     * <p/>
     * A particular imlementation of UWS may choose to allow the parameters to
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

    public void setParameterList(final List<Parameter> parameterList)
    {
        this.parameterList = parameterList;
    }

    /**
     * Add a parameter to the Parameter List.
     *
     * @param parameter  A Parameter to add.  NULLs are not allowed, and
     *                   duplicates will be discarded.
     */
    public void addParameter(final Parameter parameter)
    {
        if (!getParameterList().contains(parameter))
        {
            getParameterList().add(parameter);
        }
    }

    /**
     * The nebulous ANY object accomodates a domain object meaningful to
     * specific implementors of the UWS system.  The default implementation will
     * not make use of it at all.
     *
     * @return  An Object.  Anything in the world.
     */
    public Object getAny()
    {
        return any;
    }

    public void setAny(final Object any)
    {
        this.any = any;
    }
}
