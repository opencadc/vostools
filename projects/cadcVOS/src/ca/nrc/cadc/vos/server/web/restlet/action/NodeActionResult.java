package ca.nrc.cadc.vos.server.web.restlet.action;

import org.restlet.data.Status;
import org.restlet.representation.Representation;

import ca.nrc.cadc.vos.NodeFault;
import ca.nrc.cadc.vos.server.web.representation.NodeErrorRepresentation;

public class NodeActionResult
{
    
    private Status status = Status.SUCCESS_OK;
    private NodeFault nodeFault;
    private Representation representation;
    
    public NodeActionResult(Representation representation)
    {
        this.representation = representation;
    }
    
    public NodeActionResult(Representation representation, Status status)
    {
        this.representation = representation;
        this.status = status;
    }
    
    public NodeActionResult(NodeFault nodeFault)
    {
        this.nodeFault = nodeFault;
        this.status = nodeFault.getStatus();
    }
    
    public Status getStatus()
    {
        return status;
    }
    
    public Representation getRepresentation()
    {
        if (nodeFault != null)
        {
            return new NodeErrorRepresentation(nodeFault);
        }
        return representation;
    }

}
