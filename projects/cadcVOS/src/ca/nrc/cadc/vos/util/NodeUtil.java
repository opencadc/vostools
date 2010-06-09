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

package ca.nrc.cadc.vos.util;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.Stack;

import org.apache.log4j.Logger;

import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobWriter;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodePersistence;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;

/**
 * Methods that add convenience in dealing with Nodes.
 * 
 * @author majorb
 *
 */
public class NodeUtil
{

    private static Logger log = Logger.getLogger(NodeUtil.class);

    /**
     * Iterate the parental hierarchy of the node from root to self.
     * 
     * @param targetNode The node whos hierarchy is to be iterated
     * @param listener An optional listener notified at each stack level.
     * @param nodePersistence The node persistence implementation
     * @return The persistent version of the target node.
     * @throws NodeNotFoundException If the target node could not be found.
     */
    public static Node iterateStack(Node targetNode, NodeStackListener listener, NodePersistence nodePersistence) throws NodeNotFoundException
    {

        if (targetNode == null)
        {
            // root container, return null
            return null;
        }

        Stack<Node> nodeStack = targetNode.stackToRoot();
        Node persistentNode = null;
        Node nextNode = null;
        ContainerNode parent = null;

        while (!nodeStack.isEmpty())
        {
            nextNode = nodeStack.pop();
            nextNode.setParent(parent);
            log.debug("Retrieving node with path: " + nextNode.getPath());

            // get the node from the persistence layer
            persistentNode = nodePersistence.getFromParent(nextNode.getName(), parent);

            // check if it is marked for deletion
            if (persistentNode.isMarkedForDeletion())
            {
                throw new NodeNotFoundException("Node is marked for deletion.");
            }

            // call the listener
            if (listener != null)
            {
                listener.nodeVisited(persistentNode, !nodeStack.isEmpty());
            }

            // get the parent 
            if (persistentNode instanceof ContainerNode)
            {
                parent = (ContainerNode) persistentNode;
            }
            else if (!nodeStack.isEmpty())
            {
                final String message = "Non-container node found mid-tree";
                log.warn(message);
                throw new NodeNotFoundException(message);
            }

        }

        if (persistentNode instanceof DataNode)
        {
            persistentNode.setParent(parent);
        }
        return persistentNode;
    }

    /**
     * Create a VOSURI from a Node and it's parent.
     *
     * @param node The Node to create the VOSURI for.
     * @param parent The parent of the Node.
     * @return A VOSURI for the node.
     * @throws URISyntaxException if the VOSURI syntax is invalid.
     */
    public static VOSURI createVOSURI(Node node, Node parent) throws URISyntaxException
    {
        StringBuilder sb = new StringBuilder();
        sb.append(VOS.VOS_URI);
        if (parent != null)
        {
            sb.append("/");
            sb.append(parent.getPath());
        }
        sb.append("/");
        sb.append(node.getName());

        return new VOSURI(sb.toString());
    }

    /**
     * get the XML string of node.
     * 
     * @param node
     * @return XML string of the node
     * 
     * @author Sailor Zhang
     */
    public static String xmlString(Node node)
    {
        String xml = null;
        StringWriter sw = new StringWriter();
        try
        {
            NodeWriter nodeWriter = new NodeWriter();
            nodeWriter.write(node, sw);
            xml = sw.toString();
            sw.close();
        }
        catch (IOException e)
        {
            xml = "Error getting XML string from node: " + e.getMessage();
        }
        return xml;
    }

    /**
     * get the XML string of a job.
     * 
     * @param job
     * @return XML string of the job
     * 
     * @author Sailor Zhang
     */
    public static String xmlString(Job job)
    {
        String xml = null;
        StringWriter sw = new StringWriter();
        try
        {
            JobWriter jobWriter = new JobWriter(job);
            jobWriter.writeTo(sw);
            xml = sw.toString();
            sw.close();
        }
        catch (IOException e)
        {
            xml = "Error getting XML string from job: " + e.getMessage();
        }
        return xml;
    }
}
