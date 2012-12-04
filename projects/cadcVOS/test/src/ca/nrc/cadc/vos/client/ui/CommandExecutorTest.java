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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.easymock.EasyMock;
import org.easymock.IArgumentMatcher;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.util.Log4jInit;

public class CommandExecutorTest
{
    
    private static Logger log = Logger.getLogger(CommandExecutorTest.class);

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc.vos", Level.INFO);
    }
    
    @Test
    public void testCommandExecutorWithinBuffer() throws Exception
    {
        this.testCommandExecutor(5, 100);
    }
    
    @Test
    public void testCommandExecutorExceedingBuffer() throws Exception
    {
        this.testCommandExecutor(100, 5);
    }
    
    private void testCommandExecutor(int commands, int bufferSize) throws Exception
    {
        CommandQueueListener listener =
                EasyMock.createMock(CommandQueueListener.class);
        final Throwable error = null;
        
        for (long i=1; i<=commands; i++)
        {
            listener.commandConsumed(matchQueueCommandsProcessed(i),
                                     matchQueueCommandsRemaining(bufferSize),
                                     EasyMock.eq(error));
            EasyMock.expectLastCall().once();
        }
        
        CommandQueue queue = new CommandQueue(bufferSize, listener);
        CommandExecutor commandExecutor = new CommandExecutor(null, queue, null);
        
        VOSpaceCommand command = EasyMock.createMock(VOSpaceCommand.class);
        command.execute(null);
        EasyMock.expectLastCall().times(commands);
        
        EasyMock.replay(listener, command);
        
        Thread t = new Thread(commandExecutor);
        t.setDaemon(true);
        t.start();
        
        for (int i=0; i<commands; i++)
        {
            queue.put(command);
            log.debug("Added command " + (i + 1) + " to queue.");
        }
        
        // wait for 5 seconds and ensure the we were notified on each command processed
        Thread.sleep(5000);
        
        EasyMock.verify(listener);
    }
    
    private Long matchQueueCommandsProcessed(final long commandNumber)
    {
        EasyMock.reportMatcher(
            new IArgumentMatcher()
                {
                    @Override
                    public void appendTo(StringBuffer sb)
                    {
                        sb.append("eqException(Expected \"CommandsProcessed = " + commandNumber + "\"");
                    }
        
                    @Override
                    public boolean matches(Object arg0)
                    {
                        long value = (Long) arg0;
                        return value == commandNumber;
                    }
                });
        return null;
    }
    
    private Long matchQueueCommandsRemaining(final long bufferSize)
    {
        EasyMock.reportMatcher(
            new IArgumentMatcher()
                {
                    @Override
                    public void appendTo(StringBuffer sb)
                    {
                        sb.append("eqException(Expected \"CommandsRemaining <= " + bufferSize + "\"");
                    }
        
                    @Override
                    public boolean matches(Object arg0)
                    {
                        long commandsRemaining = (Long) arg0;
                        return commandsRemaining <= bufferSize;
                    }
                });
        return null;
    }

}
