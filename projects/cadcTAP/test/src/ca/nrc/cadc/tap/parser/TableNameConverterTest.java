
package ca.nrc.cadc.tap.parser;

import ca.nrc.cadc.tap.AdqlQuery;
import ca.nrc.cadc.tap.parser.converter.TableNameConverter;
import ca.nrc.cadc.tap.parser.navigator.ExpressionNavigator;
import ca.nrc.cadc.tap.parser.navigator.FromItemNavigator;
import ca.nrc.cadc.tap.parser.navigator.ReferenceNavigator;
import ca.nrc.cadc.tap.parser.navigator.SelectNavigator;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.uws.Parameter;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Assert;

/**
 *
 * @author pdowler
 */
public class TableNameConverterTest 
{
 private static final Logger log = Logger.getLogger(ColumnNameConverterTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.tap.parser", org.apache.log4j.Level.INFO);
    }

    public TableNameConverterTest() { }

    @BeforeClass
    public static void setUpClass() throws Exception { }

    @AfterClass
    public static void tearDownClass() throws Exception { }

    @Before
    public void setUp() { }

    @After
    public void tearDown() { }

    @Test
    public final void testCaseInsensitive()
    {
        String[] cols = { "oldTable", "OldTable", "oldtable" };
        try
        {
            for (String c : cols)
            {
                String query = "select * from " + c;
                String sql = convert("testSelectCaseInsensitive", query, ot, nt, true);
                Assert.assertTrue("testSelectCaseInsensitive: something", sql.contains(nt));
            }
        }
        catch(Throwable t)
        {
            log.error("testSelectCaseInsensitive: " + t);
            Assert.assertFalse(true);
        }
    }

    @Test
    public final void testCaseSensitive()
    {

        try
        {
           String query = "select * from oldTable";
           String sql = convert("testCaseSensitive", query, ot, nt, false);
           Assert.assertTrue("testCaseSensitive: !oldTable", !sql.contains(ot));
           Assert.assertTrue("testCaseSensitive: newTable", sql.contains(nt));

           query = "select * from OLDTABLE";
           sql = convert("testCaseSensitive", query, ot, nt, false);
           Assert.assertTrue("testCaseSensitive: OLDTABLE", sql.contains("OLDTABLE"));
           Assert.assertTrue("testCaseSensitive: !newTable", !sql.contains("newTable"));
        }
        catch(Exception t)
        {
            log.error("testCaseSensitive: " + t);
            Assert.assertFalse(true);
        }
    }

    @Test
    public final void testWithSchema()
    {
        try
        {
            String ost = "someSchema."+ot;
            String nst = "someSchema."+nt;
            String query = "select * from "+ost;
            String sql = convert("testWithSchema", query, ost, nst, false);
            Assert.assertTrue("testWithSchema: something", !sql.contains(ost));
            Assert.assertTrue("testWithSchema: something", sql.contains(nst));
        }
        catch(Throwable t)
        {
            log.error("testWithSchema: " + t);
            Assert.assertFalse(true);
        }
    }

    @Test
    public final void testWithSchemaChange()
    {
        try
        {
            String ost = "oldSchema."+ot;
            String nst = "newSchema."+ot;
            String query = "select * from "+ost;
            String sql = convert("testWithSchemaChange", query, ost, nst, false);
            Assert.assertTrue("testWithSchemaChange: something", !sql.contains(ost));
            Assert.assertTrue("testWithSchemaChange: something", sql.contains(nst));
        }
        catch(Throwable t)
        {
            log.error("testWithSchemaChange: " + t);
            Assert.assertFalse(true);
        }
    }

    @Test
    public final void testChangeBoth()
    {
        try
        {
            String ost = "oldSchema."+ot;
            String nst = "newSchema."+nt;
            String query = "select * from "+ost;
            String sql = convert("testChangeBoth", query, ost, nst, false);
            Assert.assertTrue("testChangeBoth: something", !sql.contains(ost));
            Assert.assertTrue("testChangeBoth: something", sql.contains(nst));
        }
        catch(Throwable t)
        {
            log.error("testChangeBoth: " + t);
            Assert.assertFalse(true);
        }
    }

    @Test
    public final void testSubQuery()
    {
        try
        {
            String query = "select * from foo where bar in (select bar from "+ot + ")";
            String sql = convert("testChangeBoth", query, ot, nt, false);
            Assert.assertTrue("testChangeBoth: something", !sql.contains(ot));
            Assert.assertTrue("testChangeBoth: something", sql.contains(nt));
        }
        catch(Throwable t)
        {
            log.error("testChangeBoth: " + t);
            Assert.assertFalse(true);
        }
    }

    @Test
    public final void testDatabaseSchemaTable()
    {
        try
        {
            String ost = "someDB.oldSchema."+ot;
            String nst = "someDB.newSchema."+ot;
            String query = "select * from "+ost;
            String sql = convert("testDatabaseSchemaTable", query, ost, nst, false);
            Assert.fail("testDatabaseSchemaTable: expected an exception here");
        }
        catch(IllegalArgumentException expected)
        {
            Assert.assertTrue("testDatabaseSchemaTable: caught expected exception: " + expected, true);
        }
        catch(Throwable t)
        {
            Assert.fail("testDatabaseSchemaTable: " + t);
        }
    }

    private String convert(String test, String query, String ot, String nt, boolean ignoreCase)
    {
        List<Parameter> params = new ArrayList<Parameter>();
        params.add(new Parameter("QUERY", query));
        log.debug(test + ", before: " + query);
        TestQuery tq = new TestQuery(ot, nt, ignoreCase);
        tq.setParameterList(params);
        String sql = tq.getSQL();
        log.debug(test + ", after: " + sql);
        return sql;
    }

    String ot = "oldTable";
    String nt = "newTable";

    static class TestQuery extends AdqlQuery
    {
        TableNameConverter tnc;

        TestQuery(String oldTable, String newTable, boolean ignoreCase)
        {
            this.tnc = new TableNameConverter(ignoreCase);
            tnc.put(oldTable, newTable);
        }
        protected void init()
        {
            //super.init();
            SelectNavigator sn = new SelectNavigator(
                    new ExpressionNavigator(), new ReferenceNavigator(), tnc);
            super.navigatorList.add(sn);
        }
    }

}
