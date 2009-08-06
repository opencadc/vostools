/*
 * Copyright (c) 2000, 2005 by Patrick Dowler.
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
import java.awt.Component;
import java.awt.Container;

import javax.swing.JApplet;
import javax.swing.JLayeredPane;
import javax.swing.JMenuBar;
import javax.swing.JRootPane;

/**
 * Container for an Application that stays in the browser.
 * The BrowserApplet uses JApplet.getParameter() to find the
 * size of the applet in the web page and sets it's size to
 * that value. It looks for PARAMs with name="width" and
 * name="height". How this is actually accomplished in a
 * particular browser is highly variable.
 *
 * @version 1.0
 * @author Patrick Dowler
 */
public class BrowserApplet implements ApplicationContainer
{
	Application app;
	ApplicationConfig config;
	JApplet applet;
	
	public BrowserApplet(String name, Application app, JApplet applet)
	{
		super();
		this.app = app;
		this.applet = applet;

		readParameters();
		config = new ApplicationConfig(app.getClass(), name);
		readConfig();
		app.setApplicationContainer(this);
	}

	public void setVisible(boolean v)
	{
		System.out.println("BrowserApplet.setVisible()");
		if (v)
			applet.validate();
	}
	public AppletContext getAppletContext() { return applet.getAppletContext(); }
	
	public ApplicationConfig getConfig() { return config; }
	
	public void quit()
	{
		throw new UnsupportedOperationException("'quit' not allowed in a BrowserApplet");
	}
	
	private void readParameters()
	{
		String s;
		s = applet.getParameter("width");
		int w = Toolkit.toInt(s, 300);
		s = applet.getParameter("height");
		int h = Toolkit.toInt(s, 300);
		applet.setSize(w,h);
	}

	private void readConfig() { }

	/**
	 * @see ca.onfire.ak.ApplicationContainer#getContentPane()
	 */
	public Container getContentPane()
	{
		return applet.getContentPane();
	}

	/**
	 * @see ca.onfire.ak.ApplicationContainer#getGlassPane()
	 */
	public Component getGlassPane()
	{
		return applet.getGlassPane();
	}

	/**
	 * @see ca.onfire.ak.ApplicationContainer#getJMenuBar()
	 */
	public JMenuBar getJMenuBar()
	{
		return applet.getJMenuBar();
	}
	public void setJMenuBar(JMenuBar m) { applet.setJMenuBar(m); }
	
	/**
	 * @see ca.onfire.ak.ApplicationContainer#getLayeredPane()
	 */
	public JLayeredPane getLayeredPane()
	{
		return applet.getLayeredPane();
	}

	/**
	 * @see ca.onfire.ak.ApplicationContainer#getRootPane()
	 */
	public JRootPane getRootPane()
	{
		return applet.getRootPane();
	}
}

// end of BrowserApplet.java

