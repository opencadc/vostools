// Created on 14-Jul-2005

package ca.onfire.ak;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Simple WindowAdaptor implementation for use with ApplicationFrame. 
 * This class does some simple things in response to events. For 
 * windowClosing, it calls ApplicationFrame.quit(); for windowClosed(), 
 * it calls System.exit(0).
 *
 * @version 1.0
 * @author Patrick Dowler
 */
public class ApplicationWindowAdapter extends WindowAdapter
{
	private ApplicationFrame af;
	
	public ApplicationWindowAdapter(ApplicationFrame af)
	{
		super();
		this.af = af;
    }
	
  	public void windowClosing(WindowEvent e)
	{
  		af.quit();
    }
	
  	public void windowClosed(WindowEvent e)
	{
	  	System.exit(0);
	}
}
