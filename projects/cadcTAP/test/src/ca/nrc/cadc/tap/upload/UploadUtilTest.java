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
package ca.nrc.cadc.tap.upload;

import ca.nrc.cadc.util.Log4jInit;
import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jburke
 */
public class UploadUtilTest
{
    private static Logger log = Logger.getLogger(UploadUtilTest.class);
    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.tap.upload", org.apache.log4j.Level.DEBUG);
    }
    
    public UploadUtilTest()
    {
    }
    
    @Before
    public void setUp()
    {
    }
    
    @After
    public void tearDown()
    {
    }
    
    @Test
    public void testIsValidateIdentifier()
    {
        try
        {
            Assert.assertTrue(UploadUtil.isValidateIdentifier("a"));
            Assert.assertTrue(UploadUtil.isValidateIdentifier("A"));
            Assert.assertTrue(UploadUtil.isValidateIdentifier("z"));
            Assert.assertTrue(UploadUtil.isValidateIdentifier("Z"));
            Assert.assertTrue(UploadUtil.isValidateIdentifier("a1"));
            Assert.assertTrue(UploadUtil.isValidateIdentifier("a_1"));
            Assert.assertTrue(UploadUtil.isValidateIdentifier("Z1_"));
            
            try
            {
                UploadUtil.isValidateIdentifier(null);
                fail("null is not a valid identifier");
            }
            catch (ADQLIdentifierException ignore) { }
            
            try
            {
                UploadUtil.isValidateIdentifier("");
                fail("empty string is not a valid identifier");
            }
            catch (ADQLIdentifierException ignore) { }
            
            try
            {
                UploadUtil.isValidateIdentifier("1name");
                fail("identifier cannot start with a digit");
            }
            catch (ADQLIdentifierException ignore) { }
            
            try
            {
                UploadUtil.isValidateIdentifier("_");
                fail("identifier cannot start with an underscore");
            }
            catch (ADQLIdentifierException ignore) { }
            
            try
            {
                UploadUtil.isValidateIdentifier(" name");
                fail("identifier cannot start with a space");
            }
            catch (ADQLIdentifierException ignore) { }
            
            try
            {
                UploadUtil.isValidateIdentifier("a name");
                fail("identifier cannot contain with a space");
            }
            catch (ADQLIdentifierException ignore) { }
            
            try
            {
                UploadUtil.isValidateIdentifier("name ");
                fail("identifier cannot end with a space");
            }
            catch (ADQLIdentifierException ignore) { }
            
            try
            {
                UploadUtil.isValidateIdentifier("name?");
                fail("identifier must only contain letter, digits, or an underscore");
            }
            catch (ADQLIdentifierException ignore) { }
            
            try
            {
                UploadUtil.isValidateIdentifier("n-ame");
                fail("identifier must only contain letter, digits, or an underscore");
            }
            catch (ADQLIdentifierException ignore) { }
            
            try
            {
                UploadUtil.isValidateIdentifier("name!");
                fail("identifier must only contain letter, digits, or an underscore");
            }
            catch (ADQLIdentifierException ignore) { }
            
            try
            {
                UploadUtil.isValidateIdentifier("name=");
                fail("identifier must only contain letter, digits, or an underscore");
            }
            catch (ADQLIdentifierException ignore) { }
            
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }
    
    @Test
    public void testIsAscii()
    {
        try
        {
            Assert.assertTrue(UploadUtil.isAsciiLetter('a'));
            Assert.assertTrue(UploadUtil.isAsciiLetter('A'));
            Assert.assertTrue(UploadUtil.isAsciiLetter('z'));
            Assert.assertTrue(UploadUtil.isAsciiLetter('Z'));
            Assert.assertFalse(UploadUtil.isAsciiLetter('1'));
            Assert.assertFalse(UploadUtil.isAsciiLetter(' '));
            Assert.assertFalse(UploadUtil.isAsciiLetter('@'));
            Assert.assertFalse(UploadUtil.isAsciiLetter('_'));
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }
    
    @Test
    public void testIsValidIdentifierCharacter()
    {
        try
        {
            Assert.assertTrue(UploadUtil.isValidIdentifierCharacter('a'));
            Assert.assertTrue(UploadUtil.isValidIdentifierCharacter('A'));
            Assert.assertTrue(UploadUtil.isValidIdentifierCharacter('z'));
            Assert.assertTrue(UploadUtil.isValidIdentifierCharacter('Z'));
            Assert.assertTrue(UploadUtil.isValidIdentifierCharacter('0'));
            Assert.assertTrue(UploadUtil.isValidIdentifierCharacter('9'));
            Assert.assertTrue(UploadUtil.isValidIdentifierCharacter('_'));
            Assert.assertFalse(UploadUtil.isValidIdentifierCharacter(' '));
            Assert.assertFalse(UploadUtil.isValidIdentifierCharacter('@'));
            Assert.assertFalse(UploadUtil.isValidIdentifierCharacter('?'));
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }
    
}
