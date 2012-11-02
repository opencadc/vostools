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
 * 1/14/11 - 2:50 PM
 *
 * 
 * 
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.vos.server.web.restlet.action;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import org.easymock.EasyMock;
import org.restlet.data.Form;
import org.restlet.data.MediaType;

import ca.nrc.cadc.vos.server.AbstractView;


public class GetNodeActionTest extends NodeActionTest<GetNodeAction>
{
    private AbstractView mockAbstractView = createMock(AbstractView.class);

    /**
     * Set and initialize the Test Subject.
     *
     * @throws Exception If anything goes awry.
     */
    @Override
    protected void initializeTestSubject() throws Exception
    {
        setTestSubject(new GetNodeAction()
        {
            /**
             * Return the view requested by the client, or null if none specified.
             *
             * @return Instance of an AbstractView.
             * @throws InstantiationException If the object could not be constructed.
             * @throws IllegalAccessException If a constructor could not be found.
             */
            @Override
            protected AbstractView getView() throws InstantiationException,
                                                    IllegalAccessException
            {
                return getMockAbstractView();
            }
        });
    }


    /**
     * Any necessary preface action before the performNodeAction method is
     * called to be tested.  This is a good place for Mock expectations and/or
     * replays to be set.
     *
     * @throws Exception If anything goes wrong, just pass it up.
     */
    @Override
    protected void prePerformNodeAction() throws Exception
    {
        
        Form queryForm = EasyMock.createMock(Form.class);
        expect(queryForm.getFirstValue("view")).andReturn("VIEW/REFERENCE").once();
        expect(queryForm.getFirstValue("detail")).andReturn("VIEW/REFERENCE").once();
        
        getTestSubject().setQueryForm(queryForm);
        
        expect(getMockRef().toUrl()).andReturn(getMockURL()).atLeastOnce();
        expect(getMockRequest().getOriginalRef()).andReturn(getMockRef()).atLeastOnce();

        getMockAbstractView().setNode(getMockNodeS(), "VIEW/REFERENCE", getMockURL());
        expectLastCall().once();
        
        expect(getMockAbstractView().getRedirectURL()).andReturn(null).once();
        expect(getMockAbstractView().getMediaType()).andReturn(MediaType.TEXT_XML).
                once();
        
        getMockNodePersistence().getProperties(getMockNodeS());
        expectLastCall().once();
        
        expect(getMockNodeS().getUri()).andReturn(getTestSubject().vosURI).anyTimes();
        
        expect(getTestSubject().vosURI.getURIObject()).andReturn(vosURIObject).atLeastOnce();
        expect(getTestSubject().vosURI.getPath()).andReturn("/parent/" + nodeName).times(2);
        expect(getTestSubject().vosURI.getQuery()).andReturn(null).atLeastOnce();
        
        expect(mockPartialPathAuth.getReadPermission(vosURIObject)).andReturn(getMockNodeS()).anyTimes();

        replay(queryForm);
        replay(getMockRef());
        replay(getMockRequest());
        replay(getMockAbstractView());
        replay(getMockNodePersistence());
        replay(getMockNodeS());
        replay(getMockNodeS().getUri());
        replay(mockPartialPathAuth);
    }
   
    /**
     * Any necessary post method call result checking.  This is a good place
     * for any Mock verifications to take place as well.
     *
     * @param result The result of the performNodeAction call.
     * @throws Exception If anything goes wrong, just pass it up.
     */
    @Override
    protected void postPerformNodeAction(final NodeActionResult result)
            throws Exception
    {
        verify(getMockRef());
        verify(getMockRequest());
        verify(getMockAbstractView());
        verify(getMockNodePersistence());
        verify(getMockNodeS());
        verify(getTestSubject().vosURI);
    }


    public AbstractView getMockAbstractView()
    {
        return mockAbstractView;
    }
}
