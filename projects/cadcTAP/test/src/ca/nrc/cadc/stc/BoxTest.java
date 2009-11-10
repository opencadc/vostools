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

public class BoxTest
{
    public static final String SPACE = "Box";
    public static final String FILLFACTOR = "fillfactor 1.0";
    public static final String FRAME = "ICRS";
    public static final String REFPOS = "BARYCENTER";
    public static final String FLAVOR = "SPHER2";
    public static final String POS = "148.9 69.1";
    public static final String BSIZE = "2.0 3.0";
    public static final String POSITION = "Position 0.1 0.2";
    public static final String UNIT = "unit deg";
    public static final String ERROR = "Error 0.1 0.2 0.3 0.4";
    public static final String RESOLUTION = "Resolution 0.0001 0.0001 0.0003 0.0003";
    public static final String SIZE = "Size 0.5 0.5 0.67 0.67";
    public static final String PIXSIZE = "PixSize 0.00005 0.00005 0.00015 0.00015";
    public static final String VELOCITY = "VelocityInterval fillfactor 1.0 1.0 2.0 3.0 4.0";

    public Box box;
    public String phrase;

    public BoxTest() {}

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
        sb.append(BSIZE).append(" ");
        sb.append(POSITION).append(" ");
        sb.append(UNIT).append(" ");
        sb.append(ERROR).append(" ");
        sb.append(RESOLUTION).append(" ");
        sb.append(SIZE).append(" ");
        sb.append(PIXSIZE).append(" ");
        sb.append(VELOCITY).append(" ");
        phrase = sb.toString();
        box = new Box(phrase);
    }

    @After
    public void tearDown()
    {
    }

    @Test
    public void testFillfactor() throws Exception
    {
        assertEquals(new Double(1.0), box.fill);
    }

    @Test
    public void testFrame() throws Exception
    {
        assertEquals("ICRS", box.frame);
    }

    @Test
    public void testRefPos() throws Exception
    {
        assertEquals("BARYCENTER", box.refpos);
    }

    @Test
    public void testFlavor() throws Exception
    {
        assertEquals("SPHER2", box.flavor);
    }

    @Test
    public void testPos() throws Exception
    {
        assertEquals(new Double(148.9), box.pos.get(0));
        assertEquals(new Double(69.1), box.pos.get(1));
    }

    @Test
    public void testBsize() throws Exception
    {
        assertEquals(new Double(2.0), box.bsize.get(0));
        assertEquals(new Double(3.0), box.bsize.get(1));
    }

    @Test
    public void testPosition() throws Exception
    {
        assertEquals(new Double(0.1), box.position.get(0));
        assertEquals(new Double(0.2), box.position.get(1));
    }

    @Test
    public void testUnit() throws Exception
    {
        assertEquals("deg", box.unit);
    }

    @Test
    public void testError() throws Exception
    {
        assertEquals(new Double(0.1), box.error.get(0));
        assertEquals(new Double(0.2), box.error.get(1));
        assertEquals(new Double(0.3), box.error.get(2));
        assertEquals(new Double(0.4), box.error.get(3));
    }

    @Test
    public void testResolution() throws Exception
    {
        assertEquals(new Double(0.0001), box.resln.get(0));
        assertEquals(new Double(0.0001), box.resln.get(1));
        assertEquals(new Double(0.0003), box.resln.get(2));
        assertEquals(new Double(0.0003), box.resln.get(3));
    }

    @Test
    public void testSize() throws Exception
    {
        assertEquals(new Double(0.5), box.size.get(0));
        assertEquals(new Double(0.5), box.size.get(1));
        assertEquals(new Double(0.67), box.size.get(2));
        assertEquals(new Double(0.67), box.size.get(3));
    }

    @Test
    public void testPixSize() throws Exception
    {
        assertEquals(new Double(0.00005), box.pixsiz.get(0));
        assertEquals(new Double(0.00005), box.pixsiz.get(1));
        assertEquals(new Double(0.00015), box.pixsiz.get(2));
        assertEquals(new Double(0.00015), box.pixsiz.get(3));
    }

    @Test
    public void testVelocity() throws Exception
    {
        assertEquals(new Double(1.0), box.velocity.intervals.get(0).fill);
        assertEquals(new Double(1.0), box.velocity.intervals.get(0).lolimit.get(0));
        assertEquals(new Double(2.0), box.velocity.intervals.get(0).lolimit.get(1));
        assertEquals(new Double(3.0), box.velocity.intervals.get(0).hilimit.get(0));
        assertEquals(new Double(4.0), box.velocity.intervals.get(0).hilimit.get(1));
    }

    @Test
    public void testToSTCString() throws Exception
    {
        assertEquals(phrase, box.toSTCString());
    }

}
