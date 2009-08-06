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

// Created on 20-Jul-2005

package ca.nrc.cadc.dlm.client;

import ca.onfire.ak.ApplicationFrame;
import java.util.Arrays;
import java.util.List;

/**
 * TODO
 *
 * @version $Version$
 * @author pdowler
 */
public class Main
{
    public static void main(String[] args)
    {
        try
        {
            ArgumentMap am = new ArgumentMap(args);
            
            String uriStr = fixNull(am.getValue("uris"));
            String fragment = fixNull(am.getValue("fragment"));
            
            String[] uris = uriStr.split(",");
            CoreUI ui = new CoreUI();
            ui.add(uris, fragment);
            
            ApplicationFrame frame  = new ApplicationFrame(Constants.name, ui);
            frame.getContentPane().add(ui);
            frame.setVisible(true);
            
            ui.start();
        }
        catch(Throwable oops) 
        {
            oops.printStackTrace();
        }
    }
    // convert string 'null' and empty string to a null, trim() and return
    private static String fixNull(String s)
    {
        if ( "null".equals(s) )
            return null;
        s = s.trim();
        if (s.length() == 0)
            return null;
        return s;
    }
}
