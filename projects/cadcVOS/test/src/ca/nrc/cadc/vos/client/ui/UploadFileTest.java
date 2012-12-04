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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.easymock.EasyMock;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Direction;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.Protocol;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.client.ClientTransfer;
import ca.nrc.cadc.vos.client.VOSpaceClient;
import ca.nrc.cadc.vos.client.VOSpaceTransferListener;


public class UploadFileTest
{
    private static Logger log = Logger.getLogger(UploadFileTest.class);
    
    private static String ROOT_URI = "vos://cadc.nrc.ca~vospace/uploadFileTest";

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc.vos", Level.INFO);
    }
    
    @Test
    public void testNullNodeArgument() throws Exception
    {
        try
        {
            File testFile = new File("test/src/resources/testFile");
            new UploadFile(null, testFile);
            Assert.fail("expected an illegal argument exception");
        }
        catch (IllegalArgumentException e)
        {
            Assert.assertTrue("wrong error", e.getMessage().contains("dataNode cannot be null"));
        }
    }
    
    @Test
    public void testNullFileArgument() throws Exception
    {
        try
        {
            VOSURI uri = new VOSURI(ROOT_URI + "/dataNode");
            DataNode dataNode = new DataNode(uri);
            new UploadFile(dataNode, null);
            Assert.fail("expected an illegal argument exception");
        }
        catch (IllegalArgumentException e)
        {
            Assert.assertTrue("wrong error", e.getMessage().contains("file cannot be null"));
        }
    }
    
    @Test
    public void testFileActuallyADirectory() throws Exception
    {
        try
        {
            VOSURI uri = new VOSURI(ROOT_URI + "/dataNode");
            DataNode dataNode = new DataNode(uri);
            File testFile = new File("test/src/resources");
            new UploadFile(dataNode, testFile);
            Assert.fail("expected an illegal argument exception");
        }
        catch (IllegalArgumentException e)
        {
            Assert.assertTrue("wrong error", e.getMessage().contains("not a file"));
        }
    }
    
    @Test
    public void testFileNotReadable() throws Exception
    {
        File testFile = null;
        try
        {
            VOSURI uri = new VOSURI(ROOT_URI + "/dataNode");
            DataNode dataNode = new DataNode(uri);
            testFile = new File("test/src/resources/testFile");
            testFile.setReadable(false);
            new UploadFile(dataNode, testFile);
            Assert.fail("expected an illegal argument exception");
        }
        catch (IllegalArgumentException e)
        {
            Assert.assertTrue("wrong error", e.getMessage().contains("cannot read file"));
        }
        finally
        {
            if (testFile != null)
                testFile.setReadable(true);
        }
    }
    
    @Test
    public void testNewNode() throws Exception
    {
        VOSURI uri = new VOSURI(ROOT_URI + "/newDataNode");
        DataNode dataNode = new DataNode(uri);
        
        File testFile = new File("test/src/resources/testFile");
        UploadFile uploadFile = new UploadFile(dataNode, testFile);
        
        VOSpaceClient mockClient = EasyMock.createMock(VOSpaceClient.class);
        
        EasyMock.expect(mockClient.getNode("/uploadFileTest/newDataNode", "limit=0&detail=min")).andThrow(new NodeNotFoundException(uri.getPath())).once();
        
        EasyMock.expect(mockClient.createNode(dataNode, false)).andReturn(dataNode).once();
        
        setupSuccessfulFileUpload(dataNode, testFile, mockClient);
        EasyMock.replay(mockClient);
        
        uploadFile.execute(mockClient);
        
        EasyMock.verify(mockClient);
    }
    
    private void setupSuccessfulFileUpload(DataNode dataNode, File testFile, VOSpaceClient mockClient)
            throws Exception
    {
        List<Protocol> protocols = new ArrayList<Protocol>();
        protocols.add(new Protocol(VOS.PROTOCOL_HTTP_PUT));
        Transfer transfer = new Transfer(dataNode.getUri(), Direction.pushToVoSpace, null, protocols)
        {
            @Override
            public boolean equals(Object t)
            {
                return true;
            }
        };
        ClientTransfer mockClientTransfer = EasyMock.createMock(ClientTransfer.class);
        VOSpaceTransferListener transListener = new VOSpaceTransferListener(false)
        {
            @Override
            public boolean equals(Object t)
            {
                return true;
            }
        };
        
        EasyMock.expect(mockClient.createTransfer(transfer)).andReturn(mockClientTransfer).once();
        EasyMock.expect(mockClient.getSslSocketFactory()).andReturn(null).once();
        mockClientTransfer.setMaxRetries(Integer.MAX_VALUE);
        EasyMock.expectLastCall().once();
        mockClientTransfer.setTransferListener(transListener);
        EasyMock.expectLastCall().once();
        mockClientTransfer.setSSLSocketFactory(null);
        EasyMock.expectLastCall().once();
        mockClientTransfer.setFile(testFile);
        EasyMock.expectLastCall().once();
        mockClientTransfer.runTransfer();
        EasyMock.expectLastCall().once();
        EasyMock.expect(mockClientTransfer.getThrowable()).andReturn(null).once();
        EasyMock.expect(mockClientTransfer.getPhase()).andReturn(ExecutionPhase.COMPLETED).once();

        EasyMock.replay(mockClientTransfer);
    }

}
