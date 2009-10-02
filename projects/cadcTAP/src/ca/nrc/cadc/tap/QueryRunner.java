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
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;

import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobRunner;
import ca.nrc.cadc.uws.Parameter;

public class QueryRunner implements JobRunner
{
	private static final Logger logger = Logger.getLogger(QueryRunner.class);
	
	private static final HashMap<String,String> langValidators = new HashMap<String,String>();
	private static final HashMap<String,String> langQueries    = new HashMap<String,String>();
	
	static
	{
		langValidators.put( Validator.ADQL, "ca.nrc.cadc.tap.AdqlValidator" );
		langValidators.put( Validator.SQL,  "ca.nrc.cadc.tap.SqlValidator"  );
		langQueries.put(    Validator.ADQL, "ca.nrc.cadc.tap.AdqlQuery"     );
		langQueries.put(    Validator.SQL,  "ca.nrc.cadc.tap.SqlQuery"      );
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
    	try
        {
            job.setExecutionPhase( ExecutionPhase.EXECUTING );
            
            TapValidator tapValidator = new TapValidator();
            List<Parameter> paramList = job.getParameterList();
            tapValidator.validate( paramList );
            
            //  The only way to get this far is with REQUEST=doQuery and LANG=ADQL or LANG=SQL.

        	String lang = tapValidator.getLang();
        	
            Validator langValidator = (Validator) Class.forName( langValidators.get(lang) ).newInstance();
            TapQuery  tapQuery      = (TapQuery)  Class.forName( langQueries.get(lang) ).newInstance();
        	
        	langValidator.validate( paramList );
        	
        	String sql = tapQuery.getSQL( paramList );
        	
        	//  Run the sql here.
            
            String fileStoreClassName = System.getProperty( "ca.nrc.cadc.tap.QueryRunner.fileStoreClassName" );
            fs = (FileStore) Class.forName( fileStoreClassName ).newInstance();
            URL fsURL = fs.put( null );

            throw new UnsupportedOperationException( "Getting here would normally mean success."); // for now
        	
            //job.setExecutionPhase( ExecutionPhase.COMPLETED );
		}
        catch ( Throwable t )
        {
        	String errorMessage = null;
        	URI    errorURI     = null;
        	
        	try
        	{
        		errorMessage = t.getMessage();
        		logger.debug( "Error message: "+errorMessage );
        		
        		String tmpDir        = System.getProperty( "java.io.tmpdir" );
            	String separator     = System.getProperty( "file.separator" );
            	String errorFileName = System.getProperty( "ca.nrc.cadc.tap.QueryRunner.errorFileName" );
            	String errorFullPath = tmpDir+separator+errorFileName;
           		logger.debug( "Full path name of error file: "+errorFullPath );
           		
           		File errorFile = new File( errorFullPath );
           		errorURI = errorFile.toURI();
           		logger.debug( "URI of error file: "+errorURI.toString() );
           		
           		FileOutputStream errorOutput = new FileOutputStream( errorFile );
           		TapTableWriter errorWriter = new TapTableWriter();
           		errorWriter.write( t, errorOutput );
           		errorOutput.close();
           		logger.debug( "Error written to file." );
        	}
        	catch ( IOException ioe )
        	{
        		logger.error( "Failed to write error to file: "+ioe.getMessage() );
        	}
        	
			ErrorSummary error = new ErrorSummary( errorMessage, errorURI );
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
