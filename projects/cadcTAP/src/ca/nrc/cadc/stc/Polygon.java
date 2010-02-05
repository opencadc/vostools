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

/**
 * Class to represent a STC-S Polygon.
 *
 */
public class Polygon extends SpatialSubphrase implements Region
{
    public static final String NAME = "POLYGON";
    public List<CoordPair> coordPairs;

    public Polygon() {}
    
    /**
     * Create from a Box object
     * 
     * @param box
     */
    public Polygon(Box box)
    {
        double ra  = box.getCoordPair().getCoord1().doubleValue();
        double dec = box.getCoordPair().getCoord2().doubleValue();
        double halfWidth = box.getWidth().doubleValue() / 2;
        double halfHeight = box.getHeight().doubleValue() / 2;
        
        CoordPair corner;
        List<CoordPair> corners = new ArrayList<CoordPair>(4);
        corner = new CoordPair(ra - halfWidth, dec - halfHeight);
        corners.add(corner);
        corner = new CoordPair(ra - halfWidth, dec + halfHeight);
        corners.add(corner);
        corner = new CoordPair(ra + halfWidth, dec + halfHeight);
        corners.add(corner);
        corner = new CoordPair(ra + halfWidth, dec - halfHeight);
        corners.add(corner);
        
        this.coordPairs = corners;
    }

    public String format(Region space)
    {
        if (!(space instanceof Polygon))
            throw new IllegalArgumentException("Expected Polygon, was " + space.getClass().getName());
        Polygon polygon = (Polygon) space;
        StringBuilder sb = new StringBuilder();
        sb.append(NAME).append(" ");
        if (polygon.frame != null)
            sb.append(polygon.frame).append(" ");
        if (polygon.refpos != null)
            sb.append(polygon.refpos).append(" ");
        if (polygon.flavor != null)
            sb.append(polygon.flavor).append(" ");
        if (polygon.coordPairs != null)
        {
            for (CoordPair coordPair : polygon.coordPairs)
                sb.append(coordPair).append(" ");
        }
        return sb.toString().trim();
    }

    public Region parse(String phrase)
        throws StcsParsingException
    {
        init(phrase);
        return this;
    }

    protected void getCoordinates()
        throws StcsParsingException
    {
        // current word or next word as a Double.
        Double value = null;
        if (currentWord == null)
        {
            if (words.hasNextDouble())
                value = words.nextDouble();
        }
        else
        {
            try
            {
                value = Double.valueOf(currentWord);
            }
            catch (NumberFormatException ignore) {}
        }

        // Double value not found?
        if (value == null)
            throw new StcsParsingException("coordpair values not found");

        // Get the first coordpair.
        coordPairs = new ArrayList<CoordPair>();
        CoordPair coordPair = new CoordPair();
        coordPair.coord1 = value;
        if (words.hasNextDouble())
        {
            coordPair.coord2 = words.nextDouble();
            coordPairs.add(coordPair);
        }
        else
        {
            throw new StcsParsingException("coordpair values not found");
        }

        // Get the rest of the coordpairs.
        while (words.hasNextDouble())
        {   
            coordPair = new CoordPair();
            coordPair.coord1 = words.nextDouble();
            if (words.hasNextDouble())
            {
                coordPair.coord2 = words.nextDouble();
                coordPairs.add(coordPair);
            }
            else
            {
                throw new StcsParsingException("coordpair values not found");
            }
        }

        // Should be a even multiple of dimensions values.
        if (coordPairs.size() % dimensions != 0)
            throw new StcsParsingException("Invalid number of coordpair values " + coordPairs.size());

        currentWord = null;
    }

    public List<CoordPair> getCoordPairs()
    {
        return coordPairs;
    }

    public void setCoordPairs(List<CoordPair> coordPairs)
    {
        this.coordPairs = coordPairs;
    }

}
