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

import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.naming.NamingException;
import javax.security.auth.Subject;
import javax.sql.DataSource;

import org.apache.log4j.Logger;

import ca.nrc.cadc.auth.IdentityManager;
import ca.nrc.cadc.auth.X500IdentityManager;
import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobRef;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.Result;

/**
 * JobPersistence implementation that uses an underlying relational database
 * to store jobs. This class is thread-safe and may be used by many request
 * threads at the same time. It creates a new JobDAO class internally for each
 * call.
 *
 * @author pdowler
 */
public abstract class DatabaseJobPersistence implements JobPersistence, JobUpdater
{
    private static final Logger log = Logger.getLogger(DatabaseJobPersistence.class);

    protected StringIDGenerator idGenerator;
    protected IdentityManager identityManager;

    /**
     * Constructor. This uses a RandomStringGenerator(16) as the ID generator.
     */
    protected DatabaseJobPersistence()
    {
        this(new RandomStringGenerator(16), new X500IdentityManager());
    }

    /**
     * Constructor.
     * 
     * @param idGenerator
     * @param identityManager 
     */
    protected DatabaseJobPersistence(StringIDGenerator idGenerator, IdentityManager identityManager)
    {
        this.idGenerator = idGenerator;
        this.identityManager = identityManager;
    }

    public void terminate() throws InterruptedException
    {
        // no-op: assume this relies on JNDI data sources that are managed externally
    }


    protected JobDAO getDAO()
        throws JobPersistenceException
    {
        try
        {
            DataSource ds = getDataSource();
            JobDAO.JobSchema js = getJobSchema();
            return new JobDAO(ds, js, identityManager, idGenerator);
        }
        catch(NamingException ex)
        {
            throw new JobPersistenceException("failed to find/create DataSource", ex);
        }
        finally { }
        
    }

    /**
     * Get a JobSchema describing the tables in the RDBMS.
     *
     * @return
     */
    protected abstract JobDAO.JobSchema getJobSchema();

    /**
     * Get a DataSource to use to persist jobs to a RDBMS.
     * 
     * @return
     */
    protected abstract DataSource getDataSource()
        throws NamingException;

    public Job get(String jobID)
        throws JobNotFoundException, JobPersistenceException, TransientException
    {
        JobDAO dao = getDAO();
        return dao.get(jobID);
    }

    public void getDetails(Job job)
        throws JobPersistenceException, TransientException
    {
        JobDAO dao = getDAO();
        dao.getDetails(job);
    }

    public Iterator<JobRef> iterator()
        throws JobPersistenceException, TransientException
    {
        JobDAO dao = getDAO();
        return dao.iterator();
    }

    public Job put(Job job)
        throws JobPersistenceException, TransientException
    {
        AccessControlContext acContext = AccessController.getContext();
        Subject caller = Subject.getSubject(acContext);
        JobDAO dao = getDAO();
        return dao.put(job, caller);
    }

    public void addParameters(String jobID, List<Parameter> params) 
        throws JobNotFoundException, JobPersistenceException, TransientException
    {
        log.debug("addParameters: " + jobID + "," + toString(params));
        JobDAO dao = getDAO();
        dao.addParameters(jobID, params);
    }
    private String toString(List list)
    {
        if (list==null)
            return "null";
        return "List[" + list.size() +"]";
    }

    public void delete(String jobID)
        throws JobPersistenceException, TransientException
    {
        JobDAO dao = getDAO();
        dao.delete(jobID);
    }

    public ExecutionPhase getPhase(String jobID)
        throws JobNotFoundException, JobPersistenceException, TransientException
    {
        JobDAO dao = getDAO();
        return dao.getPhase(jobID);
    }

    /**
     * Set the phase.
     * @param jobID
     * @param ep
     * @throws JobNotFoundException
     * @throws JobPersistenceException
     * @throws TransientException 
     */
    public void setPhase(String jobID, ExecutionPhase ep)
        throws JobNotFoundException, JobPersistenceException, TransientException
    {
        setPhase(jobID, null, ep);
    }

    /**
     * Conditionally change phase from start to end.
     * 
     * @param jobID
     * @param start
     * @param end
     * @return the final phase (end) or null if not successful
     * @throws JobNotFoundException
     * @throws TransientException 
     */
    public ExecutionPhase setPhase(String jobID, ExecutionPhase start, ExecutionPhase end)
        throws JobNotFoundException, JobPersistenceException, TransientException
    {
        JobDAO dao = getDAO();
        return dao.set(jobID, start, end, null);
    }

    public ExecutionPhase setPhase(String jobID, ExecutionPhase start, ExecutionPhase end, Date date)
        throws JobNotFoundException, JobPersistenceException, TransientException
    {
        JobDAO dao = getDAO();
        return dao.set(jobID, start, end, date);
    }

    /**
     * Conditionally change phase from start to end and, if successful, add the specified results to the
     * job.
     * 
     * @param jobID
     * @param start
     * @param end
     * @param results
     * @return the final phase (end) or null if not successful
     * @throws JobNotFoundException
     * @throws TransientException 
     */
    public ExecutionPhase setPhase(String jobID, ExecutionPhase start, ExecutionPhase end, List<Result> results, Date date)
        throws JobNotFoundException, JobPersistenceException, TransientException
    {
        JobDAO dao = getDAO();
        return dao.set(jobID, start, end, results, date);
    }

    public ExecutionPhase setPhase(String jobID, ExecutionPhase start, ExecutionPhase end, ErrorSummary error, Date date)
        throws JobNotFoundException, JobPersistenceException, TransientException
    {
        JobDAO dao = getDAO();
        return dao.set(jobID, start, end, error, date);
    }
}
