/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2012.                         (c) 2012.
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
 * 10/17/12 - 12:34 PM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.vos.client.ui;

import ca.nrc.cadc.vos.AbstractCADCVOSTest;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.client.VOSpaceClient;
import org.apache.log4j.Level;

import javax.swing.*;
import java.awt.*;
import java.net.URI;

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.easymock.EasyMock.*;


public class GraphicUITest extends AbstractCADCVOSTest<GraphicUI>
{
    private final VOSURI targetVOSpaceURI;
    private final VOSpaceClient mockVOSpaceClient =
            createMock(VOSpaceClient.class);
    private boolean tabPaneAdded = false;
    private boolean selectSourceDirectoryOffered = false;


    public GraphicUITest()
    {
        targetVOSpaceURI =
                new VOSURI(URI.create("vos://cadc.nrc.ca!vospace/CADCtest"));
    }


    @Test
    @Ignore("How can we test something in a different thread?")
    public void makeUI() throws Exception
    {
        final JTabbedPane mockTabPane = createMock(JTabbedPane.class);
        final JScrollPane mockLogPane = createMock(JScrollPane.class);

        setTestSubject(new GraphicUI(Level.OFF, getTargetVOSpaceURI(),
                                     getMockVOSpaceClient())
        {
            @Override
            public JTabbedPane getTabPane()
            {
                return mockTabPane;
            }

            /**
             * Create an instance of a JScrollPane to contain the log output.
             *
             * @param logTextArea The JTextArea to scroll.
             * @return The JScrollPane instance.
             */
            @Override
            protected JScrollPane createLogScrollPane(JTextArea logTextArea)
            {
                return mockLogPane;
            }

            /**
             * Add the tabbed pane.
             */
            @Override
            protected void addMainPane()
            {
                setTabPaneAdded(true);
            }

            @Override
            public void selectSourceDirectory(Component parent,
                                              SourceDirectoryChooserCallback callback)
            {
                setSelectSourceDirectoryOffered(true);
            }
        });

        mockTabPane.addTab("Log Messages", mockLogPane);
        expectLastCall().once();

        replay(mockLogPane, mockTabPane);

        getTestSubject().makeUI();

        final Insets borderInsets =
                getTestSubject().getBorder().getBorderInsets(getTestSubject());

        assertEquals("Should be 4 all around.", 4, borderInsets.bottom);
        assertEquals("Should be 4 all around.", 4, borderInsets.top);
        assertEquals("Should be 4 all around.", 4, borderInsets.left);
        assertEquals("Should be 4 all around.", 4, borderInsets.right);

        assertTrue("Tab pane should be added.", isTabPaneAdded());
        assertTrue("Select was not offered.", isSelectSourceDirectoryOffered());

        verify(mockLogPane, mockTabPane);
    }

    @Test
    public void selectSourceDirectory() throws Exception
    {

    }


    /**
     * Set and initialize the Test Subject.
     *
     * @throws Exception If anything goes awry.
     */
    @Override
    protected void initializeTestSubject() throws Exception
    {
        setTestSubject(new GraphicUI(Level.OFF, getTargetVOSpaceURI(),
                                     getMockVOSpaceClient()));
    }

    public VOSURI getTargetVOSpaceURI()
    {
        return targetVOSpaceURI;
    }

    public VOSpaceClient getMockVOSpaceClient()
    {
        return mockVOSpaceClient;
    }

    public boolean isTabPaneAdded()
    {
        return tabPaneAdded;
    }

    public void setTabPaneAdded(boolean tabPaneAdded)
    {
        this.tabPaneAdded = tabPaneAdded;
    }

    public boolean isSelectSourceDirectoryOffered()
    {
        return selectSourceDirectoryOffered;
    }

    public void setSelectSourceDirectoryOffered(
            boolean selectSourceDirectoryOffered)
    {
        this.selectSourceDirectoryOffered = selectSourceDirectoryOffered;
    }
}
