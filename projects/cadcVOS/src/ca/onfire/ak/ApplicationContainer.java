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

import javax.swing.*;
import java.applet.AppletContext;
import java.awt.*;


/**
 * Interface for an Application to interact with its parent (Japplet or JFrame) and environment.
 *
 * @version 1.0
 * @author Patrick Dowler
 */
public interface ApplicationContainer
{
	/**
	 * Applications should call this to initiate terminating execution. The
	 * container will call Application.quit() to request permission to actually
	 * terminate before doing so; termination proceeds if that call returns true.
	 */
	public void quit();
	
	/**
	 * The application accesses the configuration with this method.
	 */
	public ApplicationConfig getConfig();
	
	/**
	 * Access the applet's context. This returns null if this
	 * is not running as an applet.
	 */
	public AppletContext getAppletContext();
	
	/**
	 * Standard method common to the JApplet and JFrame classes.
	 */
	public Container getContentPane();
	
	/**
	 * Standard method common to the JApplet and JFrame classes.
	 */
	public Component getGlassPane();
	
	/**
	 * Standard method common to the JApplet and JFrame classes.
	 */
	public JMenuBar getJMenuBar();
	public void setJMenuBar(JMenuBar m);
	
	/**
	 * Standard method common to the JApplet and JFrame classes.
	 */
	public JLayeredPane getLayeredPane();
	
	/**
	 * Standard method common to the JApplet and JFrame classes.
	 */
	public JRootPane getRootPane();
}

// end of ApplicationContainer.UploadApplicationTest

