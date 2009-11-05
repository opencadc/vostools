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

package ca.nrc.cadc.stc;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public abstract class Space
{
    private static Logger log;

    protected boolean endOfWords;
    protected String currentWord;
    protected Scanner words;

    public static List frames;
    public static List refposs;
    public static List flavors;
    public static List units;
    static
    {
        // default log level is debug.
        log = Logger.getLogger(Space.class);
        log.setLevel((Level)Level.DEBUG);

        frames = new ArrayList();
        frames.add("ICRS");
        frames.add("FK5");
        frames.add("FK4");
        frames.add("J2000");
        frames.add("B1950");
        frames.add("ECLIPTIC");
        frames.add("GALACTIC");
        frames.add("GALACTIC_II");
        frames.add("SUPER_GALACTIC");
        frames.add("GEO_C");
        frames.add("GEO_D");
        frames.add("UNKNOWNFrame");

        refposs = new ArrayList();
        refposs.add("GEOCENTER");
        refposs.add("BARYCENTER");
        refposs.add("HELIOCENTER");
        refposs.add("TOPOCENTER");
        refposs.add("GALACTIC_CENTER");
        refposs.add("EMBARYCENTER");
        refposs.add("MOON");
        refposs.add("MERCURY");
        refposs.add("VENUS");
        refposs.add("MARS");
        refposs.add("JUPITER");
        refposs.add("SATURN");
        refposs.add("URANUS");
        refposs.add("NEPTUNE");
        refposs.add("PLUTO");
        refposs.add("UNKNOWNRefPos");

        flavors = new ArrayList();
        flavors.add("SPHER2");
        flavors.add("UNITSPHER");
        flavors.add("CART1");
        flavors.add("CART2");
        flavors.add("CART3");
        flavors.add("SPHER3");

        units = new ArrayList();
        units.add("deg");
        units.add("arcmin");
        units.add("arcsec");
        units.add("m");
        units.add("mm");
        units.add("km");
        units.add("AU");
        units.add("pc");
        units.add("kpc");
        units.add("Mpc");
    }

    public String stcs;
    public String space;
    public Double fill;
    public String frame;
    public String refpos;
    public String flavor;
    public List<Double> position;
    public String unit;
    public List<Double> error;
    public List<Double> resln;
    public List<Double> size;
    public List<Double> pixsiz;
    public Velocity velocity;

    public Space(String space)
    {
        this.space = space;
    }

    public Space(String space, String stcs)
    {
        if (stcs == null || stcs.length() == 0)
            return;
        this.space = space;
        this.stcs = stcs;
        
        endOfWords = false;
        currentWord = null;
        words = new Scanner(stcs);
        words.useDelimiter("\\s");

        valiateSpace(space);
        getFillfactor();
        getFrame();
        getRefpos();
        getFlavor();
        getPos();
        getPosition();
        getUnit();
        getError();
        getResolution();
        getSize();
        getPixSize();
    }

    public abstract String toSTCString();

    protected abstract void getPos();

    protected void valiateSpace(String space)
    {
        if (words.hasNext(space))
            words.next();
        else
            throw new IllegalArgumentException("Invalid space " + words.next());
        log.debug("space: " + space);
    }

    protected void getFillfactor()
    {
        if (words.hasNext("fillfactor"))
        {
            words.next();
            if (words.hasNextDouble())
                fill = words.nextDouble();
            else
                throw new IllegalArgumentException("Unexpected end to STC-S string, missing fillfactor value");
        }
        log.debug("fillfactor: " + fill);
    }

    protected void getFrame()
    {
        if (words.hasNext())
        {
            frame = words.next();
            if (!frames.contains(frame))
                throw new IllegalArgumentException("Invalid frame " + frame);
        }
        log.debug("frame: " + frame);
    }

    protected void getRefpos()
    {
        if (words.hasNext())
        {
            currentWord = words.next();
            if (refposs.contains(currentWord))
            {
                refpos = currentWord;
                currentWord = null;
            }
        }
        else
        {
            throw new IllegalArgumentException("Unexpected end to STC-S string reached before pos assigned");
        }
        log.debug("refpos: " + refpos);
    }

    protected void getFlavor()
    {
        if (currentWord == null)
        {
            if (words.hasNext())
                currentWord = words.next();
            else
                throw new IllegalArgumentException("Unexpected end to STC-S string reached before pos assigned");
        }
        if (flavors.contains(currentWord))
        {
            flavor = currentWord;
            currentWord = null;
        }
        log.debug("flavor: " + flavor);
    }

    protected void getPosition()
    {
        position = getDoubleListForWord("Position");
    }

    protected void getUnit()
    {
        if (endOfWords)
        {
            unit = null;
            return;
        }
        if (currentWord == null)
        {
            if (words.hasNext())
                currentWord = words.next();
            else
                endOfWords = true;
        }
        if (currentWord.equals("unit"))
        {
            if (words.hasNext())
            {
                currentWord = words.next();
                if (units.contains(currentWord))
                {
                    unit = currentWord;
                    currentWord = null;
                }
            }
        }
        log.debug("unit: " + unit);
    }

    protected void getError()
    {
        error = getDoubleListForWord("Error");
    }

    protected void getResolution()
    {
        resln = getDoubleListForWord("Resolution");
    }

    protected void getSize()
    {
        size = getDoubleListForWord("Size");
    }

    protected void getPixSize()
    {
        pixsiz = getDoubleListForWord("PixSize");
    }

    private List<Double> getDoubleListForWord(String word)
    {
        if (endOfWords)
            return null;
        List<Double> value = null;
        if (currentWord == null)
        {
            if (words.hasNext())
                currentWord = words.next();
            else
                endOfWords = true;
        }
        if (currentWord.equals(word))
        {
            while (words.hasNextDouble())
            {
                if (value == null)
                    value = new ArrayList<Double>();
                value.add(words.nextDouble());
            }
            if (value.size() == 0)
                throw new IllegalArgumentException(word + " parameter has no values");
            currentWord = null;
        }

        log.debug(word + ": " + value);
        return value;
    }

    protected String getListValues(List<Double> list)
    {
        StringBuilder sb = new StringBuilder();
        for (Double d : list)
            sb.append(d).append(" ");
        return sb.toString();
    }
}
