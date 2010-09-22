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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Class to represent a STC-S Union operator.
 *
 */
public class Union extends SpatialSubphrase implements Region
{
    public static final String NAME = "UNION";

    private List<Region> regions;

    Union() { }

    public Union(String coordsys, List<Region> regions)
    {
        super(coordsys);
        this.regions = regions;
    }

    public Union(String frame, String refpos, String flavor, List<Region> regions)
    {
        super(frame, refpos, flavor);
        this.regions = regions;
    }
    
    public String format(Region space)
    {
        if (!(space instanceof Union))
            throw new IllegalArgumentException("Expected Union, was " + space.getClass().getName());
        Union union = (Union) space;
        StringBuilder sb = new StringBuilder();
        if (union.region == null)
            sb.append(NAME).append(" ");
        else
            sb.append(union.region).append(" ");
        if (union.frame != null)
            sb.append(union.frame).append(" ");
        if (union.refpos != null)
            sb.append(union.refpos).append(" ");
        if (union.flavor != null)
            sb.append(union.flavor).append(" ");
        sb.append("( ");
        for (Region r : union.regions)
            sb.append(STC.format(r)).append(" ");
        sb.append(")");
        return sb.toString();
    }

    public Region parse(String phrase)
        throws StcsParsingException
    {
        init(phrase);
        return this;
    }

    /**
     * 
     * @return
     */
    public List<Region> getRegions()
    {
        return regions;
    }

    protected void parseCoordinates()
        throws StcsParsingException
    {
        // Get the string within the opening and closing parentheses.
        int open = phrase.indexOf("(");
        int close = phrase.lastIndexOf(")");
        if (open == -1 || close == -1)
            throw new StcsParsingException("Union arguments must be enclosed in parentheses: " + phrase);
        String union = phrase.substring(open + 1, close).trim();

        List<String> phrases = getRegions(union);
        regions = new ArrayList<Region>(phrases.size());
        for (String s : phrases)
            regions.add(STC.parse(s));

        // Must be two or more regions in a Union.
        if (regions.size() < 2)
            throw new StcsParsingException("Union must contain 2 or more regions : " + phrase);
    }

    private List<String> getRegions(String subPhrase)
    {
        // Uppercase phrase.
        String upperPhrase = subPhrase.toUpperCase();

        // List of parsed regions.
        List<String> phrases = new ArrayList<String>();

        int index = -1;
        int start = 0;
        int end = 0;
        while (start != -1)
        {
            index = findRegion(upperPhrase, index);
            end = index;
            if (start != -1 && end > start)
                phrases.add(subPhrase.substring(start, end));
            else if (start != -1 && end == -1)
                phrases.add(subPhrase.substring(start));
            start = index;
            index = index + 1;
        }

        return phrases;
    }

    private int findRegion(String s, int start)
    {
        String upper = s.toUpperCase();
        Regions[] candidates = Regions.values();
        int[] indexes = new int[candidates.length];
        for (int i = 0; i < candidates.length; i++)
            indexes[i] = upper.indexOf(candidates[i].name(), start);

        // Sort in ascending order.
        Arrays.sort(indexes);

        int index = -1;
        for (int i = 0; i < indexes.length; i++)
        {
            if (indexes[i] != -1)
            {
                index = indexes[i];
                break;
            }
        }
        return index;
    }

}
