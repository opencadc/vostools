package ca.nrc.cadc.vos.server;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ca.nrc.cadc.vos.View;

public class ViewFactoryTest
{
    
    @Test
    public void testDefaultConfiguration() throws Exception
    {
        ViewFactory viewFactory = new ViewFactory();
        View view = null;
        view = viewFactory.getView("data");
        assertEquals(view.getClass(), DataView.class);
        viewFactory.getView("ivo://cadc.nrc.ca/vospace/core#dataview");
        assertEquals(view.getClass(), DataView.class);
        viewFactory.getView("rss");
        assertEquals(view.getClass(), RssView.class);
        viewFactory.getView("ivo://cadc.nrc.ca/vospace/core#rssview");
        assertEquals(view.getClass(), RssView.class);
    }

}
