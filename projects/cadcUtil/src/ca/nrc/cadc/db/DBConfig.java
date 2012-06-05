/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2009.                            (c) 2009.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 4 $
*
************************************************************************
*/

package ca.nrc.cadc.db;

import ca.nrc.cadc.util.StringUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

/**
 * Reads username/password pairs and (optionally) JDBC driver and connection URL data
 * from the ${user.home}/.dbrc file. The format of this file is:
 * </p></pre>
 * $server $database $username $password [$driver $url]
 * </pre>
 * To remove redundancy, one can have one line to list the driver and url for all
 * connections to a server with a line like:
 * </p></pre>
 * $server * $username $password $driver $url
 * </pre>
 *
 * @author pdowler
 */
public class DBConfig implements Serializable
{
    private final static long serialVersionUID = 201103241000L;

    // Used to indicate a comment in a line.
    public final static String COMMENT_INDICATOR = "#";

    private List<ConnectionConfig> lookup = new ArrayList<ConnectionConfig>();

	/**
	 * Create a DBConfig object from the ${user.home}/.dbrc file.
     *
     * @throws FileNotFoundException if the .dbrc file cannot be found
     * @throws IOException if the .dbrc file cannot be read
	 */
	public DBConfig()
		throws FileNotFoundException, IOException
	{
		this(new File(System.getProperty("user.home") + "/.dbrc"));
	}

	/**
	 * Create a DBConfig object from the specified file.
     *
     * @param dbrc
     * @throws FileNotFoundException if the .dbrc file cannot be found
     * @throws IOException if the .dbrc file cannot be read
	 */
	public DBConfig(File dbrc)
		throws FileNotFoundException, IOException
	{
		final LineNumberReader r = new LineNumberReader(new FileReader(dbrc));

		boolean eof = false;
		int n = 0;
		while ( !eof )
		{
			final String s = r.readLine();
			eof = (s == null);

            if (!eof)
			{
                // Skip over blank lines.
                if ("".equals(s.trim()) ||
                    StringUtil.startsWith(s, COMMENT_INDICATOR))
                {
                    continue;
                }

                n++;
				final String srv;
				final String db;
				final String usr;
				final String pwd;
				final String jdbc_driver;
				final String jdbc_url;

                // break on spaces
                final StringTokenizer st = new StringTokenizer(s);

                if ( st.hasMoreTokens() )
					srv = st.nextToken();
				else
					throw new IOException("DBConfig: .dbrc syntax error on line " + n);
				if ( st.hasMoreTokens() )
					db = st.nextToken();
				else
					throw new IOException("DBConfig: .dbrc syntax error on line " + n);
				if ( st.hasMoreTokens() )
					usr = st.nextToken();
				else
					throw new IOException("DBConfig: .dbrc syntax error on line " + n);
				if ( st.hasMoreTokens() )
					pwd = st.nextToken();
				else
					throw new IOException("DBConfig: .dbrc syntax error on line " + n);

				// optional: jdbc_driver and jdbc_url
				if ( st.hasMoreTokens() )
                {
                    jdbc_driver = st.nextToken();
                }
                else
                {
                    jdbc_driver = null;
                }

                if ( st.hasMoreTokens() )
                {
                    jdbc_url = st.nextToken();
                }
                else
                {
                    jdbc_url = null;
                }

                // OK
				lookup.add( new ConnectionConfig(srv, db, usr, pwd, jdbc_driver, jdbc_url) );
			}
			// now we must populate the JDBC driver and connection URL parts since they are
			// not specified in every line of the .dbrc file
			setDrivers();
		}
	}

	/**
	 * Get the connection configuration for the specified server and database.
	 *
	 * @throws NoSuchElementException if the server and database are found, usually because they
	 *	are missing from the .dbrc file
	 *
	 * @return populated ConnectionConfig object
	 */
	public ConnectionConfig getConnectionConfig(String server, String database)
		throws NoSuchElementException
	{
		ConnectionConfig cc = find(server, database);
		if (cc != null)
			return cc;
		throw new NoSuchElementException("connecting to '" + server + "/" +
                                         database + "' not supported");
	}

    /**
     * TODO - This is very implementation specific, there must be a better way!
     * TODO - For now though this will need to do.  We need to know which
     * TODO - configuration to load as some applications talk to multiple
     * TODO - database servers.  A better way would be to classify the entries
     * TODO - in the .dbrc file.
     * TODO - 2006.05.29 jenkinsd
     *
     * Obtain whether the given server and database related to an entry that is
     * that of an IBM DB2 database.  This implementation checks to see if the
     * JDBC URL contains a db2 String, so it's dependant on the URL containing
     * the characters 'db2'.
     *
     * @param server
     * @param db
     * @return True if this URL is a db2 one, False otherwise.
     */
    public boolean isDB2(final String server, final String db)
    {
        return getJDBCURL(server, db).indexOf("db2") >= 0;
    }

    /**
     * TODO - This is very implementation specific, there must be a better way!
     * TODO - For now though this will need to do.  We need to know which
     * TODO - configuration to load as some applications talk to multiple
     * TODO - database servers.  A better way would be to classify the entries
     * TODO - in the .dbrc file.
     * TODO - 2006.05.29 jenkinsd
     *
     * Obtain whether the given server and database related to an entry that is
     * that of a Sybase database.  This implementation checks to see if the
     * JDBC URL contains a sybase String, so it's dependant on the URL containing
     * the characters 'sybase'.
     *
     * @param server
     * @param db
     * @return True if this URL is a sybase one, False otherwise.
     */
    public boolean isSybase(final String server, final String db)
    {
        return getJDBCURL(server, db).indexOf("sybase") >= 0;
    }

    /**
     * Obtain all the configurations.  This is useful for caching strategies
     * surrounding ORMs.
     * @return ConnectionConfig array.
     */
    public ConnectionConfig[] getConfigurations()
    {
        if (lookup == null)
        {
            return null;
        }

        final ConnectionConfig[] configs = new ConnectionConfig[lookup.size()];
        final Object[] objects = lookup.toArray();

        for (int i = 0; i < objects.length; i++)
        {
            configs[i] = (ConnectionConfig) objects[i];
        }

        return configs;
    }

    /**
     * Obtain the JDBC URL the RDBMS pool is using.
     * @param server
     * @param db
     * @return String URL according to the RDBMS (driver) implementation
     */
    private String getJDBCURL(final String server, final String db)
    {
        final ConnectionConfig cc = getConnectionConfig(server, db);

        if (cc == null)
        {
            return "";
        }
        else
        {
            return cc.getURL();
        }
    }

    private ConnectionConfig find(String s, String d)
	{
		Iterator<ConnectionConfig> i = lookup.iterator();
		while ( i.hasNext() )
		{
			ConnectionConfig cc = i.next();
			if ( cc.matches(s, d, false) ) // look for explicit match
				return cc;
		}
        i = lookup.iterator();
		while ( i.hasNext() )
		{
			ConnectionConfig cc = i.next();
			if ( cc.matches(s, d, true) ) // look for wildcard match
				return cc;
		}
		return null;
	}

	private void setDrivers()
	{
		ListIterator i = lookup.listIterator();
		while ( i.hasNext() )
		{
			ConnectionConfig cc = (ConnectionConfig) i.next();
			if ( cc.getDriver() == null )
			{
				ConnectionConfig cc2 = find(cc.getServer(), "*");
				if (cc2 == null)
					i.remove();
				else
				{
					cc.setDriver( cc2.getDriver() );
					cc.setURL( cc2.getURL() );
				}
			}
		}
	}
}
