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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Base class for a STC-S Space.
 *
 */
public abstract class SpatialSubphrase
{
    // Default values.
    protected static final String DEFAULT_FRAME = Frame.UNKNOWNFRAME;
    protected static final String DEFAULT_REFPOS = ReferencePosition.UNKNOWNREFPOS;
    protected static final String DEFAULT_FLAVOR = Flavor.SPHER2;
    protected static final String DEFAULT_UNIT = SpatialUnit.DEG;

    // Formatter for Double values.
    protected static DecimalFormat doubleFormat;

    static
    {
        // TODO what is the format, should it even be used?
        doubleFormat = new DecimalFormat("####.########");
        doubleFormat.setDecimalSeparatorAlwaysShown(true);
        doubleFormat.setMinimumFractionDigits(1);
    }

    /**
     * STC-S phrase elements.
     */
    public String phrase;
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

    // Dimensionality of the frame.
    protected int dimensions;

    // Words to process in the phrase?
    protected boolean endOfWords;

    // The current word from the scanner.
    protected String currentWord;

    // The tokenized phrase.
    protected Scanner words;

    /**
     *
     * @param space
     */
    public SpatialSubphrase(String space)
    {
        this.space = space;
        this.frame = DEFAULT_FRAME;
    }

    public void init(String phrase)
        throws StcsParsingException
    {
        if (phrase == null || phrase.length() == 0)
            return;
        this.phrase = phrase.trim();

        endOfWords = false;
        currentWord = null;
        words = new Scanner(phrase);
        words.useDelimiter("\\s+");

        valiateSpace(space);
        getFillfactor();
        getFrame();
        getRefpos();
        getFlavor();
        getDimensionality();
        getPos();
        getPosition();
        getUnit();
        getError();
        getResolution();
        getSize();
        getPixSize();
        getVelocity();
    }

    protected abstract void getPos()
        throws StcsParsingException;

    protected void valiateSpace(String space)
        throws StcsParsingException
    {
        if (words.hasNext(space))
            words.next();
        else
        {
            if (words.hasNext())
                throw new StcsParsingException("Invalid space value, found " + words.next() + ", expecting " + space);
            else
                throw new StcsParsingException("Unexpected end to STC-S phrase, missing space value");
        }
    }

    protected void getFillfactor()
        throws StcsParsingException
    {
        if (words.hasNext("fillfactor"))
        {
            words.next();
            if (words.hasNextDouble())
                fill = words.nextDouble();
            else
            {
                if (words.hasNext())
                    throw new StcsParsingException("Invalid fillfactor value, expecting double, found " + words.next());
                else
                    throw new StcsParsingException("Unexpected end to STC-S phrase, missing fillfactor value");
            }
        }
    }

    protected void getFrame()
        throws StcsParsingException
    {
        if (words.hasNext())
        {
            frame = words.next();
            if (!Frame.FRAMES.contains(frame))
                throw new StcsParsingException("Invalid frame element " + frame);
        }
        else
        {
            throw new StcsParsingException("Unexpected end to STC-S phrase, missing frame element");
        }
    }

    protected void getRefpos()
        throws StcsParsingException
    {
        if (words.hasNext())
        {
            currentWord = words.next();
            if (ReferencePosition.REFERENCE_POSITIONS.contains(currentWord))
            {
                refpos = currentWord;
                currentWord = null;
            }
        }
    }

    protected void getFlavor()
        throws StcsParsingException
    {
        if (currentWord == null)
        {
            if (words.hasNext())
                currentWord = words.next();
        }
        if (Flavor.FLAVORS.contains(currentWord))
        {
            flavor = currentWord;
            currentWord = null;
        }
    }

    protected void getDimensionality()
    {
        String f = flavor;
        if (f == null)
            f = DEFAULT_FLAVOR;
        if (f.equals(Flavor.CART1))
            dimensions = 1;
        if (f.equals(Flavor.CART2) || f.equals(Flavor.SPHER2))
            dimensions = 2;
        if (f.equals(Flavor.CART3) || f.equals(Flavor.SPHER3) || f.equals(Flavor.UNITSPHER))
            dimensions = 3;
    }

    protected void getPosition()
        throws StcsParsingException
    {
        position = getListForElement("Position");
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
                if (SpatialUnit.UNITS.contains(currentWord))
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

    protected void getSize()
        throws StcsParsingException
    {
        size = getListForElement("Size");
    }

    protected void getPixSize()
        throws StcsParsingException
    {
        pixsiz = getListForElement("PixSize");
    }

    protected void getVelocity()
        throws StcsParsingException
    {
        if (endOfWords)
            return;
        String subPhrase = words.nextLine();
        if (currentWord != null)
            subPhrase = currentWord + " " + subPhrase;
        velocity = (Velocity) STC.parse(subPhrase);
    }

    protected String listToString(List<Double> list)
    {
        StringBuilder sb = new StringBuilder();
        for (Double d : list)
            sb.append(doubleFormat.format(d)).append(" ");
        return sb.toString();
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
            if (!words.hasNext())
                endOfWords = true;
        }
        return values;
    }

}
