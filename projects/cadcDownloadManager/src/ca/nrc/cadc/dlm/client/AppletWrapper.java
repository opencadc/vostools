/*****************************************************************************
 *  
 *  Copyright (C) 2009				Copyright (C) 2009
 *  National Research Council		Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6			Ottawa, Canada, K1A 0R6
 *  All rights reserved				Tous droits reserves
 *  					
 *  NRC disclaims any warranties,	Le CNRC denie toute garantie
 *  expressed, implied, or statu-	enoncee, implicite ou legale,
 *  tory, of any kind with respect	de quelque nature que se soit,
 *  to the software, including		concernant le logiciel, y com-
 *  without limitation any war-		pris sans restriction toute
 *  ranty of merchantability or		garantie de valeur marchande
 *  fitness for a particular pur-	ou de pertinence pour un usage
 *  pose.  NRC shall not be liable	particulier.  Le CNRC ne
 *  in any event for any damages,	pourra en aucun cas etre tenu
 *  whether direct or indirect,		responsable de tout dommage,
 *  special or general, consequen-	direct ou indirect, particul-
 *  tial or incidental, arising		ier ou general, accessoire ou
 *  from the use of the software.	fortuit, resultant de l'utili-
 *  								sation du logiciel.
 *  
 *  
 *  This file is part of cadcDownloadManager.
 *  
 *  CadcDownloadManager is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  CadcDownloadManager is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with cadcDownloadManager.  If not, see <http://www.gnu.org/licenses/>.			
 *  
 *****************************************************************************/

// Created on 8-Aug-2005

package ca.nrc.cadc.dlm.client;

import ca.onfire.ak.BrowserApplet;

import javax.swing.JApplet;
import java.io.IOException;

/**
 * TODO
 *
 * @version $Version$
 * @author pdowler
 */
public class AppletWrapper extends JApplet
{
    private CoreUI ui;
	
    public void init()
    {
        try
        {        
            String uriStr = fixNull(getParameter("uris"));
            String fragment = fixNull(getParameter("fragment"));
            String[] uris = uriStr.split(",");
            
            this.ui = new CoreUI();
            ui.add(uris, fragment);
            
            BrowserApplet f = new BrowserApplet(Constants.name, ui, this);
            this.validate();
        }
        catch(Throwable oops)
        {
            try
            {
                // temporary
                oops.printStackTrace();
            }
            catch(Throwable ignore) { }
            finally
            {
                // terminate
                ui.stop();
            }
        }
    }
    
    public void start()
    {
        ui.start();
    }
    
    public void stop()
    {
        ui.stop();
        try { ui.getApplicationContainer().getConfig().writeConfig(); }
        catch(IOException ignore) { }
    }
    
    // convert string 'null' and empty string to a null, trim() and return
    private String fixNull(String s)
    {
        if (s == null)
            return null;
        if ( "null".equals(s) )
            return null;
        s = s.trim();
        if (s.length() == 0)
            return null;
        return s;
    }
}
