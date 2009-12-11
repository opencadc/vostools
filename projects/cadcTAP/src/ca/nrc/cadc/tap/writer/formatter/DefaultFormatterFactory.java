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

package ca.nrc.cadc.tap.writer.formatter;

import ca.nrc.cadc.tap.parser.adql.TapSelectItem;
import ca.nrc.cadc.tap.schema.ColumnDesc;
import ca.nrc.cadc.tap.schema.SchemaDesc;
import ca.nrc.cadc.tap.schema.TableDesc;
import ca.nrc.cadc.tap.schema.TapSchema;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;

/**
 * Returns a Formatter for a given data type.
 *
 */
public class DefaultFormatterFactory implements FormatterFactory
{
    private static final String CLASS_NAME = "ca.nrc.cadc.tap.writer.formatter.DefaultFormatterFactory";
    private static final Logger LOG = Logger.getLogger(DefaultFormatterFactory.class);

    public DefaultFormatterFactory() { }

    public static FormatterFactory getFormatterFactory()
    {
        try
        {
            return (FormatterFactory) Class.forName(CLASS_NAME).newInstance();
        }
        catch (Exception e)
        {
            LOG.error("Failed to instantiate FormatterFactory class " + CLASS_NAME, e);
            throw new RuntimeException(e);
        }
    }

    public List<Formatter> getFormatters(TapSchema tapSchema, List<TapSelectItem> selectList)
    {
        List<Formatter> formatters = new ArrayList<Formatter>();
        for (TapSelectItem selectItem : selectList)
        {
            if (selectItem != null)
                formatters.add(getFormatter(tapSchema, selectItem));
        }
        return formatters;
    }

    public Formatter getFormatter(TapSchema tapSchema, TapSelectItem selectItem)
    {
        // Find the class name of the formatter for this colummn.
        for (SchemaDesc schemaDesc : tapSchema.schemaDescs)
        {
            for (TableDesc tableDesc : schemaDesc.tableDescs)
            {
                if (tableDesc.tableName.equals(selectItem.getTableName()))
                {
                    for (ColumnDesc columnDesc : tableDesc.columnDescs)
                    {
                        if (columnDesc.columnName.equals(selectItem.getColumnName()))
                        {
                            String datatype = columnDesc.datatype;
                            if (datatype.equals("adql:INTEGER"))
                                return getIntegerFormatter(columnDesc);
                            if (datatype.equals("adql:BIGINT"))
                                return getLongFormatter(columnDesc);
                            if (datatype.equals("adql:DOUBLE"))
                                return getDoubleFormatter(columnDesc);
                            if (datatype.equals("adql:VARCHAR"))
                                return getStringFormatter(columnDesc);
                            else if (datatype.equals("adql:POINT"))
                                return getSPointFormatter(columnDesc);
                            else if (datatype.equals("adql:REGION"))
                                return getRegionFormatter(columnDesc);
                            else if (datatype.equals("adql:TIMESTAMP"))
                                return getUTCTimestampFormatter(columnDesc);
                            else if (datatype.equals("adql:VARBINARY"))
                                return getByteArrayFormatter(columnDesc);
                            else if (datatype.equals("int[]"))
                                return getIntArrayFormatter(columnDesc);
                            return new DefaultFormatter();
                        }
                    }
                }
            }
        }

        // Custom formatter not found, return the default Formatter.
        return new DefaultFormatter();
    }

    public Formatter getIntegerFormatter(ColumnDesc columnDesc)
    {
        return new DefaultFormatter();
    }

    public Formatter getDoubleFormatter(ColumnDesc columnDesc)
    {
        return new DefaultFormatter();
    }

    public Formatter getLongFormatter(ColumnDesc columnDesc)
    {
        return new DefaultFormatter();
    }

    public Formatter getStringFormatter(ColumnDesc columnDesc)
    {
        return new DefaultFormatter();
    }

    public Formatter getByteArrayFormatter(ColumnDesc columnDesc)
    {
        return new ByteArrayFormatter();
    }

    public Formatter getIntArrayFormatter(ColumnDesc columnDesc)
    {
        return new IntArrayFormatter();
    }

    public Formatter getLocalTimestampFormatter(ColumnDesc columnDesc)
    {
        return new LocalTimestampFormatter();
    }

    public Formatter getUTCTimestampFormatter(ColumnDesc columnDesc)
    {
        return new UTCTimestampFormatter();
    }

    public Formatter getSPointFormatter(ColumnDesc columnDesc)
    {
        throw new UnsupportedOperationException();
    }

    public Formatter getRegionFormatter(ColumnDesc columnDesc)
    {
        throw new UnsupportedOperationException();
    }
    
}
