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

import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * Simple command-line argument utility that takes all arguments of the
 * form --key=value and stores them in a map of key->value. As a shortcut,
 * --key is equivalent to --key=<em>true</em>, where <em>true</em> is the
 * String representation of Boolean.TRUE. Arguments that start with a single
 * dash (-) are always mapped to Boolean.TRUE, whether or not they contain
 * an = sign.
 * </p>
 * <p>
 * As an added bonus/complexity, the character sequence %% can be used to delimit
 * values that would otherwise be split up by the invoking shell.
 *
 * @version $Revision: 325 $
 * @author $Author: pdowler $
 */
public class ArgumentMap
{
	private Map map;
	private boolean verbose;

	public ArgumentMap(String[] args) { this(args,false); }

	public ArgumentMap(String[] args, boolean verbose)
	{
		this.map = new HashMap();
		this.verbose = verbose;
		for (int i=0; i<args.length; i++)
		{
			imsg( args[i] );
			String key = null;
			String str = null;
			Object value = null;
			if ( args[i].startsWith("--") )
			{
				// put generic arg in argmap
				try
				{
					int j = args[i].indexOf('=');
					if (j <= 0)
					{
						// map to true
						key = args[i].substring(2,args[i].length());
						value = Boolean.TRUE;
					}
					else
					{
						// map to string value
						key = args[i].substring(2,j);
						str = args[i].substring(j+1,args[i].length());

						// special %% stuff %% delimiters
						if ( str.startsWith("%%") )
						{
							// look for the next %% on the command-line
							str = str.substring(2,str.length());
							if ( str.endsWith("%%") )
							{
								value = str.substring(0,str.length()-2);
							}
							else
							{
								StringBuffer sb = new StringBuffer(str);
								boolean done = false;
								while ( i+1 < args.length && !done )
								{
									i++;
									imsg( args[i] );
									if ( args[i].endsWith("%%") )
									{
										str = args[i].substring(0, args[i].length()-2);
										done = true;
									}
									else
										str = args[i];
									sb.append(" " + str);
								}
								value = sb.toString();
							}
						}
						else
							value = str;
					}
				}
				catch(Exception ignorable)
				{
					imsg(" skipping: " + ignorable.toString());
				}
			}
			else if ( args[i].startsWith("-") )
			{
				try
				{
					key = args[i].substring(1,args[i].length());
					value = Boolean.TRUE;
				}
				catch(Exception ignorable)
				{
					imsg(" skipping: " + ignorable.toString());
				}
			}

			if ( key != null && value != null )
			{
				imsg("adding " + key + "->" + value);
				Object old_value = map.put(key, value);
				if ( old_value != null )
					imsg(" (old mapping removed: " + key + " : " + old_value + ")");
			}
			imsg3();
		}
	}

	public String getValue(String key)
	{
		Object obj = map.get(key);
		if ( obj != null)
			return obj.toString();
		return null;
	}

	public boolean isSet(String key) { return map.containsKey(key); }

    public Set keySet() { return map.keySet(); }
    
	private void imsg(String s)
	{
		if (verbose)
			System.out.println("[ArgumentMap] " + s);
	}

	private void imsg1(String s)
	{
		if (verbose)
			System.out.print("[ArgumentMap] " + s);
	}
	private void imsg2(String s)
	{
		if (verbose)
			System.out.print(s);
	}
	private void imsg3()
	{
		if (verbose)
			System.out.println();
	}


}

// end of ArgumentMap.java

