package ca.nrc.cadc.vos.web.restlet.resource.action;

import org.apache.log4j.Logger;
import org.restlet.data.Status;
import org.restlet.representation.Representation;

import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeAlreadyExistsException;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.web.representation.NodeErrorRepresentation;
import ca.nrc.cadc.vos.web.representation.NodeOutputRepresentation;
import ca.nrc.cadc.vos.web.restlet.resource.NodeFault;
import ca.nrc.cadc.vos.web.restlet.resource.NodeResource;

public class CreateNodeAction implements NodeAction
{
    
    private static Logger log = Logger.getLogger(CreateNodeAction.class);

    @Override
    public Representation perform(NodeResource nodeResource) throws Throwable
    {
        try
        {
            Node storedNode = nodeResource.getNodePersistence().putInContainer(
                    nodeResource.getNode(), nodeResource.getNode().getParent());
            
            // return the node in xml format
            NodeWriter nodeWriter = new NodeWriter();
            NodeOutputRepresentation nodeOutputRepresentation =
                new NodeOutputRepresentation(storedNode, nodeWriter);
            nodeResource.setStatus(Status.SUCCESS_CREATED);
            return nodeOutputRepresentation;
        }
        catch (NodeAlreadyExistsException e)
        {
            log.debug("Node already exists: " + nodeResource.getPath(), e);
            nodeResource.setStatus(NodeFault.DuplicateNode.getStatus());
            return new NodeErrorRepresentation(
                    NodeFault.DuplicateNode, nodeResource.getVosURI().toString());
        }
        catch (NodeNotFoundException e)
        {
            log.debug("Could not resolve part of path for node: " + nodeResource.getPath(), e);
            nodeResource.setStatus(NodeFault.ContainerNotFound.getStatus());
            return new NodeErrorRepresentation(
                    NodeFault.ContainerNotFound);
        }
    }

}
