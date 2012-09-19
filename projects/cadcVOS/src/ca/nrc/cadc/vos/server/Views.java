/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2009.                            (c) 2009.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*                                       
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*                                       
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*                                       
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*                                       
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*                                       
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 4 $
*
************************************************************************
*/

package ca.nrc.cadc.vos.server;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;

import ca.nrc.cadc.vos.VOS;

/**
 * The class Views is responsible for loading the views from Views.properties and
 * for creating instances of views through method getView(String viewReference)
 * where viewReference can either be the alias or URI of a view that has been loaded
 * from configuration.
 * 
 * @author majorb
 *
 */
public class Views
{
    
    private static Logger log = Logger.getLogger(Views.class);
    
    private static final String VIEWS_PROPERTY_FILE = Views.class.getSimpleName();
    
    private static final String KEY_VIEW_LIST = "views";
    private static final String KEY_VIEW_ALIAS = "alias";
    private static final String KEY_VIEW_URI = "uri";
    private static final String KEY_VIEW_CLASS = "class";
    private static final String KEY_VIEW_ACCEPTS = "accepts";
    private static final String KEY_VIEW_PROVIDES = "provides";
    
    /**
     * Load the views upon class loading.
     */
    static
    {
        loadConfiguredViews();
    }
    
    // The maps of configured view classes
    private static Map<String, Class<AbstractView>> uriViewMap;
    private static Map<String, Class<AbstractView>> aliasViewMap;
    
    // The list of accepting views
    private static List<String> accepts;
    
    // The list of providing views
    private static List<String> provides;
    
    /**
     * Given a viewReference, return a new instance of the associated view or
     * null if no mapping exists for the viewReference.
     * 
     * @param viewReference  Either the alias or URI of a configured view.
     * If no class exists for the given reference, this method returns null.
     * @return A new instance of the view object.
     * @throws InstantiationException If the object could not be constructed.
     * @throws IllegalAccessException If a constructor could not be found.
     */
    public AbstractView getView(String viewReference) throws Exception
    {
        Class<AbstractView> viewClass = aliasViewMap.get(viewReference);
        URI viewURI = null;
        if (viewClass == null)
        {
            // try the URI map
            viewClass = uriViewMap.get(viewReference);
            if (viewClass == null)
            {
                log.debug("No view found for reference: " + viewReference);
                return null;
            }
            viewURI = new URI(viewReference);
        }
        log.debug("Returning new view of type " + viewClass + " for reference " + viewReference);
        return createAbstractView(viewClass, viewURI);
    }
    
    /**
     * Given a view URI, return a new instance of the associated view or null if
     * no mapping exists for the view URI.
     * @param viewURI
     * @return A new instance of the view object.
     * @throws InstantiationException If the object could not be constructed.
     * @throws IllegalAccessException If a constructor could not be found.
     */
    public AbstractView getView(URI viewURI) throws Exception
    {
        Class<AbstractView> viewClass = uriViewMap.get(viewURI.toString());
        if (viewClass == null)
        {
            log.debug("No view found for reference: " + viewURI.toString());
            return null;
        }
        log.debug("Returning new view of type " + viewClass + " for reference " + viewURI.toString());
        return (AbstractView) createAbstractView(viewClass, viewURI);
    }
    
    /**
     * Get a list of all unique view objects configured.
     * 
     * @return
     * @throws Exception
     */
    public List<AbstractView> getViews() throws Exception
    {
        Collection<String> viewURIs = uriViewMap.keySet();
        Class<AbstractView> nextViewClass = null;
        AbstractView nextView = null;
        List<AbstractView> viewList = new ArrayList<AbstractView>();
        for (String viewURI : viewURIs)
        {
            nextViewClass = uriViewMap.get(viewURI);
            nextView = createAbstractView(nextViewClass, new URI(viewURI));
            viewList.add(nextView);
        }
        return viewList;
    }
    
    /**
     * Instantiate the view with the URI if it's available.
     * @param clazz
     * @param uri
     * @return
     * @throws Exception
     */
    private AbstractView createAbstractView(Class<AbstractView> clazz, URI uri)
        throws Exception
    {
        if (uri == null)
        {
            return clazz.newInstance();
        }
        else
        {
            Constructor<AbstractView> constructor = clazz.getConstructor(URI.class);
            return constructor.newInstance(uri);
        }
    }
    
    /**
     * Get the list of accepts URIs.
     */
    public static List<String> accepts()
    {
        return accepts;
    }
    
    /**
     * Get the list of provides URIs.
     */
    public static List<String> provides()
    {
        return provides;
    }
    
    /**
     * Load the view aliases, uri, and classes from Views.properties
     */
    @SuppressWarnings("unchecked")
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
        
        uriViewMap = new HashMap<String, Class<AbstractView>>();
        aliasViewMap = new HashMap<String, Class<AbstractView>>();
        accepts = new ArrayList<String>();
        provides = new ArrayList<String>();
        
        // Add the default accepts and provides views
        accepts.add(VOS.VIEW_DEFAULT);
        provides.add(VOS.VIEW_DEFAULT);
        
        try
        {
            String viewNamesString = rb.getString(KEY_VIEW_LIST);
            String[] viewNames = viewNamesString.split(" ");
            
            log.debug("ViewFactory loading views: " + viewNamesString);
            
            for (String viewName : viewNames)
            {
                String viewAlias = rb.getString(viewName + "." + KEY_VIEW_ALIAS);
                String viewURI = rb.getString(viewName + "." + KEY_VIEW_URI);
                String viewClassName = rb.getString(viewName + "." + KEY_VIEW_CLASS);
                Class<?> configClass = Class.forName(viewClassName);
                Object configObject = configClass.newInstance();
                
                // check to ensure subclass of AbstractView
                if (!(configObject instanceof AbstractView))
                {
                    throw new ExceptionInInitializerError(configClass.getName()
                            + " is not an instance of " + AbstractView.class);
                }
                
                // check for a valid URI
                try
                {
                    new URI(viewURI);
                }
                catch (URISyntaxException e)
                {
                    throw new ExceptionInInitializerError("URI reference for "
                            + configClass.getName() + " has an invalid syntax.");
                }
                
                Class<AbstractView> viewClass = (Class<AbstractView>) configClass;
                if (aliasViewMap.containsKey(viewAlias))
                {
                    throw new ExceptionInInitializerError("Duplicate view reference " + viewAlias
                            + " in file " + VIEWS_PROPERTY_FILE + ".properties");
                }
                if (uriViewMap.containsKey(viewURI))
                {
                    throw new ExceptionInInitializerError("Duplicate view reference " + viewURI
                            + " in file " + VIEWS_PROPERTY_FILE + ".properties");
                }
                aliasViewMap.put(viewAlias, viewClass);
                log.debug("Mapped alias '" + viewAlias + "' to class " + viewClass);
                uriViewMap.put(viewURI, viewClass);
                log.debug("Mapped URI '" + viewURI + "' to class " + viewClass);
                
                // see if this view 'accepts'
                try
                {
                    String acceptsValue = rb.getString(viewName + "." + KEY_VIEW_ACCEPTS);
                    if (acceptsValue != null && acceptsValue.trim().equalsIgnoreCase("true"))
                    {
                        accepts.add(viewURI);
                    }
                }
                catch (MissingResourceException e)
                {
                    // ingore--no accepts setting in configuration
                }
                
                // see if this view 'provides'
                try
                {
                    String providesValue = rb.getString(viewName + "." + KEY_VIEW_PROVIDES);
                    if (providesValue != null && providesValue.trim().equalsIgnoreCase("true"))
                    {
                        provides.add(viewURI);
                    }
                }
                catch (MissingResourceException e)
                {
                    // ingore--no provides setting in configuration
                }
            }
        }
        catch (Exception e)
        {
            log.error(e);
            throw new ExceptionInInitializerError(e);
        }
        
    }

}
