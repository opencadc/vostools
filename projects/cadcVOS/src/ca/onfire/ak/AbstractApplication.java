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
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;


/**
 * AbstractApplication is a base class for applications that can be added to an
 * ApplicationContainer (ApplicationFrame, AppletFrame, BrowserApplet). Subclasses
 * need to implement a constructor where they chose a top-level LayoutManager
 * and enable or disable double-buffering and they need to implement the makeUI()
 * method where they actually construct the UI
 *
 * @version 1.0 
 * @author Patrick Dowler
 */
public abstract class AbstractApplication extends JPanel implements Application
{
  	private ApplicationContainer ac;
  	private String baseResourceURL;

	public AbstractApplication()
	{
		super();
    }
    public AbstractApplication(boolean doubleBuffered)
    {
    	super(doubleBuffered);
    }
    public AbstractApplication(LayoutManager layout)
    {
    	super(layout);
    }
    public AbstractApplication(LayoutManager layout, boolean doubleBuffered)
    {
    	super(layout, doubleBuffered);
    }

 	/**
  	 * The GUI can be constructed using information from the
  	 * <code>getApplicationContainer()</code> method, which includes
  	 * the access to the AppletContext (if in applet mode) and 
  	 * possibly to an ApplicationConfig object.
  	 */
  	protected abstract void makeUI();
  	
    /**
     * The default method always returns true immediately.
     * 
     * @return true
     */
	public boolean quit() { return true; }

  	/**
  	 * Stores the container reference and then calls makeUI.
  	 */
	public void setApplicationContainer(ApplicationContainer ac) 
  	{
  		this.ac = ac;
  		makeUI();
  		ac.getContentPane().add(this);
  	}
	
  	/**
	 * @return the value supplied via setApplicationContainer()
	 */
	public ApplicationContainer getApplicationContainer() { return ac; }

	/**
	 * Retrieve an image resource. The supplied filename should be relative to the location
	 * of the application class (i.e. the subclass of AbstractApplication) without the path
	 * components that are part of the class name. Typically, one might put something like
	 * "images/MyIcon.png" in the same jar file as the application class(es); this could
	 * be subsequently loaded via <code>getImageIcon("images/MyIcon.png")</code>.
	 * 
	 * @return an ImageIcon holding the specified image
	 * @throws java.net.MalformedURLException
	 */
	public ImageIcon getImageIcon(Class c, String name)
		throws MalformedURLException
	{
		if (baseResourceURL == null)
			baseResourceURL = Toolkit.getBaseResourceURL(c);
		String str = baseResourceURL + name;
		return new ImageIcon( new URL(str) );
	}
	
	public String getTextResource(Class c, String name)
		throws MalformedURLException, IOException
	{
		String lineSep = System.getProperty("line.separator");
		if (baseResourceURL == null)
			baseResourceURL = Toolkit.getBaseResourceURL(c);
		String url = baseResourceURL + name;
		StringBuffer sb = new StringBuffer();
		BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(url).openStream()));
		String s = reader.readLine();
		while ( s != null )
			sb.append(s + lineSep);
		reader.close();
		return sb.toString();
	}

 	public void showInfoDialog(String msg)
	{
		JOptionPane.showMessageDialog(this,
	  		msg,
	  		"Information",
			JOptionPane.INFORMATION_MESSAGE);
	}
	public void showWarningDialog(String msg)
	{
		JOptionPane.showMessageDialog(this,
	  		msg,
	  		"Warning",
			JOptionPane.WARNING_MESSAGE);
	}
	public void showErrorDialog(String msg)
	{
		JOptionPane.showMessageDialog(this,
	  		msg,
	  		"Error",
			JOptionPane.ERROR_MESSAGE);
	}
	
	public boolean getConfirmation(String msg)
	{
		int i = JOptionPane.showConfirmDialog(this,
			msg,
			"Confirm",
			JOptionPane.OK_CANCEL_OPTION);
		return (i == JOptionPane.OK_OPTION);
	}
	
	public String getStringInput(String msg)
	{
		return JOptionPane.showInputDialog(this,
			msg,
			"Enter",
			JOptionPane.QUESTION_MESSAGE);
	}
	
	public String getStringInput(String msg, String title, String[] options)
	{
		return (String) JOptionPane.showInputDialog(this,
			msg, 
			title,
			JOptionPane.QUESTION_MESSAGE, 
			null, 
			options, 
			options[0]);
	}
}

// end of GUIApplication.UploadApplicationTest

