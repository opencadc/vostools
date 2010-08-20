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

package ca.nrc.cadc.vos.server;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;

/**
 * Class to test the thread safety and isolation levels of the NodeDAO
 * but launching multiple threads that perform a variety of transactions
 * from different instances of the NodeDAO.
 * 
 * @author majorb
 *
 */
public abstract class NodeDAOLoadTest extends AbstractPersistenceTest
{
    
    private static Logger log = Logger.getLogger(NodeDAOLoadTest.class);
    
    private static final int DAO_COUNT = 10;
    private static final int THREAD_COUNT = 20;
    private static final int NODE_COUNT = 50;
    
    /**
     * Constructor.
     */
    public NodeDAOLoadTest()
    {
        super();
    }
    
    @Before
    public void before() throws Exception
    {
        super.commonBefore();
    }

    @Test
    public void loadTest() throws Exception
    {
        // create a set of DAOs
        List<Thread> runners = new ArrayList<Thread>(THREAD_COUNT * DAO_COUNT);
        
        for (int i=0; i<DAO_COUNT; i++)
        {
            NodeDAO nextDAO = getNodeDAO(dataSource);
            ContainerNode rootContainer = (ContainerNode) nextDAO.getFromParent(getRootContainerName(), null);

            // create the runners
            for (int j=0; j<THREAD_COUNT;j++)
            {
                runners.add(new Thread(
                    new LoadTestRunner(
                        nextDAO, rootContainer,
                        Integer.valueOf(i).toString()
                        + "-"
                        + Integer.valueOf(j).toString())));
            }
        }
        
        // start the runners
        for (Thread runner : runners)
        {
            runner.start();
        }
        
        // wait for each of the threads to finish
        for (Thread thread : runners)
        {
            thread.join();
        }
        
    }
    
    public class LoadTestRunner implements Runnable
    {
        private ContainerNode parent;
        private String threadId;
        private NodeDAO runnerDAO;
        
        LoadTestRunner(NodeDAO runnerDAO, ContainerNode parent, String threadId)
        {
            this.runnerDAO = runnerDAO;
            this.parent = parent;
            this.threadId = threadId;
        }

        @Override
        public void run()
        {
            try
            {
                List<Node> putNodes = new ArrayList<Node>(NODE_COUNT);
                List<Node> getNodes = new ArrayList<Node>(NODE_COUNT);
                
                // create the nodes
                for (int i=0; i<NODE_COUNT; i++)
                {
                    String nodePath = getRootContainerName() + "/" + getNodeName(threadId + "-" + i);
                    VOSURI vosuri = new VOSURI(getVOSURIPrefix() + nodePath);
                    DataNode dataNode = new DataNode(vosuri);
                    dataNode.setOwner(getNodeOwner());
                    putNodes.add(runnerDAO.putInContainer(dataNode, parent));
                }
                
                // update properties
                for (Node node : putNodes)
                {
                    node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_DESCRIPTION, "test description"));
                    runnerDAO.updateProperties(node);
                }
                
                // get the nodes
                for (Node node : putNodes)
                {
                    getNodes.add(runnerDAO.getFromParent(node.getName(), parent));
                }
                
                // delete the nodes
                for (Node node : getNodes)
                {
                    runnerDAO.delete(node, true);
                }
                
            } catch (Throwable t)
            {
                t.printStackTrace();
                log.error(t.getMessage(), t);
                Assert.fail(t.getMessage());
            }
            
        }
        
    }
    

}
