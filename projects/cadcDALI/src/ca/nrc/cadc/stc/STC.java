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

import ca.nrc.cadc.stc.util.BoxFormat;
import ca.nrc.cadc.stc.util.CircleFormat;
import ca.nrc.cadc.stc.util.IntersectionFormat;
import ca.nrc.cadc.stc.util.NotFormat;
import ca.nrc.cadc.stc.util.PolygonFormat;
import ca.nrc.cadc.stc.util.PositionFormat;
import ca.nrc.cadc.stc.util.SpectralIntervalFormat;
import ca.nrc.cadc.stc.util.UnionFormat;

/**
 * Factory methods to create a Region from a STC-S phrase,
 * and to build a STC-S phrase from a Region.
 *
 */
public class STC
{
    /**
     * Parse a STC-S phrase to a Region. If the phrase is null or empty, null
     * is returned.
     *
     * @param phrase the STC-S phrase to parse.
     * @return a Region object.
     * @throws StcsParsingException if unable to parse the phrase.
     */
    public static Region parseRegion(String phrase)
        throws StcsParsingException
    {
        if (phrase == null || phrase.trim().isEmpty())
            return null;

        // Find index of first whitespace in phrase.
        phrase = phrase.trim();
        int index = phrase.indexOf(" ");

        // Parse out the first word which should be the region.
        String region;
        if (index == -1)
            region = phrase;
        else
            region = phrase.substring(0, index);

        if (region.equalsIgnoreCase(Box.NAME))
        {
            BoxFormat format = new BoxFormat();
            return format.parse(phrase);
        }
        if (region.equalsIgnoreCase(Circle.NAME))
        {
            CircleFormat format = new CircleFormat();
            return format.parse(phrase);
        }
        if (region.equalsIgnoreCase(Not.NAME))
        {
            NotFormat format = new NotFormat();
            return format.parse(phrase);
        }
        if (region.equalsIgnoreCase(Polygon.NAME))
        {
            PolygonFormat format = new PolygonFormat();
            return format.parse(phrase);
        }
        if (region.equalsIgnoreCase(Position.NAME))
        {
            PositionFormat format = new PositionFormat();
            return format.parse(phrase);
        }
        if (region.equalsIgnoreCase(Union.NAME))
        {
            UnionFormat format = new UnionFormat();
            return format.parse(phrase);
        }
        if (region.equalsIgnoreCase(Intersection.NAME))
        {
            IntersectionFormat format = new IntersectionFormat();
            return format.parse(phrase);
        }
        throw new UnsupportedOperationException("Unsupported phrase " + phrase);
    }

    /**
     * Parse a STC-S phrase to a SpectralInterval. If the phrase is null or
     * empty, null is returned.
     *
     * @param phrase the STC-S phrase to parse.
     * @return a SpectralInterval object.
     * @throws StcsParsingException if unable to parse the phrase.
     */
    public static SpectralInterval parseSpectralInterval(String phrase)
        throws StcsParsingException
    {
        if (phrase == null || phrase.trim().isEmpty())
            return null;

        // Find index of first whitespace in phrase.
        phrase = phrase.trim();
        SpectralIntervalFormat format = new SpectralIntervalFormat();
        return format.parse(phrase);
    }

    /**
     * Parse a STC-S phrase to a AstroCoordArea. If the phrase is null or empty,
     * null is returned.
     *
     * @param phrase the STC-S phrase to parse.
     * @return an AstroCoordArea object.
     * @throws StcsParsingException if unable to parse the phrase.
     */
    public static AstroCoordArea parseAstroCoordArea(String phrase)
        throws StcsParsingException
    {
        if (phrase == null || phrase.trim().isEmpty())
            return null;

        // Find index of first whitespace in phrase.
        phrase = phrase.trim();

        // Get the first word of the phrase.
        int index = 0;
        String[] words = phrase.split("\\s+");
        String currentWord = words[index];

        Region region = null;
        if (Regions.contains(currentWord.toUpperCase()))
        {
            StringBuilder sb = new StringBuilder();
            sb.append(currentWord);
            sb.append(" ");
            for (index = 1; index < words.length; index++)
            {
                currentWord = words[index];
                if (currentWord.equalsIgnoreCase(SpectralInterval.NAME))
                {
                    break;
                }
                else
                {
                    sb.append(currentWord);
                    sb.append(" ");
                    currentWord = null;
                }
            }
            region = parseRegion(sb.toString().trim());
        }

        SpectralInterval spectralInterval = null;
        if (currentWord != null)
        {
            StringBuilder sb = new StringBuilder();
            for (int i = index; i < words.length; i++)
            {
                sb.append(words[i]);
                sb.append(" ");
            }
            spectralInterval = parseSpectralInterval(sb.toString().trim());
        }
        return new AstroCoordArea(region, spectralInterval);
    }
    
    /**
     * Parses a STC-S phrase to a Region.
     * 
     * @deprecated, use <code>parseAstroCoordArea</code>.
     * This method will be changed to return an AstroCoordArea instead
     * of a Region.
     */
    public static Region parse(String phrase)
        throws StcsParsingException
    {
        return parseRegion(phrase);
    }

    /**
     * Format a Region object to a STC-S phrase. If region is null an empty
     * String is returned.
     *
     * @param region the Region to format.
     * @return STC-S String representation of the Region.
     */
    public static String format(Region region)
    {
        if (region == null)
            return "";
        
        if (region instanceof Box)
        {
            BoxFormat format = new BoxFormat();
            return format.format((Box) region);
        }
        if (region instanceof Circle)
        {
            CircleFormat format = new CircleFormat();
            return format.format((Circle) region);
        }
        if (region instanceof Not)
        {
            NotFormat format = new NotFormat();
            return format.format((Not) region);
        }
        if (region instanceof Polygon)
        {
            PolygonFormat format = new PolygonFormat();
            return format.format((Polygon) region);
        }
        if (region instanceof Position)
        {
            PositionFormat format = new PositionFormat();
            return format.format((Position) region);
        }
        if (region instanceof Union)
        {
            UnionFormat format = new UnionFormat();
            return format.format((Union) region);
        }
        if (region instanceof Intersection)
        {
            IntersectionFormat format = new IntersectionFormat();
            return format.format((Intersection) region);
        }
        throw new UnsupportedOperationException("Unsupported Region " + region.getClass().getName());
    }

    /**
     * Format a SpectralInterval object to a STC-S phrase. If spectralInterval
     * is null, an empty String is returned.
     *
     * @param spectralInterval the SpectralInterval to format.
     * @return STC-S String representation of the SpectralInterval.
     */
    public static String format(SpectralInterval spectralInterval)
    {
        if (spectralInterval == null)
            return "";
        
        SpectralIntervalFormat format = new SpectralIntervalFormat();
        return format.format(spectralInterval);
    }

    /**
     * Format a AstroCoordArea object to a STC-S phrase. If astroCoordArea is
     * null an empty String is returned.
     *
     * @param astroCoordArea the AstroCoordArea to format.
     * @return STC-S String representation of the AstroCoordArea.
     */
    public static String format(AstroCoordArea astroCoordArea)
    {
        if (astroCoordArea == null)
            return "";

        StringBuilder sb = new StringBuilder();
        if (astroCoordArea.getRegion() != null)
        {
            sb.append(format(astroCoordArea.getRegion()));
        }
        if (astroCoordArea.getRegion() != null && astroCoordArea.getSpectralInterval() != null)
        {
            sb.append(" ");
        }
        if (astroCoordArea.getSpectralInterval() != null)
        {
            sb.append(format(astroCoordArea.getSpectralInterval()));
        }
        return sb.toString();
    }
    
}
