package ca.nrc.cadc.vos.web.restlet.resource.action;

import org.apache.log4j.Logger;
import org.restlet.representation.Representation;

import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.web.representation.NodeErrorRepresentation;
import ca.nrc.cadc.vos.web.restlet.resource.NodeFault;
import ca.nrc.cadc.vos.web.restlet.resource.NodeResource;

public class DeleteNodeAction implements NodeAction
{
    
    private static Logger log = Logger.getLogger(DeleteNodeAction.class);

    @Override
    public Representation perform(NodeResource nodeResource) throws Throwable
    {
        try
        {
            nodeResource.getNodePersistence().delete(nodeResource.getNode(), true);
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
