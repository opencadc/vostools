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
import java.util.Calendar;
import java.util.Date;


/**
 * Default implementation of the Job Service.
 */
public class BasicJobManager implements JobManager
{
    private JobPersistence jobPersistence;

    /**
     * Maximum execution time in seconds.
     */
    protected int timeLimit = 600; // 10 minutes

    /**
     * Number of hours a job will be stored before being destroyed.
     */
    protected int destructionLimit = 24; // hours


    /**
     * Obtain the Jobs that are available to the client.
     *
     * @return Collection of Job instances, or empty Collection, never NULL.
     */
    public Collection<Job> getJobs()
    {
        return getJobPersistence().getJobs();
    }

    /**
     * Obtain the job with the given identifier.
     *
     * @param jobID Job Identifier.
     * @return Job instance, or null if none found.
     */
    public Job getJob(final String jobID)
    {
        return getJobPersistence().getJob(jobID);
    }

    /**
     * Delete the specified job.
     *
     * @param jobID
     */
    public void delete(String jobID)
    {
        getJobPersistence().delete(jobID);
    }


    /**
     * Persist the given Job.
     *
     * @param job The Job to persist.
     * @return The persisted Job, populated, as necessary, with a
     *         unique identifier.
     */
    public Job persist(final Job job)
    {
        insertDefaultValues(job);
        return getJobPersistence().persist(job);
    }

    /**
     * Insert default values to those fields that require a value, but haven't
     * been given one.
     *
     * @param job The job to insert values for.
     */
    public void insertDefaultValues(final Job job)
    {
        if (job.getExecutionPhase() == null)
        {
            job.setExecutionPhase(ExecutionPhase.PENDING);
        }

        if (job.getExecutionDuration() == 0)
        {
            job.setExecutionDuration(timeLimit);
        }

        if (job.getDestructionTime() == null)
        {
            final Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.HOUR, destructionLimit);

            job.setDestructionTime(cal.getTime());
        }

        if (job.getQuote() == null)
        {
            final Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.SECOND, timeLimit);
            // assuming immediate queue and execution
            job.setQuote(cal.getTime());
        }
    }


    public JobPersistence getJobPersistence()
    {
        return jobPersistence;
    }

    public void setJobPersistence(final JobPersistence jobPersistence)
    {
        this.jobPersistence = jobPersistence;
    }
}
