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

package ca.nrc.cadc.vos.client;

import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.util.ArgumentMap;
import ca.nrc.cadc.util.Log4jInit;


/**
 * Main test code. 
 * 
 * @author yeunga
 */
public class MainTest
{
    private static Logger log = Logger.getLogger(MainTest.class);

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc.vos", Level.INFO);
    }

    @Test 
    public void testValidateCommandArguments() throws Exception
    {
    	// Test creation of all node types.
    	final Main command = new Main();
    	Method validateCommand = command.getClass().getDeclaredMethod("validateCommand", ArgumentMap.class);
    	Method validateCommandArguments = command.getClass().getDeclaredMethod("validateCommandArguments", ArgumentMap.class);
    	validateCommand.setAccessible(true);
    	validateCommandArguments.setAccessible(true);
    	
    	// test --create --target=vos://cadc.nrc.ca~vospace/foo/bar (default: ContainerNode)
    	String[] args1 = {"--create", "--target=vos://cadc.nrc.ca~vospace/foo/bar"};
    	ArgumentMap argMap1 = new ArgumentMap(args1);
    	validateCommand.invoke(command, argMap1);
    	validateCommandArguments.invoke(command, argMap1);
    	
    	// test --create=ContainerNode --target=vos://cadc.nrc.ca~vospace/foo/bar
    	String[] args2 = {"--create=ContainerNode", "--target=vos://cadc.nrc.ca~vospace/foo/bar"};
    	ArgumentMap argMap2 = new ArgumentMap(args2);
    	validateCommand.invoke(command, argMap2);
    	validateCommandArguments.invoke(command, argMap2);
    	
    	// test --create=DataNode --target=vos://cadc.nrc.ca~vospace/foo/bar
    	String[] args3 = {"--create=DataNode", "--target=vos://cadc.nrc.ca~vospace/foo/bar"};
    	ArgumentMap argMap3 = new ArgumentMap(args3);
    	validateCommand.invoke(command, argMap3);
    	validateCommandArguments.invoke(command, argMap3);
    	
    	// test --create=UnstructuredDataNode --target=vos://cadc.nrc.ca~vospace/foo/bar
    	String[] args4 = {"--create=UnstructuredDataNode", "--target=vos://cadc.nrc.ca~vospace/foo/bar"};
    	ArgumentMap argMap4 = new ArgumentMap(args4);
    	validateCommand.invoke(command, argMap4);
    	validateCommandArguments.invoke(command, argMap4);
    	
    	// test --create=StructuredDataNode --target=vos://cadc.nrc.ca~vospace/foo/bar 
    	String[] args5 = {"--create=StructuredDataNode", "--target=vos://cadc.nrc.ca~vospace/foo/bar"};
    	ArgumentMap argMap5 = new ArgumentMap(args5);
    	validateCommand.invoke(command, argMap5);
    	validateCommandArguments.invoke(command, argMap5);
    	
    	// test --create=LinkNode --target=vos://cadc.nrc.ca~vospace/foo/bar --uri=http://www.google.com
    	String[] args6 = {"--create=LinkNode", "--target=vos://cadc.nrc.ca~vospace/foo/bar",
    			"--link=http://www.google.com"};
    	ArgumentMap argMap6 = new ArgumentMap(args6);
    	validateCommand.invoke(command, argMap6);
    	validateCommandArguments.invoke(command, argMap6);
    	
    	// test missing uri argument --create=LinkNode --target=vos://cadc.nrc.ca~vospace/foo/bar 
    	String[] args7 = {"--create=LinkNode", "--target=vos://cadc.nrc.ca~vospace/foo/bar"};
    	ArgumentMap argMap7 = new ArgumentMap(args7);
    	validateCommand.invoke(command, argMap7);
    	try
    	{
    	    validateCommandArguments.invoke(command, argMap7);
    	    log.debug("failed");
    	    fail("Failed to detect missing uri argument for a LinkNode.");
    	}
    	catch (Exception ex)
    	{
    		// expecting IllegalArgumentException
    		if (!(ex.getCause() instanceof IllegalArgumentException))
    		    fail("Unexpected exception: " + ex.getMessage());
    	}
    	
    	// test unsupported node type --create=LinkDataNode --target=vos://cadc.nrc.ca~vospace/foo/bar 
    	String[] args8 = {"--create=LinkDataNode", "--target=vos://cadc.nrc.ca~vospace/foo/bar"};
    	ArgumentMap argMap8 = new ArgumentMap(args8);
    	validateCommand.invoke(command, argMap8);
    	try
    	{
    	    validateCommandArguments.invoke(command, argMap8);
    	    fail("Failed to detect unsupported node type.");
    	}
    	catch (Exception ex)
    	{
    		// expecting IllegalArgumentException
    		if (!(ex.getCause() instanceof IllegalArgumentException))
    		    fail("Unexpected exception: " + ex.getMessage());
    	}    	
    	
    	// test --set --target=vos://cadc.nrc.ca~vospace/foo/bar --group-read="test:g1 test:g2" --group-write="test:g3 test:g4"
    	String[] args9 = {"--set", "--target=vos://cadc.nrc.ca~vospace/foo/bar", 
    			"--group-read=\"test:g1 test:g2\"", "--group-write=\"test:g3 test:g4\""};
    	ArgumentMap argMap9 = new ArgumentMap(args9);
    	validateCommand.invoke(command, argMap9);
    	validateCommandArguments.invoke(command, argMap9);
    }
}
