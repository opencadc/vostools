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
package ca.nrc.cadc.vos.server.util;


import ca.nrc.cadc.vos.*;
import ca.nrc.cadc.vos.server.NodePersistence;

import org.junit.Test;

import java.util.List;

import static org.easymock.EasyMock.*;


public class NodeUtilTest extends AbstractCADCVOSTest<NodeUtil>
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

        setTestSubject(new NodeUtil(getMockNodePersistence()));
    }


    @Test
    @SuppressWarnings("unchecked")
    public void updateContentLengthProperties() throws Exception
    {
        // 5 byte difference.
        long contentLengthDifference = 5l;
        
        final Node mockBaseNode = createMock(Node.class);
        final ContainerNode mockBaseParentNode =
                createMock(ContainerNode.class);
        final List<NodeProperty> mockParentProperties = createMock(List.class);

        final ContainerNode mockBaseGrandParentNode =
                createMock(ContainerNode.class);
        final List<NodeProperty> mockGrandParentProperties =
                createMock(List.class);

        final ContainerNode mockBaseGreatGrandParentNode =
                createMock(ContainerNode.class);
        final List<NodeProperty> mockGreatGrandParentProperties =
                createMock(List.class);

        final NodeProperty comparison =
                new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, null);

        final NodeProperty mockParentNodeProperty =
                createMock(NodeProperty.class);
        final NodeProperty newParentNodeProperty =
                new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, "72");
        final NodeProperty mockGrandParentNodeProperty =
                createMock(NodeProperty.class);
        final NodeProperty newGrandParentNodeProperty =
                new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, "0");
        final NodeProperty mockGreatGrandParentNodeProperty =
                createMock(NodeProperty.class);
        final NodeProperty newGreatGrandParentNodeProperty =
                new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, "94");        

        expect(mockBaseNode.getParent()).andReturn(mockBaseParentNode).once();      

        expect(mockBaseParentNode.getParent()).andReturn(
                mockBaseGrandParentNode).once();
        expect(mockBaseParentNode.getProperties()).andReturn(
                mockParentProperties).once();
        expect(mockParentProperties.indexOf(comparison)).andReturn(1).once();
        expect(mockParentProperties.get(1)).andReturn(mockParentNodeProperty).
                once();
        expect(mockParentNodeProperty.getPropertyValue()).andReturn("77").
                once();
        expect(mockParentProperties.remove(newParentNodeProperty)).
                andReturn(true).once();
        expect(mockParentProperties.add(newParentNodeProperty)).
                andReturn(true).once();
        expect(getMockNodePersistence().updateProperties(mockBaseParentNode)).
                andReturn(mockBaseParentNode).once();

        expect(mockBaseGrandParentNode.getParent()).andReturn(
                mockBaseGreatGrandParentNode).once();
        expect(mockBaseGrandParentNode.getProperties()).andReturn(
                mockGrandParentProperties).once();
        expect(mockGrandParentProperties.indexOf(comparison)).andReturn(-1).
                once();
        expect(mockGrandParentProperties.remove(newGrandParentNodeProperty)).
                andReturn(true).once();
        expect(mockGrandParentProperties.add(newGrandParentNodeProperty)).
                andReturn(true).once();
        expect(getMockNodePersistence().updateProperties(mockBaseGrandParentNode)).
                andReturn(mockBaseGrandParentNode).once();

        expect(mockBaseGreatGrandParentNode.getParent()).andReturn(null).
                once();
        expect(mockBaseGreatGrandParentNode.getProperties()).andReturn(
                mockGreatGrandParentProperties).once();
        expect(mockGreatGrandParentProperties.indexOf(comparison)).andReturn(7).
                once();
        expect(mockGreatGrandParentProperties.get(7)).andReturn(
                mockGreatGrandParentNodeProperty).once();
        expect(mockGreatGrandParentNodeProperty.getPropertyValue()).
                andReturn("99").once();
        expect(mockGreatGrandParentProperties.remove(
                newGreatGrandParentNodeProperty)).andReturn(true).once();
        expect(mockGreatGrandParentProperties.add(
                newGreatGrandParentNodeProperty)).andReturn(true).once();        
        expect(getMockNodePersistence().updateProperties(
                mockBaseGreatGrandParentNode)).andReturn(
                mockBaseGreatGrandParentNode).once();

        replay(mockBaseNode, mockBaseParentNode, mockParentProperties,
               mockParentNodeProperty, mockBaseGrandParentNode,
               mockGrandParentProperties, mockGrandParentNodeProperty,
               mockBaseGreatGrandParentNode, mockGreatGrandParentProperties,
               mockGreatGrandParentNodeProperty, getMockNodePersistence());

        getTestSubject().updateContentLengthProperties(mockBaseNode.getParent(),
                                                       contentLengthDifference);

        verify(mockBaseNode, mockBaseParentNode, mockParentProperties,
               mockParentNodeProperty, mockBaseGrandParentNode,
               mockGrandParentProperties, mockGrandParentNodeProperty,
               mockBaseGreatGrandParentNode, mockGreatGrandParentProperties,
               mockGreatGrandParentNodeProperty, getMockNodePersistence());
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
