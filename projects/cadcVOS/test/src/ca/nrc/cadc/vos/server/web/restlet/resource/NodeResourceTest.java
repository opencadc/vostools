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
 * Jan 13, 2011 - 9:04:30 AM
 *
 * 
 * 
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.vos.server.web.restlet.resource;


import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.Test;
import org.restlet.Application;
import org.restlet.Context;
import org.restlet.data.Form;


public class NodeResourceTest extends BaseResourceTest<NodeResource>
{
    private Context mockContext = createMock(Context.class);
    private Application mockApplication = createMock(Application.class);
    private Form mockForm = createMock(Form.class);


    /**
     * Set and initialize the Test Subject.
     *
     * @throws Exception If anything goes awry.
     */
    @Override
    protected void initializeTestSubject() throws Exception
    {
        final ConcurrentMap<String, Object> attributes =
                new ConcurrentHashMap<String, Object>();
        expect(getMockApplication().getContext()).andReturn(getMockContext()).
                once();
        expect(getMockContext().getAttributes()).andReturn(attributes).once();

        replay(getMockApplication(), getMockContext());

        setTestSubject(new NodeResource()
        {
            /**
             * Returns the resource reference's optional query.
             *
             * @return The resource reference's optional query.
             * @see org.restlet.data.Reference#getQueryAsForm()
             */
            @Override
            public Form getQuery()
            {
                return getMockForm();
            }

            /**
             * Returns the parent application. If it wasn't set,
             * it attempts to retrieve
             * the current one via {@link org.restlet.Application#getCurrent
             * ()} if it
             * exists, or instantiates a new one as a last resort.
             *
             * @return The parent application if it exists, or a new one.
             */
            @Override
            public Application getApplication()
            {
                return getMockApplication();
            }
        });

        verify(getMockApplication(), getMockContext());
        reset(getMockApplication(), getMockContext());
    }
    
    @Test
    public void testPerformNodeAction()
    {
        // TODO: Write unit tests for perform node action
    }


    public Form getMockForm()
    {
        return mockForm;
    }

    public Context getMockContext()
    {
        return mockContext;
    }

    public Application getMockApplication()
    {
        return mockApplication;
    }
}
