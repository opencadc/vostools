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

package ca.nrc.cadc.tap.test;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import ca.nrc.cadc.tap.TapValidator;
import ca.nrc.cadc.uws.Parameter;

public class TapValidatorTest extends TestCase
{
	TapValidator validator = new TapValidator();
	
	public void testNullParamList() {
		try {
			validator.validate(null);
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}

	public void testEmptyParamList() {
		try {
			validator.validate( new ArrayList<Parameter>() );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}

	public void testAllKnownParams() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
			paramList.add( new Parameter( "QUERY",   "Sensible query" ) );
			paramList.add( new Parameter( "FORMAT",  "votable" ) );
			paramList.add( new Parameter( "MAXREC",  "10" ) );
			paramList.add( new Parameter( "MTIME",   "2009-09-30T12:34:56.789" ) );
			paramList.add( new Parameter( "RUNID",   "100" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a,http://host_a/path" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}

	public void testCaseInsenseParamNames() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "request", "doQuery" ) );
			paramList.add( new Parameter( "version", "1.0" ) );
			paramList.add( new Parameter( "lang",    "ADQL-1.0" ) );
			paramList.add( new Parameter( "query",   "Sensible query" ) );
			paramList.add( new Parameter( "format",  "votable" ) );
			paramList.add( new Parameter( "maxrec",  "10" ) );
			paramList.add( new Parameter( "mtime",   "2009-09-30T12:34:56.789" ) );
			paramList.add( new Parameter( "runid",   "100" ) );
			paramList.add( new Parameter( "upload",  "table_a,http://host_a/path" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}

	public void testCaseSenseRequestValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "DOQUERY" ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
			paramList.add( new Parameter( "FORMAT",  "votable" ) );
			paramList.add( new Parameter( "MAXREC",  "10" ) );
			paramList.add( new Parameter( "MTIME",   "2009-09-30T12:34:56.789" ) );
			paramList.add( new Parameter( "RUNID",   "100" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a,http://host_a/path" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}

	public void testCaseSenseLangValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "adql" ) );
			paramList.add( new Parameter( "FORMAT",  "votable" ) );
			paramList.add( new Parameter( "MAXREC",  "10" ) );
			paramList.add( new Parameter( "MTIME",   "2009-09-30T12:34:56.789" ) );
			paramList.add( new Parameter( "RUNID",   "100" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a,http://host_a/path" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}

	public void testCaseSenseFormatValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "adql" ) );
			paramList.add( new Parameter( "FORMAT",  "VOTOABLE" ) );
			paramList.add( new Parameter( "MAXREC",  "10" ) );
			paramList.add( new Parameter( "MTIME",   "2009-09-30T12:34:56.789" ) );
			paramList.add( new Parameter( "RUNID",   "100" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a,http://host_a/path" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}

	public void testCaseSenseMtimeValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "adql" ) );
			paramList.add( new Parameter( "FORMAT",  "VOTOABLE" ) );
			paramList.add( new Parameter( "MAXREC",  "10" ) );
			paramList.add( new Parameter( "MTIME",   "2009-09-30t12:34:56.789" ) );
			paramList.add( new Parameter( "RUNID",   "100" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a,http://host_a/path" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}

	public void testUnknownParam() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
			paramList.add( new Parameter( "FORMAT",  "votable" ) );
			paramList.add( new Parameter( "MAXREC",  "10" ) );
			paramList.add( new Parameter( "MTIME",   "2009-09-30T12:34:56.789" ) );
			paramList.add( new Parameter( "RUNID",   "100" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a,http://host_a/path" ) );
			paramList.add( new Parameter( "Unknown", null ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testRequestMissing() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
			paramList.add( new Parameter( "FORMAT",  "votable" ) );
			paramList.add( new Parameter( "MAXREC",  "10" ) );
			paramList.add( new Parameter( "MTIME",   "2009-09-30T12:34:56.789" ) );
			paramList.add( new Parameter( "RUNID",   "100" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a,http://host_a/path" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}

	public void testRequestNullValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", null ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
			paramList.add( new Parameter( "FORMAT",  "votable" ) );
			paramList.add( new Parameter( "MAXREC",  "10" ) );
			paramList.add( new Parameter( "MTIME",   "2009-09-30T12:34:56.789" ) );
			paramList.add( new Parameter( "RUNID",   "100" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a,http://host_a/path" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testRequestEmptyValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "" ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
			paramList.add( new Parameter( "FORMAT",  "votable" ) );
			paramList.add( new Parameter( "MAXREC",  "10" ) );
			paramList.add( new Parameter( "MTIME",   "2009-09-30T12:34:56.789" ) );
			paramList.add( new Parameter( "RUNID",   "100" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a,http://host_a/path" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testRequestUnknownValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "unKnown" ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
			paramList.add( new Parameter( "FORMAT",  "votable" ) );
			paramList.add( new Parameter( "MAXREC",  "10" ) );
			paramList.add( new Parameter( "MTIME",   "2009-09-30T12:34:56.789" ) );
			paramList.add( new Parameter( "RUNID",   "100" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a,http://host_a/path" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testRequestDoQuery() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testRequestGetCapabilities() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testRequestGetAvailability() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getAvailability" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testRequestGetTableMetadata() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getTableMetadata" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}

	public void testVersionNullValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "VERSION", null ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testVersionEmptyValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "VERSION", "" ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testVersionUnsupportedValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "VERSION", "Unsupported" ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testLangMissingFromRequestDoQuery() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "FORMAT",  "votable" ) );
			paramList.add( new Parameter( "MAXREC",  "10" ) );
			paramList.add( new Parameter( "MTIME",   "2009-09-30T12:34:56.789" ) );
			paramList.add( new Parameter( "RUNID",   "100" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a,http://host_a/path" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testLangMissingFromRequestGetCapabilities() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "FORMAT",  "votable" ) );
			paramList.add( new Parameter( "MAXREC",  "10" ) );
			paramList.add( new Parameter( "MTIME",   "2009-09-30T12:34:56.789" ) );
			paramList.add( new Parameter( "RUNID",   "100" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a,http://host_a/path" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testLangMissingFromRequestGetAvailability() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getAvailability" ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "FORMAT",  "votable" ) );
			paramList.add( new Parameter( "MAXREC",  "10" ) );
			paramList.add( new Parameter( "MTIME",   "2009-09-30T12:34:56.789" ) );
			paramList.add( new Parameter( "RUNID",   "100" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a,http://host_a/path" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testLangMissingFromRequestGetTableMetadata() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getTableMetadata" ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "FORMAT",  "votable" ) );
			paramList.add( new Parameter( "MAXREC",  "10" ) );
			paramList.add( new Parameter( "MTIME",   "2009-09-30T12:34:56.789" ) );
			paramList.add( new Parameter( "RUNID",   "100" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a,http://host_a/path" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}

	public void testLangNullValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "LANG",    null ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testLangEmptyValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "LANG",    "" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testLangSqlValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "LANG",    "SQL" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testLangPqlValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "LANG",    "PQL" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testLangUnknownValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "LANG",    "Java" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}

	public void testFormatNullValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "Format",    null ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testFormatEmptyValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "Format",    "" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testFormatUnknownValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "Format",  "Unknown" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testFormatVoTableMime1() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "Format",  "application/x-votable+xml" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testFormatVoTableMime2() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "Format",  "text/xml" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testFormatVoTableShort() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "Format",  "votable" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testFormatCsvMime() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "Format",  "text/csv" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testFormatCsvShort() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "Format",  "csv" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testFormatTsvMime() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "Format",  "text/tab-separated-values" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testFormatTsvShort() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "Format",  "tsv" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testFormatFitsMime() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "Format",  "application/fits" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testFormatFitsShort() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "Format",  "fits" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testFormatTextMime() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "Format",  "text/plain" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testFormatTextShort() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "Format",  "text" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testFormatHtmlMime() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "Format",  "text/html" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testFormatHtmlShort() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "Format",  "html" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}

	public void testMaxrecNullValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "MAXREC",   null ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testMaxrecEmptyValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "MAXREC",  "" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testMaxrecCharValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "MAXREC",  "Char" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testMaxrecFloatValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "MAXREC",  "3.14" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testMaxrecNegValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "MAXREC",  "-1" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}

	public void testMtimeNullValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "MTIME",   null ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testMtimeEmptyValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "MTIME",  "" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testMtimeUnsupportedFormat() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "MTIME",  "2009-09-30 12:34:56.789:" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}

	public void testRunidNullValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "RUNID",   null ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testRunidEmptyValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "RUNID",  "" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}

	public void testUploadNullValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "UPLOAD",   null ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testUploadEmptyValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "UPLOAD",  "" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testUploadUnknownFormat() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "UPLOAD",  "abcdef" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testUploadMissingTable() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "UPLOAD",  ",http://host_a/path" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testUploadMissingURL() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a," ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testUploadFaultyURL() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a,FaultyURL" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testUploadMultipleGood() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a,http://host_a/path;table_b,http://host_b/path" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}
	
	public void testUploadMissingFirstPair() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "UPLOAD",  ";table_b,http://host_b/path" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
	}
	
	public void testUploadMissingSecondPair() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "getCapabilities" ) );
			paramList.add( new Parameter( "UPLOAD",  "table_a,http://host_a/path;" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
	}

}
