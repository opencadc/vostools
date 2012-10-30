/*
 * Copyright (c) 2000 by Patrick Dowler.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the
 *
 * Free Software Foundation, Inc.
 * 59 Temple Place - Suite 330
 * Boston, MA  02111-1307, USA.
 *
 * http://www.gnu.org
 */

package ca.onfire.ak;

import javax.swing.*;
import java.io.IOException;
import java.net.URL;


/**
 * Miscellaneous static methods.
 *
 * @version 1.0
 * @author Patrick Dowler
 */
public class Toolkit
{
	/**
	 * Get the base URL for the specified class. If the class is in 
	 * the same classpath entry or jar file as other resources, 
	 * then the returned value can be used to construct URLs to 
	 * other resources.
	 *
	 * @return base URL string, not including the name of the arg class
	 */
	public static String getBaseResourceURL(Class c)
	{
		String s = c.getName() + ".class";
		URL u = c.getClassLoader().getResource(s);
		if (u != null)
		{
			String us = u.toString();
			return us.substring(0, us.length() - s.length());
		}
		return null;
	}
    
    public static int toInt(String s, int def)
    {
        try { return Integer.parseInt(s); }
        catch(NumberFormatException ignore) { }
        return def;
    }
    
    public static void doWindowGeometry(JFrame frame, ApplicationConfig config)
    {
        String s;
    	try
    	{
      		if ( config.setSection("geometry") )
      		{
      			int w = Toolkit.toInt(config.getValue("width"), 300);
				int h = Toolkit.toInt(config.getValue("height"), 300);
				int x = Toolkit.toInt(config.getValue("xpos"), 100);
				int y = Toolkit.toInt(config.getValue("ypos"), 100);
      			frame.setSize(w,h);
      			frame.setLocation(x,y);
      		}
      		else
      		{
      			frame.setSize(300,300);
      			frame.setLocation(100,100);
      		}
      	}
      	catch (IOException ignore) { }
    }
}

