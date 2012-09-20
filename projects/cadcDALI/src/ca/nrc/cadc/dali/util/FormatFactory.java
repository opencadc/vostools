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
package ca.nrc.cadc.dali.util;

import ca.nrc.cadc.dali.tables.votable.TableField;
import ca.nrc.cadc.stc.Box;
import ca.nrc.cadc.stc.Circle;
import ca.nrc.cadc.stc.Intersection;
import ca.nrc.cadc.stc.Not;
import ca.nrc.cadc.stc.Polygon;
import ca.nrc.cadc.stc.Position;
import ca.nrc.cadc.stc.Union;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * This factory class implements the mapping of datatypes to VOTable according
 * to the DALI-1.0 specification.
 * 
 * @author jburke
 */
public abstract class FormatFactory
{
    // Maps Class to the appropriate Format.
    private static final Map<Class, Format> formats;
    static
    {
        formats = new HashMap<Class, Format>();
        formats.put(Boolean.class, new BooleanFormat());
        formats.put(byte[].class, new ByteArrayFormat());
        formats.put(Byte.class, new ByteFormat());
        formats.put(double[].class, new DoubleArrayFormat());
        formats.put(Double.class, new DoubleFormat());
        formats.put(float[].class, new FloatArrayFormat());
        formats.put(Float.class, new FloatFormat());
        formats.put(int[].class, new IntArrayFormat());
        formats.put(Integer.class, new IntegerFormat());
        formats.put(long[].class, new LongArrayFormat());
        formats.put(Long.class, new LongFormat());
        formats.put(short[].class, new ShortArrayFormat());
        formats.put(Short.class, new ShortFormat());
        formats.put(String.class, new StringFormat());
        formats.put(Date.class, new UTCTimestampFormat());
        formats.put(Box.class, new RegionFormat());
        formats.put(Circle.class, new RegionFormat());
        formats.put(Intersection.class, new RegionFormat());
        formats.put(Not.class, new RegionFormat());
        formats.put(Polygon.class, new RegionFormat());
        formats.put(Union.class, new RegionFormat());
        formats.put(Position.class, new PointFormat());
    }

    /**
     *
     * @param c
     * @return
     */
    public static Format getFormat(Class c)
    {
        Format format = formats.get(c);
        if (format == null)
        {
            throw new UnsupportedOperationException("Format not found for class " + c.getName());
        }
        return format;
    }

    /**
     * 
     * @param field
     * @return
     */
    public static Format getFormat(TableField field)
    {
        String datatype = field.getDatatype();
        
        if (datatype.equalsIgnoreCase("boolean"))
        {
            return new BooleanFormat();
        }
        if (datatype.equalsIgnoreCase("bit"))
        {
            if (isArray(field))
            {
                return new ByteArrayFormat();
            }
            else
            {
                return new ByteFormat();
            }
        }
        if (datatype.equalsIgnoreCase("unsignedByte"))
        {
            if (isArray(field))
            {
                return new ByteArrayFormat();
            }
            else
            {
                return new ByteFormat();
            }
        }
        if (datatype.equalsIgnoreCase("short"))
        {
            if (isArray(field))
            {
                return new ShortArrayFormat();
            }
            else
            {
                return new ShortFormat();
            }
        }
        if (datatype.equalsIgnoreCase("int"))
        {
            if (isArray(field))
            {
                return new IntArrayFormat();
            }
            else
            {
                return new IntegerFormat();
            }
        }
        if (datatype.equalsIgnoreCase("long"))
        {
            if (isArray(field))
            {
                return new LongArrayFormat();
            }
            else
            {
                return new LongFormat();
            }
        }
        if (datatype.equalsIgnoreCase("unicodeChar"))
        {
            return new StringFormat();
        }
        if (datatype.equalsIgnoreCase("float"))
        {
            if (isArray(field))
            {
                return new FloatArrayFormat();
            }
            else
            {
                return new FloatFormat();
            }
        }
        if (datatype.equalsIgnoreCase("double"))
        {
            if (isArray(field))
            {
                return new DoubleArrayFormat();
            }
            else
            {
                return new DoubleFormat();
            }
        }
        if (datatype.equalsIgnoreCase("floatComplex"))
        {
            throw new UnsupportedOperationException("floatComplex datatype not supported for column " + field.getName());
        }
        if (datatype.equalsIgnoreCase("doubleComplex"))
        {
            throw new UnsupportedOperationException("doubleComplex datatype not supported for column " + field.getName());
        }
        if (datatype.equalsIgnoreCase("char"))
        {
            if (isArray(field))
            {
                if (field.xtype != null)
                {
                    if (field.xtype.equalsIgnoreCase("adql:timestamp"))
                    {
                        return new UTCTimestampFormat();
                    }
                    if (field.xtype.equalsIgnoreCase("adql:point"))
                    {
                        return new PointFormat();
                    }
                    if (field.xtype.equalsIgnoreCase("adql:region"))
                    {
                        return new RegionFormat();
                    }
                }
            }
            return new StringFormat();
        }

        throw new UnsupportedOperationException(datatype + " datatype not supported for column " + field.getName());
    }

    private static boolean isArray(TableField field)
    {
        return field.arraysize != null && field.arraysize > 1 ||
               field.variableSize != null && field.variableSize;
    }

}
