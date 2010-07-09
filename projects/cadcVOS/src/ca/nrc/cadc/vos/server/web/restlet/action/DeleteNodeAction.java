package ca.nrc.cadc.vos.server.web.restlet.action;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.AccessControlException;

import org.apache.log4j.Logger;
import org.restlet.representation.Representation;

import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeFault;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodeParsingException;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.server.NodePersistence;
import ca.nrc.cadc.vos.server.SearchNode;
import ca.nrc.cadc.vos.server.auth.VOSpaceAuthorizer;

public class DeleteNodeAction extends NodeAction
{
    
    private static Logger log = Logger.getLogger(DeleteNodeAction.class);
    
    @Override
    public Node getClientNode(VOSURI vosURI, Representation nodeXML)
            throws URISyntaxException, NodeParsingException, IOException 
    {
        return new SearchNode(vosURI);
    }
    
    @Override
    public Node doAuthorizationCheck(VOSpaceAuthorizer voSpaceAuthorizer, Node clientNode)
            throws AccessControlException, FileNotFoundException
    {
        Node node = (Node) voSpaceAuthorizer.getWritePermission(clientNode);
        node.setProperties(clientNode.getProperties());
        return node;
    }

    /**
     * Perform the node, and all child nodes, for deletion.
     */
    @Override
    public NodeActionResult performNodeAction(Node node, NodePersistence nodePersistence) throws Exception
    {
        try
        {
            nodePersistence.markForDeletion(node, true);
            return null;
        }
        catch (NodeNotFoundException e)
        {
            log.debug("Could not find node with path: " + node.getPath(), e);
            NodeFault nodeFault = NodeFault.NodeNotFound;
            nodeFault.setMessage(node.getUri().toString());
            return new NodeActionResult(nodeFault);
        }
    }


    
}
