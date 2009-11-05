/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.nrc.cadc.stc;

import java.util.List;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class Velocity
{
    private static Logger log = Logger.getLogger(Velocity.class);
    static
    {
        // default log level is debug.
        log = Logger.getLogger(Velocity.class);
        log.setLevel((Level)Level.DEBUG);
    }

    public Double fill;
    public List<Double> lolimit;
    public List<Double> hilimit;
    public Double vel;
    public String unit;
    public List<Double> error;
    public List<Double> resln;
    public List<Double> pixsiz;


    public String toSTCString()
    {
        return "";
    }
}
