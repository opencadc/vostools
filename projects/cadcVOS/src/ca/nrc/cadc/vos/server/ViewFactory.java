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

import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;

import ca.nrc.cadc.vos.View;

/**
 * The ViewFactory is responsible for loading the views from Views.properties and
 * for creating instances of views through method getView(String viewReference)
 * where viewReference can either be the alias or URI of a view that has been loaded
 * from configuration.
 * 
 * @author majorb
 *
 */
public class ViewFactory
{
    
    private static Logger log = Logger.getLogger(ViewFactory.class);
    
    private static final String VIEWS_PROPERTY_FILE = "Views";
    
    private static final String KEY_VIEW_LIST = "views";
    private static final String KEY_VIEW_ALIAS = "alias";
    private static final String KEY_VIEW_URI = "uri";
    private static final String KEY_VIEW_CLASS = "class";
    
    /**
     * Load the views upon class loading.
     */
    static
    {
        loadConfiguredViews();
    }
    
    // The map of configured view classes
    private static Map<String, Class<View>> viewMap;
    
    /**
     * Given a viewReference, return a new instance of the associated view or
     * null of no mapping exists for the viewReference.
     * 
     * @param viewReference  Either the alias or URI of a configured view.
     * If no class exists for the given reference, this method returns null.
     * @return A new instance of the view object.
     * @throws InstantiationException If the object could not be constructed.
     * @throws IllegalAccessException If a constructor could not be found.
     */
    public View getView(String viewReference) throws InstantiationException, IllegalAccessException
    {
        Class<View> viewClass = viewMap.get(viewReference);
        if (viewClass == null)
        {
            log.debug("No view found for reference: " + viewReference);
            return null;
        }
        log.debug("Returning new view of type " + viewClass + " for reference " + viewReference);
        return (View) viewClass.newInstance();
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
        
        viewMap = new HashMap<String, Class<View>>();
        
        try
        {
            String viewNamesString = rb.getString(KEY_VIEW_LIST);
            String[] viewNames = viewNamesString.split(" ");
            
            log.info("ViewFactory loading views: " + viewNamesString);
            
            for (String viewName : viewNames)
            {
                String viewAlias = rb.getString(viewName + "." + KEY_VIEW_ALIAS);
                String viewURI = rb.getString(viewName + "." + KEY_VIEW_URI);
                String viewClassName = rb.getString(viewName + "." + KEY_VIEW_CLASS);
                Class<?> configClass = Class.forName(viewClassName);
                Object configObject = configClass.newInstance();
                if (!(configObject instanceof AbstractView))
                {
                    throw new ExceptionInInitializerError(configClass
                            + " is not an instance of " + AbstractView.class);
                }
                Class<View> viewClass = (Class<View>) configClass;
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
                viewMap.put(viewAlias, viewClass);
                log.debug("Mapped alias " + viewAlias + " to class " + viewClass);
                viewMap.put(viewURI, viewClass);
                log.debug("Mapped URI " + viewURI + " to class " + viewClass);
            }
        }
        catch (Exception e)
        {
            log.error(e);
            throw new ExceptionInInitializerError(e);
        }
        
    }

}
