package ca.nrc.cadc.vos.server.web.restlet.action;

import org.restlet.representation.Representation;

import ca.nrc.cadc.vos.server.web.restlet.resource.NodeResource;

public interface NodeAction
{
    
    Representation perform(NodeResource nodeResource) throws Throwable;

}
