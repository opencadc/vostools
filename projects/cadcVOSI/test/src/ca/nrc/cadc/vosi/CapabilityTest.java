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

package ca.nrc.cadc.vosi;

import java.util.ArrayList;
import java.util.List;
import org.jdom.Document;
import org.junit.Assert;

import org.jdom.Element;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author zhangsa
 *
 */
public class CapabilityTest
{

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterClass
    public static void tearDownAfterClass() throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception
    {
    }

    @Test
    public void testCapability() throws Exception
    {
        Capability cap = new Capability("http://localhost/vosiTest/", "ivo://ivoa.net/std/VOSI#tables", "tables", "std");
        Element ele = cap.toXmlElement();
        //XMLOutputter xop = new XMLOutputter(Format.getPrettyFormat());
        // TODO: direct the XML to System.out via log.debug
        //xop.output(ele, System.out);
        // TODO: compare ele content o expectations via Assert
        compare(cap, ele);
    }

    @Test
    public void testCapabilities() throws Exception
    {
        List<Capability> capList = new ArrayList<Capability>();
        // no trailing slash on ciontext url
        Capability cap1 = new Capability("http://example.com/myApp", "ivo://ivoa.net/std/VOSI#capability", "capabilities", null);
        capList.add(cap1);
        // with trailing slash on context url
        Capability cap2 = new Capability("http://example.com/myApp/", "ivo://ivoa.net/std/VOSI#availability", "availability", null);
        capList.add(cap2);
        // with a role
        Capability cap3 = new Capability("http://example.com/myApp/", "ivo://ivoa.net/std/Something", "something", "std");
        capList.add(cap3);
        
        Capabilities caps = new Capabilities(capList);
        Document doc = caps.toXmlDocument();
        Element root = doc.getRootElement();
        List nodes  = root.getChildren();

        //XMLOutputter xop = new XMLOutputter(Format.getPrettyFormat());
        // TODO: direct the XML to System.out via log.debug
        //xop.output(ele, System.out);
        // TODO: compare ele content o expectations via Assert
        Assert.assertEquals(root.getName(), "capabilities");
        Assert.assertEquals(nodes.size(), 3);
        Element ele;
        ele = (Element) nodes.get(0);
        compare(cap1, ele);
        ele = (Element) nodes.get(1);
        compare(cap2, ele);
        ele = (Element) nodes.get(2);
        compare(cap3, ele);

        // TODO: factor out and re-use detailed comparison of a Capability vs Element
    }

    private void compare(Capability c, Element e)
    {
        Assert.assertEquals(e.getName(), "capability");
        // TODO: compare attributes
        // TODO: compare child nodes and their attributes
    }
}
