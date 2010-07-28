package ca.nrc.cadc.vos.server;

import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;

import ca.nrc.cadc.vos.View;

public class ViewFactory
{
    
    private static Logger log = Logger.getLogger(ViewFactory.class);
    
    private static final String VIEWS_PROPERTY_FILE = "Views";
    
    private static final String KEY_VIEW_LIST = "views";
    private static final String KEY_VIEW_ALIAS = "alias";
    private static final String KEY_VIEW_URI = "uri";
    private static final String KEY_VIEW_CLASS = "class";
    
    static
    {
        loadConfiguredViews();
    }
    
    private static Map<String, View> viewMap;
    
    public View getView(String viewReference)
    {
        return viewMap.get(viewReference);
    }
    
    private static void loadConfiguredViews()
    {
        ResourceBundle rb = null;
        try
        {
            rb = ResourceBundle.getBundle(VIEWS_PROPERTY_FILE);
        }
        catch (MissingResourceException e)
        {
            throw new ExceptionInInitializerError("Could not load properties file: "
                    + VIEWS_PROPERTY_FILE + ".properties: " + e.getMessage());
        }
        
        viewMap = new HashMap<String, View>();
        
        try
        {
            String viewNamesString = rb.getString(KEY_VIEW_LIST);
            String[] viewNames = viewNamesString.split(" ");
            
            log.info("ViewFactory loading views: " + viewNames);
            
            for (String viewName : viewNames)
            {
                String viewAlias = rb.getString(viewName + "." + KEY_VIEW_ALIAS);
                String viewURI = rb.getString(viewName + "." + KEY_VIEW_URI);
                String viewClassName = rb.getString(viewName + "." + KEY_VIEW_CLASS);
                Class<?> viewClass = Class.forName(viewClassName);
                View view = (View) viewClass.newInstance();
                if (view instanceof View)
                {
                    throw new ExceptionInInitializerError("Class " + viewClass
                            + " is not an instance of " + View.class);
                }
                
                if (viewMap.containsKey(viewAlias))
                {
                    throw new ExceptionInInitializerError("Duplicate view reference " + viewAlias
                            + " in file " + VIEWS_PROPERTY_FILE + ".properties");
                }
                if (viewMap.containsKey(viewURI))
                {
                    throw new ExceptionInInitializerError("Duplicate view reference " + viewURI
                            + " in file " + VIEWS_PROPERTY_FILE + ".properties");
                }
                viewMap.put(viewAlias, view);
                viewMap.put(viewURI, view);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            log.error(e);
            throw new ExceptionInInitializerError(e);
        }
        
    }

}
