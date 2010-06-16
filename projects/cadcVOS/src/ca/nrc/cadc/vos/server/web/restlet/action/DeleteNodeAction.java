package ca.nrc.cadc.vos.server.web.restlet.action;

import org.apache.log4j.Logger;
import org.restlet.representation.Representation;

import ca.nrc.cadc.vos.NodeFault;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.server.web.representation.NodeErrorRepresentation;
import ca.nrc.cadc.vos.server.web.restlet.resource.NodeResource;

public class DeleteNodeAction implements NodeAction
{
    
    private static Logger log = Logger.getLogger(DeleteNodeAction.class);

    /**
     * Perform the node, and all child nodes, for deletion.
     */
    @Override
    public Representation perform(NodeResource nodeResource) throws Throwable
    {
        try
        {
            nodeResource.getNodePersistence().markForDeletion(
                    nodeResource.getNode(), true);
            return null;
        }
        catch (NodeNotFoundException e)
        {
            log.debug("Could not find node with path: " + nodeResource.getPath(), e);
            nodeResource.setStatus(NodeFault.NodeNotFound.getStatus());
            return new NodeErrorRepresentation(
                    NodeFault.NodeNotFound, nodeResource.getVosURI().toString());
        }
    }

}
