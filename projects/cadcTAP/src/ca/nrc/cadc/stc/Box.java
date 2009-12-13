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
 * Class to represent a STC-S Box.
 * 
 */
public class Box extends SpatialSubphrase implements Space
{
    public static final String NAME = "Box";
    public List<Double> pos;
    public List<Double> bsize;

    public Box()
    {
        super(NAME);
    }
    
    public String format(Space space)
    {
        if (!(space instanceof Box))
            throw new IllegalArgumentException("Expected Box, was " + space.getClass().getName());
        Box box = (Box) space;
        StringBuilder sb = new StringBuilder();
        sb.append(NAME).append(" ");
        if (box.fill != null)
            sb.append("fillfactor ").append(box.fill).append(" ");
        sb.append(box.frame).append(" ");
        if (box.refpos != null)
            sb.append(box.refpos).append(" ");
        if (box.flavor != null)
            sb.append(box.flavor).append(" ");
        if (box.pos != null)
            sb.append(listToString(box.pos));
        if (box.bsize != null)
            sb.append(listToString(box.bsize));
        if (box.position != null)
            sb.append("Position ").append(listToString(box.position));
        if (box.unit != null)
            sb.append("unit ").append(box.unit).append(" ");
        if (box.error != null)
            sb.append("Error ").append(listToString(box.error));
        if (box.resln != null)
            sb.append("Resolution ").append(listToString(box.resln));
        if (box.size != null)
            sb.append("Size ").append(listToString(box.size));
        if (box.pixsiz != null)
            sb.append("PixSize ").append(listToString(box.pixsiz));
        if (box.velocity != null)
            sb.append(STC.format(box.velocity));
        return sb.toString().trim();
    }

    public Space parse(String phrase)
        throws StcsParsingException
    {
        init(phrase);
        return this;
    }

    protected void getPos()
        throws StcsParsingException
    {
        // current word or next word as a Double.
        Double value = null;
        if (currentWord == null)
        {
            if (words.hasNextDouble())
                value = words.nextDouble();
            else if (words.hasNext())
                throw new StcsParsingException("Invalid pos element " + words.next());
            else
                throw new StcsParsingException("Unexpected end to STC-S phrase before pos element");
        }
        else
        {
            try
            {
                value = Double.valueOf(currentWord);
            }
            catch (NumberFormatException e)
            {
                throw new StcsParsingException("Invalid pos value " + currentWord, e);
            }
        }

        // Add all Double values up to the next element.
        List<Double> values = new ArrayList<Double>();
        values.add(value);
        while (words.hasNextDouble())
            values.add(words.nextDouble());
        
        // Should be 2 times dimensions values.
        if (values.size() / 2 != dimensions)
            throw new StcsParsingException("Invalid number of pos or bsize values");
        
        pos = new ArrayList<Double>(dimensions);
        pos.addAll(values.subList(0, dimensions));

        bsize = new ArrayList<Double>(dimensions);
        bsize.addAll(values.subList(dimensions, values.size()));

        currentWord = null;
    }

}
