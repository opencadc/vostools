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

import java.io.*;
import java.net.URL;
import java.security.AccessControlException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 * <p>
 * Simple configuration object reader/write with lookup capabilities.
 * </p>
 * <p>
 * ApplicationConfig reads a config file and stores the key,value
 * mappings for query by the application. The application can
 * request that the current state of the map be written to
 * the config file at any time, although this typically occurs
 * upon exit and is handled by the ApplicationFrame class.
 * AppletFrame and BrowserApplet can read configuration information
 * (from a resource found in the search path: the default
 * configuration file should be included in jar file) but they
 * never write it back again.
 * </p>
 *
 * <p>
 * The file is located using the <user.home> system property,
 * a <em>base</em> config directory name (onfire by default),
 * and a config file <em>extension</em> (.conf by default).
 * In order of priority, the following locations are checked
 * to find the configuration directory:
 * </p>
 *
 * <p><ul>
 * <li> [user.home]/.[base]
 * <li> [user.home]/[base]
 * <li> [user.dir]
 * </ul></p>
 *
 * <p>
 * The above list is only searched for places that the config file could
 * be stored on the system. Thus, if the first location could be
 * used (i.e. if the directory [user.home]/.[base] exists or can be
 * created) then that is the location of the config file. The fact
 * that the file itself doesn't exist is taken to mean that the
 * application is running for the first time.
 * </p>
 *
 * <p>
 * The configuration file is called [appname][extension]. If it is
 * not found in the configuration directory, a default configuration
 * is searched for as a resource named [appname][extension]. This allows
 * an application to store its default configuration file along with
 * the classes (ie. in the JAR file). In application mode, a changed
 * configuration is written to one of the above locations and not back
 * into the default resource. Untrusted applets (those that cause
 * a security exception to be throw when they try to access the file
 * system) embedded in AppletFrame or BrowserApplet always read their
 * configuration from the resource location and do not write it. Trusted
 * applets may save their configuration as described above.
 * </p>
 */
public class ApplicationConfig
{
	protected Map toc = new HashMap();
	protected Map current = null;
	protected boolean dirty = false;
	protected File config_file;
	protected String newline = System.getProperty("line.separator");
	protected String configBase = "onfire";
	protected String configExt = ".conf";
	private boolean fileAccess = true;

	private Class appClass;

  	/**
   	 * Constructor. Opens the application config file,
     * creating it if necessary. The default values for
     * the base and extension are used.
     */
  	public ApplicationConfig(Class appClass, String appname)
    {
  		this.appClass = appClass;
    	initFile( appname, findConfigDir() );
    }

  	/**
   	 * Constructor. Opens the application config file,
     * creating it if necessary. The default values for
     * the base and extension are used.
     */
  	public ApplicationConfig(Class appClass, String appname, String base)
    {
  		this.appClass = appClass;
    	this.configBase = base;
    	initFile( appname, findConfigDir() );
    }
  	/**
   	 * Constructor. Opens the application config file,
     * creating it if necessary. The default values for
     * the base and extension are used.
     */
  	public ApplicationConfig(Class appClass, String appname, String base,
                               String extension)
    {
  		this.appClass = appClass;
  		this.configBase = base;
  		this.configExt = extension;
    	initFile( appname, findConfigDir() );
    }

	private String findConfigDir()
	{
		try
		{
			String uh = System.getProperty("user.home");

			// check for .configBase
			File confdir = new File( uh, "."+configBase );
			if ( confdir.exists() )
				return confdir.toString();
			// try to create .configBase
			if ( confdir.mkdir() )
				return confdir.toString();

			// we failed to find/create .configBase, so
			// try configBase (without the dot) instead
			confdir = new File( uh, configBase );
			if ( confdir.exists() )
				return uh + File.separator + configBase;
			if ( confdir.mkdir() )
				return uh + File.separator + configBase;

			return System.getProperty("user.dir");
		}
		catch (AccessControlException ex)
		{
			//must be in an untrusted applet
			fileAccess = false;
		}
		return null;
	}
    private void initFile(String appname, String configdir)
    {
		String cfname = appname + configExt;
		if (configdir != null)
			cfname = configdir + File.separator + cfname;
      	config_file = new File(cfname);
    }

	/**
     * Make a section the current section. This method does not create
     * a new section by default.
     *
     * @return true if the section exists and is now current
     */
	public boolean setSection(String section)
    	throws IOException
    {
    	readConfig();
    	return setSection(section,false);
    }

	/**
     * Make a section the current section, creating it if necessary.
     *
     * @return true if the section exists (even if just created),
     */
	public boolean setSection(String section, boolean create)
    	throws IOException
    {
    	readConfig();

		Map tmp = (Map) toc.get(section);
		if ( tmp != null )
		{
	  		current = tmp;
	  		return true;
		}
      	if ( !create )
			return false;

		current = new HashMap();
      	toc.put(section, current );
      	return true;
    }

  	/**
     * Retrieve a value for a key in the current section.
     *
     * @return the value associated with the key, or null if it
     *  is not found
     */
  	public String getValue(String key)
    	throws IOException
    {
    	readConfig();
    	if (current == null)
    		return null;
    	return (String) current.get(key);
    }

	/**
     * Change sections and retrieve a value for a key.
     *
     * @return the value associated with the key, or null if it
     *  is not found
     */
	public String getValue(String section, String key)
    	throws IOException
    {
	    readConfig();
    	if ( setSection(section) )
			return (String) current.get(key);
    	return null;
    }

  /**
    * Put a key=value pair in the current section. If there is no
    * current section (setSection was never called) then the key
    * is placed in a section called "default", which is created if
    * necessary. This will overwrite the old value if it exists.
    */
  	public void putValue(String key, String value)
    	throws IOException
    {
      	readConfig();
      	// if there is no section, put the entry in the default section
      	if (current == null)
			setSection("default",true);
      	current.remove(key);
      	current.put(key,value);
      	dirty = true;
    }

  /**
    * Put a key=value pair in the requested section.
    * This will overwrite the old value if it exists. The current
    * section changes to the requested section as a side effect. A
    * new section is created if it doesn't exist. This is
    * equivalent to using:
    *
    * <pre>
    * config.setSection(section,true);
    * config.putValue(key,value);
    * </pre>
    */
  	public void putValue(String section, String key, String value)
    	throws IOException
    {
      	readConfig();
      	// switch or create
      	boolean ok = setSection(section,true);
      	putValue(key,value);
      	dirty = true;
    }

	/**
	 * @return true if the settings have been changed since read
	 */
  	public boolean isDirty()
    {
    	return dirty;
    }

	/**
	 * Write the configuration to the configuration file. No IO
	 * actually occurs if the application cannot write files (because
	 * it is an untrusted applet) or if the configuration settings
	 * are not dirty (see isDirty).
	 */
  	public void writeConfig()
    	throws IOException
	{
    		if (!fileAccess)
    			return;
      		readConfig();
      		if ( !dirty )
			return;

		try
		{
		      	Writer out = new BufferedWriter( new FileWriter(config_file) );

	      		Iterator i = toc.entrySet().iterator();
	      		while ( i.hasNext() )
	      		{
    		  		Map.Entry me = (Map.Entry) i.next(); // next section
    		  		String sec = (String) me.getKey();
    		  		Map props = (Map) me.getValue();
    		  		out.write("[" + sec + "]" + newline);
    		  		Iterator p = props.entrySet().iterator();
    		  		while ( p.hasNext() )
    		    	{
    		  		    Map.Entry me2 = (Map.Entry) p.next();
    		      	    out.write( me2.getKey() + "=" + me2.getValue() + newline );
                    }
	      		}
	      		out.close();
		}
		catch(AccessControlException ignore) { /*applet*/ }
      	dirty = false;
	}

  	// lazy read
  	protected boolean hasBeenRead = false;

  	/**
  	 * Actually read the configuration from the config file or
  	 * a resource. This method is implemented in a "lazy" fashion:
  	 * no IO occurs until one of the access methods is called.
  	 * This means the file/resource are not found, opened, and read
  	 * until some value(s) are actually needed.
  	 */
  	protected void readConfig()
    		throws IOException
	{
      	// only read the file once
      	if ( hasBeenRead )
      		return;

      	// read
      	hasBeenRead = true;
      	if ( fileAccess && config_file.exists() )
      	{
	      	readConfig(new LineNumberReader( new FileReader( config_file ) ));
	      	dirty = false;
      	}
      	else
      	{
      		// check resource and read defaults
      		String cname = config_file.getName();
     		URL def = appClass.getClassLoader().getResource(cname);
            if (def != null)
            {
                System.out.println("readConfig: " + def);
                readConfig(new LineNumberReader(new InputStreamReader(def.openStream())));
                dirty = true;
            }
      	}
      	current = null;
    }

    private void readConfig(LineNumberReader in)
    	throws IOException
    {
    	boolean eof = false;
		while ( !eof )
	    {
	    	String line = in.readLine();
	      	if ( line == null )
				eof = true;
	      	else
			{
		  		line = line.trim();
		  		if ( line.charAt(0) == '[' )
		    	{
		    		// section heading
		    		String s = line.substring(1, line.indexOf(']'));
		      		setSection(s,true);
		    	}
		  		else
		    	{
		      		int i = line.indexOf('=');
		      		String lhs = line.substring(0,i);
		      		String rhs = line.substring(i+1,line.length());
		      		putValue(lhs,rhs);
		    	}
			}
	   	}
	   	in.close();
    }
}
