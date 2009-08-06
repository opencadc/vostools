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

/**
 * Primary interface for applications. This interface is used by various ApplicationContainer
 * implementations to interact with the application.
 *
 * @version 1.0
 * @author Patrick Dowler
 */
public interface Application
{
	/**
	 * Request to quit the application. The various framework classes 
	 * (ApplicationFrame, AppletFrame, BrowserApplet) call this method
	 * if a user action indicates they are done. This gives the application 
	 * a chance to get confirmation, save files, and release resources 
	 * before quiting.
	 *
	 * @return true if the application agrees to quit
	 */
	public boolean quit();

	/**
	 * The container attaches itself using this method.
	 */
	public void setApplicationContainer(ApplicationContainer container);
}
