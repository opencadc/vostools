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

package ca.nrc.cadc.uws.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.jdom2.Content;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.Test;

import ca.nrc.cadc.util.Log4jInit;

/**
 * Unit tests for IterableContnet
 *
 * @author majorb
 */
public class IterableContentTest
{
    private static Logger log = Logger.getLogger(XmlUtilTest.class);
    static
    {
        Log4jInit.setLevel("ca.nrc.cadc", org.apache.log4j.Level.INFO);
    }

    public IterableContentTest() 
    {
    }

    @Test
    public void testContentIteration()
    {
        try
        {
            List<String> testData = new ArrayList<String>(3);
            testData.add("string1");
            testData.add("string2");
            testData.add("string3");
            
            ContentConverter<Element, String> cc = new ContentConverter<Element, String>()
            {
                public Element convert(String obj)
                {
                    Element e = new Element(obj);
                    e.setText(obj);
                    return e;
                }
            };
            
            Namespace ns = Namespace.getNamespace("test", "http://test.name.space");
            IterableContent<Element, String> ic = new IterableContent<Element, String>("name", ns, testData.iterator(), cc);
            
            List<Content> contentList = ic.getContent();
            
            // ensure these operations are not supported
            try { contentList.add(null); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.add(0, null); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.addAll(null); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.addAll(0, null); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.clear(); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.contains(null); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.containsAll(null); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.equals(null); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.get(0); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.indexOf(null); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.lastIndexOf(null); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.listIterator(); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.remove(0); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.remove(null); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.removeAll(null); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.retainAll(null); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.set(0, null); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.size(); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.subList(0, 0); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.toArray(); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            try { contentList.toArray(null); Assert.fail("Should be unsupported."); } catch (UnsupportedOperationException e) {};
            
            Assert.assertFalse("should not be empty.", contentList.isEmpty());
            Iterator<Content> it = contentList.iterator();
            
            Assert.assertTrue(it.hasNext());
            Assert.assertTrue("wrong content", ((Element)it.next()).getName().equals("string1"));
            Assert.assertTrue(it.hasNext());
            Assert.assertTrue("wrong content", ((Element)it.next()).getName().equals("string2"));
            Assert.assertTrue(it.hasNext());
            Assert.assertTrue("wrong content", ((Element)it.next()).getName().equals("string3"));
            
            Assert.assertFalse(it.hasNext());
            try
            {
                it.next();
                Assert.fail("Should be No Such Element");
            }
            catch (NoSuchElementException e)
            {
                // expected
            }
            
            Assert.assertTrue("should be empty.", contentList.isEmpty());
        }
        catch (Throwable t)
        {
            log.error(t);
            Assert.fail(t.getMessage());
        }
        
    }
    
    @Test
    public void testMaxContentIterations()
    {
        this.testMaxContentIterations(0);
        this.testMaxContentIterations(1);
        this.testMaxContentIterations(2);
        this.testMaxContentIterations(3);
        this.testMaxContentIterations(4);
        this.testMaxContentIterations(5);
    }
    
    private void testMaxContentIterations(int max)
    {
        log.debug("Testing max iterations: " + max);
        try
        {
            List<String> testData = new ArrayList<String>(3);
            testData.add("string1");
            testData.add("string2");
            testData.add("string3");
            testData.add("string4");
            testData.add("string5");
            
            ContentConverter<Element, String> cc = new ContentConverter<Element, String>()
            {
                public Element convert(String obj)
                {
                    Element e = new Element(obj);
                    e.setText(obj);
                    return e;
                }
            };
            
            TestMaxIterations maxIter = new TestMaxIterations(max);
            
            Namespace ns = Namespace.getNamespace("test", "http://test.name.space");
            IterableContent<Element, String> ic = new IterableContent<Element, String>("name", ns, testData.iterator(), cc, maxIter);
            
            List<Content> contentList = ic.getContent();
            
            if (max == 0)
                Assert.assertTrue("should not be empty.", contentList.isEmpty());
            else
                Assert.assertFalse("should not be empty.", contentList.isEmpty());
            
            Iterator<Content> it = contentList.iterator();
            
            for (int i=0; i<max; i++)
            {
                Assert.assertTrue(it.hasNext());
                maxIter.itCount++;
                Assert.assertTrue("wrong content", ((Element)it.next()).getName().equals("string" + (i + 1)));                
            }
            
            Assert.assertFalse("Shoud not have more elements", it.hasNext());
            try
            {
                it.next();
                Assert.fail("Should be No Such Element");
            }
            catch (NoSuchElementException e)
            {
                // expected
            }
            
            Assert.assertTrue("should be empty.", contentList.isEmpty());
            Assert.assertTrue("maxInterationsReached not called.", maxIter.maxIterationsCalled);
        }
        catch (Throwable t)
        {
            log.error(t.getMessage(), t);
            Assert.fail(t.getMessage());
        }
        
    }
    
    class TestMaxIterations implements MaxIterations
    {
        private long max;
        public long itCount = 0L;
        public boolean maxIterationsCalled = false;
        
        TestMaxIterations(long max)
        {
            this.max = max;
        }
        
        public long getMaxIterations()
        {
            return max;
        }

        @Override
        public void maxIterationsReached()
        {
            maxIterationsCalled = true;
            Assert.assertEquals("wrong num iterations", max, itCount);
        }
    };
    
}
