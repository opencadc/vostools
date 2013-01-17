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

package ca.nrc.cadc.stc.util;

import ca.nrc.cadc.stc.Flavor;
import ca.nrc.cadc.stc.Frame;
import ca.nrc.cadc.stc.ReferencePosition;
import ca.nrc.cadc.stc.Region;
import ca.nrc.cadc.stc.Regions;
import ca.nrc.cadc.stc.StcsParsingException;
import java.text.DecimalFormat;
import java.util.Scanner;

/**
 * Base class for parsing and formatting STC-S phrases and objects.
 *
 */
public abstract class RegionFormat
{
    // Default values.
    public static final Frame DEFAULT_FRAME = Frame.UNKNOWNFRAME;
    public static final ReferencePosition DEFAULT_REFPOS = ReferencePosition.UNKNOWNREFPOS;
    public static final Flavor DEFAULT_FLAVOR = Flavor.SPHERICAL2;

    protected String name;
    protected Frame frame;
    protected ReferencePosition refpos;
    protected Flavor flavor;

    protected Scanner words;
    protected String currentWord;

    /**
     * Parse a Region to it's name and coordinate descriptions (frame,
     * reference position, flavor).
     *
     * @param phrase the phrase to parse.
     * @throws StcsParsingException if unable to parse the phrase.
     */
    protected void parseRegion(String phrase)
        throws StcsParsingException
    {
        if (phrase == null || phrase.isEmpty())
            return;
        phrase = phrase.trim();

        frame = null;
        refpos = null;
        flavor = null;
        currentWord = null;
        words = new Scanner(phrase);
        words.useDelimiter("\\s+");

        // The phrase must contain a Region name, i.e. BOX
        currentWord = getNextWord(words, currentWord);
        if (!Regions.contains(currentWord.toUpperCase()))
        {
            throw new StcsParsingException("Invalid region " + currentWord);
        }
        name = currentWord;
        currentWord = null;

        currentWord = getNextWord(words, currentWord);
        if (Frame.contains(currentWord.toUpperCase()))
        {
            frame = Frame.valueOf(currentWord.toUpperCase());
            currentWord = null;
        }

        currentWord = getNextWord(words, currentWord);
        if (ReferencePosition.contains(currentWord.toUpperCase()))
        {
            refpos = ReferencePosition.valueOf(currentWord.toUpperCase());
            currentWord = null;
        }

        currentWord = getNextWord(words, currentWord);
        if (Flavor.contains(currentWord.toUpperCase()))
        {
            flavor =Flavor.valueOf(currentWord.toUpperCase());
            currentWord = null;
        }
    }

    /**
     * Format a Region to a STC-S phrase containing the name and
     * coordinate descriptions (frame, reference position, flavor).
     *
     * @param region the region to format.
     * @return a STC-S string of the name and coordinate descriptions.
     */
    protected String formatRegion(Region region)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(region.getName());
        sb.append(" ");
        if (region.getFrame() != null)
        {
            sb.append(region.getFrame().name());
            sb.append(" ");
        }
        if (region.getRefPos() != null)
        {
            sb.append(region.getRefPos().name());
            sb.append(" ");
        }
        if (region.getFlavor() != null)
        {
            sb.append(region.getFlavor().name());
            sb.append(" ");
        }
        return sb.toString().trim();
    }

    protected String getNextWord(Scanner words, String currentWord)
        throws StcsParsingException
    {
        if (currentWord == null)
        {
            if (words.hasNext())
                return words.next();
            else
                throw new StcsParsingException("Unexpected end to STC-S phrase " + words.toString());
        }
        return currentWord;
    }

    /**
     * Get the name of this Region.
     *
     * @return the region name.
     */
    public String getName()
    {
        return name;
    }

    /**
     * Get the frame of this Region.
     *
     * @return the region frame.
     */
    public Frame getFrame()
    {
        if (frame == null)
            return DEFAULT_FRAME;
        return frame;
    }

    /**
     * Get the reference position of this Region.
     *
     * @return the region reference position.
     */
    public ReferencePosition getReferencePosition()
    {
        if (refpos == null)
            return DEFAULT_REFPOS;
        return refpos;
    }

    /**
     * get the flavor of this Region.
     *
     * @return the region flavor.
     */
    public Flavor getFlavor()
    {
        if (flavor == null)
            return DEFAULT_FLAVOR;
        return flavor;
    }
    
}
