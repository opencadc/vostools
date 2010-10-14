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

import ca.nrc.cadc.tap.parser.TapSelectItem;
import ca.nrc.cadc.tap.schema.ColumnDesc;
import ca.nrc.cadc.tap.schema.SchemaDesc;
import ca.nrc.cadc.tap.schema.TableDesc;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.uws.Parameter;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;

/**
 * Returns a Formatter for a given data type.
 *
 */
public class DefaultFormatterFactory implements FormatterFactory
{
    private static final String IMPL_CLASS = "ca.nrc.cadc.tap.impl.FormatterFactoryImpl";
    private static final Logger LOG = Logger.getLogger(DefaultFormatterFactory.class);

    protected String jobID;
    protected List<Parameter> params;
    
    public DefaultFormatterFactory() { }

    /**
     * Create a FormatterFactory. This method loads and instantiates a class named
     * <code>ca.nrc.cadc.tap.impl.FormatterFactoryImpl</code> that must be provided
     * at runtime (by the application). The simplest way to provide that class is to
     * extend this one.
     *
     * @return a FormatterFactory implementation
     * @throws RuntimeException if the implementation class cannot be created
     */  
    public static FormatterFactory getFormatterFactory()
    {
        try
        {
            return (FormatterFactory) Class.forName(IMPL_CLASS).newInstance();
        }
        catch (Exception e)
        {
            LOG.error("Failed to instantiate FormatterFactory class " + IMPL_CLASS, e);
            throw new RuntimeException(e);
        }
    }

    public void setJobID(String jobID)
    {
        this.jobID = jobID;
    }
    
    public void setParamList(List<Parameter> params)
    {
        this.params = params;
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

    /**
     * Finds a ColumnDesc in the TapSchema that matches the argument selectItem and returns the
     * right Formatter. This method  finds the ColumnDesc and then calls getFormatter(ColumnDesc).
     * If the selectItem cannot be found in the TapSchema (eg it is a function or operator expression),
     * a DefaultFormatter is returned.
     * 
     * @param tapSchema
     * @param selectItem
     * @return a Formatter for the selectItem
     */
    public Formatter getFormatter(TapSchema tapSchema, TapSelectItem selectItem)
    {
        // Find the class name of the formatter for this colummn.
        for (SchemaDesc schemaDesc : tapSchema.schemaDescs)
        {
        	if (schemaDesc.tableDescs == null)
        		continue;
            for (TableDesc tableDesc : schemaDesc.tableDescs)
            {
                if (tableDesc.tableName.equals(selectItem.getTableName()))
                {
                    for (ColumnDesc columnDesc : tableDesc.columnDescs)
                    {
                        if (columnDesc.columnName.equals(selectItem.getColumnName()))
                        {
                            return getFormatter(columnDesc);
                        }
                    }
                }
            }
        }

        // Custom formatter not found, return the default Formatter.
        return new DefaultFormatter();
    }
    
    /**
     * Create a formatter for the specified column. The default implementation simply checks the 
     * datatype in the argument ColumnDesc and then calls the appropriate (public) get<type>Formatter
     * method. Subclasses should override this method if they need to support additional datatypes
     * (as specified in the TapSchema: tap_schema.columns.datatype).
     * 
     * @param desc
     * @return
     */
    protected Formatter getFormatter(ColumnDesc desc)
    {
        String datatype = desc.datatype;
        if (datatype.equalsIgnoreCase("adql:INTEGER"))
            return getIntegerFormatter(desc);
        
        if (datatype.equalsIgnoreCase("adql:BIGINT"))
            return getLongFormatter(desc);
        
        if (datatype.equalsIgnoreCase("adql:DOUBLE"))
            return getDoubleFormatter(desc);
        
        if (datatype.equalsIgnoreCase("adql:VARCHAR"))
            return getStringFormatter(desc);
        
        if (datatype.equalsIgnoreCase("adql:TIMESTAMP"))
            return getTimestampFormatter(desc);
        
        if (datatype.equalsIgnoreCase("adql:VARBINARY") || datatype.equalsIgnoreCase("byte[]"))
            return getByteArrayFormatter(desc);
        
        if (datatype.equalsIgnoreCase("int[]"))
            return getIntArrayFormatter(desc);

        if (datatype.equalsIgnoreCase("double[]"))
            return getDoubleArrayFormatter(desc);
        
        if (datatype.equalsIgnoreCase("adql:POINT"))
            return getPointFormatter(desc);
        
        if (datatype.equalsIgnoreCase("adql:REGION"))
            return getRegionFormatter(desc);

        if (datatype.equalsIgnoreCase("adql:CLOB"))
            return getClobFormatter(desc);
        
        return new DefaultFormatter();
    }

    /**
     * @param columnDesc
     * @return a DefaultFormatter
     */
    public Formatter getIntegerFormatter(ColumnDesc columnDesc)
    {
        return new DefaultFormatter();
    }

    /**
     * @param columnDesc
     * @return a DefaultFormatter
     */
    public Formatter getDoubleFormatter(ColumnDesc columnDesc)
    {
        return new DefaultFormatter();
    }

    /**
     * @param columnDesc
     * @return a DefaultFormatter
     */
    public Formatter getLongFormatter(ColumnDesc columnDesc)
    {
        return new DefaultFormatter();
    }

    /**
     * @param columnDesc
     * @return a DefaultFormatter
     */
    public Formatter getStringFormatter(ColumnDesc columnDesc)
    {
        return new DefaultFormatter();
    }

    /**
     * @param columnDesc
     * @return a ByteArrayFormatter
     */
    public Formatter getByteArrayFormatter(ColumnDesc columnDesc)
    {
        return new ByteArrayFormatter();
    }

    /**
     * @param columnDesc
     * @return an IntArrayFormatter
     */
    public Formatter getIntArrayFormatter(ColumnDesc columnDesc)
    {
        return new IntArrayFormatter();
    }

    /**
     * @param columnDesc
     * @return an IntArrayFormatter
     */
    public Formatter getDoubleArrayFormatter(ColumnDesc columnDesc)
    {
        return new DoubleArrayFormatter();
    }


    /**
     * @param columnDesc
     * @return a UTCTimestampFormatter
     */
    public Formatter getTimestampFormatter(ColumnDesc columnDesc)
    {
        return new UTCTimestampFormatter();
    }

    /**
     * @param columnDesc
     * @return a DefaultFormatter
     * @throws UnsupportedOperationException
     */
    public Formatter getPointFormatter(ColumnDesc columnDesc)
    {
        throw new UnsupportedOperationException("no formatter for column " + columnDesc.columnName);
    }

    /**
     * @param columnDesc
     * @return a DefaultFormatter
     * @throws UnsupportedOperationException
     */
    public Formatter getRegionFormatter(ColumnDesc columnDesc)
    {
        throw new UnsupportedOperationException("no formatter for column " + columnDesc.columnName);
    }

    /**
     * @param columnDesc
     * @return a DefaultFormatter
     * @throws UnsupportedOperationException
     */
    public Formatter getClobFormatter(ColumnDesc columnDesc)
    {
        return new DefaultFormatter();
    }

}
