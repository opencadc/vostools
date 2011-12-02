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

package ca.nrc.cadc.tap.parser.schema;

import org.apache.log4j.Level;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.tap.parser.TestUtil;
import ca.nrc.cadc.util.Log4jInit;
import java.util.ArrayList;
import java.util.List;
import net.sf.jsqlparser.expression.Expression;
import org.apache.log4j.Logger;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author jburke
 */
public class ExpressionValidatorTest
{
    private static final Logger log = Logger.getLogger(ExpressionValidatorTest.class);

    static TapSchema TAP_SCHEMA;
    ExpressionValidator validator;
    
    public ExpressionValidatorTest() { }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc.tap.parser.schema", Level.INFO);
        TAP_SCHEMA = TestUtil.loadDefaultTapSchema();
    }

    @Before
    public void setUp() throws Exception
    {
        validator = new ExpressionValidator(TAP_SCHEMA);
    }

    /**
     * Test of visit method, of class ExpressionValidator.
     */
    @Test
    public void testVisitValidFunction()
    {
        try
        {
            Function function = new Function();
            function.setName("area");            
            try
            {
                validator.visit(function);
            }
            catch (Exception e)
            {
                Assert.fail("" + e.getMessage());
            }
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testVisitInvalidFunction()
    {
        try
        {
            Function function = new Function();
            function.setName("foo");
            try
            {
                validator.visit(function);
                Assert.fail("select is not a valid function name");
            }
            catch (Exception ignore) { }
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testVisitValidSubFunction()
    {
        try
        {
            Function function = new Function();
            function.setName("sum");
            function.setParameters(new ExpressionList());

            List<Expression> list = new ArrayList<Expression>();
            Function parameter = new Function();
            parameter.setName("min");
            list.add(parameter);
            function.getParameters().setExpressions(list);

            try
            {
                validator.visit(function);
            }
            catch (Exception e)
            {
                Assert.fail("" + e.getMessage());
            }
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testVisitInvalidSubFunction()
    {
        try
        {
            Function function = new Function();
            function.setName("area");
            function.setParameters(new ExpressionList());

            List<Expression> list = new ArrayList<Expression>();
            Function parameter = new Function();
            parameter.setName("foo");
            list.add(parameter);
            function.getParameters().setExpressions(list);

            try
            {
                validator.visit(function);
                Assert.fail("select is not a valid function name");
            }
            catch (Exception ignore) { }
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

}
