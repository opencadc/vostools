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


import ca.nrc.cadc.vos.Search;
import org.junit.Test;
import org.restlet.Application;
import org.restlet.Context;
import org.restlet.data.Form;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;


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
    public void hasSearchCriteria() throws Exception
    {
        expect(getMockForm().getNames()).andReturn(
                new HashSet<String>()).once();

        replay(getMockForm());
        assertFalse("No criteria given.", getTestSubject().hasSearchCriteria(
                getMockForm()));
        verify(getMockForm());

        // TEST 2
        final Set<String> queryParameterNames = new HashSet<String>();
        queryParameterNames.add("Detail");
        queryParameterNames.add("Bogus");

        reset(getMockForm());

        expect(getMockForm().getNames()).andReturn(queryParameterNames).once();

        replay(getMockForm());
        assertFalse("No criteria given.", getTestSubject().hasSearchCriteria(
                getMockForm()));
        verify(getMockForm());

        // TEST 3
        queryParameterNames.clear();
        reset(getMockForm());

        queryParameterNames.add("detail");
        queryParameterNames.add("boGUs");

        expect(getMockForm().getNames()).andReturn(queryParameterNames).once();

        replay(getMockForm());
        assertTrue("Detail param given.", getTestSubject().hasSearchCriteria(
                getMockForm()));
        verify(getMockForm());
    }

    @Test
    public void createSearchCriteria() throws Exception
    {
        final Set<String> queryParameterNames = new HashSet<String>();

        expect(getMockForm().getNames()).andReturn(queryParameterNames).once();

        replay(getMockForm());

        assertNull("Search should be null with no items.",
                   getTestSubject().createSearchCriteria());

        verify(getMockForm());


        // TEST 2
        reset(getMockForm());

        queryParameterNames.add("detail");
        queryParameterNames.add("bogus");

        expect(getMockForm().getNames()).andReturn(queryParameterNames).once();
        expect(getMockForm().getFirstValue("detail")).andReturn("min").once();

        replay(getMockForm());
        final Search search = getTestSubject().createSearchCriteria();
        assertNotNull("Search should not be null detail item.", search);
        assertEquals("Detail should be set.", Search.Results.Detail.MIN,
                     search.getResults().getDetail());

        verify(getMockForm());
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
