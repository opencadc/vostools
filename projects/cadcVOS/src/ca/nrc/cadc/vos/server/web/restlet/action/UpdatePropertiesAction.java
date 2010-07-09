package ca.nrc.cadc.vos.server.web.restlet.action;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.AccessControlException;

import org.apache.log4j.Logger;
import org.restlet.representation.Representation;

import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeFault;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodeParsingException;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.server.NodePersistence;
import ca.nrc.cadc.vos.server.auth.VOSpaceAuthorizer;
import ca.nrc.cadc.vos.server.web.representation.NodeInputRepresentation;
import ca.nrc.cadc.vos.server.web.representation.NodeOutputRepresentation;

public class UpdatePropertiesAction extends NodeAction
{
    
    private static Logger log = Logger.getLogger(UpdatePropertiesAction.class);
    
    @Override
    public Node getClientNode(VOSURI vosURI, Representation nodeXML)
            throws URISyntaxException, NodeParsingException, IOException 
    {
        NodeInputRepresentation nodeInputRepresentation =
            new NodeInputRepresentation(nodeXML, vosURI.getPath());
        return nodeInputRepresentation.getNode();
    }
    
    @Override
    public Node doAuthorizationCheck(VOSpaceAuthorizer voSpaceAuthorizer, Node clientNode)
            throws AccessControlException, FileNotFoundException
    {
        Node node = (Node) voSpaceAuthorizer.getWritePermission(clientNode);
        node.setProperties(clientNode.getProperties());
        return node;
    }

    @Override
    public NodeActionResult performNodeAction(Node node, NodePersistence nodePersistence) throws Exception
    {
        
        // check for a busy node
        if (node instanceof DataNode)
        {
            if (((DataNode) node).isBusy())
            {
                log.debug("Node is busy: " + node.getPath());
                NodeFault nodeFault = NodeFault.NodeBusy;
                nodeFault.setMessage(node.getUri().toString());
                return new NodeActionResult(nodeFault);
            }
        }
        
        try
        {
            // filter out any non-modifiable properties
            filterPropertiesForUpdate(node);
            
            Node updatedNode = nodePersistence.updateProperties(node);
            updatedNode.setUri(node.getUri());
            
            // return the node in xml format
            NodeWriter nodeWriter = new NodeWriter();
            return new NodeActionResult(new NodeOutputRepresentation(updatedNode, nodeWriter));
        }
        catch (NodeNotFoundException e)
        {
            log.debug("Could not resolve part of path for node: " + node.getPath());
            NodeFault nodeFault = NodeFault.NodeNotFound;
            nodeFault.setMessage(node.getUri().toString());
            return new NodeActionResult(nodeFault);
        }
    }
    

    
    /**
     * Remove any properties from the Node that cannot be updated.
     * @param node
     */
    private void filterPropertiesForUpdate(Node node)
    {
        if (node.getProperties().contains(VOS.PROPERTY_URI_DATE))
        {
            node.getProperties().remove(new NodeProperty(VOS.PROPERTY_URI_DATE, null));
        }
    }

}
