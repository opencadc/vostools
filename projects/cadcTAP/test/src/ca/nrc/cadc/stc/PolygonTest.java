/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.nrc.cadc.stc;

import java.util.Scanner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class PolygonTest
{
    public static final String SPACE = "Polygon";
    public static final String FILLFACTOR = "fillfactor 1.0";
    public static final String FRAME = "ICRS";
    public static final String REFPOS = "BARYCENTER";
    public static final String FLAVOR = "SPHER2";
    public static final String POS = "148.9 69.1 76.4 22.8";
    public static final String POSITION = "Position 0.1 0.2";
    public static final String UNIT = "unit deg";
    public static final String ERROR = "Error 0.1 0.2 0.3 0.4";
    public static final String RESOLUTION = "Resolution 0.0001 0.0001 0.0003 0.0003";
    public static final String SIZE = "Size 0.5 0.5 0.67 0.67";
    public static final String PIXSIZE = "PixSize 0.00005 0.00005 0.00015 0.00015";
    public static final String VELOCITY = "VelocityInterval fillfactor 1.0 1.0 2.0 3.0 4.0";

    public Polygon polygon;
    public String phrase;

    public PolygonTest() {}

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
        sb.append(POSITION).append(" ");
        sb.append(UNIT).append(" ");
        sb.append(ERROR).append(" ");
        sb.append(RESOLUTION).append(" ");
        sb.append(SIZE).append(" ");
        sb.append(PIXSIZE).append(" ");
        sb.append(VELOCITY).append(" ");
        phrase = sb.toString();
        polygon = new Polygon(phrase);
    }

    @After
    public void tearDown()
    {
    }

    @Test
    public void testFillfactor() throws Exception
    {
        assertEquals(new Double(1.0), polygon.fill);
    }

    @Test
    public void testFrame() throws Exception
    {
        assertEquals("ICRS", polygon.frame);
    }

    @Test
    public void testRefPos() throws Exception
    {
        assertEquals("BARYCENTER", polygon.refpos);
    }

    @Test
    public void testFlavor() throws Exception
    {
        assertEquals("SPHER2", polygon.flavor);
    }

    @Test
    public void testPos() throws Exception
    {
        assertEquals(new Double(148.9), polygon.pos.get(0));
        assertEquals(new Double(69.1), polygon.pos.get(1));
        assertEquals(new Double(76.4), polygon.pos.get(2));
        assertEquals(new Double(22.8), polygon.pos.get(3));
    }

    @Test
    public void testPosition() throws Exception
    {
        assertEquals(new Double(0.1), polygon.position.get(0));
        assertEquals(new Double(0.2), polygon.position.get(1));
    }

    @Test
    public void testUnit() throws Exception
    {
        assertEquals("deg", polygon.unit);
    }

    @Test
    public void testError() throws Exception
    {
        assertEquals(new Double(0.1), polygon.error.get(0));
        assertEquals(new Double(0.2), polygon.error.get(1));
        assertEquals(new Double(0.3), polygon.error.get(2));
        assertEquals(new Double(0.4), polygon.error.get(3));
    }

    @Test
    public void testResolution() throws Exception
    {
        assertEquals(new Double(0.0001), polygon.resln.get(0));
        assertEquals(new Double(0.0001), polygon.resln.get(1));
        assertEquals(new Double(0.0003), polygon.resln.get(2));
        assertEquals(new Double(0.0003), polygon.resln.get(3));
    }

    @Test
    public void testSize() throws Exception
    {
        assertEquals(new Double(0.5), polygon.size.get(0));
        assertEquals(new Double(0.5), polygon.size.get(1));
        assertEquals(new Double(0.67), polygon.size.get(2));
        assertEquals(new Double(0.67), polygon.size.get(3));
    }

    @Test
    public void testPixSize() throws Exception
    {
        assertEquals(new Double(0.00005), polygon.pixsiz.get(0));
        assertEquals(new Double(0.00005), polygon.pixsiz.get(1));
        assertEquals(new Double(0.00015), polygon.pixsiz.get(2));
        assertEquals(new Double(0.00015), polygon.pixsiz.get(3));
    }

    @Test
    public void testVelocity() throws Exception
    {
        assertEquals(new Double(1.0), polygon.velocity.intervals.get(0).fill);
        assertEquals(new Double(1.0), polygon.velocity.intervals.get(0).lolimit.get(0));
        assertEquals(new Double(2.0), polygon.velocity.intervals.get(0).lolimit.get(1));
        assertEquals(new Double(3.0), polygon.velocity.intervals.get(0).hilimit.get(0));
        assertEquals(new Double(4.0), polygon.velocity.intervals.get(0).hilimit.get(1));
    }

    @Test
    public void testToSTCString() throws Exception
    {
        assertEquals(phrase, polygon.toSTCString());
    }

}
