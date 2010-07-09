package ca.nrc.cadc.vos.server.web.restlet.action;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Principal;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;

import org.restlet.data.Status;
import org.restlet.representation.Representation;

import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeAlreadyExistsException;
import ca.nrc.cadc.vos.NodeFault;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodeParsingException;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.server.NodePersistence;
import ca.nrc.cadc.vos.server.auth.VOSpaceAuthorizer;
import ca.nrc.cadc.vos.server.web.representation.NodeInputRepresentation;
import ca.nrc.cadc.vos.server.web.representation.NodeOutputRepresentation;

public class CreateNodeAction extends NodeAction
{
    
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
        ContainerNode parent = (ContainerNode) voSpaceAuthorizer.getWritePermission(clientNode.getParent());
        clientNode.setParent(parent);
        clientNode.setOwner(getOwnerDistinguishedName());
        return clientNode;
    }

    @Override
    public NodeActionResult performNodeAction(Node node, NodePersistence nodePersistence) throws Exception
    {
        try
        {
            Node storedNode = nodePersistence.putInContainer(
                    node, node.getParent());
            storedNode.setUri(node.getUri());
            
            // return the node in xml format
            NodeWriter nodeWriter = new NodeWriter();
            NodeOutputRepresentation nodeOutputRepresentation =
                new NodeOutputRepresentation(storedNode, nodeWriter);
            return new NodeActionResult(nodeOutputRepresentation, Status.SUCCESS_CREATED);
        }
        catch (NodeAlreadyExistsException e)
        {
            log.debug("Node already exists: " + node.getPath(), e);
            NodeFault nodeFault = NodeFault.DuplicateNode;
            nodeFault.setMessage(node.getUri().toString());
            return new NodeActionResult(nodeFault);
        }
        catch (NodeNotFoundException e)
        {
            log.debug("Could not resolve part of path for node: " + node.getPath(), e);
            return new NodeActionResult(NodeFault.ContainerNotFound);
        }
    }
    
    private String getOwnerDistinguishedName()
    {
        AccessControlContext acContext = AccessController.getContext();
        Subject subject = Subject.getSubject(acContext);
        
        if (subject != null) {
            Set<Principal> principals = subject.getPrincipals();
            for (Principal principal : principals)
            {
                if (principal instanceof X500Principal)
                {
                    return principal.getName();
                }
            }
        }
        return "";
    }

}
