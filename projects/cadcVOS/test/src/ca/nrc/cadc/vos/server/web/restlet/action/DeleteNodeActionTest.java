/**
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2010.                            (c) 2010.
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
 ************************************************************************
 */
package ca.nrc.cadc.vos.server.web.restlet.action;


import ca.nrc.cadc.vos.*;

import ca.nrc.cadc.vos.server.NodePersistence;
import ca.nrc.cadc.vos.server.util.NodeUtil;
import org.junit.Test;
import org.restlet.Request;

import java.util.List;

import static org.easymock.EasyMock.*;


public class DeleteNodeActionTest extends AbstractCADCVOSTest<DeleteNodeAction>
{
    private NodePersistence mockNodePersistence;


    /**
     * Set and initialize the Test Subject.
     *
     * @throws Exception If anything goes awry.
     */
    @Override
    protected void initializeTestSubject() throws Exception
    {
        setMockNodePersistence(createMock(NodePersistence.class));

        setTestSubject(new DeleteNodeAction());
    }


    @Test
    @SuppressWarnings("unchecked")
    public void performNodeAction() throws Exception
    {
        final NodeUtil mockNodeUtil = createMock(NodeUtil.class);

        setTestSubject(new DeleteNodeAction()
        {
            /**
             * Obtain an instance of a NodeUtil.
             *
             * @param nodePersistence The Node Persistence instance to use.
             * @return A Node Util instance.
             */
            @Override
            protected NodeUtil createNodeUtil(NodePersistence nodePersistence)
            {
                return mockNodeUtil;
            }
        });

        final Node mockNode = createMock(Node.class);
        final Node mockPersistentNode = createMock(Node.class);
        final ContainerNode mockParentNode = createMock(ContainerNode.class);
        final Request mockRequest = createMock(Request.class);
        final List<NodeProperty> mockNodeProperties = createMock(List.class);
        final NodeProperty comparison =
                new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, null);
        final NodeProperty mockLengthProperty = createMock(NodeProperty.class);        

        expect(mockNode.getName()).andReturn("MOCK_NODE_NAME").once();
        expect(mockNode.getParent()).andReturn(mockParentNode).once();

        expect(getMockNodePersistence().getFromParent("MOCK_NODE_NAME",
                                                      mockParentNode)).
                andReturn(mockPersistentNode).once();
        getMockNodePersistence().markForDeletion(mockPersistentNode, true);
        expectLastCall().once();

        expect(mockPersistentNode.getProperties()).andReturn(
                mockNodeProperties).once();
        expect(mockNodeProperties.indexOf(comparison)).andReturn(3).once();
        expect(mockNodeProperties.get(3)).andReturn(mockLengthProperty).once();

        expect(mockLengthProperty.getPropertyValue()).andReturn("88").once();

        mockNodeUtil.updateStackContentLengths(mockPersistentNode, -88l);
        expectLastCall().once();

        replay(getMockNodePersistence(), mockNode, mockPersistentNode,
               mockParentNode, mockRequest, mockNodeUtil, mockNodeProperties,
               mockLengthProperty);

        getTestSubject().performNodeAction(mockNode, getMockNodePersistence(),
                                           mockRequest);

        verify(getMockNodePersistence(), mockNode, mockPersistentNode,
               mockParentNode, mockRequest, mockNodeUtil, mockNodeProperties,
               mockLengthProperty);
    }


    public NodePersistence getMockNodePersistence()
    {
        return mockNodePersistence;
    }

    public void setMockNodePersistence(NodePersistence mockNodePersistence)
    {
        this.mockNodePersistence = mockNodePersistence;
    }
}
