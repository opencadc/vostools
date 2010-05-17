package ca.nrc.cadc.vos.util;

import ca.nrc.cadc.vos.Node;

public interface NodeStackListener
{
    
    public void nodeVisited(Node node, boolean isParentNode);
    

}
