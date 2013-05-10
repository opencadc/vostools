
package ca.nrc.cadc.uws.server;

import ca.nrc.cadc.db.ConnectionConfig;
import ca.nrc.cadc.db.DBConfig;
import ca.nrc.cadc.db.DBUtil;
import ca.nrc.cadc.util.Log4jInit;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 *
 * @author pdowler
 */
public class JobDAOTest_Sybase extends JobDAOTest
{
    private static Logger log = Logger.getLogger(JobDAOTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.uws.server", Level.INFO);
        try
        {
            DBConfig conf = new DBConfig();
            ConnectionConfig cc = conf.getConnectionConfig("UWS_SYB_TEST", "cadctest");
            dataSource = DBUtil.getDataSource(cc);
            log.info("configured data source: " + cc.getServer() + "," + cc.getDatabase() + "," + cc.getDriver() + "," + cc.getURL());

            Map<String,Integer> jobTabLimits = new HashMap<String,Integer>();
            jobTabLimits.put("error_documentURL", 256);
            jobTabLimits.put("jobInfo_content", 256);
            
            Map<String,Integer> detailTabLimits = new HashMap<String,Integer>();
            detailTabLimits.put("value", 1024);

            JOB_SCHEMA = new JobDAO.JobSchema("uws_Job", "uws_JobDetail", true, jobTabLimits, detailTabLimits);
        }
        catch(Exception ex)
        {
            log.error("setup failed", ex);
            throw new IllegalStateException("failed to create DataSource", ex);
        }
    }

    // TODO: specific tests for column redirection when above limits are exceeded
}
