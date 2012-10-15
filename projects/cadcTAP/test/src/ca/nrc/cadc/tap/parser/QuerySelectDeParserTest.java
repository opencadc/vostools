/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.nrc.cadc.tap.parser;

import ca.nrc.cadc.tap.AdqlQuery;
import ca.nrc.cadc.tap.TapQuery;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.uws.Parameter;
import java.util.ArrayList;
import java.util.List;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;

/**
 *
 * @author jburke
 */
public class QuerySelectDeParserTest
{
    private static final Logger log = Logger.getLogger(QuerySelectDeParserTest.class);

    public QuerySelectDeParserTest()
    {
    }

    @BeforeClass
    public static void setUpClass()
    {
        Log4jInit.setLevel("ca.nrc.cadc.tap.parser", org.apache.log4j.Level.INFO);
    }

    /**
     * Test of visit method, of class QuerySelectDeParser.
     */
    @Ignore("Not implemented")
    @Test
    public void testVisit_Table()
    {
        System.out.println("visit");
        Table table = null;
        QuerySelectDeParser instance = new QuerySelectDeParser();
        instance.visit(table);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of deparseJoin method, of class QuerySelectDeParser.
     */
    @Ignore("Not implemented")
    @Test
    public void testDeparseJoin()
    {
        System.out.println("deparseJoin");
        Join join = null;
        QuerySelectDeParser instance = new QuerySelectDeParser();
        instance.deparseJoin(join);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of visit method, of class QuerySelectDeParser.
     */
    @Ignore("Not implemented")
    @Test
    public void testVisit_PlainSelect()
    {
        System.out.println("visit");
        PlainSelect plainSelect = null;
        QuerySelectDeParser instance = new QuerySelectDeParser();
        instance.visit(plainSelect);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of deparseLimit method, of class QuerySelectDeParser.
     */
    @Test
    public void testDeparseLimit()
    {
        String[] queries = new String[] { "select foo from caom.Plane limit 0" };
        try
        {
            for (int t = 0; t < queries.length; t++)
            {
                String query = queries[t];
                List<Parameter> params = new ArrayList<Parameter>();
                params.add(new Parameter("QUERY", query));
                log.debug("testDeparseLimit, before: " + query);
                TapQuery tq = new TestQuery();
                tq.setParameterList(params);
                String sql = tq.getSQL();
                log.debug("testDeparseLimit, after: " + sql);

                sql = sql.toLowerCase();
                assertTrue("testDeparseLimit: no change", sql.equalsIgnoreCase(query));
            }
        }
        catch (Throwable t)
        {
            log.error("testNotFound", t);
            fail();
        }
    }

    static class TestQuery extends AdqlQuery
    {
        @Override
        protected void init() { }
    }
    
}

