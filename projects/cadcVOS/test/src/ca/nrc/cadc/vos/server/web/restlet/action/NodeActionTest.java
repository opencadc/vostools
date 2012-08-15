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


import static org.easymock.EasyMock.createMock;

import java.net.URL;

import org.junit.Test;
import org.restlet.Request;
import org.restlet.data.Reference;

import ca.nrc.cadc.vos.AbstractCADCVOSTest;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.server.NodePersistence;
import ca.nrc.cadc.vos.server.auth.VOSpaceAuthorizer;


public abstract class NodeActionTest<N extends NodeAction>
        extends AbstractCADCVOSTest<N>
{
    private Node mockNodeC = createMock(Node.class);
    private Node mockNodeS = createMock(Node.class);
    
    private NodePersistence mockNodePersistence =
            createMock(NodePersistence.class);
    private Request mockRequest = createMock(Request.class);
    private VOSpaceAuthorizer mockAuth = createMock(VOSpaceAuthorizer.class);
    private Reference mockRef = createMock(Reference.class);
    protected ContainerNode mockParentNode = createMock(ContainerNode.class);
    protected VOSURI mockVOS = createMock(VOSURI.class);
    protected VOSURI mockParentVOS = createMock(VOSURI.class);
    private URL fakeURL;

    protected String nodeName = "child";
    protected String vosURI = "vos://example.com!vopspace/parent/" + nodeName;

    @Test
    public void performNodeAction() throws Exception
    {
        fakeURL = new URL("http://example/com/foo");
        
        getTestSubject().setNodePersistence(mockNodePersistence);
        getTestSubject().setRequest(mockRequest);
        getTestSubject().setVOSpaceAuthorizer(mockAuth);
        getTestSubject().setVosURI(mockVOS);
        
        prePerformNodeAction();
        Node cnode = getTestSubject().doAuthorizationCheck();
        NodeActionResult result =
                getTestSubject().performNodeAction(cnode, getMockNodeS());
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

    public VOSpaceAuthorizer getMockAuth()
    {
        return mockAuth;
    }

    public Node getMockNodeC()
    {
        return mockNodeC;
    }

    public Node getMockNodeS()
    {
        return mockNodeS;
    }

    public NodePersistence getMockNodePersistence()
    {
        return mockNodePersistence;
    }

    public Request getMockRequest()
    {
        return mockRequest;
    }
    public Reference getMockRef()
    {
        return mockRef;
    }
    public URL getMockURL()
    {
        return fakeURL;
    }
}
