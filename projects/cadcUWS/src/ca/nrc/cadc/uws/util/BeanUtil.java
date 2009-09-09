/******************************************************************************
 *
 *  Copyright (C) 2009                          Copyright (C) 2009
 *  National Research Council           Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6                     Ottawa, Canada, K1A 0R6
 *  All rights reserved                         Tous droits reserves
 *
 *  NRC disclaims any warranties,       Le CNRC denie toute garantie
 *  expressed, implied, or statu-       enoncee, implicite ou legale,
 *  tory, of any kind with respect      de quelque nature que se soit,
 *  to the software, including          concernant le logiciel, y com-
 *  without limitation any war-         pris sans restriction toute
 *  ranty of merchantability or         garantie de valeur marchande
 *  fitness for a particular pur-       ou de pertinence pour un usage
 *  pose.  NRC shall not be liable      particulier.  Le CNRC ne
 *  in any event for any damages,       pourra en aucun cas etre tenu
 *  whether direct or indirect,         responsable de tout dommage,
 *  special or general, consequen-      direct ou indirect, particul-
 *  tial or incidental, arising         ier ou general, accessoire ou
 *  from the use of the software.       fortuit, resultant de l'utili-
 *                                                              sation du logiciel.
 *
 *
 *  This file is part of cadcUWS.
 *
 *  cadcUWS is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  cadcUWS is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with cadcUWS.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/
package ca.nrc.cadc.uws.util;

import ca.nrc.cadc.uws.InvalidServiceException;
import org.apache.log4j.Logger;


public class BeanUtil
{
    private static final Logger LOGGER = Logger.getLogger(BeanUtil.class);
    
    public final static String UWS_EXECUTOR_SERVICE =
            "ca.nrc.cadc.uws.JobExecutor";
    public final static String UWS_JOB_MANAGER_SERVICE =
            "ca.nrc.cadc.uws.JobManager";
    public final static String UWS_PERSISTENCE =
            "ca.nrc.cadc.uws.JobPersistence";
    public final static String UWS_RUNNER = "ca.nrc.cadc.uws.JobRunner";

    
    private String className;


    /**
     * Constructor for this Bean Util.
     *
     * @param className     The name of the Class to create.
     */
    public BeanUtil(final String className)
    {
        this.className = className;
    }


    /**
     * Create the actual Object associated with this BeanUtil's class.
     * @return      The Object instantiated.
     */
    public Object createBean()
    {
        try
        {
            return Class.forName(getClassName()).newInstance();
        }
        catch (ClassNotFoundException e)
        {
            LOGGER.error("No such bean >> " + getClassName(), e);
            throw new InvalidServiceException("No such bean >> "
                                              + getClassName(), e);
        }
        catch (IllegalAccessException e)
        {
            LOGGER.error("Class or Constructor is inaccessible for "
                         + "bean  >> " + getClassName(), e);
            throw new InvalidServiceException("Class or Constructor is "
                                              + "inaccessible for bean "
                                              + ">> " + getClassName(), e);
        }
        catch (InstantiationException e)
        {
            LOGGER.error("Cannot create bean instance >> "
                         + getClassName(), e);
            throw new InvalidServiceException("Cannot create bean instance >> "
                                              + getClassName(), e);
        }
    }


    public String getClassName()
    {
        return className;
    }
}
