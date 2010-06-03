package ca.nrc.cadc.vos.web.restlet.resource.action;

import org.restlet.representation.Representation;

import ca.nrc.cadc.vos.web.restlet.resource.NodeResource;

public interface NodeAction
{
    
    Representation perform(NodeResource nodeResource) throws Throwable;

}
