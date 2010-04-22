/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.nrc.cadc.tap.parser;

import ca.nrc.cadc.tap.parser.function.Concatenate;
import java.util.ArrayList;
import java.util.List;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jburke
 */
public class ConcatenateTest {

    public ConcatenateTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of toString method, of class Concatenate.
     */
    @Test
    public void testToString()
    {
        System.out.println("toString");
        Table table = new Table("schema", "table");
        Column columnA = new Column(table, "A");
        Column columnB = new Column(table, "B");
        Column columnC = new Column(table, "C");

        List expressions = new ArrayList();
        expressions.add(columnA);
        expressions.add(columnB);
        expressions.add(columnC);
        ExpressionList list = new ExpressionList(expressions);

        String separator = "+";
        StringBuilder sb = new StringBuilder();
        sb.append(columnA.getWholeColumnName());
        sb.append(Concatenate.DEFAULT_OPERATOR);
        sb.append(separator);
        sb.append(Concatenate.DEFAULT_OPERATOR);
        sb.append(columnB.getWholeColumnName());
        sb.append(Concatenate.DEFAULT_OPERATOR);
        sb.append(separator);
        sb.append(Concatenate.DEFAULT_OPERATOR);
        sb.append(columnC.getWholeColumnName());
        String expResult = sb.toString();;

        Concatenate instance = new Concatenate();
        instance.setParameters(list);
        instance.setSeparator(separator);
        String result = instance.toString();
        assertEquals(expResult, result);
    }

}