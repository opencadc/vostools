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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

import org.apache.log4j.Logger;

import ca.nrc.cadc.tap.parser.adql.TapSelectItem;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.tap.schema.TapSchemaDAO;
import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobRunner;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.Result;

public class QueryRunner implements JobRunner
{
	private static final Logger logger = Logger.getLogger(QueryRunner.class);
	
	private static final HashMap<String,String> langQueries    = new HashMap<String,String>();
	
    
    private static String dataSourceName;
    private static String fileStoreClassName;
    
	static
	{
		langQueries.put(Validator.ADQL, "ca.nrc.cadc.tap.AdqlQuery");
		langQueries.put(Validator.SQL, "ca.nrc.cadc.tap.SqlQuery");
        
        // TODO: these need to be easily configurable
        dataSourceName = "jdbc/pg";
        fileStoreClassName = System.getProperty( "ca.nrc.cadc.tap.QueryRunner.fileStoreClassName" );
	}
	
	private Job job;

    public QueryRunner()
    {
        try
        {
        	logger.debug( "Initializing" );
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            return;
        }
    }

    public void setJob( Job job )
    {
        this.job = job;
    }

    public void run()
    {
        logger.debug("START");

        try
        {
        	doit();
        }
        finally
        {
            logger.debug("DONE");
        }
    }
    
    private void doit()
    {
        FileStore fs = null;
        String tmpDir = System.getProperty( "java.io.tmpdir" );
        job.setExecutionPhase(ExecutionPhase.UNKNOWN);
        try
        {
            if (fileStoreClassName == null)
                logger.error("System property ca.nrc.cadc.tap.QueryRunner.fileStoreClassName was not set");
            fs = (FileStore) Class.forName( fileStoreClassName ).newInstance();
        }
        catch ( Throwable t )
        {
        	logger.error( "Failed to instantiate FileStore class: "+fileStoreClassName, t );
        	return;
        }

        try
        {
            job.setExecutionPhase( ExecutionPhase.EXECUTING );
            
            // start processing the job
            List<Parameter> paramList = job.getParameterList();
            
            // REQUEST, VERSION
            TapValidator tapValidator = new TapValidator();
            tapValidator.validate( paramList );
            
            // find DataSource via JNDI lookup
            Context initContext = new InitialContext();
            Context envContext = (Context) initContext.lookup("java:/comp/env");
            DataSource dataSource = (DataSource) envContext.lookup(dataSourceName);
            
            // extract TapSchema
            TapSchemaDAO dao = new TapSchemaDAO(dataSource);
            TapSchema tapSchema = dao.get();
            
            // LANG
        	String lang = tapValidator.getLang();
            TapQuery  tapQuery      = (TapQuery)  Class.forName( langQueries.get(lang) ).newInstance();
            tapQuery.setTapSchema(tapSchema);
            tapQuery.setParameterList(paramList);
        	String sql = tapQuery.getSQL();
            List<TapSelectItem> selectList = tapQuery.getSelectList();
            
            // TODO: UPLOAD
            
            // TODO: MAXREC
            
            // FORMAT
            TableWriter writer = TableWriterFactory.getWriter(paramList);
            writer.setTapSchema(tapSchema);
            writer.setSelectList(selectList);
            
            Connection connection = null;
            PreparedStatement pstmt = null;
            ResultSet rs = null;
            File tmpFile = null;
            try
            {
                connection = dataSource.getConnection();

                // execute
                pstmt = connection.prepareStatement(sql);
                rs = pstmt.executeQuery();

                // write result
                tmpFile = new File(tmpDir, "result_" + job.getJobId() + "." + writer.getExtension());
                OutputStream ostream = new FileOutputStream(tmpFile);
                writer.write(rs, ostream);
                ostream.close();

                rs.close();
                pstmt.close();
            } 
            catch (SQLException ex)
            {
                logger.error("SQL Execution error.", ex);
                throw ex;
            } 
            finally
            {
                if (connection != null)
                    connection.close();
            }

            // store result
            URL url = fs.put(tmpFile);
            Result res = new Result(tmpFile.getName(), url);
            
            job.setExecutionPhase( ExecutionPhase.COMPLETED );
		}
        catch ( Throwable t )
        {
        	String errorMessage = null;
        	URL errorURL        = null;
        	
        	try
        	{
        		errorMessage = t.getClass().getSimpleName() + ":" + t.getMessage();
        		logger.debug( "Error message: "+errorMessage );
        		VOTableWriter writer = new VOTableWriter();
                File errorFile = new File( tmpDir, "error_" + job.getJobId() + "." + writer.getExtension() );
                logger.debug( "Error file: "+errorFile.getAbsolutePath());
           		FileOutputStream errorOutput = new FileOutputStream( errorFile );
           		writer.write(t, errorOutput );
           		errorOutput.close();          		
                errorURL = fs.put(errorFile);
           		logger.debug( "Error URL: " + errorURL);
        	}
        	catch ( IOException ioe )
        	{
        		logger.error( "Failed to write error to file: "+ioe.getMessage() );
        	}
        	
			ErrorSummary error = new ErrorSummary( errorMessage, errorURL );
			job.setErrorSummary( error );
            job.setExecutionPhase( ExecutionPhase.ERROR );
		}
		return;
    }

    public Job getJob()
    {
        return job;
    }
}
