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

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import junit.framework.TestCase;

import ca.nrc.cadc.tap.TapValidator;
import ca.nrc.cadc.uws.Parameter;

public class TapValidatorTest extends TestCase {
    
    static {
        BasicConfigurator.configure();
    }

    private static final Logger log = Logger.getLogger( TapValidatorTest.class );

	TapValidator validator = new TapValidator();
	
	public void testNullParamList() {
		try {
			validator.validate(null);
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
        log.debug( "*****  Finished test method: testNullParamList()  *****" );
	}

	public void testEmptyParamList() {
		try {
			validator.validate( new ArrayList<Parameter>() );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
        log.debug( "*****  Finished test method: testEmptyParamList()  *****" );
	}

	public void testAllKnownParams() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
            paramList.add( new Parameter( "UPLOAD",  "mytable,http://localhost/voTableExample1.xml" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
        log.debug( "*****  Finished test method: testAllKnownParams()  *****" );
	}

	public void testCaseInsenseParamNames() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "request", "doQuery" ) );
			paramList.add( new Parameter( "version", "1.0" ) );
			paramList.add( new Parameter( "lang",    "ADQL-1.0" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
        log.debug( "*****  Finished test method: testCaseInsenseParamNames()  *****" );
	}

	public void testCaseSenseRequestValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "DOQUERY" ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
        log.debug( "*****  Finished test method: testCaseSenseRequestValue()  *****" );
	}
	
	public void testRequestMissing() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
        log.debug( "*****  Finished test method: testRequestMissing()  *****" );
	}

	public void testRequestNullValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", null ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
        log.debug( "*****  Finished test method: testRequestNullValue()  *****" );
	}
	
	public void testRequestEmptyValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "" ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
        log.debug( "*****  Finished test method: testRequestEmptyValue()  *****" );
	}
	
	public void testRequestUnknownValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "Unknown" ) );
			paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
        log.debug( "*****  Finished test method: testRequestUnknownValue()  *****" );
	}
    
    public void testVersionMissingFromRequest() {
        try {
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "LANG",    "ADQL" ) );
            validator.validate( paramList );
            assertTrue( true );
        }
        catch ( IllegalStateException ise ) {
            assertTrue( false );
        }
        log.debug( "*****  Finished test method: testVersionMissingFromRequest()  *****" );
    }

	public void testVersionNullValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "VERSION", null ) );
			paramList.add( new Parameter( "LANG",    "ADQL" ) );
			validator.validate( paramList );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( false );
		}
        log.debug( "*****  Finished test method: testVersionNullValue()  *****" );
	}
	
	public void testVersionEmptyValue() {
		try {
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "VERSION", "" ) );
            paramList.add( new Parameter( "LANG",    "ADQL" ) );
            validator.validate( paramList );
            assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( false );
		}
        log.debug( "*****  Finished test method: testVersionEmptyValue()  *****" );
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
        log.debug( "*****  Finished test method: testVersionUnsupportedValue()  *****" );
	}
	
	public void testLangMissingFromRequestDoQuery() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "VERSION", "1.0" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
        log.debug( "*****  Finished test method: testLangMissingFromRequestDoQuery()  *****" );
	}

	public void testLangNullValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    null ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
        log.debug( "*****  Finished test method: testLangNullValue()  *****" );
	}
	
	public void testLangEmptyValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "" ) );
			validator.validate( paramList );
			assertTrue( false );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( true );
		}
        log.debug( "*****  Finished test method: testLangEmptyValue()  *****" );
	}
    
    public void testLangAdqlValue() {
        try {
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "LANG",    "ADQL" ) );
            validator.validate( paramList );
            assertEquals( "ADQL", validator.getLang() );
            assertTrue( true );
        }
        catch ( IllegalStateException ise ) {
            assertTrue( ise.getMessage(), false );
        }
        log.debug( "*****  Finished test method: testLangAdqlValue()  *****" );
    }
	
	public void testLangSqlValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "VERSION", "1.0" ) );
			paramList.add( new Parameter( "LANG",    "SQL" ) );
			validator.validate( paramList );
            assertEquals( "SQL", validator.getLang() );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( ise.getMessage(), false );
		}
        log.debug( "*****  Finished test method: testLangSqlValue()  *****" );
	}

	public void testLangPqlValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "LANG",    "PQL" ) );
			validator.validate( paramList );
            assertEquals( "PQL", validator.getLang() );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( false );
		}
        log.debug( "*****  Finished test method: testLangPqlValue()  *****" );
	}
	
	public void testLangUnknownValue() {
		try {
			List<Parameter> paramList = new ArrayList<Parameter>();
			paramList.add( new Parameter( "REQUEST", "doQuery" ) );
			paramList.add( new Parameter( "LANG",    "Unknown" ) );
			validator.validate( paramList );
            assertEquals( "Unknown", validator.getLang() );
			assertTrue( true );
		}
		catch ( IllegalStateException ise ) {
			assertTrue( false );
		}
        log.debug( "*****  Finished test method: testLangUnknownValue()  *****" );
	}

}
