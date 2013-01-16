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

/**
 * Abstract base class for a STC-S Region.
 * 
 */
public abstract class Region
{
    private String name;
    private String frame;
    private String refpos;
    private String flavor;

    /**
     * Construct an Region with the given name and coordinate system. The
     * name should not be null or an empty String.
     * 
     * @param name the name of the Region.
     * @param coordsys the Region coordinate system, which is a space delimited
     *                 string containing any of frame, reference position, or flavor.
     */
    public Region(String name, String coordsys)
    {
        this.name = name;
        this.frame = null;
        this.refpos = null;
        this.flavor = null;

        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("Null or empty name");

        // If coordsys is null or empty string.
        if (coordsys == null || coordsys.trim().isEmpty())
            return;

        // Split coordsys on whitespace.
        String[] tokens = coordsys.split("\\s+");

        // First token could be Frame, Reference Position, or Flavor.
        if (tokens.length >= 1)
        {
            String token = tokens[0].toUpperCase();
            if (Frame.contains(token))
                frame = tokens[0];
            else if (ReferencePosition.contains(token))
                this.refpos = tokens[0];
            else if (Flavor.contains(token))
                flavor = tokens[0];
            else
                throw new IllegalArgumentException("Invalid coordsys value: " + tokens[0]);
        }

        // Second token can only be Reference Position or Flavor.
        if (tokens.length >= 2)
        {
            String token = tokens[1].toUpperCase();
            if (ReferencePosition.contains(token))
                refpos = tokens[1];
            else if (Flavor.contains(token))
                flavor = tokens[1];
            else
                throw new IllegalArgumentException("Invalid coordsys value: " + tokens[1]);
        }

        // Third token must be Reference Position.
        if (tokens.length == 3)
        {
            if (Flavor.contains(tokens[2].toUpperCase()))
                flavor = tokens[2];
            else
                throw new IllegalArgumentException("Invalid coordsys value: " + tokens[2]);
        }
    }

    /**
     * Construct a Region with the given name and coordinate descriptions. The
     * name should not be null or an empty String.
     *
     * @param name the name of the Region.
     * @param frame the frame describing the Region. Allowed values for frame are
     *              from <code>ca.nrc.cadc.stc.Frame</code>.
     * @param refpos the reference position describing the Region. Allowed values
     *               for reference position are from <code>ca.nrc.cadc.stc.ReferencePosition</code>.
     * @param flavor the flavor describing the Region. Allowed values for flavor are
     *               from <code>ca.nrc.cadc.stc.Flavor</code>.
     * @param regions the regions of the Intersection.
     */
    public Region(String name, String frame, String refpos, String flavor)
    {
        this.name = name;
        this.frame = frame;
        this.refpos = refpos;
        this.flavor = flavor;

        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("Null or empty name");

        if (frame == null || frame.trim().isEmpty())
            this.frame = null;
        else
        {
            if (!Frame.contains(frame.toUpperCase()))
                throw new IllegalArgumentException("Invalid frame: " + frame);
        }

        if (refpos == null || refpos.trim().isEmpty())
            this.refpos = null;
        else
        {
            if (!ReferencePosition.contains(refpos.toUpperCase()))
                throw new IllegalArgumentException("Invalid reference position: " + refpos);
        }

        if (flavor == null || flavor.trim().isEmpty())
            this.flavor = null;
        else
        {
            if (!Flavor.contains(flavor.toUpperCase()))
                throw new IllegalArgumentException("Invalid coordinate flavor: " + flavor);
        }
    }

    /**
     * Get the name of this Region.
     *
     * @return the name of the Region.
     */
    public String getName()
    {
        return name;
    }

    /**
     * Get the frame of this Region.
     *
     * @return the frame of this Region.
     */
    public String getFrame()
    {
        return frame;
    }

    /**
     * Get the reference position of this Region.
     *
     * @return the reference position of this Region.
     */
    public String getRefPos()
    {
        return refpos;
    }

    /**
     * Get the flavor of this Region.
     *
     * @return the flavor of this Region.
     */
    public String getFlavor()
    {
        return flavor;
    }

}
