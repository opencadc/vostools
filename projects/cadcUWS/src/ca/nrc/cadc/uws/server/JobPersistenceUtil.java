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

import java.lang.reflect.Field;
import java.util.Date;
import java.util.Iterator;

import org.apache.log4j.Logger;

import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobRef;

/**
 * Static utility methods to help implement the JobPersistence interface.
 *
 * @author pdowler
 */
public class JobPersistenceUtil 
{
    private static Logger log = Logger.getLogger(JobPersistenceUtil.class);
    
    /**
     * Assign a jobID to the job using the specified ID generator.
     * 
     * @param job
     * @param jobID
     */
    public static void assignID(Job job, String jobID)
    {
        try
        {
            Field f = Job.class.getDeclaredField("jobID");
            f.setAccessible(true);
            f.set(job, jobID);
        }
        catch(NoSuchFieldException fex) { throw new RuntimeException("BUG", fex); }
        catch(IllegalAccessException bug) { throw new RuntimeException("BUG", bug); }
    }

    public static boolean isFinalPhase(ExecutionPhase ep)
    {
        if (ExecutionPhase.ABORTED.equals(ep))
            return true;
        if (ExecutionPhase.COMPLETED.equals(ep))
            return true;
        if (ExecutionPhase.ERROR.equals(ep))
            return true;
        return false;
    }

    /**
     * Determine if the specified transition is allowed by the UWS specification.
     * See UWS-1.0 section 2.1.3 for details.
     *
     * @param start
     * @param end
     * @throws IllegalArgumentException if the transition is invalid
     */
    public static void constraintPhaseTransition(ExecutionPhase start, ExecutionPhase end)
        throws IllegalArgumentException
    {
        if (ExecutionPhase.PENDING.equals(start) || ExecutionPhase.HELD.equals(start))
        {
            if (ExecutionPhase.QUEUED.equals(end))
                return;
            if (ExecutionPhase.ERROR.equals(end))
                return;
            if (ExecutionPhase.ABORTED.equals(end))
                return;
            throw new IllegalArgumentException("cannot change from " + start +  " -> " + end);
        }
        if (ExecutionPhase.QUEUED.equals(start))
        {
            if (ExecutionPhase.EXECUTING.equals(end))
                return;
            if (ExecutionPhase.ERROR.equals(end))
                return;
            if (ExecutionPhase.ABORTED.equals(end))
                return;
            throw new IllegalArgumentException("cannot change from " + start +  " -> " + end);
        }
        if (ExecutionPhase.EXECUTING.equals(start))
        {
            if (ExecutionPhase.EXECUTING.equals(end)) // allow no-op to append intermediate results
                return;
            if (ExecutionPhase.COMPLETED.equals(end))
                return;
            if (ExecutionPhase.ERROR.equals(end))
                return;
            if (ExecutionPhase.ABORTED.equals(end))
                return;
            throw new IllegalArgumentException("cannot change from " + start +  " -> " + end);
        }
        if (ExecutionPhase.ERROR.equals(start))
        {
            throw new IllegalArgumentException("cannot change from " + start +  " -> " + end);
        }
        if (ExecutionPhase.ABORTED.equals(start))
        {
            throw new IllegalArgumentException("cannot change from " + start +  " -> " + end);
        }
        if (ExecutionPhase.UNKNOWN.equals(start))
        {
            return; // allow it
        }
        // other values possible of someone adds to ExecutionPhase enum: assume a BUG
        throw new RuntimeException("BUG: found unexpected phase: " + start);
    }

    /**
     * Constrain the job destuction time to acceptable values. In this implementation,
     * we just use the current time to set or extend the destruction time since the
     * user is still interested in it.
     *
     * @param job
     * @param minDestruction 
     * @param maxDestruction 
     */
    public static void constrainDestruction(Job job, long minDestruction, long maxDestruction)
    {
        Date orig = job.getDestructionTime();
        Date d = orig;
        Date now = new Date();

        long min = now.getTime() + 1000*minDestruction;
        long max = now.getTime() + 1000*maxDestruction;
        if (d == null)
            d = new Date(now.getTime() + 1000* maxDestruction);
        else if (d.getTime() < min)
            d = new Date(min);
        else if (d.getTime() > max)
            d = new Date(max);
        log.debug("constrainDestruction: " + minDestruction + "," + maxDestruction
                + "," + orig + " -> " + d);
        job.setDestructionTime(d);
    }

    /**
     * Constrain the job execution time to acceptable values.
     *
     * @param job
     * @param minDuration
     * @param maxDuration 
     */
    public static void constrainDuration(Job job, long minDuration, long maxDuration)
    {
        Long orig = job.getExecutionDuration();
        Long dur = orig;
        if (dur == null)
            dur = maxDuration;
        else if ( dur < minDuration )
            dur = minDuration;
        else if (dur > maxDuration)
            dur = maxDuration;
        log.debug("constrainDuration: " + minDuration + "," + maxDuration
                + "," + orig + " -> " + dur);
        job.setExecutionDuration(dur);

    }

    /**
     * Constrain the job quote to acceptable values.
     *
     * @param job
     * @param minQuote 
     * @param maxQuote
     */
    public static void constrainQuote(Job job, long minQuote, long maxQuote)
    {
        Date orig = job.getQuote();
        Date d = orig;
        Date now = new Date();

        long min = now.getTime() + 1000*minQuote;
        long max = now.getTime() + 1000*maxQuote;
        if (d == null)
            d = new Date(max); // TODO: use defaultQuote=unknown
        else if (d.getTime() < min)
            d = new Date(min);
        else if (d.getTime() > max)
            d = new Date(max);
        log.debug("constrainQuote: " + minQuote + "," + maxQuote
                + "," + orig + " -> " + d);
        job.setQuote(d);
    }

    /**
     * Make a deep copy of the specified job, including the jobID.
     * 
     * @param job
     * @return
     */
    public static Job deepCopy(Job job)
    {
        // the Job copy constructor is deep but does not copy the ID
        Job ret = new Job(job);
        assignID(ret, job.getID());
        return ret;
    }

    /**
     * Creates an iterator with immutable access to the jobs in the underlying
     * iterator. This iterator suppresses remove (throws UnsupportedOperationException)
     * and returns deep copies of all the jobs.
     * 
     * @param inner
     * @return immutable job iterator
     */
    public static Iterator<JobRef> createImmutableIterator(Iterator<Job> inner)
    {
        return new JobIterator(inner);
    }

    private static class JobIterator implements Iterator<JobRef>
    {
        private Iterator<Job> inner;
        JobIterator(Iterator<Job> inner) { this.inner = inner; }
        public boolean hasNext()
        {
            return inner.hasNext();
        }
        public JobRef next()
        {
            Job next = inner.next();
            return new JobRef(next.getID(), next.getExecutionPhase());
        }
        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }
}
