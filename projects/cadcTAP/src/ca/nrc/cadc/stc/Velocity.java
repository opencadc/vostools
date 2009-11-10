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

public class Velocity
{
    private static Logger log = Logger.getLogger(Velocity.class);

    private static final String DEFAULT_UNIT = "m/s";
    
    static
    {
        // default log level is debug.
        log = Logger.getLogger(Velocity.class);
        log.setLevel((Level)Level.DEBUG);
    }

    private Scanner words;

    protected boolean endOfWords;
    protected String currentWord;

    public List<VelocityInterval> intervals;
    public Double vel;
    public String unit;
    public List<Double> error;
    public List<Double> resln;
    public List<Double> pixsiz;

    public Velocity(Scanner words)
        throws StcsParsingException
    {
        this.words = words;
        endOfWords = false;
        currentWord = null;

        getVelocityIntervals();
        getVelocity();
        getUnit();
        getError();
        getResolution();
        getPixSize();
    }

    public String toSTCString()
    {
        StringBuilder sb = new StringBuilder();
        for (VelocityInterval interval : intervals)
        {
            sb.append(interval.space).append(" ");
            if (interval.fill != null)
                sb.append("fillfactor ").append(interval.fill).append(" ");
            for (Double loLimit : interval.lolimit)
                sb.append(loLimit).append(" ");
            for (Double hiLimit : interval.hilimit)
                sb.append(hiLimit).append(" ");
        }
        if (vel != null)
            sb.append("Velocity ").append(vel).append(" ");
        if (unit != null)
            sb.append("unit ").append(unit).append(" ");
        if (error != null)
            sb.append("Error ").append(doubleListToString(error));
        if (resln != null)
            sb.append("Resolution ").append(doubleListToString(resln));
        if (pixsiz != null)
            sb.append("PixSize ").append(doubleListToString(pixsiz));
        return sb.toString();
    }

    protected void getVelocityIntervals()
        throws StcsParsingException
    {
        while (words.hasNext("VelocityInterval"))
        {
            VelocityInterval interval = new VelocityInterval();
            interval.space = words.next();

            if (words.hasNext("fillfactor"))
            {
                words.next();
                if (words.hasNextDouble())
                    interval.fill = words.nextDouble();
                else
                    throw new StcsParsingException("fillfactor value missing in VelocityInterval");
            }
            List<Double> limits = new ArrayList<Double>();
            while (words.hasNextDouble())
                limits.add(words.nextDouble());
            if (limits.size() % 2 != 0)
                throw new StcsParsingException("Unmatched lolimit hilimit pair value");
            int split = limits.size()/2;
            interval.lolimit = new ArrayList<Double>();
            interval.lolimit.addAll(limits.subList(0, split));
            interval.hilimit = new ArrayList<Double>();
            interval.hilimit.addAll(limits.subList(split, limits.size()));

            if (intervals == null)
                intervals = new ArrayList<VelocityInterval>();
            intervals.add(interval);
        }
    }

    protected void getVelocity()
        throws StcsParsingException
    {
       if (endOfWords)
            return;
        if (currentWord == null)
        {
            if (words.hasNext())
                currentWord = words.next();
            else
                endOfWords = true;
        }
        if (!endOfWords && currentWord.equals("Velocity"))
        {
            if (words.hasNextDouble())
            {
                vel = words.nextDouble();
                currentWord = null;
            }
            else
            {
                throw new StcsParsingException("Unexpected end to STC-S phrase, missing Velocity value");
            }
        }
        log.debug("Velocity: " + vel);
    }

    protected void getUnit()
        throws StcsParsingException
    {
        if (endOfWords)
            return;
        if (currentWord == null)
        {
            if (words.hasNext())
                currentWord = words.next();
            else
                endOfWords = true;
        }
        if (!endOfWords && currentWord.equals("unit"))
        {
            if (words.hasNext())
            {
                currentWord = words.next();
                if (currentWord.endsWith(DEFAULT_UNIT) ||
                    Space.units.contains(currentWord))
                {
                    unit = currentWord;
                    currentWord = null;
                }
                else
                {
                    throw new StcsParsingException("Invalid unit value " + currentWord);
                }
            }
            else
            {
                throw new StcsParsingException("Unexpected end to STC-S phrase, missing unit value");
            }
        }
        log.debug("unit: " + unit);
    }

    protected void getError()
        throws StcsParsingException
    {
        error = getListForElement("Error");
    }

    protected void getResolution()
        throws StcsParsingException
    {
        resln = getListForElement("Resolution");
    }

    protected void getPixSize()
        throws StcsParsingException
    {
        pixsiz = getListForElement("PixSize");
    }

    private List<Double> getListForElement(String word)
        throws StcsParsingException
    {
        if (endOfWords)
            return null;
        List<Double> values = null;
        if (currentWord == null)
        {
            if (words.hasNext())
                currentWord = words.next();
            else
                endOfWords = true;
        }
        if (!endOfWords && currentWord.equals(word))
        {
            if (!words.hasNextDouble())
                throw new StcsParsingException(word + " element has no values");
            while (words.hasNextDouble())
            {
                if (values == null)
                    values = new ArrayList<Double>();
                values.add(words.nextDouble());
            }
            currentWord = null;
        }
        log.debug(word + ": " + values);
        return values;
    }

    protected String doubleListToString(List<Double> list)
    {
        StringBuilder sb = new StringBuilder();
        for (Double d : list)
            sb.append(d).append(" ");
        return sb.toString();
    }

    public class VelocityInterval
    {
        public String space;
        public Double fill;
        public List<Double> lolimit;
        public List<Double> hilimit;

        public VelocityInterval() {}
    }

}
