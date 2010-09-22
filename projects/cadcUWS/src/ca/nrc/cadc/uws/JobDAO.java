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

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.security.auth.Subject;
import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.net.NetUtil;

/**
 * JobDAO class that stores the jobs in a RDBMS. This is an abstract class;
 * users of this class must implement the abstract methods to return the names of tables
 * where the job is to be stored. The subclass must also call setDataSource before any
 * persistence methods are called.
 * </p><p>
 * Users must create at least 3 tables (possibly multiple parameter tables) with the
 * following columns. TODO: List the required columns for each table.
 *
 * @author jburke
 */
public class JobDAO
{
    private static Logger log = Logger.getLogger(JobDAO.class);
    
    // Database connection.
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactionManager;
    private DefaultTransactionDefinition defaultTransactionDef;
    private TransactionStatus transactionStatus;
    
    // Class holding table information
    DatabasePersistence databasePersistence;

    protected JobDAO() 
    {
        this.defaultTransactionDef = new DefaultTransactionDefinition();
        defaultTransactionDef.setIsolationLevel(DefaultTransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /**
     * Initialize the JobDAO using the specified DataSource.
     *
     * @param dataSource JobDAO DataSource.
     */
    public void setDataSource(DataSource dataSource)
    {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactionManager = new DataSourceTransactionManager(dataSource);
    }
    
    /**
     * Set the class holding table information
     * 
     * @param databasePersistence
     */
    public void setDatabasePersistence(DatabasePersistence databasePersistence)
    {
        this.databasePersistence = databasePersistence;
    }

    /**
     * Start a transaction to the data source.
     */
    protected void startTransaction()
    {
        if (transactionStatus != null)
            throw new IllegalStateException("transaction already in progress");
        log.debug("startTransaction");
        this.transactionStatus = transactionManager.getTransaction(defaultTransactionDef);
        log.debug("startTransaction: OK");
    }

    /**
     * Commit the transaction to the data source.
     */
    protected void commitTransaction()
    {
        if (transactionStatus == null)
            throw new IllegalStateException("no transaction in progress");
        log.debug("commitTransaction");
        transactionManager.commit(transactionStatus);
        this.transactionStatus = null;
        log.debug("commit: OK");
    }

    /**
     * Rollback the transaction to the data source.
     */
    protected void rollbackTransaction()
    {
        if (transactionStatus == null)
            throw new IllegalStateException("no transaction in progress");
        log.debug("rollbackTransaction");
        transactionManager.rollback(transactionStatus);
        this.transactionStatus = null;
        log.debug("rollback: OK");
    }

    /**
     * Obtain a Job from the persistence layer.
     *
     * @param jobID     The job identifier.
     * @return          Job instance, or null if none found.
     */
    public Job getJob(final String jobID)
    {
        log.debug("getJob: " + jobID);
        try
        {
            startTransaction();
            List jobs = jdbc.query(getSelectJobSQL(jobID), new JobMapper());
            /*
            if (jobs.isEmpty())
            {
                log.warn("Job not found for query: " + getSelectJobSQL(jobID));
                jobs = jdbc.query(getSelectJobSQL(jobID), new JobMapper());
            }
            if (jobs.isEmpty())
            {
                log.error("Job not found after (2x) query: " + getSelectJobSQL(jobID));
                job = null;
            }
            else
             */
            if (jobs.size() == 0)
            {
                commitTransaction();
                return null;
            }

            if (jobs.size() == 1)
            {
                Job job = (Job) jobs.get(0);

                // List of Parameters for this jobID.
                List<Parameter> parameterList = new ArrayList<Parameter>();
                for (String table : databasePersistence.getParameterTables())
                {
                    parameterList.addAll(jdbc.query(getSelectParameterSQL(jobID, table), new ParameterMapper()));
                }
                job.setParameterList(parameterList);

                // List of Results for this jobID.
                job.setResultsList(jdbc.query(getSelectResultSQL(jobID), new ResultMapper()));
                commitTransaction();
                return job;
            }
            // state invariant broken == serious bug
            commitTransaction();
            throw new IllegalStateException("BUG: found " + jobs.size() + " jobs with jobID = " + jobID);
        }
        catch(Throwable t)
        {
            log.error("failed to get job: " + jobID, t);
            if (transactionStatus != null)
                try { rollbackTransaction(); }
                catch(Throwable oops) { log.error("failed to rollback transaction", oops); }
        }
        finally
        {
            if (transactionStatus != null)
                try 
                {
                    log.warn("getJob - BUG - transaction still open in finally... calling rollback");
                    rollbackTransaction();
                }
                catch(Throwable oops) { log.error("failed to rollback transaction in finally", oops); }
        }
        return null;
    }

    /**
     * Delete the specified job.
     *
     * @param jobID
     */
    public void delete(String jobID)
    {
        // Delete the Job by setting the deletedByUser field to true.
        try
        {
            startTransaction();
            jdbc.update(getUpdateJobDeletedSQL(jobID));
            log.debug("delete jobID = " + jobID);
            commitTransaction();
        }
        catch(Throwable t)
        {
            log.error("failed to delete job", t);
            if (transactionStatus != null)
                try { rollbackTransaction(); }
                catch(Throwable oops) { log.error("failed to rollback transaction", oops); }
        }
        finally
        {
            if (transactionStatus != null)
                try
                {
                    log.warn("delete - BUG - transaction still open in finally... calling rollback");
                    rollbackTransaction();
                }
                catch(Throwable oops) { log.error("failed to rollback transaction in finally", oops); }
        }
    }

    /**
     * Obtain a listing of Job instances.
     *
     * @return  Collection of Job instances, or empty Collection.  Never null.
     */
    public Collection<Job> getJobs()
    {
        throw new UnsupportedOperationException("Job listing not implemented");
    }

    /**
     * Persist the given Job.
     *
     * @param job       Job to persist.
     * @return          The persisted Job, complete with a surrogate key, if
     *                  necessary.
     */
    public Job persist(final Job job)
    {
        Job ret = null;
        try
        {
            startTransaction();
            
            if (job.getID() != null)
            {
                ret = job;
                jdbc.update(getUpdateJobSQL(job));
            }
            else
            {
                // create
                ret = new Job(databasePersistence.generateID(), job);
                jdbc.update(getInsertJobSQL(ret));
            }

            // delete and insert the parameters to make sure list content is correct
            for (String table : databasePersistence.getParameterTables())
                jdbc.update(getDeleteParameterSQL(ret.getID(), table));
            for (Parameter parameter : ret.getParameterList())
                jdbc.update(getInsertParameterSQL(ret.getID(), parameter));

            // delete and insert the results to make sure list content is correct
            jdbc.update(getDeleteResultSQL(ret.getID()));
            for (Result result : ret.getResultsList())
                jdbc.update(getInsertResultSQL(ret.getID(), result));
                    
            commitTransaction();
            log.debug("persist jobID = " + ret.getID());

            return ret;
        }
        catch(Throwable t)
        {
            log.error("failed to persist job", t);
            if (transactionStatus != null)
                try { rollbackTransaction(); }
                catch(Throwable oops) { log.error("failed to rollback transaction", oops); }
        }
        finally
        {
            if (transactionStatus != null)
                try
                {
                    log.warn("persist - BUG - transaction still open in finally... calling rollback");
                    rollbackTransaction();
                }
                catch(Throwable oops) { log.error("failed to rollback transaction in finally", oops); }
        }
        return null;
    }

    /**
     * Builds the SQL to persist the specified Job.
     *
     * @param job Job being persisted.
     * @return SQL to persist the Job.
     */
    protected String getInsertJobSQL(final Job job)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("insert into ");
        sb.append(databasePersistence.getJobTable());
        sb.append(" (");
        sb.append("jobID,");
        sb.append("executionPhase,");
        sb.append("executionDuration,");
        sb.append("destructionTime,");
        sb.append("quote,");
        sb.append("startTime,");
        sb.append("endTime,");
        sb.append("error_summaryMessage,");
        sb.append("error_documentURL,");
        sb.append("owner,");
        sb.append("runID,");
        sb.append("requestPath");
        sb.append(") values ('");
        sb.append(job.getID());
        sb.append("','");
        sb.append(job.getExecutionPhase().name());
        sb.append("',");
        sb.append(job.getExecutionDuration());
        sb.append(",");
        if (job.getDestructionTime() == null)
        {
            sb.append("NULL");
        }
        else
        {
            sb.append("'");
            sb.append(DateUtil.toString(job.getDestructionTime(), DateUtil.ISO_DATE_FORMAT, DateUtil.UTC));
            sb.append("'");
        }
        sb.append(",");
        if (job.getQuote() == null)
        {
            sb.append("NULL");
        }
        else
        {
            sb.append("'");
            sb.append(DateUtil.toString(job.getQuote(), DateUtil.ISO_DATE_FORMAT, DateUtil.UTC));
            sb.append("'");
        }
        sb.append(",");
        if (job.getStartTime() == null)
        {
            sb.append("NULL");
        }
        else
        {
            sb.append("'");
            sb.append(DateUtil.toString(job.getStartTime(), DateUtil.ISO_DATE_FORMAT, DateUtil.UTC));
            sb.append("'");
        }
        sb.append(",");
        if (job.getEndTime() == null)
        {
            sb.append("NULL");
        }
        else
        {
            sb.append("'");
            sb.append(DateUtil.toString(job.getEndTime(), DateUtil.ISO_DATE_FORMAT, DateUtil.UTC));
            sb.append("'");
        }
        sb.append(",");
        if (job.getErrorSummary() == null)
        {
            sb.append("NULL,NULL");
        }
        else
        {
            if (job.getErrorSummary().getSummaryMessage() == null)
            {
                sb.append("NULL");
            }
            else
            {
                sb.append("'");
                sb.append(NetUtil.encode(job.getErrorSummary().getSummaryMessage()));
                sb.append("'");
            }
            sb.append(",");
            if (job.getErrorSummary().getDocumentURL() == null)
            {
                sb.append("NULL");
            }
            else
            {
                sb.append("'");
                sb.append(NetUtil.encode(job.getErrorSummary().getDocumentURL().toString()));
                sb.append("'");
            }
        }
        sb.append(",");
        if (job.getOwner() == null)
            sb.append("NULL");
        else
        {
            String owner = AuthenticationUtil.encodeSubject(job.getOwner());
            if (owner.length() == 0)
                sb.append("NULL");
            else
            {
                sb.append("'");
                sb.append(owner);
                sb.append("'");
            }
        }
        sb.append(",");
        if (job.getRunID() == null)
            sb.append("NULL");
        else
        {
            sb.append("'");
            sb.append(NetUtil.encode(job.getRunID()));
            sb.append("'");
        }
        sb.append(",");
        if (job.getRequestPath() == null)
            sb.append("NULL");
        else
        {
            sb.append("'");
            sb.append(job.getRequestPath());
            sb.append("'");
        }
        sb.append(")");
        return sb.toString();
    }
    
    /**
     * Builds the SQL to update the specified Job.
     *
     * @param job Job being updated.
     * @return SQL to persist the Job.
     */
    protected String getUpdateJobSQL(final Job job)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("update ");
        sb.append(databasePersistence.getJobTable());
        sb.append(" set ");
        
        sb.append("executionPhase = '");
        sb.append(job.getExecutionPhase().name());
        sb.append("', ");
        
        sb.append("executionDuration = ");
        sb.append(job.getExecutionDuration());
        sb.append(", ");
        
        sb.append("destructionTime = ");
        if (job.getDestructionTime() == null)
        {
            sb.append("NULL");
        }
        else
        {
            sb.append("'");
            sb.append(DateUtil.toString(job.getDestructionTime(), DateUtil.ISO_DATE_FORMAT, DateUtil.UTC));
            sb.append("'");
        }
        sb.append(", ");
        
        sb.append("quote = ");
        
        if (job.getQuote() == null)
        {
            sb.append("NULL");
        }
        else
        {
            sb.append("'");
            sb.append(DateUtil.toString(job.getQuote(), DateUtil.ISO_DATE_FORMAT, DateUtil.UTC));
            sb.append("'");
        }
        sb.append(", ");
        
        sb.append("startTime = ");
        if (job.getStartTime() == null)
        {
            sb.append("NULL");
        }
        else
        {
            sb.append("'");
            sb.append(DateUtil.toString(job.getStartTime(), DateUtil.ISO_DATE_FORMAT, DateUtil.UTC));
            sb.append("'");
        }
        sb.append(", ");
        
        sb.append("endTime = ");
        if (job.getEndTime() == null)
        {
            sb.append("NULL");
        }
        else
        {
            sb.append("'");
            sb.append(DateUtil.toString(job.getEndTime(), DateUtil.ISO_DATE_FORMAT, DateUtil.UTC));
            sb.append("'");
        }
        sb.append(", ");
        
        sb.append("error_summaryMessage = ");
        if (job.getErrorSummary() == null)
        {
            sb.append("NULL");
        }
        else
        {
            if (job.getErrorSummary().getSummaryMessage() == null)
            {
                sb.append("NULL");
            }
            else
            {
                sb.append("'");
                sb.append(NetUtil.encode(job.getErrorSummary().getSummaryMessage()));
                sb.append("'");
            }
        }
        sb.append(", ");
        
        sb.append("error_documentURL = ");
        if (job.getErrorSummary() == null)
        {
            sb.append("NULL");
        }
        else
        {
            if (job.getErrorSummary().getDocumentURL() == null)
            {
                sb.append("NULL");
            }
            else
            {
                sb.append("'");
                sb.append(NetUtil.encode(job.getErrorSummary().getDocumentURL().toString()));
                sb.append("'");
            }
        }
        sb.append(", ");
        
        sb.append("owner = ");
        if (job.getOwner() == null)
            sb.append("NULL");
        else
        {
            String owner = AuthenticationUtil.encodeSubject(job.getOwner());
            if (owner.length() == 0)
                sb.append("NULL");
            else
            {
                sb.append("'");
                sb.append(owner);
                sb.append("'");
            }
        }
        sb.append(", ");
        
        sb.append("runID = ");
        if (job.getRunID() == null)
            sb.append("NULL");
        else
        {
            sb.append("'");
            sb.append(NetUtil.encode(job.getRunID()));
            sb.append("'");
        }
        sb.append(", ");
        
        sb.append("requestPath = ");
        if (job.getRequestPath() == null)
            sb.append("NULL");
        else
        {
            sb.append("'");
            sb.append(job.getRequestPath());
            sb.append("'");
        }
        
        sb.append(" where jobID = '");
        sb.append(job.getID());
        sb.append("'");
 
        return sb.toString();
    }

    /**
     * Returns the SQL to persist the specified Parameter in the Job specified
     * by the jobID.
     *
     * @param jobID of Job for the Parameter.
     * @param parameter to persist.
     * @return SQL to persist the Parameter in the Job.
     */
    protected String getInsertParameterSQL(final String jobID, final Parameter parameter)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("insert into ");
        sb.append(databasePersistence.getParameterTable(parameter.getName()));
        sb.append(" (jobID, name, value) values ('");
        sb.append(jobID);
        sb.append("','");
        sb.append(NetUtil.encode(parameter.getName()));
        sb.append("',");
        if (parameter.getValue() == null)
        {
            sb.append("NULL");
        }
        else
        {
            sb.append("'");
            sb.append(NetUtil.encode(parameter.getValue()));
            sb.append("'");
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Returns the SQL to persist the specified Result in the Job specified
     * by the jobID.
     *
     * @param jobID of the Job for the Result.
     * @param result to persist.
     * @return SQL to persist the Result in the Job.
     */
    protected String getInsertResultSQL(final String jobID, final Result result)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("insert into ");
        sb.append(databasePersistence.getResultTable());
        sb.append(" (jobID, name, url, primaryResult) values ('");
        sb.append(jobID);
        sb.append("','");
        sb.append(NetUtil.encode(result.getName()));
        sb.append("',");
        if (result.getURL() == null)
        {
            sb.append("NULL");
        }
        else
        {
            sb.append("'");
            sb.append(NetUtil.encode(result.getURL().toString()));
            sb.append("'");
        }
        sb.append(",");
        sb.append(result.isPrimaryResult() ? "1" : " 0");
        sb.append(")");
        return sb.toString();
    }

    /**
     * Returns the SQL to select the Job with the specified jobID.
     *
     * @param jobID of the Job.
     * @return SQL to select the Job.
     */
    protected String getSelectJobSQL(final String jobID)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("select * from ");
        sb.append(databasePersistence.getJobTable());
        sb.append(" where deletedByUser = 0 and jobID = '");
        sb.append(jobID);
        sb.append("'");
        return sb.toString();
    }

    /**
     * Returns the SQL to select all the Parameters in the specified
     * Parameter table that have the specified jobID.
     *
     * @param jobID of the Job.
     * @param table name of the Parameter table.
     * @return SQL to select Parameters for the jobID.
     */
    protected String getSelectParameterSQL(final String jobID, final String table)
    {
        List<String> tables = databasePersistence.getParameterTables();
        StringBuilder sb = new StringBuilder();
        sb.append("select * from ");
        sb.append(table);
        sb.append(" where jobID = '");
        sb.append(jobID);
        sb.append("'");
        return sb.toString();
    }

    /**
     * Returns the SQL to select all the Results with the specified jobID.
     *
     * @param jobID of the Job.
     * @return SQL to select the Results for the jobID.
     */
    protected String getSelectResultSQL(final String jobID)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("select * from ");
        sb.append(databasePersistence.getResultTable());
        sb.append(" where jobID = '");
        sb.append(jobID);
        sb.append("'");
        return sb.toString();
    }

    /**
     * Returns the SQL to delete the Job in the Jobs table
     * with the specified jobID.
     *
     * @param jobID of the Job.
     * @return SQL to delete the Job for this jobID.
     */
    protected String getDeleteJobSQL(final String jobID)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("delete from ");
        sb.append(databasePersistence.getJobTable());
        sb.append(" where jobID = '");
        sb.append(jobID);
        sb.append("'");
        return sb.toString();
    }

    /**
     * Returns SQL to delete all Parameters in the given Parameter table
     * with the specified jobID.
     *
     * @param jobID of the Job.
     * @param table name of the Parameter table.
     * @return SQL to delete the Parameters for this jobID.
     */
    protected String getDeleteParameterSQL(final String jobID, final String table)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("delete from ");
        sb.append(table);
        sb.append(" where jobID = '");
        sb.append(jobID);
        sb.append("'");
        return sb.toString();
    }

    /**
     * Returns the SQL to delete all Results with the specified jobID.
     *
     * @param jobID of the Job.
     * @return SQL to delete the Results for this jobID.
     */
    protected String getDeleteResultSQL(final String jobID)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("delete from ");
        sb.append(databasePersistence.getResultTable());
        sb.append(" where jobID = '");
        sb.append(jobID);
        sb.append("'");
        return sb.toString();
    }

    /**
     * Returns the SQL to mark a Job as deleted. The deletedByUser field is set
     * from the default value of 0, to 1, to indicate the Job is deleted.
     * @param jobID of the Job.
     * @return SQL to mark the Job as deleted.
     */
    protected String getUpdateJobDeletedSQL(final String jobID)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("update ");
        sb.append(databasePersistence.getJobTable());
        sb.append(" set deletedByUser = 1 ");
        sb.append(" where jobID = '");
        sb.append(jobID);
        sb.append("'");
        return sb.toString();
    }

    

    /**
     * Creates a List of Job populated from the ResultSet.
     */
    private static final class JobMapper implements RowMapper
    {
        public Object mapRow(ResultSet rs, int rowNum) throws SQLException
        {
            // jobID
            String jobID = rs.getString("jobID");

            // executionPhase
            ExecutionPhase executionPhase = ExecutionPhase.valueOf(rs.getString("executionPhase").toUpperCase());

            // executionDuration
            long executionDuration = rs.getLong("executionDuration");

            // destructionTime
            Date destructionTime = rs.getTimestamp("destructionTime", Calendar.getInstance(DateUtil.UTC));

            // quote
            Date quote = rs.getTimestamp("quote", Calendar.getInstance(DateUtil.UTC));

            // startTime
            Date startTime = rs.getTimestamp("startTime", Calendar.getInstance(DateUtil.UTC));

            // endTime
            Date endTime = rs.getTimestamp("endTime", Calendar.getInstance(DateUtil.UTC));

            // errorSummary
            ErrorSummary errorSummary = null;
            if (rs.getString("error_summaryMessage") != null)
            {
                URL errorUrl;
                try
                {
                    errorUrl = new URL(NetUtil.decode(rs.getString("error_documentURL")));
                }
                catch (MalformedURLException e)
                {
                    errorUrl = null;
                }
                boolean hasDetail = false;
                if (errorUrl != null)
                    hasDetail = true;
                errorSummary = new ErrorSummary(NetUtil.decode(rs.getString("error_summaryMessage")), ErrorType.FATAL, hasDetail);
                errorSummary.setDocumentURL(errorUrl);
            }

            // owner
            Subject owner = AuthenticationUtil.decodeSubject(rs.getString("owner"));
            
            // runID
            String runID = NetUtil.decode(rs.getString("runID"));

            // request path
            String requestPath = NetUtil.decode(rs.getString("requestPath"));

            // Create the job
            Job job = new Job(jobID, executionPhase, executionDuration, destructionTime,
                              quote, startTime, endTime, errorSummary, owner, runID,
                              null, null, requestPath);

            return job;
        }
    }

    /**
     * Creates a List of Parameter populated from the ResultSet.
     */
    private static final class ParameterMapper implements RowMapper
    {
        public Object mapRow(ResultSet rs, int rowNum) throws SQLException
        {
            return new Parameter(NetUtil.decode(rs.getString("name")), NetUtil.decode(rs.getString("value")));
        }
    }
    
    /**
     * Creates a List of Result populated from the ResultSet.
     */
    private static final class ResultMapper implements RowMapper
    {
        public Object mapRow(ResultSet rs, int rowNum) throws SQLException
        {
            URL url;
            try
            {
                url = new URL(NetUtil.decode(rs.getString("url")));
            }
            catch (MalformedURLException e)
            {
                url = null;
            }
            return new Result(NetUtil.decode(rs.getString("name")), url, rs.getInt("primaryResult") == 0 ? false : true);
        }
    }
    
}
