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

package ca.nrc.cadc.uws.server;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobRef;
import ca.nrc.cadc.uws.Parameter;

/**
 * Service interface between the application and the Universal Worker Service
 * library components. Each application must provide an implementation that is fully
 * configured with a JobExecutor and JobPersistence. The provided
 * <code>ca.nrc.cadc.uws.server.SimpleJobManager</code> should suffice for most purposes.
 * 
 * @author pdowler
 */
public interface JobManager 
{
    /**
     * Shutdown and release any resources. This includes ThreadPools, connections, open files, etc.
     */
    public void terminate()
        throws InterruptedException;

    public void setJobExecutor(JobExecutor je);

    public void setJobPersistence(JobPersistence jp);

    /**
     * Create a new persisted job from the content of the specified job
     * description.
     *
     * @param job
     * @return the created job
     * @throws JobPersistenceException
     * @throws TransientException 
     */
    public Job create(Job job)
        throws JobPersistenceException, TransientException;

    /**
     * Get the specified job.
     *
     * @param jobID
     * @return
     * @throws JobNotFoundException
     * @throws JobPersistenceException
     * @throws TransientException 
     */
    public Job get(String jobID)
        throws JobNotFoundException, JobPersistenceException, TransientException;

    /**
     * Get an iterator over the current jobs.
     * 
     * @return
     */
    public Iterator<JobRef> iterator()
        throws JobPersistenceException, TransientException;

    /**
     * Delete the specified job.
     *
     * @param jobID
     * @throws JobNotFoundException
     * @throws JobPersistenceException
     * @throws TransientException 
     */
    public void delete(String jobID)
        throws JobNotFoundException, JobPersistenceException, TransientException;

    /**
     * Attempt to update the specified job with new job control settings. The
     * implementation may or may not allow the changes, or may limit the values.
     * Null values for job control parameters mean the caller did not specify.
     *
     * @param jobID
     * @param destruction
     * @param duration
     * @param quote
     * @throws JobNotFoundException
     * @throws JobPersistenceException
     * @throws JobPhaseException
     * @throws TransientException 
     */
    public void update(String jobID, Date destruction, Long duration, Date quote)
        throws JobNotFoundException, JobPersistenceException, JobPhaseException, TransientException;

    /**
     * Add parameters to the the specified job.
     *
     * @param jobID
     * @param params
     * @throws JobNotFoundException
     * @throws JobPersistenceException
     * @throws TransientException 
     */
    public void update(String jobID, List<Parameter> params)
        throws JobNotFoundException, JobPersistenceException, JobPhaseException, TransientException;

    // not currently needed by any use cases, but plausible
    //public void update(String jobID, JobInfo info)
    //    throws JobNotFoundException;

    /**
     * Execute the specified job asynchronously.
     *
     * @param jobID
     * @throws JobNotFoundException
     * @throws JobPersistenceException
     * @throws JobPhaseException
     * @throws TransientException 
     */
    public void execute(String jobID)
        throws JobNotFoundException, JobPersistenceException, JobPhaseException, TransientException;
    
    public void execute(Job job)
        throws JobNotFoundException, JobPersistenceException, JobPhaseException, TransientException;

    public void execute(String jobID, SyncOutput output)
        throws JobNotFoundException, JobPersistenceException, JobPhaseException, TransientException;

    public void execute(Job job, SyncOutput outout)
        throws JobNotFoundException, JobPersistenceException, JobPhaseException, TransientException;

    /**
     * Abort the specified job.
     *
     * @param jobID
     * @throws JobNotFoundException
     * @throws JobPersistenceException
     * @throws JobPhaseException
     * @throws TransientException 
     * @throws IllegalStateException if the job is not in a state from which it can be aborted
     */
    public void abort(String jobID)
        throws JobNotFoundException, JobPersistenceException, JobPhaseException, TransientException;
}
