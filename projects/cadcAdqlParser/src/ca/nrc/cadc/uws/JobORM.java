/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÃES ASTRONOMIQUES  **************
*
*  (c) 2009.                            (c) 2009.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits rÃ©servÃ©s
*                                       
*  NRC disclaims any warranties,        Le CNRC dÃ©nie toute garantie
*  expressed, implied, or               Ã©noncÃ©e, implicite ou lÃ©gale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           Ãªtre tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou gÃ©nÃ©ral,
*  arising from the use of the          accessoire ou fortuit, rÃ©sultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        Ãªtre utilisÃ©s pour approuver ou
*  products derived from this           promouvoir les produits dÃ©rivÃ©s
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  prÃ©alable et particuliÃ¨re
*                                       par Ã©crit.
*                                       
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*                                       
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la âGNU Affero General Public
*  License as published by the          Licenseâ telle que publiÃ©e
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (Ã  votre grÃ©)
*  any later version.                   toute version ultÃ©rieure.
*                                       
*  OpenCADC is distributed in the       OpenCADC est distribuÃ©
*  hope that it will be useful,         dans lâespoir quâil vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans mÃªme la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÃ
*  or FITNESS FOR A PARTICULAR          ni dâADÃQUATION Ã UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           GÃ©nÃ©rale Publique GNU Affero
*  more details.                        pour plus de dÃ©tails.
*                                       
*  You should have received             Vous devriez avoir reÃ§u une
*  a copy of the GNU Affero             copie de la Licence GÃ©nÃ©rale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce nâest
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 4 $
*
************************************************************************
*/

package ca.nrc.cadc.uws;

import ca.nrc.cadc.util.ConversionUtil;
import ca.nrc.cadc.util.DateUtil;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.apache.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * Manages the persistance of UwsJob instances.
 * @author pdowler
 */
public class JobORM 
{
    private static Logger log = Logger.getLogger(JobORM.class);
 
    private static int MAX_STRING_LENGTH = 8192;
    
    // FOR CRUDE TESTING ONLY
    private static Map joblist = new TreeMap();

    private String serviceName;
    private String schema;
    private String jobTable;
    private String resourceTable;
    private String resultTable;
    private String listenerTable;
    
    private DataSourceTransactionManager writeTxnManager;
    private DefaultTransactionDefinition def = new DefaultTransactionDefinition();
    
    public JobORM(String serviceName, String schema)
    {
        this.serviceName = serviceName;
        this.schema = schema;
        this.jobTable = schema + ".UwsJob";
        this.resourceTable = schema + ".UwsResource";
        this.resultTable = schema + ".UwsResult";
        this.listenerTable = schema + ".UwsListener";
    }
    
    public UwsJob get(String id, JdbcTemplate jdbc)
    {
        return get(id, false, jdbc);
    }
    
    public UwsJob get(String id, boolean evenIfDeleted, JdbcTemplate jdbc)
    {
        UwsJob job = null;
        String jobSQL = getJobSQL(id, evenIfDeleted);
        String resourceSQL = getResourceSQL(id);
        String resultSQL = getResultSQL(id);
        
        log.debug("SQL: " + jobSQL);
        log.debug("SQL: " + resourceSQL);
        log.debug("SQL: " + resultSQL);
        
        if (jdbc == null)  // testing SQL generation
        {
            log.warn("**** JdbcTemplate is null, using Map for testing ****");
            job = (UwsJob) joblist.get(id);
            return job;
        }
        List results = jdbc.query(jobSQL, new JobMapper());
        if (results.size() == 0)
            return null;
        job = (UwsJob) results.get(0);
        job.resources = jdbc.query(resourceSQL, new ResourceMapper());
        job.results = jdbc.query(resultSQL, new ResultMapper());
          
        log.debug("found: " + job);
        if (job == null)
            return null;
        if (job.deleted)
            return null;
        return job;
    }
    
    public void put(UwsJob job, JdbcTemplate jdbc)
    {
        if (job.jobID == null)
            job.jobID = generateID();
        
        List sql = getUpdateSQL(job);
        for (int i=0; i<sql.size(); i++)
            log.debug("SQL: " + sql.get(i));
        
        if (jdbc == null) // testing SQL generation
        {
            log.warn("**** JdbcTemplate is null, using Map for testing ****");
            joblist.put(job.jobID, job);
            return;
        }
        
        // TODO: start a txn
        writeTxnManager = new DataSourceTransactionManager(jdbc.getDataSource());
        log.debug("starting transaction");
        TransactionStatus txn = writeTxnManager.getTransaction(def);
        boolean ok = false;
        try
        {
            for (int i=0; i<sql.size(); i++)
            {
                String s = (String) sql.get(i);
                jdbc.update(s);
            }
            log.debug("committing transaction");
            writeTxnManager.commit(txn);
            log.debug("commit: OK");
            ok = true;
        }
        finally
        {
            if (!ok)
            {
                log.warn("failed to insert/update " + job  + ": trying to rollback the transaction");
                writeTxnManager.rollback(txn);
                log.warn("rollback: OK");
            }
        }
    }
    
    public void delete(String id, JdbcTemplate jdbc)
    {
        // mark it deleted
        String sql = "UPDATE " + jobTable + " SET isDeleted = " + toLiteral(true) 
            + " WHERE jobID = '" + id + "'";
        log.debug("SQL: " + sql);
        if (jdbc == null) // testing SQL generation
        {
            log.warn("**** JdbcTemplate is null, using Map for testing ****");
            UwsJob job = (UwsJob) joblist.get(id);
            job.deleted = true;
            return;
        }
        jdbc.update(sql);
    }
    
    public void addListener(String id, String listener, JdbcTemplate jdbc)
    {
        String sql = "INSERT INTO  " + listenerTable + " VALUES (" 
                + toLiteral(id) + "," 
                + toLiteral(listener) + ")";
        jdbc.update(sql);
    }
    
    public List getListeners(String id, JdbcTemplate jdbc)
    {
        String sql = "SELECT uri FROM " + listenerTable + " where jobID = " + toLiteral(id);
        return (List) jdbc.query(sql, new StringListMapper());
    }
    
    private class StringListMapper implements RowMapper
    {

        public Object mapRow(ResultSet rs, int row) 
            throws SQLException
        {
            return rs.getString(1);
        }
        
    }
    
    private String getJobSQL(String id, boolean getIfDeleted)
    {
        String ret = "SELECT " + JOB_COLUMNS + " FROM " + jobTable + " WHERE jobID = '" + id + "'";
        if (!getIfDeleted)
            ret += " AND isDeleted = " + toLiteral(false);
        return ret;
    }
    private String getResourceSQL(String id)
    {
        return "SELECT " + RESOURCE_COLUMNS + " FROM " + resourceTable + " WHERE jobID = '" + id + "'";
    }
    private String getResultSQL(String id)
    {
        return "SELECT " + RESULT_COLUMNS + " FROM " + resultTable + " WHERE jobID = '" + id + "'";
    }
    
    private static String JOB_COLUMNS = 
        "jobID,creation,lastModified,isDeleted,"
        + "quote,termination,destruction,phase,startTime,endTime,"
        + "error_mimetype,error_message,serviceName";

    private List getUpdateSQL(UwsJob job)
    {
        String emimetype = null;
        //byte[] emessage = null;
        String emessage = null;
        if (job.error != null)
        {
            emimetype = job.error.mimetype;
            emessage = Util.encode(job.error.message);
            if (emessage.length() > MAX_STRING_LENGTH)
            {
                log.warn("truncating long error mesage for job " + job.jobID);
                emessage = emessage.substring(0, MAX_STRING_LENGTH - 4) + "+...";
            }
        }
        List stmts = new ArrayList();
        stmts.add("DELETE from " + jobTable + " WHERE jobID = " + toLiteral(job.getID()));
        stmts.add("DELETE from " + resourceTable + " WHERE jobID = " + toLiteral(job.getID()));
        stmts.add("DELETE from " + resultTable + " WHERE jobID = " + toLiteral(job.getID()));
        
        stmts.add("INSERT INTO " + jobTable + " VALUES (" 
                + toLiteral(job.getID()) + "," 
                + toLiteral(job.creation) + ","
                + toLiteral(job.lastModified) + ","
                + toLiteral(job.deleted) + ","
                + toLiteral(job.getQuote()) + ","
                + toLiteral(job.getTermination()) + ","
                + toLiteral(job.getDestruction()) + ","
                + toLiteral(job.getPhase()) + ","
                + toLiteral(job.getStartTime()) + ","
                + toLiteral(job.getEndTime()) + ","
                + toLiteral(emimetype) + ","
                + toLiteral(emessage) + "," 
                + toLiteral(this.serviceName) + ")"

                );
        
        for (int i=0; i < job.resources.size(); i++)
        {
            UwsResource u = (UwsResource) job.resources.get(i);
            // carefully handle case where value is too long for the DB column
            String val = Util.encode(u.value);
            if (val.length() > MAX_STRING_LENGTH)
            {
                if (job.error == null)
                {
                    UwsError e = new UwsError();
                    job.setError(e);
                }
                job.error.mimetype = "text/plain";
                job.error.message = "value for resource exceeds internal limit ("+MAX_STRING_LENGTH+")";
                job.resources.remove(i); // can there be multiple excessive resources?
                return getUpdateSQL(job);
            }
            stmts.add("INSERT INTO " + resourceTable + " VALUES (" 
                    + toLiteral(job.getID()) + "," 
                    + toLiteral(u.name) + ","
                    + toLiteral(val)
                    + ")");
        }
        
        for (int i=0; i < job.results.size(); i++)
        {
            UwsResult u = (UwsResult) job.results.get(i);
            // carefully handle case where value is too long for the DB column
            String val = Util.encode(u.uri);
            if (val.length() > MAX_STRING_LENGTH)
            {
                if (job.error == null)
                {
                    UwsError e = new UwsError();
                    job.setError(e);
                }
                job.error.mimetype = "text/plain";
                job.error.message = "value for result URI exceeded internal limit ("+MAX_STRING_LENGTH+")";
                job.results.clear(); // there can be multiple results, so be safe
                return getUpdateSQL(job);
            }
            stmts.add("INSERT INTO " + resultTable + " VALUES (" 
                    + toLiteral(job.getID()) + "," 
                    + toLiteral(u.name) + ","
                    + toLiteral(val)
                    + ")");
        }
        return stmts;
    }
    private class JobMapper implements RowMapper
    {
        public Object mapRow(ResultSet rs, int row)
            throws SQLException
        {
            UwsJob job = new UwsJob();
            int i = 1;
            job.jobID = rs.getString(i++);
            job.creation = toDateOrNull(rs.getObject(i++));
            job.lastModified = toDateOrNull(rs.getObject(i++));
            job.deleted = Boolean.parseBoolean(rs.getString(i++));
            job.setQuote(toDateOrNull(rs.getObject(i++)));
            job.setTermination( toLongOrNull(rs.getObject(i++)));
            job.setDestruction(toDateOrNull(rs.getObject(i++)));
            job.setPhase(rs.getString(i++));
            job.setStartTime(toDateOrNull(rs.getObject(i++)));
            job.setEndTime(toDateOrNull(rs.getObject(i++)));
            String etype = rs.getString(i++);
            String emsg = rs.getString(i++);
            if (etype != null && emsg != null)
            {
                UwsError error = new UwsError();
                error.mimetype = (String) etype;
                error.message = (String) Util.decode(emsg);
                job.setError(error);
            }
            else
            {
                i++; // consume null mimetype column
                i++; // consume null message column
            }
            return job;
        }
    }
    
    private static String RESOURCE_COLUMNS = "jobID,name,value";
    private class ResourceMapper implements RowMapper
    {
        public Object mapRow(ResultSet rs, int row)
            throws SQLException
        {
            // 1-offset, skip jobID column
            String name = rs.getString(2);
            String value = rs.getString(3);
            return new UwsResource(name, Util.decode(value)); 
        }
    }
    
    private static String RESULT_COLUMNS = "jobID,name,uri";
    private class ResultMapper implements RowMapper
    {
        public Object mapRow(ResultSet rs, int row)
            throws SQLException
        {
            // 1-offset, skip jobID column
            String name = rs.getString(2);
            String uri = rs.getString(3);
            return new UwsResult(name, (URI) Util.decode(uri));
        }
    }
    private Date toDateOrNull(Object obj)
    {
        if (obj == null)
            return null;
        return DateUtil.toDate(obj);
    }
    private Integer toIntegerOrNull(Object obj)
        throws SQLException
    {
        if (obj == null)
            return null;
        Number n = (Number) obj;
        if (n instanceof Integer)
            return (Integer) n;
        return new Integer(n.intValue());
    }
    private Long toLongOrNull(Object obj)
    {
        if (obj == null)
            return null;
        
        if (obj instanceof byte[])
            return new Long(ConversionUtil.hexToLong((byte[]) obj));
        
        Number n = (Number) obj;
        if (n instanceof Integer)
            return (Long) n;
        return new Long(n.longValue());
    }
    
    private String toLiteral(byte[] b)
    {
        if (b == null)
            return "NULL";
        return "X'" + ConversionUtil.toHexString(b) + "'";
    }
    private String toLiteral(Long val)
    {
        if (val == null)
            return "NULL";
        return toLiteral(ConversionUtil.longToHex(val.longValue()));
    }
    private String toLiteral(Date d)
    {
        if (d == null)
            return "NULL";
        return "'" + DateUtil.isoDateFormat.format(d) + "'";
    }
    private String toLiteral(String s)
    {
        if (s == null)
            return "NULL";
        return "'" + s + "'";
    }
    private String toLiteral(URI uri)
    {
        if (uri == null)
            return "NULL";
        return toLiteral(uri.toString());
    }
    private String toLiteral(Number n)
    {
        if (n == null)
            return "NULL";
        return n.toString();
    }
    private String toLiteral(boolean b)
    {
        return "'" + Boolean.toString(b) + "'";
    }
    // generate a random modest-length lower case string
    private static int ID_LENGTH = 16;
    private static String ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    
    private static String generateID()
    {
        Random rnd = new Random(System.currentTimeMillis());
        char[] c = new char[ID_LENGTH];
        c[0] = ID_CHARS.charAt(rnd.nextInt(ID_CHARS.length() - 10)); // letters only
        for (int i=1; i<ID_LENGTH; i++)
            c[i] = ID_CHARS.charAt(rnd.nextInt(ID_CHARS.length()));
        return new String(c);
    }
    
    public static void main(String[] args)
    {
        for (int i=0; i<20; i++)
            System.out.println("generateID: " + generateID());
    }
}
