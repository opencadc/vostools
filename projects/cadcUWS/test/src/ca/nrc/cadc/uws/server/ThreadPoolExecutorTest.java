/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2011.                            (c) 2011.
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
*  $Revision: 5 $
*
************************************************************************
*/

package ca.nrc.cadc.uws.server;

import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.server.ThreadExecutorTest.TestJob;
import ca.nrc.cadc.uws.server.ThreadExecutorTest.TestJobRunner;
import ca.nrc.cadc.uws.server.ThreadExecutorTest.TestJobUpdater;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author pdowler
 */
public class ThreadPoolExecutorTest 
{
    private static Logger log = Logger.getLogger(ThreadPoolExecutorTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.uws.server", Level.INFO);
    }

    //@Test
    public void testInvalidArgs()
    {
        try
        {
            JobUpdater ju = new TestJobUpdater();
            try
            {
                ThreadPoolExecutor exec = new ThreadPoolExecutor(null, null, 0);
                Assert.fail("expected IllegalArgumentException: null JobUpdater");
            }
            catch(IllegalArgumentException expected)
            {
                log.debug("caught expected: " + expected);
            }

            try
            {
                ThreadPoolExecutor exec = new ThreadPoolExecutor(ju, null, 0);
                Assert.fail("expected IllegalArgumentException: null JobRunner.class");
            }
            catch(IllegalArgumentException expected)
            {
                log.debug("caught expected: " + expected);
            }

            try
            {
                ThreadPoolExecutor exec = new ThreadPoolExecutor(ju, TestJobRunner.class, 0);
                Assert.fail("expected IllegalArgumentException: null poolName");
            }
            catch(IllegalArgumentException expected)
            {
                log.debug("caught expected: " + expected);
            }

            try
            {
                ThreadPoolExecutor exec = new ThreadPoolExecutor(ju, TestJobRunner.class, 0);
                Assert.fail("expected IllegalArgumentException: illegal poolSize");
            }
            catch(IllegalArgumentException expected)
            {
                log.debug("caught expected: " + expected);
            }

            try
            {
                ThreadPoolExecutor exec = new ThreadPoolExecutor(ju, TestJobRunner.class, 1, null);
                Assert.fail("expected IllegalArgumentException: null poolName");
            }
            catch(IllegalArgumentException expected)
            {
                log.debug("caught expected: " + expected);
            }

            // success
            ThreadPoolExecutor exec = new ThreadPoolExecutor(new TestJobUpdater(), TestJobRunner.class, 2);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testExec()
    {
        try
        {
            TestJobUpdater ju = new TestJobUpdater();
            ThreadPoolExecutor exec = new ThreadPoolExecutor(ju, TestJobRunner.class, 2);
            Job job = new TestJob(100L);
            ju.jobs.put(job.getID(), job);
            
            exec.execute(job);
            Thread.sleep(120L);
            ExecutionPhase actual = ju.getPhase(job.getID());
            Assert.assertEquals("phase", ExecutionPhase.COMPLETED, actual);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testExecQueued()
    {
        try
        {
            TestJobUpdater ju = new TestJobUpdater();
            ThreadPoolExecutor exec = new ThreadPoolExecutor(ju, TestJobRunner.class, 2);
            for (int i=0; i<10; i++) // 10 jobs * 100ms / 2 threads ~ 500ms
            {
                String id = "abc123_" + i;
                Job job = new TestJob(id, 100L);
                ju.jobs.put(job.getID(), job);
                exec.execute(job);
            }
            
            Thread.sleep(750L);

            for (int i=0; i<10; i++) // 10 jobs * 100ms / 2 threads ~ 500ms
            {
                String id = "abc123_" + i;
                ExecutionPhase actual = ju.getPhase(id);
                Assert.assertEquals("phase", ExecutionPhase.COMPLETED, actual);
            }
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testAbort()
    {
        try
        {
            TestJobUpdater ju = new TestJobUpdater();
            ThreadPoolExecutor exec = new ThreadPoolExecutor(ju, TestJobRunner.class, 2);
            Job job = new TestJob(100L);
            ju.jobs.put(job.getID(), job);
            
            long t1 = System.currentTimeMillis();
            exec.execute(job);
            Thread.sleep(20L);
            exec.abort(job);
            long dt = System.currentTimeMillis() - t1;
            log.debug("aborted job: " + dt + "ms");
            ExecutionPhase actual = ju.getPhase(job.getID());
            Assert.assertEquals("phase", ExecutionPhase.ABORTED, actual);
            Assert.assertTrue("duration < 30ms", dt < 30L); // test job sleep was actually interrupted
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
}
