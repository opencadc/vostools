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

import ca.nrc.cadc.date.DateUtil;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.Principal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import javax.security.auth.Subject;
import javax.sql.DataSource;
import org.apache.log4j.Logger;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * JobPersistence c lass that stores the jobs in a RDBMS. This is an abstract class;
 * users of this class must implement the abstract methods to return the names of tables
 * where the job is to be stored. The subclass must also call setDataSource before any
 * persistence methods are called.
 * </p><p>
 * Users must create at least 3 tables (possibly multiple parameter tables) with the
 * following columns. TODO: List the required columns for each table.
 *
 * @author jburke
 */
public abstract class JobDAO implements JobPersistence
{
    // generate a random modest-length lower case string
    private static final int ID_LENGTH = 16;
    private static final String ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    
    // Number of time to attempt to insert a Job.
    private static final int MAX_INSERT_ATTEMPTS = 2;
    private static Logger log = Logger.getLogger(JobDAO.class);

    // Database connection.
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactionManager;
    private DefaultTransactionDefinition defaultTransactionDef;
    private TransactionStatus transactionStatus;

    protected JobDAO() 
    {
        this.defaultTransactionDef = new DefaultTransactionDefinition();
        defaultTransactionDef.setIsolationLevel(DefaultTransactionDefinition.ISOLATION_READ_COMMITTED);
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
        // Job for this jobID.
        Job job;
        try
        {
            synchronized(this)
            {
                List jobs = jdbc.query(getSelectJobSQL(jobID), new JobMapper());
                if (jobs.isEmpty())
                {
                    job = null;
                }
                else if (jobs.size() == 1)
                {
                    job = (Job) jobs.get(0);

                    // List of Parameters for this jobID.
                    List<Parameter> parameterList = null;
                    for (String table : getParameterTables())
                    {
                        if (parameterList == null)
                            parameterList = new ArrayList<Parameter>();
                        parameterList.addAll(jdbc.query(getSelectParameterSQL(jobID, table), new ParameterMapper()));
                    }
                    job.setParameterList(parameterList);

                    // List of Results for this jobID.
                    job.setResultsList(jdbc.query(getSelectResultSQL(jobID), new ResultMapper()));
                }
                else
                {
                    throw new IllegalStateException("Multiple jobs with jobID " + jobID);
                }
            }
        }
        finally
        {

        }
        log.debug("getJob jobID = " + jobID);
        return job;
    }

    /**
     * Delete the specified job.
     *
     * @param jobID
     */
    public void delete(String jobID)
    {
        // Delete the Job by setting the deletedByUser field to true.
        synchronized(this)
        {
            jdbc.update(getUpdateJobDeletedSQL(jobID));
        }
        log.debug("delete jobID = " + jobID);
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
        String jobID = job.getID();
        synchronized(this)
        {
            try
            {

                // Start the transaction.
                startTransaction();

                // If Job exists, i.e. has a jobID, then delete the current Job
                // and any Parameter's and Result's for the Job.
                if (job.getID() != null)
                {
                    // Delete all the Parameter's for the Job.
                    for (String table : getParameterTables())
                        jdbc.update(getDeleteParameterSQL(job.getID(), table));

                    // Delete all the Result's for the Job.
                    jdbc.update(getDeleteResultSQL(job.getID()));

                    // Delete the Job.
                    jdbc.update(getDeleteJobSQL(job.getID()));
                }

                // Insert Job, catching any possible errors due to constraint violations.
                int attempts = 0;
                boolean done = false;
                while (!done)
                {
                    try
                    {
                        // Create new Job to persist.
                        if (job.getID() == null)
                            ret = new Job(generateID(), job);
                        else
                            ret = job;
                        jobID = job.getID();
                        
                        // Add a new Job.
                        jdbc.update(getInsertJobSQL(ret));

                        // Add the Job Parameter's.
                        for (Parameter parameter : job.getParameterList())
                            jdbc.update(getInsertParameterSQL(ret.getID(), parameter));

                        // Add the Job Result's.
                        for (Result result : job.getResultsList())
                            jdbc.update(getInsertResultSQL(ret.getID(), result));

                        done = true;
                    }
                    catch (DataAccessException e)
                    {
                        log.error(e);
                        attempts++;
                        if (attempts == MAX_INSERT_ATTEMPTS)
                            throw new JobPersistenceException(e.getMessage());
                    }
                }

                // Commit the transaction.
                commitTransaction();
                log.debug("persist jobID = " + ret.getID());

                return ret;
            }
            catch (Throwable t)
            {
                rollbackTransaction();
                log.error("persist rollback jobID = " + job.getID(), t);
                return null;
            }
        }
    }

    /**
     * Returns the name of the Job table.
     *
     * @return job table name.
     */
    protected abstract String getJobTable();

    /**
     * Returns the name of the Parameter table for the given Parameter name.
     *
     * @param name Parameter name.
     * @return Parameter table name for this Parameter name.
     */
    protected abstract String getParameterTable(String name);

    /**
     * Returns the name of the Result table.
     * 
     * @return Result table name.
     */
    protected abstract String getResultTable();

    /**
     * Returns a List containing the names of all Parameter tables.
     *
     * @return List of Parameter table names.
     */
    protected abstract List<String> getParameterTables();

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
        sb.append(getJobTable());
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
                sb.append(encode(job.getErrorSummary().getSummaryMessage()));
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
                sb.append(encode(job.getErrorSummary().getDocumentURL().toString()));
                sb.append("'");
            }
        }
        sb.append(",");
        if (job.getOwner() == null)
            sb.append("NULL");
        else
        {
            String owner = encodeSubject(job.getOwner());
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
            sb.append(encode(job.getRunID()));
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
        sb.append(getParameterTable(parameter.getName()));
        sb.append(" (jobID, name, value) values ('");
        sb.append(jobID);
        sb.append("','");
        sb.append(encode(parameter.getName()));
        sb.append("',");
        if (parameter.getValue() == null)
        {
            sb.append("NULL");
        }
        else
        {
            sb.append("'");
            sb.append(encode(parameter.getValue()));
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
        sb.append(getResultTable());
        sb.append(" (jobID, name, url, primaryResult) values ('");
        sb.append(jobID);
        sb.append("','");
        sb.append(encode(result.getName()));
        sb.append("',");
        if (result.getURL() == null)
        {
            sb.append("NULL");
        }
        else
        {
            sb.append("'");
            sb.append(encode(result.getURL().toString()));
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
        sb.append(getJobTable());
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
        List<String> tables = getParameterTables();
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
        sb.append(getResultTable());
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
        sb.append(getJobTable());
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
        sb.append(getResultTable());
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
        sb.append(getJobTable());
        sb.append(" set deletedByUser = 1 ");
        sb.append(" where jobID = '");
        sb.append(jobID);
        sb.append("'");
        return sb.toString();
    }

    private static String generateID()
    {
        Random rnd = new Random(System.currentTimeMillis());
        char[] c = new char[ID_LENGTH];
        c[0] = ID_CHARS.charAt(rnd.nextInt(ID_CHARS.length() - 10)); // letters only
        for (int i=1; i<ID_LENGTH; i++)
            c[i] = ID_CHARS.charAt(rnd.nextInt(ID_CHARS.length()));
        return new String(c);
    }

    // URLEncode a string.
    private static String encode(String s)
    {
        if (s == null)
            return null;
        try
        {
            return URLEncoder.encode(s, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("Unsupported encoding used", e);
        }
    }

    // URLDecode a string.
    private static String decode(String s)
    {
        if (s == null)
            return null;
        try
        {
            return URLDecoder.decode(s, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("Unsupported decoding used", e);
        }
    }

    // Encode a Subject in the format:
    // Principal Class name[Principal name]
    private static String encodeSubject(Subject subject)
    {
        if (subject == null)
            return null;
        StringBuilder sb = new StringBuilder();
        Iterator it = subject.getPrincipals().iterator();
        while (it.hasNext())
        {
            Principal principal = (Principal) it.next();
            sb.append(principal.getClass().getName());
            sb.append("[");
            sb.append(encode(principal.getName()));
            sb.append("]");
        }
        return sb.toString();
    }

    // Build a Subject from the encoding.
    private static Subject decodeSubject(String s)
    {
        if (s == null || s.length() == 0)
            return null;
        Subject subject = null;
        int pStart = 0;
        int nameStart = s.indexOf("[", pStart);
        try
        {
            while (nameStart != -1)
            {
                int nameEnd = s.indexOf("]", nameStart);
                if (nameEnd == -1)
                {
                    log.error("Invalid Principal encoding: " + s);
                    return null;
                }
                Class c = Class.forName(s.substring(pStart, nameStart));
                Class[] args = new Class[]{String.class};
                Constructor constructor = c.getDeclaredConstructor(args);
                String name = decode(s.substring(nameStart + 1, nameEnd));
                Principal principal = (Principal) constructor.newInstance(name);
                if (subject == null)
                    subject = new Subject();
                subject.getPrincipals().add(principal);
                pStart = nameEnd + 1;
                nameStart = s.indexOf("[", pStart);
            }
        }
        catch (IndexOutOfBoundsException ioe)
        {
            log.error(ioe.getMessage(), ioe);
        }
        catch (Exception e)
        {
            log.error(e.getMessage(), e);
        }
        return subject;
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
                    errorUrl = new URL(decode(rs.getString("error_documentURL")));
                }
                catch (MalformedURLException e)
                {
                    errorUrl = null;
                }
                errorSummary = new ErrorSummary(decode(rs.getString("error_summaryMessage")), errorUrl);
            }

            // owner
            Subject owner = decodeSubject(rs.getString("owner"));
            
            // runID
            String runID = decode(rs.getString("runID"));

            // request path
            String requestPath = decode(rs.getString("requestPath"));

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
            return new Parameter(decode(rs.getString("name")), decode(rs.getString("value")));
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
                url = new URL(decode(rs.getString("url")));
            }
            catch (MalformedURLException e)
            {
                url = null;
            }
            return new Result(decode(rs.getString("name")), url, rs.getInt("primaryResult") == 0 ? false : true);
        }
    }
    
}
