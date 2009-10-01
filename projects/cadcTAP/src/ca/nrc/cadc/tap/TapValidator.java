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

package ca.nrc.cadc.tap;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.List;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.uws.Parameter;

public class TapValidator extends Validator
{
	private boolean requestIsQuery = false;
	
	private String lang;
	
	public void validate( List<Parameter> paramList )
	{
		if ( paramList == null )
			//  TODO:  Is this what we want?
			throw new IllegalStateException( "Missing TAP parameter list" );
		
		if ( paramList.size() == 0 )
			//  TODO:  Is this what we want?
			throw new IllegalStateException( "Empty TAP parameter list" );
		
	    if ( !listContains( paramList, TapParams.REQUEST.toString() ) )
			throw new IllegalStateException( "Missing REQUEST param" );
		
		for ( Parameter param : paramList )
		{
			String name = param.getName();
			if ( name.equalsIgnoreCase( TapParams.REQUEST.toString() ) )
			{
				String value = param.getValue();
				if ( value == null)
					throw new IllegalStateException( "Missing REQUEST value" );
				if ( value.trim().length() == 0 )
					throw new IllegalStateException( "Empty REQUEST value" );
				if ( !value.equals( "doQuery" ) &&
					 !value.equals( "getCapabilities" ) &&
					 !value.equals( "getAvailability" ) &&
					 !value.equals( "getTableMetadata" ) )
					throw new IllegalStateException( "Unknown REQUEST value: "+value );
				if ( value.equals("doQuery") )
				{
					requestIsQuery = true;
				    if ( !listContains( paramList, TapParams.LANG.toString() ) )
						throw new IllegalStateException( "REQUEST=doQuery not acompanied by LANG param" );
				}
			}
			else if ( name.equalsIgnoreCase(TapParams.VERSION.toString() ) )
			{
				String value = param.getValue();
				if ( value == null )
					throw new IllegalStateException( "Missing VERSION value" );
				if ( value==null || value.trim().length()==0 )
					throw new IllegalStateException( "Empty VERSION value" );
				if ( !value.equals("1.0") )
					throw new IllegalStateException( "Unsupported TAP version: "+value );
			}
			else if ( name.equalsIgnoreCase(TapParams.LANG.toString() ) )
			{
				String value = param.getValue();
				if ( value == null )
					throw new IllegalStateException( "Missing LANG value" );
				if ( value==null || value.trim().length()==0 )
					throw new IllegalStateException( "Empty LANG value" );
				if ( !value.startsWith("ADQL") )
					throw new IllegalStateException( "Unsupported LANG value: "+value );
				lang = value;
			}
			else if ( name.equalsIgnoreCase(TapParams.QUERY.toString() ) )
			{
				//  Do nothing.
				//  This parameter is only relevant to ADQL so it is validated
				//  by the AdqlValidator class.
			}
			else if ( name.equalsIgnoreCase(TapParams.FORMAT.toString() ) )
			{
				String value = param.getValue();
				if ( value == null )
					throw new IllegalStateException( "Missing FORMAT value" );
				if ( value==null || value.trim().length()==0 )
					throw new IllegalStateException( "Empty FORMAT value" );
				if ( ! ( value.equals("application/x-votable+xml") ||
						 value.equals("text/xml")                  || value.equalsIgnoreCase("votable") ||
					     value.equals("text/csv")                  || value.equalsIgnoreCase("csv") ||
					     value.equals("text/tab-separated-values") || value.equalsIgnoreCase("tsv") ||
						 value.equals("application/fits")          || value.equalsIgnoreCase("fits") ||
						 value.equals("text/plain")                || value.equalsIgnoreCase("text") ||
						 value.equals("text/html")                 || value.equalsIgnoreCase("html") ) )
					throw new IllegalStateException( "Unknown FORMAT value: "+value );
			}
			else if ( name.equalsIgnoreCase(TapParams.MAXREC.toString() ) )
			{
				String value = param.getValue();
				if ( value == null )
					throw new IllegalStateException( "Missing MAXREC value" );
				if ( value==null || value.trim().length()==0 )
					throw new IllegalStateException( "Empty MAXREC value" );
				int maxRec = 0;
				try
				{
					maxRec = Integer.parseInt( value );
				}
				catch ( NumberFormatException nfe )
				{
					throw new IllegalStateException( "MAXREC value not an integer" );
				}
				if ( maxRec < 0 )
					throw new IllegalStateException( "MAXREC value is negative" );
			}
			else if ( name.equalsIgnoreCase(TapParams.MTIME.toString() ) )
			{
				String value = param.getValue();
				if ( value == null )
					throw new IllegalStateException( "Missing MTIME value" );
				if ( value==null || value.trim().length()==0 )
					throw new IllegalStateException( "Empty MTIME value" );
				try
				{
					DateUtil.toDate( value, DateUtil.IVOA_DATE_FORMAT );
				}
				catch ( ParseException pe )
				{
					throw new IllegalStateException( "MTIME value not in ISO8601 format" );
				}
				//  TODO: 
				//  The MTIME  parameter cannot  be used with queries that select from multiple tables.
				//  If MTIME  is used in a such a query the service must  reject the request and return an error document.
			}
			else if ( name.equalsIgnoreCase(TapParams.RUNID.toString() ) )
			{
				String value = param.getValue();
				if ( value == null )
					throw new IllegalStateException( "Missing RUNID value" );
				if ( value==null || value.trim().length()==0 )
					throw new IllegalStateException( "Empty RUNID value" );
			}
			else if ( name.equalsIgnoreCase( TapParams.UPLOAD.toString() ) )
			{
				String value = param.getValue();
				if ( value == null )
					throw new IllegalStateException( "Missing UPLOAD value" );
				if ( value==null || value.trim().length()==0 )
					throw new IllegalStateException( "Empty UPLOAD value" );
				String pairs [] = value.split(";");
				for ( int i=0; i<pairs.length; i++ )
				{
					String tableUrlPair = null;
					try
					{
						tableUrlPair = pairs[i];
					}
					catch (IndexOutOfBoundsException iobe )
					{
						throw new IllegalStateException( "UPLOAD value missing table-URL pair at position "+i );
					}
					if ( tableUrlPair.length() == 0 )
						throw new IllegalStateException( "UPLOAD value contains empty table-URL pair at position "+i );
					String tableUrl [] = tableUrlPair.split(",");
					String table = null;
					try
					{
						table = tableUrl[0];
					}
					catch (IndexOutOfBoundsException iobe )
					{
						throw new IllegalStateException( "UPLOAD value missing table at position "+i );
					}
					if ( table.length() == 0 )
						throw new IllegalStateException( "UPLOAD value contains empty table at position "+i );
					String url = null;
					try
					{
						url = tableUrl[1];
					}
					catch (IndexOutOfBoundsException iobe )
					{
						throw new IllegalStateException( "UPLOAD value missing URL at position "+i );
					}

					if ( url.length() == 0 )
						throw new IllegalStateException( "UPLOAD value contains empty URL at position "+i );
					try
					{
						new URL( url );
					}
					catch ( MalformedURLException mue )
					{
						throw new IllegalStateException( "UPLOAD value contains invalid URL at position "+i );
					}
				}
			}
			else
			{
				throw new IllegalStateException( "UNKNOWN parameter: "+name );
			}
			
			TableWriter voTableWriter = new AsciiTableWriter();
			TableCreator tableCreator = new TableCreator();
		}
	}
	
	public boolean requestIsQuery()
	{
		return requestIsQuery;
	}

	public String getLang()
	{
		return lang;
	}
	
}
