package ca.nrc.cadc.vos.util;

import java.util.Stack;

import org.apache.log4j.Logger;

import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodePersistence;

public class NodeUtil
{
    
    private static Logger log = Logger.getLogger(NodeUtil.class);
    
    public static Node iterateStack(Node targetNode, NodeStackListener listener, NodePersistence nodePersistence)
    throws NodeNotFoundException
    {
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
            persistentNode = nodePersistence.getFromParent(nextNode, parent);
            
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
        
        return persistentNode;
    }

}
