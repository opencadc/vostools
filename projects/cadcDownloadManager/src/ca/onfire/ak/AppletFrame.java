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

import java.applet.Applet;
import java.applet.AppletContext;
import javax.swing.JFrame;
import java.io.IOException;

/**
 * An AppletFrame is a JFrame in to which one can embed an
 * Application running from an Applet. The ApplicationConfig
 * object for applets reads from a resource found in the
 * classpath, but the configuration is never saved so changes
 * are lost.
 *
 * @version 1.0
 * @author Patrick Dowler
 */
public class AppletFrame extends JFrame implements ApplicationContainer
{
	Application app;
	Applet applet;
	ApplicationConfig config;

	/**
	 * Constructor. The name argument is used in the title bar
	 * if the frame (window) and to find the application config
	 * object (file or resource).
	 */
	public AppletFrame(String name, Application app, Applet applet)
	{
		super(name);
		this.app = app;
		this.applet = applet;
		
		config = new ApplicationConfig(app.getClass(), name);
		readConfig();
		app.setApplicationContainer(this);
		
		this.setDefaultCloseOperation( JFrame.DO_NOTHING_ON_CLOSE );
		this.addWindowListener( new AppletWindowAdapter(this) );
	}

	public AppletContext getAppletContext() { return applet.getAppletContext(); }
	
	public ApplicationConfig getConfig() { return config; }
	
	public void quit()
	{
		if ( !app.quit() )
			return;
		//writeConfig();
		dispose();
		//applet.destroy()
	}

	protected void readConfig()
	{
    	Toolkit.doWindowGeometry(this, config);
    }
}

// end of AppletFrame.java

