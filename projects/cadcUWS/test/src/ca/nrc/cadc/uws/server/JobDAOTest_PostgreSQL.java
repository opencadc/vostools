
package ca.nrc.cadc.uws.server;

import ca.nrc.cadc.db.ConnectionConfig;
import ca.nrc.cadc.db.DBConfig;
import ca.nrc.cadc.db.DBUtil;
import ca.nrc.cadc.util.Log4jInit;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 *
 * @author pdowler
 */
public class JobDAOTest_PostgreSQL extends JobDAOTest
{
    private static Logger log = Logger.getLogger(JobDAOTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.uws.server", Level.INFO);
        try
        {
            DBConfig conf = new DBConfig();
            ConnectionConfig cc = conf.getConnectionConfig("UWS_PG_TEST", "cvodb");
            dataSource = DBUtil.getDataSource(cc);
            log.info("configured data source: " + cc.getServer() + "," + cc.getDatabase() + "," + cc.getDriver() + "," + cc.getURL());

            String userName = System.getProperty("user.name");
            JOB_SCHEMA = new JobDAO.JobSchema(userName + ".Job", userName + ".JobDetail");
        }
        catch(Exception ex)
        {
            log.error("setup failed", ex);
            throw new IllegalStateException("failed to create DataSource", ex);
        }
    }
}
