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
    public static final String DEFAULT_FRAME = Frame.UNKNOWNFRAME;
    public static final String DEFAULT_REFPOS = ReferencePosition.UNKNOWNREFPOS;
    public static final String DEFAULT_FLAVOR = Flavor.SPHERICAL2;

    // Possible regions.
    public static final String[] REGIONS = new String[]
    {
        "BOX", "CIRCLE", "POLYGON", "POSITION", "UNION", "NOT", "INTERSECTION"
    };

    // Formatter for Double values.
    protected static DecimalFormat doubleFormat;

    static
    {
        // TODO should support SN
        doubleFormat = new DecimalFormat("####.########");
        doubleFormat.setDecimalSeparatorAlwaysShown(true);
        doubleFormat.setMinimumFractionDigits(1);
    }

    /**
     * STC-S phrase elements.
     */
    public String phrase;
    public String region;

    public String frame;
    public String refpos;
    public String flavor;

    // Dimensionality of the region.
    protected int dimensions;

    // Words to process in the phrase?
    protected boolean endOfWords;

    // The current word from the scanner.
    protected String currentWord;

    // The tokenized phrase.
    protected Scanner words;

    protected SpatialSubphrase() 
    {
        this.frame = DEFAULT_FRAME;
        this.refpos = DEFAULT_REFPOS;
        this.flavor = DEFAULT_FLAVOR;
    }

    protected SpatialSubphrase(String frame, String refpos, String flavor)
    {
        this();
        if (frame != null)
        {
            if (Frame.FRAMES.contains(frame.toUpperCase()))
                this.frame = frame;
            else
                throw new IllegalArgumentException("illegal frame: " + frame);
        }
        if (refpos != null)
        {
            if (ReferencePosition.REFERENCE_POSITIONS.contains(refpos.toUpperCase()))
                this.refpos = refpos;
            else
                throw new IllegalArgumentException("illegal reference position: " + refpos);
        }
        if (flavor != null)
        {
            if (Flavor.FLAVORS.contains(flavor.toUpperCase()))
                this.flavor = flavor;
            else
                throw new IllegalArgumentException("illegal coordinate flavor: " + flavor);
        }

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

        getRegion();
        getFrame();
        getRefpos();
        getFlavor();
        getDimensionality();
        getCoordinates();
    }

    protected abstract void getCoordinates()
        throws StcsParsingException;

    protected void getRegion()
        throws StcsParsingException
    {
        if (currentWord == null)
        {
            if (words.hasNext())
                currentWord = words.next();
            else
                throw new StcsParsingException("Unexpected end to STC-S phrase " + phrase);
        }
        if (STC.arrayContains(REGIONS, currentWord.toUpperCase()))
        {
            region = currentWord;
            currentWord = null;
        }
        else
        {
            throw new StcsParsingException("Invalid region value " + currentWord);
        }
    }

    protected void getFrame()
        throws StcsParsingException
    {
        if (currentWord == null)
        {
            if (words.hasNext())
                currentWord = words.next();
            else
                throw new StcsParsingException("Unexpected end to STC-S phrase " + phrase);
        }
        if (Frame.FRAMES.contains(currentWord.toUpperCase()))
        {
            frame = currentWord;
            currentWord = null;
        }
    }

    protected void getRefpos()
        throws StcsParsingException
    {
        if (currentWord == null)
        {
            if (words.hasNext())
                currentWord = words.next();
            else
                throw new StcsParsingException("Unexpected end to STC-S phrase " + phrase);
        }
        if (ReferencePosition.REFERENCE_POSITIONS.contains(currentWord.toUpperCase()))
        {
            refpos = currentWord;
            currentWord = null;
        }
    }

    protected void getFlavor()
        throws StcsParsingException
    {
        if (currentWord == null)
        {
            if (words.hasNext())
                currentWord = words.next();
            else
                throw new StcsParsingException("Unexpected end to STC-S phrase " + phrase);
        }
        if (Flavor.FLAVORS.contains(currentWord.toUpperCase()))
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
        if (f.equalsIgnoreCase(Flavor.CARTESIAN2) || f.equalsIgnoreCase(Flavor.SPHERICAL2))
            dimensions = 2;
        if (f.equalsIgnoreCase(Flavor.CARTESIAN3))
            dimensions = 3;
    }
}
