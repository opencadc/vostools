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

import java.applet.AppletContext;
import javax.swing.JFrame;
import java.awt.Rectangle;
import java.io.IOException;

/**
 * A frame (window with WM decorations) into which one can
 * embed an Application.
 *
 * @see Application
 * @see ApplicationConfig
 * @see AbstractApplication
 *
 * @version 1.0
 * @author Patrick Dowler
 */
public class ApplicationFrame extends JFrame implements ApplicationContainer
{
  	private ApplicationConfig config;
  	private Application app;
  	private int exit_code;

	public ApplicationFrame(String name, Application app)
	{
		this(name,app,true);
	}
	public ApplicationFrame(String name, Application app, boolean defConfig)
	{
		super(name);
		this.app = app;
		
		config = new ApplicationConfig(app.getClass(), name);
		readConfig();
		app.setApplicationContainer(this);
		
		this.setDefaultCloseOperation( JFrame.DO_NOTHING_ON_CLOSE );
		this.addWindowListener( new ApplicationWindowAdapter(this) );
    }

	public void quit()
	{
		if ( !app.quit() )
			return;
		writeConfig();
		dispose(); // destroy window, terminate event thread
	}

	public AppletContext getAppletContext() { return null; }
	
	public ApplicationConfig getConfig() { return config; }
	
	protected void readConfig()
	{
    	Toolkit.doWindowGeometry(this, config);
    }

	private String getConfigValue(String key, String def)
		throws IOException
	{
		String ret = def;
		String s = config.getValue(key);
      	if (s != null)
      	{
      		ret = s.trim();
      	}
      	return ret;
	}

	protected void writeConfig()
    {
    	if (config == null )
    		return;
    	try
		{
	  		config.setSection("geometry",true);
			Rectangle r = this.getBounds();
	  		config.putValue("width", Integer.toString(r.width));
	  		config.putValue("height", Integer.toString(r.height));
	  		config.putValue("xpos", Integer.toString(r.x));
	  		config.putValue("ypos", Integer.toString(r.y));
	  		config.writeConfig();
	  		exit_code = 0;
		}
      	catch (IOException ex) { ex.printStackTrace(); } 
    }
}

