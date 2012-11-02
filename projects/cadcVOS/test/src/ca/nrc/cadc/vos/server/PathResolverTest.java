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

import java.net.URI;

import junit.framework.Assert;

import org.apache.log4j.Level;
import org.easymock.EasyMock;
import org.junit.Test;

import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.LinkNode;
import ca.nrc.cadc.vos.LinkingException;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.VOSURI;

/**
 * Unit tests for PathResolver.
 * 
 * @author majorb
 *
 */
public class PathResolverTest
{
    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.vos.server", Level.INFO);
    }
    
    @Test
    public void testNoLinks() throws Exception
    {
        NodePersistence np = EasyMock.createMock(NodePersistence.class);
        VOSURI vosURI = new VOSURI("vos://cadc.nrc.ca~vospace/root/container/data");
        DataNode dataNode = new DataNode(vosURI);
        
        EasyMock.expect(np.get(vosURI, true)).andReturn(dataNode);
        EasyMock.replay(np);
        
        PathResolver pathResolver = new PathResolver(np);
        Node node = pathResolver.resolve(vosURI);
        Assert.assertEquals("Wrong node.", vosURI, node.getUri());
        
        EasyMock.verify(np);
    }
    
    @Test
    public void testNotFoundNoLinks() throws Exception
    {
        NodePersistence np = EasyMock.createMock(NodePersistence.class);
        VOSURI requestedURI = new VOSURI("vos://cadc.nrc.ca~vospace/root/container/data/anotherSegment");
        VOSURI returnedURI = new VOSURI("vos://cadc.nrc.ca~vospace/root/container/data");
        
        DataNode dataNode = new DataNode(returnedURI);
        EasyMock.expect(np.get(requestedURI, true)).andReturn(dataNode);
        EasyMock.replay(np);
        
        PathResolver pathResolver = new PathResolver(np);
        try
        {
            pathResolver.resolve(requestedURI);
            Assert.fail("Should have got NodeNodeFoundException");
        }
        catch (NodeNotFoundException e)
        {
            // expected
        }
        
        EasyMock.verify(np);
    }
    
    @Test
    public void testLinkIsLastNode() throws Exception
    {
        NodePersistence np = EasyMock.createMock(NodePersistence.class);
        VOSURI vosURI1 = new VOSURI("vos://cadc.nrc.ca~vospace/root/container/link");
        URI uri2 = new URI("vos://cadc.nrc.ca~vospace/root2/container/data");
        
        LinkNode linkNode = new LinkNode(vosURI1, uri2);
        
        EasyMock.expect(np.get(vosURI1, true)).andReturn(linkNode);
        EasyMock.replay(np);
        
        PathResolver pathResolver = new PathResolver(np);
        Node node = pathResolver.resolve(vosURI1);
        Assert.assertEquals("Wrong node.", vosURI1, node.getUri());
        
        EasyMock.verify(np);
    }
    
    @Test
    public void testOneLink() throws Exception
    {
        NodePersistence np = EasyMock.createMock(NodePersistence.class);
        VOSURI requestedURI = new VOSURI("vos://cadc.nrc.ca~vospace/root/container/link/data");
        VOSURI linkURI = new VOSURI("vos://cadc.nrc.ca~vospace/root/container/link");
        URI targetURI = new URI("vos://cadc.nrc.ca~vospace/root2/container");
        VOSURI dataURI = new VOSURI("vos://cadc.nrc.ca~vospace/root2/container/data");
        
        LinkNode linkNode = new LinkNode(linkURI, targetURI);
        DataNode dataNode = new DataNode(dataURI);
        
        EasyMock.expect(np.get(requestedURI, true)).andReturn(linkNode);
        EasyMock.expect(np.get(dataURI, true)).andReturn(dataNode);
        EasyMock.replay(np);
        
        PathResolver pathResolver = new PathResolver(np);
        Node node = pathResolver.resolve(requestedURI);
        
        Assert.assertEquals("Wrong node.", dataURI, node.getUri());
        
        EasyMock.verify(np);
    }
    
    @Test
    public void testMultipleLinks() throws Exception
    {
        NodePersistence np = EasyMock.createMock(NodePersistence.class);
        
        VOSURI requestedURI = new VOSURI("vos://cadc.nrc.ca~vospace/root/container/link/data");
        
        VOSURI link1URI = new VOSURI("vos://cadc.nrc.ca~vospace/root/container/link");
        URI target1URI = new URI("vos://cadc.nrc.ca~vospace/root2/container/link2");
        
        VOSURI requestedURI2 = new VOSURI("vos://cadc.nrc.ca~vospace/root2/container/link2/data");
        VOSURI link2URI = new VOSURI("vos://cadc.nrc.ca~vospace/root2/container/link2");
        URI target2URI = new URI("vos://cadc.nrc.ca~vospace/root3/container");
        
        VOSURI dataURI = new VOSURI("vos://cadc.nrc.ca~vospace/root3/container/data");
        
        LinkNode linkNode1 = new LinkNode(link1URI, target1URI);
        LinkNode linkNode2 = new LinkNode(link2URI, target2URI);
        DataNode dataNode = new DataNode(dataURI);
        
        EasyMock.expect(np.get(requestedURI, true)).andReturn(linkNode1);
        EasyMock.expect(np.get(requestedURI2, true)).andReturn(linkNode2);
        EasyMock.expect(np.get(dataURI, true)).andReturn(dataNode);
        EasyMock.replay(np);
        
        PathResolver pathResolver = new PathResolver(np);
        Node node = pathResolver.resolve(requestedURI);
        
        Assert.assertEquals("Wrong node.", dataURI, node.getUri());
        
        EasyMock.verify(np);
    }
    
    @Test
    public void testCircularLinks() throws Exception
    {
        NodePersistence np = EasyMock.createMock(NodePersistence.class);
        
        VOSURI requestedURI = new VOSURI("vos://cadc.nrc.ca~vospace/root/container/link/data");
        
        VOSURI link1URI = new VOSURI("vos://cadc.nrc.ca~vospace/root/container/link");
        URI target1URI = new URI("vos://cadc.nrc.ca~vospace/root2/container/link2");
        
        VOSURI requestedURI2 = new VOSURI("vos://cadc.nrc.ca~vospace/root2/container/link2/data");
        VOSURI link2URI = new VOSURI("vos://cadc.nrc.ca~vospace/root2/container/link2");
        URI target2URI = new URI("vos://cadc.nrc.ca~vospace/root/container/link");
        
        
        LinkNode linkNode1 = new LinkNode(link1URI, target1URI);
        LinkNode linkNode2 = new LinkNode(link2URI, target2URI);
        
        EasyMock.expect(np.get(requestedURI, true)).andReturn(linkNode1).times(2);
        EasyMock.expect(np.get(requestedURI2, true)).andReturn(linkNode2);
        EasyMock.replay(np);
        
        PathResolver pathResolver = new PathResolver(np);
        try
        {
            pathResolver.resolve(requestedURI);
            Assert.fail("Should have detected circular loop.");
        }
        catch (LinkingException e)
        {
            // expected
        }
        
        EasyMock.verify(np);
    }

}
