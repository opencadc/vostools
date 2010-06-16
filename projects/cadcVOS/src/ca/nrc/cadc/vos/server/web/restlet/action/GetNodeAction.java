package ca.nrc.cadc.vos.server.web.restlet.action;

import org.restlet.representation.Representation;

import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.server.web.representation.NodeOutputRepresentation;
import ca.nrc.cadc.vos.server.web.restlet.resource.NodeResource;

public class GetNodeAction implements NodeAction
{

    @Override
    public Representation perform(NodeResource nodeResource) throws Throwable
    {
        NodeWriter nodeWriter = new NodeWriter();
        return new NodeOutputRepresentation(
                nodeResource.getNode(), nodeWriter);
    }

}
