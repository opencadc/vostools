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

import ca.nrc.cadc.stc.Intersection;
import ca.nrc.cadc.stc.Region;
import ca.nrc.cadc.stc.Regions;
import ca.nrc.cadc.stc.STC;
import ca.nrc.cadc.stc.StcsParsingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *Class to parse a STC-S phrase to a Intersection object, and Intersection
 * a Box object to a STC-S phrase.
 */
public class IntersectionFormat extends RegionFormat implements Format<Intersection>
{
    /**
     * Parses a String to a Intersection.
     *
     * @param phrase the String to parse.
     * @return Intersection value of the String.
     */
    public Intersection parse(String phrase)
        throws StcsParsingException
    {
        parseRegion(phrase);
        
        // Get the string within the opening and closing parentheses.
        int open = phrase.indexOf("(");
        int close = phrase.lastIndexOf(")");
        if (open == -1 || close == -1)
            throw new StcsParsingException("Intersection arguments must be enclosed in parentheses: " + phrase);
        String union = phrase.substring(open + 1, close).trim();

        int index = 0;
        List<Region> regions = null;
        String subPhrase = getNextRegion(union, index);
        if (subPhrase == null)
            throw new StcsParsingException("Intersection must contain a Region: " + phrase);
        while (subPhrase != null)
        {
            if (regions == null)
                regions = new ArrayList<Region>();
            regions.add(STC.parseRegion(subPhrase));
            index = index + subPhrase.length();
            subPhrase = getNextRegion(union, index);
        }

        if (regions == null || regions.size() < 2)
            throw new StcsParsingException("Intersection must contain 2 or more Regions: " + phrase);

        return new Intersection(frame, refpos, flavor, regions);
    }

    /**
     * Takes a Intersection and returns a String representation.
     * If the Intersection is null an empty String is returned.
     *
     * @param intersection Intersection to format
     * @return String representation of the Box.
     */
    public String format(Intersection intersection)
    {
        if (!(intersection instanceof Intersection))
            throw new IllegalArgumentException("Expected Intersection, was " + intersection.getClass().getName());

        StringBuilder sb = new StringBuilder();
        sb.append(formatRegion(intersection));
        sb.append(" ( ");
        for (Region r : intersection.getRegions())
        {
            sb.append(STC.format(r));
            sb.append(" ");
        }
        sb.append(")");
        return sb.toString().trim();
    }

    private String getNextRegion(String phrase, int index)
    {
        // Uppercase phrase.
        String upperPhrase = phrase.toUpperCase();

        // Search the phrase for a Region.
        Regions[] values = Regions.values();
        int[] indexes = new int[values.length];
        for (int i = 0; i < indexes.length; i++)
        {
            indexes[i] = upperPhrase.indexOf(values[i].name(), index);
        }

        // Sort in descending order.
        Arrays.sort(indexes);

        // Assign start the first positive index.
        // Assign end the second positive index.
        int start = -1;
        int end = -1;
        for (int i = 0; i < indexes.length; i++)
        {
            if (indexes[i] == -1)
                continue;
            if (start == -1)
            {
                start = indexes[i];
                continue;
            }
            if (end == -1)
            {
                end = indexes[i];
                break;
            }
        }
        if (start != -1 && end == -1)
            return phrase.substring(start);
        else if (start != -1 && end != -1)
            return phrase.substring(start, end);
        return null;
    }

}
