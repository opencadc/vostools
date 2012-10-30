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
package ca.nrc.cadc.vos.client.ui;

import java.net.URI;

import ca.nrc.cadc.vos.client.VOSpaceClient;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.VOSURI;

import static org.junit.Assert.*;


/**
 *
 * @author jburke
 */
public class CommandQueueTest
{
    private static Logger log = Logger.getLogger(CommandQueueTest.class);
    
    public CommandQueueTest() { }

    @BeforeClass
    public static void setUpClass()
    {
        Log4jInit.setLevel("ca.nrc.cadc.vos.client.ui", Level.INFO);
    }


    @Test
    public void abort() throws Exception
    {
        final TestListener listener = new TestListener();
        final CommandQueue testSubject = new CommandQueue(100, listener);

        for (int i = 0; i < 100; i++)
        {
            testSubject.put(new VOSpaceCommand()
            {
                @Override
                public void execute(VOSpaceClient vospaceClient) throws Exception
                {
                    // Do nothing
                }
            });
        }

        // Remove forty of them.
        for (int i = 0; i < 40; i++)
        {
            testSubject.remove();
        }

        final long remainingItems = testSubject.abortProduction();

        assertTrue("Abort not properly issued.",
                   testSubject.isAbortedProduction());
        assertEquals("Should be sixty items left.", 60l, remainingItems);
    }

    /**
     * Test of startedConsumption method, of class CommandQueue.
     */
    @Test
    public void testStartedConsumption()
    {
        try
        {
            TestListener listener = new TestListener();
            CommandQueue queue = new CommandQueue(1, listener);
            queue.startedConsumption();
            assertTrue(listener.started);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    /**
     * Test of doneConsumption method, of class CommandQueue.
     */
    @Test
    public void testDoneConsumption()
    {
        try
        {
            TestListener listener = new TestListener();
            CommandQueue queue = new CommandQueue(1, listener);
            queue.doneConsumption();
            assertTrue(listener.completed);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    /**
     * Test of doneProduction method, of class CommandQueue.
     */
    @Test
    public void testDoneProduction()
    {
        try
        {
            TestListener listener = new TestListener();
            CommandQueue queue = new CommandQueue(1, listener);
            assertFalse("isDoneProduction should be false after constructor", queue.isDoneProduction());
            queue.doneProduction();
            assertTrue("isDoneProduction be true after doneProduction called", queue.isDoneProduction());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    /**
     * Test of isDoneProduction method, of class CommandQueue.
     */
    @Test
    public void testIsDoneProduction()
    {
        try
        {
            TestListener listener = new TestListener();
            CommandQueue queue = new CommandQueue(1, listener);
            assertFalse("isDoneProduction should be false after constructor", queue.isDoneProduction());
            queue.doneProduction();
            assertTrue("isDoneProduction be true after doneProduction called", queue.isDoneProduction());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    /**
     * Test of put method, of class CommandQueue.
     */
    @Test
    public void testPut()
    {
        try
        {
            TestListener listener = new TestListener();
            CommandQueue queue = new CommandQueue(1, listener);

            ContainerNode node = new ContainerNode(new VOSURI(new URI("vos://cadc.nrc.ca!vospace/root")));
            VOSpaceCommand command = new CreateDirectory(node);

            assertNull("CommandQueue should be empty before any commands offered", queue.peek());
            queue.put(command);
            assertNotNull("CommandQueue should contain a command after command offered", queue.peek());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    /**
     * Test of peek method, of class CommandQueue.
     */
    @Test
    public void testPeek()
    {
        try
        {
            TestListener listener = new TestListener();
            CommandQueue queue = new CommandQueue(1, listener);

            ContainerNode node = new ContainerNode(new VOSURI(new URI("vos://cadc.nrc.ca!vospace/root")));
            VOSpaceCommand command = new CreateDirectory(node);

            assertNull("CommandQueue should be empty before any commands offered", queue.peek());
            queue.put(command);
            assertNotNull("CommandQueue should contain a command after command offered", queue.peek());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    /**
     * Test of remove method, of class CommandQueue.
     */
    @Test
    public void testRemove()
    {
        try
        {
            TestListener listener = new TestListener();
            CommandQueue queue = new CommandQueue(1, listener);

            ContainerNode node = new ContainerNode(new VOSURI(new URI("vos://cadc.nrc.ca!vospace/root")));
            VOSpaceCommand command = new CreateDirectory(node);

            assertNull("CommandQueue should be empty before any commands offered", queue.peek());
            queue.put(command);
            assertNotNull("CommandQueue should contain a command after command offered", queue.peek());
            queue.remove();
            assertNull("CommandQueue should be empty after command removed", queue.peek());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    /**
     * Test of poll method, of class CommandQueue.
     */
    @Test
    public void testPoll()
    {
        try
        {

        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    
    private class TestListener implements CommandQueueListener
    {
        long processed;
        long remaining;
        boolean started;
        boolean completed;
        boolean aborted;

        TestListener()
        {
            processed = 0;
            remaining = 0;
            started = false;
            completed = false;
            aborted = false;
        }

        // long commandsProcessed, long commandsRemaining
        public void commandProcessed(Long processed, Long remaining)
        {
            this.processed = processed;
            this.remaining = remaining;
        }

        public void processingStarted()
        {
            started = true;
        }

        public void processingComplete()
        {
            completed = true;
        }

        /**
         * Indicates that an Abort was issued.
         */
        @Override
        public void onAbort()
        {
            aborted = true;
        }
    }
    
}
