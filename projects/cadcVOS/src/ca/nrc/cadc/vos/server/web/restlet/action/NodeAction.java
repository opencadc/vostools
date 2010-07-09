package ca.nrc.cadc.vos.server.web.restlet.action;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.AccessControlException;
import java.security.PrivilegedAction;

import org.apache.log4j.Logger;
import org.restlet.representation.Representation;

import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeFault;
import ca.nrc.cadc.vos.NodeParsingException;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.server.NodePersistence;
import ca.nrc.cadc.vos.server.auth.VOSpaceAuthorizer;

public abstract class NodeAction implements PrivilegedAction<Object>
{
    protected static Logger log = Logger.getLogger(NodeAction.class);
    
    private VOSpaceAuthorizer voSpaceAuthorizer;
    private NodePersistence nodePersistence;
    private VOSURI vosURI;
    private Representation nodeXML;
    
    public void setVosURI(VOSURI vosURI)
    {
        this.vosURI = vosURI;
    }
    
    public void setNodeXML(Representation nodeXML)
    {
        this.nodeXML = nodeXML;
    }
    
    public void setVOSpaceAuthorizer(VOSpaceAuthorizer voSpaceAuthorizer)
    {
        this.voSpaceAuthorizer = voSpaceAuthorizer;
    }
    
    public void setNodePersistence(NodePersistence nodePersistence)
    {
        this.nodePersistence = nodePersistence;
    }
    
    abstract NodeActionResult performNodeAction(Node node, NodePersistence nodePersistence) throws Exception;
    
    abstract Node getClientNode(VOSURI vosURI, Representation nodeXML) throws URISyntaxException, NodeParsingException, IOException;
    
    abstract Node doAuthorizationCheck(VOSpaceAuthorizer voSpaceAuthorizer, Node clientNode) throws AccessControlException, FileNotFoundException;
    
    @Override
    public Object run()
    {
        
        try
        {
            // Create the client version of the node to be used for the operation
            Node clientNode = getClientNode(vosURI, nodeXML);
            log.debug("Client node is: " + clientNode);
            
            // perform the authorization check
            Node node = doAuthorizationCheck(voSpaceAuthorizer, clientNode);
            setNodeURI(node, vosURI);
            log.debug("doAuthorizationCheck() retrived node: " + node);
            
            // perform the node action
            return performNodeAction(node, nodePersistence);
            
        }
        catch (FileNotFoundException e)
        {
            String faultMessage = vosURI.toString();
            log.debug("Could not find node with path: " + vosURI.getPath(), e);
            return handleException(NodeFault.NodeNotFound, faultMessage);
        }
        catch (URISyntaxException e)
        {
            String faultMessage = "URI not well formed: " + vosURI;
            log.debug(faultMessage, e);
            return handleException(NodeFault.InvalidURI, faultMessage);
        }
        catch (AccessControlException e)
        {
            String faultMessage = "Access Denied: " + e.getMessage();
            log.debug(faultMessage, e);
            return handleException(NodeFault.PermissionDenied, faultMessage);
        }
        catch (NodeParsingException e)
        {
            String faultMessage = "Node XML not well formed: " + e.getMessage();
            log.debug(faultMessage, e);
            return handleException(NodeFault.InvalidToken, faultMessage);
        }
        catch (UnsupportedOperationException e)
        {
            String faultMessage = "Not supported: " + e.getMessage();
            log.debug(faultMessage, e);
            return handleException(NodeFault.NotSupported, faultMessage);
        }
        catch (IllegalArgumentException e)
        {
            String faultMessage = "Bad input: " + e.getMessage();
            log.debug(faultMessage, e);
            return handleException(NodeFault.BadRequest, faultMessage);
        }
        catch (Throwable t)
        {
            String faultMessage = "Internal Error:" + t.getMessage();
            log.debug(faultMessage, t);
            return handleException(NodeFault.InternalFault, faultMessage);
        }
    }
    
    private NodeActionResult handleException(NodeFault nodeFault, String message)
    {
        nodeFault.setMessage(message);
        return new NodeActionResult(nodeFault);
    }
    
    /**
     * Recursive method to set the URI of a node and its children.
     * @param node
     * @param uri
     * @throws URISyntaxException
     */
    private void setNodeURI(Node node, VOSURI uri) throws URISyntaxException
    {
        node.setUri(uri);
        if (node instanceof ContainerNode)
        {
            ContainerNode containerNode = (ContainerNode) node;
            for (Node child : containerNode.getNodes())
            {
                VOSURI childURI = new VOSURI(uri.toString() + "/" + child.getName());
                setNodeURI(child, childURI);
            }
        }
    }

}
