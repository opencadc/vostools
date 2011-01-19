/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2011.                         (c) 2011.
 * National Research Council            Conseil national de recherches
 * Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 * All rights reserved                  Tous droits reserves
 *
 * NRC disclaims any warranties         Le CNRC denie toute garantie
 * expressed, implied, or statu-        enoncee, implicite ou legale,
 * tory, of any kind with respect       de quelque nature que se soit,
 * to the software, including           concernant le logiciel, y com-
 * without limitation any war-          pris sans restriction toute
 * ranty of merchantability or          garantie de valeur marchande
 * fitness for a particular pur-        ou de pertinence pour un usage
 * pose.  NRC shall not be liable       particulier.  Le CNRC ne
 * in any event for any damages,        pourra en aucun cas etre tenu
 * whether direct or indirect,          responsable de tout dommage,
 * special or general, consequen-       direct ou indirect, particul-
 * tial or incidental, arising          ier ou general, accessoire ou
 * from the use of the software.        fortuit, resultant de l'utili-
 *                                      sation du logiciel.
 *
 *
 * @author jenkinsd
 * 1/14/11 - 2:51 PM
 *
 * 
 * 
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.vos.server.web.restlet.action;


import ca.nrc.cadc.vos.AbstractCADCVOSTest;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.server.NodePersistence;

import org.junit.Test;
import org.restlet.Request;

import static org.easymock.EasyMock.*;


public abstract class NodeActionTest<N extends NodeAction>
        extends AbstractCADCVOSTest<N>
{
    private Node mockNode = createMock(Node.class);
    private NodePersistence mockNodePersistence =
            createMock(NodePersistence.class);
    private Request mockRequest = createMock(Request.class);


    @Test
    public void performNodeAction() throws Exception
    {
        prePerformNodeAction();
        final NodeActionResult result =
                getTestSubject().performNodeAction(getMockNode(),
                                                   getMockNodePersistence(),
                                                   getMockRequest());
        postPerformNodeAction(result);
    }


    /**
     * Any necessary preface action before the performNodeAction method is
     * called to be tested.  This is a good place for Mock expectations and/or
     * replays to be set.
     *
     * @throws Exception    If anything goes wrong, just pass it up.
     */
    protected abstract void prePerformNodeAction() throws Exception;

    /**
     * Any necessary post method call result checking.  This is a good place
     * for any Mock verifications to take place as well.
     *
     * @param result        The result of the performNodeAction call.
     * @throws Exception    If anything goes wrong, just pass it up.
     */
    protected abstract void postPerformNodeAction(
            final NodeActionResult result) throws Exception;


    public Node getMockNode()
    {
        return mockNode;
    }

    public NodePersistence getMockNodePersistence()
    {
        return mockNodePersistence;
    }

    public Request getMockRequest()
    {
        return mockRequest;
    }
}
