package ca.nrc.cadc.vos.server.web.restlet.action;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.AccessControlException;

import org.restlet.representation.Representation;

import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeParsingException;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.server.NodePersistence;
import ca.nrc.cadc.vos.server.SearchNode;
import ca.nrc.cadc.vos.server.auth.VOSpaceAuthorizer;
import ca.nrc.cadc.vos.server.web.representation.NodeOutputRepresentation;

public class GetNodeAction extends NodeAction
{

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
        return (Node) voSpaceAuthorizer.getReadPermission(clientNode);
    }
    
    @Override
    public NodeActionResult performNodeAction(Node node, NodePersistence nodePersistence) throws Exception
    {
        NodeWriter nodeWriter = new NodeWriter();
        return new NodeActionResult(new NodeOutputRepresentation(node, nodeWriter));
    }
    
}
