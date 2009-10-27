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

import ca.nrc.cadc.tap.UploadManager;
import ca.nrc.cadc.uws.Parameter;

public class UploadManagerTest extends TestCase
{
	UploadManager manager = new UploadManager();
	
	public void testUploadSingleParamPair() {
        try {
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "LANG",    "ADQL" ) );
            paramList.add( new Parameter( "UPLOAD",  "mytable,http://localhost/table.xml" ) );
            manager.upload( paramList, "0" );
            assertTrue( false );
        }
        catch ( UnsupportedOperationException uoe ) {
            assertTrue( true );
        }
        catch ( Throwable t ) {
            assertTrue( t.getMessage(), false );
        }
    }
    
    public void testUploadMultipleParamPair() {
        try {
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "LANG",    "ADQL" ) );
            paramList.add( new Parameter( "UPLOAD",  "a,http://localhost/table1.xml;b,http://localhost/table2.xml" ) );
            manager.upload( paramList, "0" );
            assertTrue( false );
        }
        catch ( UnsupportedOperationException uoe ) {
            assertTrue( true );
        }
        catch ( Throwable t ) {
            assertTrue( t.getMessage(), false );
        }
    }
    
    public void testUploadRepeatedParam() {
        try {
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "LANG",    "ADQL" ) );
            paramList.add( new Parameter( "UPLOAD",  "a,http://localhost/table1.xml" ) );
            paramList.add( new Parameter( "UPLOAD",  "b,http://localhost/table2.xml" ) );
            manager.upload( paramList, "0" );
            assertTrue( false );
        }
        catch ( UnsupportedOperationException uoe ) {
            assertTrue( true );
        }
        catch ( Throwable t ) {
            assertTrue( t.getMessage(), false );
        }
    }
    
    public void testUploadParamEmpty() {
        try {
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "LANG",    "ADQL" ) );
            paramList.add( new Parameter( "UPLOAD",  "" ) );
            manager.upload( paramList, "0" );
            assertTrue( false );
        }
        catch ( Throwable t ) {
            assertTrue( t.getMessage(), true );
        }
    }
    
    public void testUploadMissingComma() {
        try {
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "LANG",    "ADQL" ) );
            paramList.add( new Parameter( "UPLOAD",  "mytablehttp://localhost/table.xml" ) );
            manager.upload( paramList, "0" );
            assertTrue( false );
        }
        catch ( Throwable t ) {
            assertTrue( t.getMessage(), true );
        }
    }
    
    public void testUploadMissingName() {
        try {
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "LANG",    "ADQL" ) );
            paramList.add( new Parameter( "UPLOAD",  ",http://localhost/table.xml" ) );
            manager.upload( paramList, "0" );
            assertTrue( false );
        }
        catch ( Throwable t ) {
            assertTrue( t.getMessage(), true );
        }
    }
    
    public void testUploadMissingURI() {
        try {
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "LANG",    "ADQL" ) );
            paramList.add( new Parameter( "UPLOAD",  "myTable," ) );
            manager.upload( paramList, "0" );
            assertTrue( false );
        }
        catch ( Throwable t ) {
            assertTrue( t.getMessage(), true );
        }
    }
    
    public void testUploadLeadingBlank() {
        try {
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "LANG",    "ADQL" ) );
            paramList.add( new Parameter( "UPLOAD",  " mytable,http://localhost/table.xml" ) );
            manager.upload( paramList, "0" );
            assertTrue( false );
        }
        catch ( Throwable t ) {
            assertTrue( t.getMessage(), true );
        }
    }
    
    public void testUploadDoubleBlanks() {
        try {
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "LANG",    "ADQL" ) );
            paramList.add( new Parameter( "UPLOAD",  "my  table,http://localhost/table.xml" ) );
            manager.upload( paramList, "0" );
            assertTrue( false );
        }
        catch ( Throwable t ) {
            assertTrue( t.getMessage(), true );
        }
    }
    
    public void testUploadTrailingBlank() {
        try {
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "LANG",    "ADQL" ) );
            paramList.add( new Parameter( "UPLOAD",  "mytable ,http://localhost/table.xml" ) );
            manager.upload( paramList, "0" );
            assertTrue( false );
        }
        catch ( Throwable t ) {
            assertTrue( t.getMessage(), true );
        }
    }
    
    public void testUploadUnsupportedProtocol() {
        try {
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "LANG",    "ADQL" ) );
            paramList.add( new Parameter( "UPLOAD",  "mytable,param://localhost/table.xml" ) );
            manager.upload( paramList, "0" );
            assertTrue( false );
        }
        catch ( Throwable t ) {
            assertTrue( t.getMessage(), true );
        }
    }
    
    public void testUploadDuplicateInSameParam() {
        try {
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "LANG",    "ADQL" ) );
            paramList.add( new Parameter( "UPLOAD",  "a,http://localhost/table1.xml;a,http://localhost/table2.xml" ) );
            manager.upload( paramList, "0" );
            assertTrue( false );
        }
        catch ( Throwable t ) {
            assertTrue( t.getMessage(), true );
        }
    }
    
    public void testUploadDuplicatInMultipleParam() {
        try {
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add( new Parameter( "REQUEST", "doQuery" ) );
            paramList.add( new Parameter( "LANG",    "ADQL" ) );
            paramList.add( new Parameter( "UPLOAD",  "a,http://localhost/table1.xml" ) );
            paramList.add( new Parameter( "UPLOAD",  "a,http://localhost/table2.xml" ) );
            manager.upload( paramList, "0" );
            assertTrue( false );
        }
        catch ( Throwable t ) {
            assertTrue( t.getMessage(), true );
        }
    }

}
