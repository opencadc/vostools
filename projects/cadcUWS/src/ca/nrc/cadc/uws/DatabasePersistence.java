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

import java.util.Collection;
import java.util.List;
import java.util.Random;

import javax.sql.DataSource;

/**
 * Class to provide database persistence of jobs through the use
 * of the JobDAO.
 * 
 * A new JobDAO object is instantiated upon each request to ensure
 * its thread safety.
 * 
 * @author majorb
 *
 */
public abstract class DatabasePersistence implements JobPersistence
{
    // generate a random modest-length lower case string
    private static final int ID_LENGTH = 16;
    private static final String ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    
    // shared random number generator for jobID generation
    private Random rnd = new Random(System.currentTimeMillis());

    /**
     * DatabasePersistence constructor.
     */
    public DatabasePersistence()
    {
    }
    
    /**
     * Create a new JobDAO and set the appropriate resources.
     * 
     * TODO:  Refactor the relationship between DatabasePersistence
     *        and JobDAO so that there isn't a circular dependency
     *        (jobDAO.setDatabasePersistence(this))
     * 
     * @return A new JobDAO
     */
    private JobDAO createJobDAO()
    {
        JobDAO jobDAO = new JobDAO();
        jobDAO.setDataSource(this.getDataSource());
        jobDAO.setDatabasePersistence(this);
        return jobDAO;
    }

    /**
     * Delete the job.
     */
    @Override
    public void delete(String jobID)
    {
        JobDAO jobDAO = createJobDAO();
        jobDAO.delete(jobID);
    }

    /**
     * Get the job.
     */
    @Override
    public Job getJob(String jobID)
    {
        JobDAO jobDAO = createJobDAO();
        return jobDAO.getJob(jobID);
    }

    /**
     * Get all the jobs.
     */
    @Override
    public Collection<Job> getJobs()
    {
        JobDAO jobDAO = createJobDAO();
        return jobDAO.getJobs();
    }

    /**
     * Save the job.
     */
    @Override
    public Job persist(Job job)
    {
        JobDAO jobDAO = createJobDAO();
        return jobDAO.persist(job);
    }

    // package access for use by JobDAO
    String generateID()
    {
        synchronized(rnd)
        {
            char[] c = new char[ID_LENGTH];
            c[0] = ID_CHARS.charAt(rnd.nextInt(ID_CHARS.length() - 10)); // letters only
            for (int i=1; i<ID_LENGTH; i++)
                c[i] = ID_CHARS.charAt(rnd.nextInt(ID_CHARS.length()));
            return new String(c);
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
     * Returns the datasource to be used.
     * @return
     */
    protected abstract DataSource getDataSource();

}
