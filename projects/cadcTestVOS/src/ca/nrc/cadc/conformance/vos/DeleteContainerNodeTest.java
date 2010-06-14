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

package ca.nrc.cadc.conformance.vos;

import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.NodeReader;
import ca.nrc.cadc.vos.VOSURI;
import com.meterware.httpunit.WebResponse;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test case for deleting ContainerNodes.
 *
 * @author jburke
 */
public class DeleteContainerNodeTest extends VOSNodeTest
{
    private static Logger log = Logger.getLogger(DeleteContainerNodeTest.class);

    public DeleteContainerNodeTest()
    {
        super();
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void deleteContainerNode()
    {
        try
        {
            log.debug("deleteContainerNode");

            // Get a ContainerNode.
            ContainerNode node = getSampleContainerNode();

            // Add ContainerNode to the VOSpace.
            WebResponse response = put(node);
            assertEquals("PUT response code should be 201", 201, response.getResponseCode());

            // Delete the node.
            response = delete(node);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            // Try and get the node from vospace
            response = get(node);
            assertEquals("GET response code should be 404 for a node that doesn't exist", 404, response.getResponseCode());
            
            log.info("deleteContainerNode passed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    /**
     * The service SHALL throw a HTTP 401 status code including a PermissionDenied
     * fault in the entity-body if the user does not have permissions to perform the operation
     */
//    @Test
    public void permissionDeniedFault()
    {
        try
        {
            log.debug("permissionDeniedFault");

            // Get a ContainerNode.
            ContainerNode node = getSampleContainerNode();
            
            // Add ContainerNode to the VOSpace.
            WebResponse response = put(node);
            assertEquals("PUT response code should be 201", 201, response.getResponseCode());

            // TODO: how do you delete a node without permissions?
            response = delete(node);
            assertEquals("DELETE response code should be 401", 401, response.getResponseCode());

            // Response message body should be 'PermissionDenied'
            assertEquals("Response message body should be 'PermissionDenied'", "PermissionDenied", response.getResponseMessage());

            // Check that the node wasn't created
            response = get(node);
            assertEquals("GET response code should be 404 for a deleted node", 404, response.getResponseCode());

            log.info("permissionDeniedFault passed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    /**
     * The service SHALL throw a HTTP 404 status code including a NodeNotFound
     * fault in the entity-body if the target Node does not exist
     */
    @Test
    public void nodeNotFoundFault()
    {
        try
        {
            log.debug("nodeNotFoundFault");

            // Create a Node that should not exist.
            ContainerNode nodeA = new ContainerNode(new VOSURI(VOS.VOS_URI + "/node_not_found"));

            // Try and delete the Node from the VOSpace.
            WebResponse response = delete(nodeA);
            assertEquals("DELETE response code should be 404 for a node that doesn't exist", 404, response.getResponseCode());

            // Response message body should be 'NodeNotFound'
            assertEquals("Response message body should be 'NodeNotFound'", "NodeNotFound", response.getResponseMessage());

            log.info("nodeNotFoundFault passed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    /**
     * If a parent node in the URI path does not exist then the service MUST
     * throw a HTTP 500 status code including a ContainerNotFound fault in the entity body.
     * For example, given the URI path /a/b/c, the service must throw a HTTP 500
     * status code including a ContainerNotFound fault in the entity body if
     * either /a or /a/b do not exist.
     */
    @Test
    public void containerNotFoundFault()
    {
        try
        {
            log.debug("containerNotFoundFault");

            // Create a Node path /A/B
            ContainerNode nodeAB = new ContainerNode(new VOSURI(VOS.VOS_URI + "/A/B"));

            // Try and delete the Node from the VOSpace.
            WebResponse response = delete(nodeAB);
            assertEquals("DELETE response code should be 500 for a invalid Node path", 500, response.getResponseCode());

            // Response message body should be 'ContainerNotFound'
            assertEquals("Response message body should be 'ContainerNotFound'", "ContainerNotFound", response.getResponseMessage());

            log.info("containerNotFoundFault passed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

}
