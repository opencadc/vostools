package ca.nrc.cadc.vos.web.restlet.resource.action;

import org.apache.log4j.Logger;
import org.restlet.representation.Representation;

import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.web.representation.NodeErrorRepresentation;
import ca.nrc.cadc.vos.web.representation.NodeOutputRepresentation;
import ca.nrc.cadc.vos.web.restlet.resource.NodeFault;
import ca.nrc.cadc.vos.web.restlet.resource.NodeResource;

public class UpdatePropertiesAction implements NodeAction
{
    
    private static Logger log = Logger.getLogger(UpdatePropertiesAction.class);

    @Override
    public Representation perform(NodeResource nodeResource) throws Throwable
    {
        try
        {
            // filter out any non-modifiable properties
            filterPropertiesForUpdate(nodeResource.getNode());
            
            Node updatedNode = nodeResource.getNodePersistence().updateProperties(
                    nodeResource.getNode());
            
            // return the node in xml format
            NodeWriter nodeWriter = new NodeWriter();
            return new NodeOutputRepresentation(updatedNode, nodeWriter);
        }
        catch (NodeNotFoundException e)
        {
            log.debug("Could not resolve part of path for node: " + nodeResource.getPath());
            nodeResource.setStatus(NodeFault.NodeNotFound.getStatus());
            return new NodeErrorRepresentation(
                    NodeFault.NodeNotFound, nodeResource.getVosURI().toString());
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
