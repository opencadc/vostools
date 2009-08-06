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

package ca.nrc.cadc.dlm.client;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JToolBar;

/**
 * TODO.
 *
 * @author pdowler
 */
public class Util
{
    private Util() { }
    
    public static Frame findParentFrame(Component parent)
    {
        while (true)
        {
            if (parent == null)
                return null;
            if (parent instanceof Frame)
                return (Frame) parent;
            parent = parent.getParent();
        }
    }
    
    public static void setPositionRelativeToParent(Window w, Component parent, int hOffset, int vOffset)
    {
        try
        {
            Point p = parent.getLocationOnScreen();
            int x = (int) p.getX() + hOffset;
            int y = (int) p.getY() + vOffset;
            w.setLocation(x, y);
            w.pack();
        } 
        catch (Throwable t) { }
        
    }
    
    public static void recursiveSetBackground(Component comp, Color color)
    {
        if (comp instanceof JToolBar || comp instanceof JButton)
            return;
        comp.setBackground(color);
        if (comp instanceof Container)
        {
            Container cc = (Container) comp;
            for (int i = 0; i < cc.getComponentCount(); i++)
                recursiveSetBackground(cc.getComponent(i), color);
        }
    }
}
