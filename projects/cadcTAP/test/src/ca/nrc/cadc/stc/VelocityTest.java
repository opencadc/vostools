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

/**
 *
 */
public class VelocityTest
{
    public static final String VELOCITYINTERVAL_1 = "VelocityInterval fillfactor 1.0 1.0 2.0 3.0 4.0";
    public static final String VELOCITYINTERVAL_2 = "VelocityInterval fillfactor 0.5 1.5 2.5 3.5 4.5";
    public static final String VELOCITY = "Velocity 10.1";
    public static final String UNIT = "unit m/s";
    public static final String ERROR = "Error 0.1 0.2";
    public static final String RESOLUTION = "Resolution 2.1 3.1";
    public static final String PIXSIZE = "PixSize 4.2 5.2";

    public Velocity velocity;
    public Scanner words;
    public String phrase;

    public VelocityTest() {}

    @Before
    public void setUp() throws Exception
    {
        StringBuilder sb = new StringBuilder();
        sb.append(VELOCITYINTERVAL_1).append(" ");
        sb.append(VELOCITYINTERVAL_2).append(" ");
        sb.append(VELOCITY).append(" ");
        sb.append(UNIT).append(" ");
        sb.append(ERROR).append(" ");
        sb.append(RESOLUTION).append(" ");
        sb.append(PIXSIZE).append(" ");
        phrase = sb.toString();
        words = new Scanner(phrase);
        velocity = new Velocity(words);
    }

    @After
    public void tearDown()
    {
    }

    @Test
    public void testFillfactor() throws Exception
    {
        assertEquals(new Double(1.0), velocity.intervals.get(0).fill);
        assertEquals(new Double(0.5), velocity.intervals.get(1).fill);
    }

    @Test
    public void testLolimit() throws Exception
    {
        assertEquals(new Double(1.0), velocity.intervals.get(0).lolimit.get(0));
        assertEquals(new Double(2.0), velocity.intervals.get(0).lolimit.get(1));
        assertEquals(new Double(1.5), velocity.intervals.get(1).lolimit.get(0));
        assertEquals(new Double(2.5), velocity.intervals.get(1).lolimit.get(1));
    }

    @Test
    public void testHilimit() throws Exception
    {
        assertEquals(new Double(3.0), velocity.intervals.get(0).hilimit.get(0));
        assertEquals(new Double(4.0), velocity.intervals.get(0).hilimit.get(1));
        assertEquals(new Double(3.5), velocity.intervals.get(1).hilimit.get(0));
        assertEquals(new Double(4.5), velocity.intervals.get(1).hilimit.get(1));
    }

    @Test
    public void testVelocity() throws Exception
    {
        assertEquals(new Double(10.1), velocity.vel);
    }

    @Test
    public void testUnit() throws Exception
    {
        assertEquals("m/s", velocity.unit);
    }

    @Test
    public void testError() throws Exception
    {
        assertEquals(new Double(0.1), velocity.error.get(0));
        assertEquals(new Double(0.2), velocity.error.get(1));
    }

    @Test
    public void testResolution() throws Exception
    {
        assertEquals(new Double(2.1), velocity.resln.get(0));
        assertEquals(new Double(3.1), velocity.resln.get(1));
    }

    @Test
    public void testPixSize() throws Exception
    {
        assertEquals(new Double(4.2), velocity.pixsiz.get(0));
        assertEquals(new Double(5.2), velocity.pixsiz.get(1));
    }

    @Test
    public void testToSTCString() throws Exception
    {
        assertEquals(phrase, velocity.toSTCString());
    }

}