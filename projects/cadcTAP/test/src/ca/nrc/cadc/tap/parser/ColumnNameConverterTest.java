
package ca.nrc.cadc.tap.parser;

import ca.nrc.cadc.tap.AdqlQuery;
import ca.nrc.cadc.tap.parser.converter.ColumnNameConverter;
import ca.nrc.cadc.tap.parser.navigator.ExpressionNavigator;
import ca.nrc.cadc.tap.parser.navigator.FromItemNavigator;
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
import static org.junit.Assert.*;

/**
 * Convert column name in the query to actual column name in tables
 *
 * @author pdowler
 */
public class ColumnNameConverterTest 
{
    private static final Logger log = Logger.getLogger(ColumnNameConverterTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.tap.parser", org.apache.log4j.Level.INFO);
    }

    public ColumnNameConverterTest() { }

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
        String[] cols = { "oldColumn", "OldColumn", "oldcolumn" };
        try
        {
            for (String c : cols)
            {
                String query = "select " + c + " from SomeTable";
                String sql = convert("testSelectCaseInsensitive", query, true);
                assertTrue("testSelectCaseInsensitive: something", sql.contains("newColumn"));
            }
        }
        catch(Throwable t)
        {
            log.error("testSelectCaseInsensitive: " + t);
            assertFalse(true);
        }
    }

    @Test
    public final void testCaseSensitive()
    {
        try
        {
           String query = "select oldColumn from SomeTable";
           String sql = convert("testCaseSensitive", query, false);
           assertTrue("testCaseSensitive: !oldColumn", !sql.contains("oldColumn"));
           assertTrue("testCaseSensitive: newColumn", sql.contains("newColumn"));

           query = "select OLDCOLUMN from SomeTable";
           sql = convert("testCaseSensitive", query, false);
           assertTrue("testCaseSensitive: OLDCOLUMN", sql.contains("OLDCOLUMN"));
           assertTrue("testCaseSensitive: !newColumn", !sql.contains("newColumn"));
        }
        catch(Exception t)
        {
            log.error("testCaseSensitive: " + t);
            assertFalse(true);
        }
    }

    @Test
    public final void testAlias()
    {
        try
        {
           String query = "select a.oldColumn from SomeTable AS a";
           String sql = convert("testAlias", query, false);
           assertTrue("testWhere: something", !sql.contains("oldColumn"));
           assertTrue("testWhere: something", sql.contains("a.newColumn"));
        }
        catch(Throwable t)
        {
            log.error("testAlias: " + t);
            assertFalse(true);
        }
    }

    @Test
    public final void testWhere()
    {
        try
        {
           String query = "select * from SomeTable WHERE oldColumn is not null";
           String sql = convert("testWhere", query, false);
           assertTrue("testWhere: !oldColumn", !sql.contains("oldColumn"));
           assertTrue("testWhere: newColumn", sql.contains("newColumn"));
        }
        catch(Throwable t)
        {
            log.error("testWhere: " + t);
            assertFalse(true);
        }
    }


    @Test
    public final void testJoin()
    {
        try
        {
           String query = "select * from SomeTable as st JOIN OtherTable as ot on st.oldColumn = ot.oldColumn";
           String sql = convert("testSelectNoAliasIgnoreCase", query, false);
           assertTrue("testJoin: something", !sql.contains("oldColumn"));
           assertTrue("testJoin: something", sql.contains("st.newColumn"));
           assertTrue("testJoin: something", sql.contains("ot.newColumn"));
        }
        catch(Throwable t)
        {
            log.error("testJoin: " + t);
            assertFalse(true);
        }
    }

    // TODO: group by, having, subquery
    
    private String convert(String test, String query, boolean ignoreCase)
    {
        List<Parameter> params = new ArrayList<Parameter>();
        params.add(new Parameter("QUERY", query));
        log.debug(test + ", before: " + query);
        TestQuery tq = new TestQuery();
        tq.ignoreCase = ignoreCase;
        tq.setParameterList(params);
        String sql = tq.getSQL();
        log.debug(test + ", after: " + sql);
        return sql;
    }
    
    static class TestQuery extends AdqlQuery
    {
        boolean ignoreCase;

        protected void init()
        {
            //super.init();
            ColumnNameConverter cnc = new ColumnNameConverter(ignoreCase);
            cnc.put("oldColumn", "newColumn");
            SelectNavigator sn = new SelectNavigator(
                    new ExpressionNavigator(), cnc, new FromItemNavigator());
            super.navigatorList.add(sn);
        }
    }
}
