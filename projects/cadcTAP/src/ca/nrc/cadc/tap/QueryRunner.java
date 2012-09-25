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

package ca.nrc.cadc.tap;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.sql.DataSource;

import org.apache.log4j.Logger;

import ca.nrc.cadc.tap.schema.ParamDesc;
import ca.nrc.cadc.tap.schema.SchemaDesc;
import ca.nrc.cadc.tap.schema.TableDesc;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.tap.schema.TapSchemaDAO;
import ca.nrc.cadc.tap.writer.VOTableWriter;
import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.ErrorType;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.Result;
import ca.nrc.cadc.uws.server.JobRunner;
import ca.nrc.cadc.uws.server.JobUpdater;
import ca.nrc.cadc.uws.server.SyncOutput;

/**
 * Implementation of the JobRunner interface from the cadcUWS framework. This is the
 * main class that implements TAP semantics; it is usable with both the async and sync
 * servlet configurations from cadcUWS.
 * </p><p>
 * This class dynamically loads and uses implementation classes as described in the
 * package documentation. This allows one to control the behavior of several key components:
 * query processing, upload support, and writing the result-set to the output file format.
 * </p><p>
 * In addition, this class uses JDNI to find <code>java.sql.DataSource</code> instances for 
 * executing database statements. 
 * </p>
 * <ul>
 * <li>A datasource named <b>jdbc/tapuser</b> is required; this datasource
 * is used to query the TAP_SCHEMA and to run user-queries. The connection(s) provided by this
 * datasource must have read permission to the TAP_SCHEMA and all tables described within the
 * TAP_SCHEMA.</li>
 * <li>A datasource named <b>jdbc/tapuploadadm</b> is optional; this datasource is used to create tables
 * in the TAP_UPLOAD schema and to populate these tables with content from uploaded tables. If this 
 * datasource is provided, it is passed to the UploadManager implementation. For uploads to actually work, 
 * the connection(s) provided by the datasource must have create table permission in the current database and
 * TAP_UPLOAD schema.</li>
 * </ul>
 * 
 * @author pdowler
 */
public class QueryRunner implements JobRunner
{
    private static final Logger log = Logger.getLogger(QueryRunner.class);

    private static final HashMap<String, String> langQueries = new HashMap<String, String>();

    private static final String queryDataSourceName = "jdbc/tapuser";
    private static final String uploadDataSourceName = "jdbc/tapuploadadm";

    // names of plugin classes that must be provided by service implementation
    private static final String resultStoreImplClassName = "ca.nrc.cadc.tap.impl.ResultStoreImpl";
    private static final String uploadManagerClassName = "ca.nrc.cadc.tap.impl.UploadManagerImpl";
    private static final String sqlParserClassName = "ca.nrc.cadc.tap.impl.SqlQueryImpl";
    private static final String adqlParserClassName = "ca.nrc.cadc.tap.impl.AdqlQueryImpl";
    private static final String pqlParserClassName = "ca.nrc.cadc.tap.impl.PqlQueryImpl";

    // optional plugin classes that may be provided to override default behaviour
    private static String maxrecValidatorClassName = "ca.nrc.cadc.tap.impl.MaxRecValidatorImpl";

    static
    {
        langQueries.put("ADQL", adqlParserClassName);
        langQueries.put("ADQL-2.0", adqlParserClassName);
        langQueries.put("SQL", sqlParserClassName);
        langQueries.put("PQL", pqlParserClassName);
    }

    private String jobID;
    private Job job;
    private JobUpdater jobUpdater;
    private SyncOutput syncOutput;

    public QueryRunner()
    {
        syncOutput = null;
    }

    public void setJob(Job job)
    {
        this.job = job;
        jobID = job.getID();
    }

    public void setJobUpdater(JobUpdater ju)
    {
        this.jobUpdater = ju;
    }

    public void setSyncOutput(SyncOutput so)
    {
        this.syncOutput = so;
    }

    public void run()
    {
        log.debug("START");
        List<Long> tList = new ArrayList<Long>();
        List<String> sList = new ArrayList<String>();

        tList.add(System.currentTimeMillis());
        sList.add("start");

        log.debug("run: " + job.getID());

        
        ResultStore rs = null;
        if (syncOutput == null)
        {
            // try to instantiate a ResultStore instance
            try
            {
                log.debug("loading " + resultStoreImplClassName);
                rs = (ResultStore) Class.forName(resultStoreImplClassName).newInstance();
            }
            catch (Throwable t)
            {
                log.warn("Failed to instantiate ResultStore class: " + resultStoreImplClassName);
            }
        }

        // Check if a store or stream have been set for a context
        if (rs == null && syncOutput == null)
        {
            throw new RuntimeException("async mode: Failed to instantiate ResultStore implementation: " + resultStoreImplClassName);
        }

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
            tList.add(System.currentTimeMillis());
            sList.add("QUEUED -> EXECUTING: ");

            // start processing the job
            List<Parameter> paramList = job.getParameterList();
            log.debug("job " + job.getID() + ": " + paramList.size() + " parameters");

            log.debug("invoking TapValidator for REQUEST and VERSION...");
            TapValidator tapValidator = new TapValidator();
            tapValidator.validate(paramList);

            tList.add(System.currentTimeMillis());
            sList.add("initialisation: ");

            log.debug("find DataSource via JNDI lookup...");
            Context initContext = new InitialContext();
            Context envContext = (Context) initContext.lookup("java:/comp/env");
            DataSource queryDataSource = (DataSource) envContext.lookup(queryDataSourceName);
            // this one is optional, so take care
            DataSource uploadDataSource = null;
            try
            {
                uploadDataSource = (DataSource) envContext.lookup(uploadDataSourceName);
            }
            catch (NameNotFoundException nex)
            {
                log.warn(nex.toString());
            }

            if (queryDataSource == null) // application server config issue
                throw new RuntimeException("failed to find the query DataSource");

            tList.add(System.currentTimeMillis());
            sList.add("find DataSources via JNDI: ");

            log.debug("reading TapSchema...");
            TapSchemaDAO dao = new TapSchemaDAO(queryDataSource);
            TapSchema tapSchema = dao.get();

            tList.add(System.currentTimeMillis());
            sList.add("read tap_schema: ");

            log.debug("loading " + uploadManagerClassName);
            UploadManager uploadManager = new DefaultUploadManager();
            try
            {
                Class umc = Class.forName(uploadManagerClassName);
                uploadManager = (UploadManager) umc.newInstance();
                uploadManager.setDataSource(uploadDataSource);
            }
            catch(Throwable t)
            {
                log.debug("failed to load " + uploadManagerClassName +": using " + DefaultUploadManager.class.getName());
            }
            log.debug("using " + uploadManager.getClass().getName());
            log.debug("invoking UploadManager for UPLOAD...");
            Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, job.getID());

            if (tableDescs != null)
            {
                log.debug("adding TAP_UPLOAD SchemaDesc to TapSchema...");
                SchemaDesc tapUploadSchema = new SchemaDesc();
                tapUploadSchema.setSchemaName("TAP_UPLOAD");
                tapUploadSchema.setTableDescs(new ArrayList(tableDescs.values()));
                tapSchema.schemaDescs.add(tapUploadSchema);
            }

            log.debug("invoking MaxRecValidator...");
            MaxRecValidator maxRecValidator = new MaxRecValidator();
            try
            {
                Class c = Class.forName(maxrecValidatorClassName);
                maxRecValidator = (MaxRecValidator) c.newInstance();
            }
            catch (Throwable t)
            {
                log.debug("failed to load " + uploadManagerClassName +": using " + DefaultUploadManager.class.getName());
            }
            log.debug("using " + maxRecValidator.getClass().getName());
            maxRecValidator.setJob(job);
            maxRecValidator.setSynchronousMode(syncOutput != null);
            maxRecValidator.setTapSchema(tapSchema);
            maxRecValidator.setExtraTables(tableDescs);
            Integer maxRows = maxRecValidator.validate(paramList);

            log.debug("invoking TapValidator to get LANG...");
            String lang = tapValidator.getLang();
            String cname = langQueries.get(lang);
            if (cname == null)
                throw new UnsupportedOperationException("unknown LANG: " + lang);

            log.debug("loading TapQuery " + cname);
            Class tqc = Class.forName(cname);
            TapQuery tapQuery = (TapQuery) tqc.newInstance();
            tapQuery.setTapSchema(tapSchema);
            tapQuery.setExtraTables(tableDescs);
            tapQuery.setParameterList(paramList);
            if (maxRows != null)
                tapQuery.setMaxRowCount(maxRows + 1); // +1 so the TableWriter can detect overflow

            log.debug("invoking TapQuery...");
            String sql = tapQuery.getSQL();
            List<ParamDesc> selectList = tapQuery.getSelectList();
            String queryInfo = tapQuery.getInfo();

            log.debug("invoking TableWriterFactory for FORMAT...");
            TableWriter tableWriter = TableWriterFactory.getWriter(job.getParameterList());
            tableWriter.setJob(job);
            tableWriter.setSelectList(selectList);
            tableWriter.setQueryInfo(queryInfo);
            if (maxRows != null)
                tableWriter.setMaxRowCount(maxRows);

            tList.add(System.currentTimeMillis());
            sList.add("parse/convert query: ");

            Connection connection = null;
            PreparedStatement pstmt = null;
            ResultSet resultSet = null;
            File tmpFile = null;
            URL url = null;
            try
            {
                if (maxRows == null || maxRows.intValue() > 0)
                {
                    log.debug("getting database connection...");
                    connection = queryDataSource.getConnection();
                    tList.add(System.currentTimeMillis());
                    sList.add("get connection from data source: ");

                    // manually control transaction, make fetch size (client batch size) small,
                    // and restrict to forward only so that client memory usage is minimal since
                    // we are only interested in reading the ResultSet once
                    connection.setAutoCommit(false);
                    pstmt = connection.prepareStatement(sql);
                    pstmt.setFetchSize(1000);
                    pstmt.setFetchDirection(ResultSet.FETCH_FORWARD);
                    
                    log.info("executing query: " + sql);
                    resultSet = pstmt.executeQuery();
                }

                tList.add(System.currentTimeMillis());
                sList.add("execute query and get ResultSet: ");
                String filename = "result_" + job.getID() + "." + tableWriter.getExtension();
                
                if (syncOutput != null)
                {
                    String contentType = tableWriter.getContentType();
                    log.debug("streaming output: " + contentType);
                    syncOutput.setHeader("Content-Type", contentType);
                    String disp = "attachment; filename=\""+filename+"\"";
                    syncOutput.setHeader("Content-Disposition", disp);
                    tableWriter.write(resultSet, syncOutput.getOutputStream());
                    tList.add(System.currentTimeMillis());
                    sList.add("stream Result set as " + contentType + ": ");
                }
                else
                {
                    ep = jobUpdater.getPhase(job.getID());
                    if (ExecutionPhase.ABORTED.equals(ep))
                    {
                        log.debug(job.getID() + ": found phase = ABORTED before writing results - DONE");
                        return;
                    }
                    
                    log.debug("result filename: " + filename);
                    rs.setJob(job);
                    rs.setFilename(filename);
                    rs.setContentType(tableWriter.getContentType());
                    url = rs.put(resultSet, tableWriter);
                    tList.add(System.currentTimeMillis());
                    sList.add("write ResultSet to ResultStore as " + tableWriter.getContentType() + ": ");
                }
                log.debug("executing query... [OK]");
            }
            catch (SQLException ex)
            {
                log.error("SQL Execution error.", ex);
                throw ex;
            }
            finally
            {
                if (connection != null)
                {
                    try
                    {
                        connection.setAutoCommit(true);
                    }
                    catch(Throwable ignore) { }
                    try
                    {
                        resultSet.close();
                    }
                    catch (Throwable ignore) { }
                    try
                    {
                        pstmt.close();
                    }
                    catch (Throwable ignore) { }
                    try
                    {
                        connection.close();
                    }
                    catch (Throwable ignore) { }
                }
            }

            if (syncOutput != null)
            {
                log.debug("setting ExecutionPhase = " + ExecutionPhase.COMPLETED);
                jobUpdater.setPhase(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.COMPLETED, new Date());
            }
            else
            {
                try
                {
                    Result res = new Result("result", new URI(url.toExternalForm()));
                    List<Result> results = new ArrayList<Result>();
                    results.add(res);
                    log.debug("setting ExecutionPhase = " + ExecutionPhase.COMPLETED + " with results");
                    jobUpdater.setPhase(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.COMPLETED, results, new Date());
                }
                catch (URISyntaxException e)
                {
                    log.error("BUG: URL is not a URI: " + url.toExternalForm(), e);
                    throw e;
                }
            }
        }
        catch (Throwable t)
        {
            String errorMessage = null;
            URL errorURL = null;
            try
            {
                tList.add(System.currentTimeMillis());
                sList.add("encounter failure: ");

                errorMessage = t.getClass().getSimpleName() + ":" + t.getMessage();
                log.debug("BADNESS", t);
                log.debug("Error message: " + errorMessage);
                VOTableWriter ewriter = new VOTableWriter();
                ewriter.setJob(job);
                String filename = "error_" + job.getID() + "." + ewriter.getExtension();
                if (syncOutput != null)
                {
                    syncOutput.setHeader("Content-Type", ewriter.getContentType());
                    String disp = "attachment; filename=\""+filename+"\"";
                    syncOutput.setHeader("Content-Disposition", disp);
                    ewriter.write(t, syncOutput.getOutputStream());
                }
                else
                {
                    rs.setJob(job);
                    rs.setFilename(filename);
                    rs.setContentType(ewriter.getContentType());
                    errorURL = rs.put(t, ewriter);

                    tList.add(System.currentTimeMillis());
                    sList.add("store error with ResultStore ");
                }
                
                log.debug("Error URL: " + errorURL);
                ErrorSummary es = new ErrorSummary(errorMessage, ErrorType.FATAL, errorURL);
                log.debug("setting ExecutionPhase = " + ExecutionPhase.ERROR);
                jobUpdater.setPhase(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.ERROR, es, new Date());
            }
            catch (Throwable t2)
            {
                log.error("failed to persist error", t2);
                // this is really bad: try without the document
                log.debug("setting ExecutionPhase = " + ExecutionPhase.ERROR);
                ErrorSummary es = new ErrorSummary(errorMessage, ErrorType.FATAL);
                try
                {
                    jobUpdater.setPhase(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.ERROR, es, new Date());
                }
                catch(Throwable ignore) { }
            }
        }
        finally
        {
            tList.add(System.currentTimeMillis());
            sList.add("set final job state: ");

            for (int i = 1; i < tList.size(); i++)
            {
                long dt = tList.get(i) - tList.get(i - 1);
                log.info(job.getID() + " -- " + sList.get(i) + dt + "ms");
            }

            log.debug("DONE");
        }
    }
    
}
