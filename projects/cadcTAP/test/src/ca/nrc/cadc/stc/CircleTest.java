/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.nrc.cadc.stc;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CircleTest
{
    public static final String SPACE = "Circle";
    public static final String FILLFACTOR = "fillfactor 1.0";
    public static final String FRAME = "ICRS";
    public static final String REFPOS = "BARYCENTER";
    public static final String FLAVOR = "SPHER2";
    public static final String POS = "148.9 69.1";
    public static final String RADIUS = "2.0";
    public static final String POSITION = "Position 0.1 0.2";
    public static final String UNIT = "unit deg";
    public static final String ERROR = "Error 0.1 0.2 0.3 0.4";
    public static final String RESOLUTION = "Resolution 0.0001 0.0001 0.0003 0.0003";
    public static final String SIZE = "Size 0.5 0.5 0.67 0.67";
    public static final String PIXSIZE = "PixSize 0.00005 0.00005 0.00015 0.00015";
    public static final String VELOCITY = "VelocityInterval fillfactor 1.0 1.0 2.0 3.0 4.0";

    public Circle circle;
    public String phrase;

    public CircleTest() {}

    @Before
    public void setUp() throws Exception
    {
        StringBuilder sb = new StringBuilder();
        sb.append(SPACE).append(" ");
        sb.append(FILLFACTOR).append(" ");
        sb.append(FRAME).append(" ");
        sb.append(REFPOS).append(" ");
        sb.append(FLAVOR).append(" ");
        sb.append(POS).append(" ");
        sb.append(RADIUS).append(" ");
        sb.append(POSITION).append(" ");
        sb.append(UNIT).append(" ");
        sb.append(ERROR).append(" ");
        sb.append(RESOLUTION).append(" ");
        sb.append(SIZE).append(" ");
        sb.append(PIXSIZE).append(" ");
        sb.append(VELOCITY).append(" ");
        phrase = sb.toString();
        circle = new Circle(phrase);
    }

    @After
    public void tearDown()
    {
    }

    @Test
    public void testFillfactor() throws Exception
    {
        assertEquals(new Double(1.0), circle.fill);
    }

    @Test
    public void testFrame() throws Exception
    {
        assertEquals("ICRS", circle.frame);
    }

    @Test
    public void testRefPos() throws Exception
    {
        assertEquals("BARYCENTER", circle.refpos);
    }

    @Test
    public void testFlavor() throws Exception
    {
        assertEquals("SPHER2", circle.flavor);
    }

    @Test
    public void testPos() throws Exception
    {
        assertEquals(new Double(148.9), circle.pos.get(0));
        assertEquals(new Double(69.1), circle.pos.get(1));
    }

    @Test
    public void testRadius() throws Exception
    {
        assertEquals(new Double(2.0), circle.radius);
    }

    @Test
    public void testPosition() throws Exception
    {
        assertEquals(new Double(0.1), circle.position.get(0));
        assertEquals(new Double(0.2), circle.position.get(1));
    }

    @Test
    public void testUnit() throws Exception
    {
        assertEquals("deg", circle.unit);
    }

    @Test
    public void testError() throws Exception
    {
        assertEquals(new Double(0.1), circle.error.get(0));
        assertEquals(new Double(0.2), circle.error.get(1));
        assertEquals(new Double(0.3), circle.error.get(2));
        assertEquals(new Double(0.4), circle.error.get(3));
    }

    @Test
    public void testResolution() throws Exception
    {
        assertEquals(new Double(0.0001), circle.resln.get(0));
        assertEquals(new Double(0.0001), circle.resln.get(1));
        assertEquals(new Double(0.0003), circle.resln.get(2));
        assertEquals(new Double(0.0003), circle.resln.get(3));
    }

    @Test
    public void testSize() throws Exception
    {
        assertEquals(new Double(0.5), circle.size.get(0));
        assertEquals(new Double(0.5), circle.size.get(1));
        assertEquals(new Double(0.67), circle.size.get(2));
        assertEquals(new Double(0.67), circle.size.get(3));
    }

    @Test
    public void testPixSize() throws Exception
    {
        assertEquals(new Double(0.00005), circle.pixsiz.get(0));
        assertEquals(new Double(0.00005), circle.pixsiz.get(1));
        assertEquals(new Double(0.00015), circle.pixsiz.get(2));
        assertEquals(new Double(0.00015), circle.pixsiz.get(3));
    }

    @Test
    public void testVelocity() throws Exception
    {
        assertEquals(new Double(1.0), circle.velocity.intervals.get(0).fill);
        assertEquals(new Double(1.0), circle.velocity.intervals.get(0).lolimit.get(0));
        assertEquals(new Double(2.0), circle.velocity.intervals.get(0).lolimit.get(1));
        assertEquals(new Double(3.0), circle.velocity.intervals.get(0).hilimit.get(0));
        assertEquals(new Double(4.0), circle.velocity.intervals.get(0).hilimit.get(1));
    }

    @Test
    public void testToSTCString() throws Exception
    {
        assertEquals(phrase, circle.toSTCString());
    }

}
