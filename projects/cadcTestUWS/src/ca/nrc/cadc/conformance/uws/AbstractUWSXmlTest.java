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

package ca.nrc.cadc.conformance.uws;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom2.input.SAXBuilder;
import org.junit.Before;

import ca.nrc.cadc.util.Log4jInit;

/**
 * Abstract class for UWS XML job test.
 * 
 * @author zhangsa
 *
 */
public abstract class AbstractUWSXmlTest extends AbstractUWSTest
{
    protected static Logger log = Logger.getLogger(AbstractUWSXmlTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc", Level.INFO);
    }

    protected List<File> testFileList;
   
    protected String xmlTestFilePrefix = null;

    protected AbstractUWSXmlTest(String xslTestFilePrefix)
    {
        super();
        this.xmlTestFilePrefix = xslTestFilePrefix;
    }
    
    @Before
    public void before()
    {
        String directoryPath = System.getProperty("properties.directory");
        if (directoryPath == null) fail("properties.directory System property not set");
        
        if (xmlTestFilePrefix == null)
            throw new RuntimeException("XML test file prefix must be specified.");

        try
        {
            testFileList = Util.loadXmlFileList(directoryPath, xmlTestFilePrefix);
        }
        catch (IOException e1)
        {
            e1.printStackTrace();
            throw new RuntimeException("Cannot load test files.");
        }
    }

    /**
     * Load a list of test XML files, for each of them, execute the testImpl(), which is implemented in child class.
     * 
     * @author zhangsa
     */
    public void testFileList()
    {
        try
        {
            SAXBuilder saxBuilder = new SAXBuilder();

            for (File testFile : testFileList)
            {
                log.debug("**************************************************");
                log.debug("processing xml test file: " + testFile.getName());
                log.debug("**************************************************");

                //String xml = Util.getXmlString(saxBuilder.build(testFile));
                String xml = Util.readFileAsString(testFile);
                
                log.debug(xml);

                testImpl(xml);  // call the actual test implementation method for the given XML string in each file.
            }
        }
        catch (Exception ex)
        {
            log.error(ex);
            fail(ex.getMessage());
        }
    }

    abstract protected void testImpl(String xml) throws Exception;

}
