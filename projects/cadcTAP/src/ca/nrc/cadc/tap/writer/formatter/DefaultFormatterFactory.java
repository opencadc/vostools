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

import ca.nrc.cadc.tap.schema.ColumnDesc;
import ca.nrc.cadc.tap.schema.ParamDesc;
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
        FormatterFactory ret = new DefaultFormatterFactory();
        try
        {
            Class c = Class.forName(IMPL_CLASS);
            ret = (FormatterFactory) c.newInstance();
        }
        catch (Exception e)
        {
            LOG.warn("Failed to instantiate FormatterFactory class " + IMPL_CLASS +", using " + ret.getClass().getName());
        }
        return ret;
    }

    public void setJobID(String jobID)
    {
        this.jobID = jobID;
    }

    public void setParamList(List<Parameter> params)
    {
        this.params = params;
    }

    public List<Formatter> getFormatters(List<ParamDesc> selectList)
    {
        List<Formatter> formatters = new ArrayList<Formatter>();
        for (ParamDesc paramDesc : selectList)
        {
            if (paramDesc != null)
            {
                if (paramDesc.columnDesc != null)
                    formatters.add(getFormatter(paramDesc.columnDesc));
                else
                    formatters.add(getFormatter(paramDesc));
            }
        }
        return formatters;
    }

    /**
     * Return the default formatter when no type-specific one is found.
     *
     * @return a DefaultFormatter
     */
    protected Formatter getDefaultFormatter()
    {
        return new DefaultFormatter();
    }

    /**
     * Create a formatter for the specified parameter description. The default implementation simply
     * checks the datatype in the argument ParamDesc and then calls the appropriate (public) get<type>Formatter
     * method. Subclasses should override this method if they need to support additional datatypes
     * (as specified in the TapSchema: tap_schema.columns.datatype).
     * 
     * @param columnDesc
     * @return
     */
    protected Formatter getFormatter(ColumnDesc columnDesc)
    {
        String datatype = columnDesc.datatype;
        if (datatype.equalsIgnoreCase("adql:INTEGER"))
            return getIntegerFormatter(columnDesc);
        
        if (datatype.equalsIgnoreCase("adql:BIGINT"))
            return getLongFormatter(columnDesc);
        
        if (datatype.equalsIgnoreCase("adql:DOUBLE"))
            return getDoubleFormatter(columnDesc);
        
        if (datatype.equalsIgnoreCase("adql:VARCHAR"))
            return getStringFormatter(columnDesc);
        
        if (datatype.equalsIgnoreCase("adql:TIMESTAMP"))
            return getTimestampFormatter(columnDesc);
        
        if (datatype.equalsIgnoreCase("adql:VARBINARY"))
            return getByteArrayFormatter(columnDesc);
        
        if (datatype.equalsIgnoreCase("adql:POINT"))
            return getPointFormatter(columnDesc);
        
        if (datatype.equalsIgnoreCase("adql:REGION"))
            return getRegionFormatter(columnDesc);

        if (datatype.equalsIgnoreCase("adql:CLOB"))
            return getClobFormatter(columnDesc);

        // VOTable datatypes in the tap_schema.columns.datatype: legal?
        // needed if the database has an array of numeric values since
        // there is no adql equivalent
        if (datatype.equalsIgnoreCase("votable:int"))
            if (columnDesc.size != null && columnDesc.size > 1)
                return getIntArrayFormatter(columnDesc);
            else
                return getIntegerFormatter(columnDesc);

        if (datatype.equalsIgnoreCase("votable:long"))
            if (columnDesc.size != null && columnDesc.size > 1)
                return getLongArrayFormatter(columnDesc);
            else
                return getLongFormatter(columnDesc);

        if (datatype.equalsIgnoreCase("votable:float"))
            if (columnDesc.size != null && columnDesc.size > 1)
                return getFloatArrayFormatter(columnDesc);
            else
                return getRealFormatter(columnDesc);

        if (datatype.equalsIgnoreCase("votable:double"))
            if (columnDesc.size != null && columnDesc.size > 1)
                return getDoubleArrayFormatter(columnDesc);
            else
                return getDoubleFormatter(columnDesc);
        
        return getDefaultFormatter();
    }

    protected Formatter getFormatter(ParamDesc paramDesc)
    {
        String datatype = paramDesc.datatype;

        if (datatype == null)
            return getDefaultFormatter();

        if (datatype.equalsIgnoreCase("adql:TIMESTAMP"))
            return new UTCTimestampFormatter();

        if (datatype.equalsIgnoreCase("adql:VARBINARY"))
            return new ByteArrayFormatter();

        if (datatype.equalsIgnoreCase("adql:POINT"))
            return getPointFormatter(paramDesc.columnDesc);

        if (datatype.equalsIgnoreCase("adql:REGION"))
            return getRegionFormatter(paramDesc.columnDesc);
        
        return getDefaultFormatter();
    }

    /**
     * @param columnDesc
     * @return a DefaultFormatter
     */
    protected Formatter getIntegerFormatter(ColumnDesc columnDesc)
    {
        return getDefaultFormatter();
    }

    /**
     * @param columnDesc
     * @return a DefaultFormatter
     */
    protected Formatter getRealFormatter(ColumnDesc columnDesc)
    {
        return getDefaultFormatter();
    }

    /**
     * @param columnDesc
     * @return a DefaultFormatter
     */
    protected Formatter getDoubleFormatter(ColumnDesc columnDesc)
    {
        return getDefaultFormatter();
    }


    /**
     * @param columnDesc
     * @return a DefaultFormatter
     */
    protected Formatter getLongFormatter(ColumnDesc columnDesc)
    {
        return getDefaultFormatter();
    }

    /**
     * @param columnDesc
     * @return a DefaultFormatter
     */
    protected Formatter getStringFormatter(ColumnDesc columnDesc)
    {
        return getDefaultFormatter();
    }

    /**
     * @param columnDesc
     * @return a ByteArrayFormatter
     */
    protected Formatter getByteArrayFormatter(ColumnDesc columnDesc)
    {
        return new ByteArrayFormatter();
    }

    /**
     * @param columnDesc
     * @return an IntArrayFormatter
     */
    protected Formatter getIntArrayFormatter(ColumnDesc columnDesc)
    {
        return new IntArrayFormatter();
    }

    /**
     * @param columnDesc
     * @return an LongArrayFormatter
     */
    protected Formatter getLongArrayFormatter(ColumnDesc columnDesc)
    {
        return new LongArrayFormatter();
    }

    /**
     * @param columnDesc
     * @return an FloatArrayFormatter
     */
    protected Formatter getFloatArrayFormatter(ColumnDesc columnDesc)
    {
        return new FloatArrayFormatter();
    }

    /**
     * @param columnDesc
     * @return an DoubleArrayFormatter
     */
    protected Formatter getDoubleArrayFormatter(ColumnDesc columnDesc)
    {
        return new DoubleArrayFormatter();
    }


    /**
     * @param columnDesc
     * @return a UTCTimestampFormatter
     */
    protected Formatter getTimestampFormatter(ColumnDesc columnDesc)
    {
        return new UTCTimestampFormatter();
    }

    /**
     * @param columnDesc
     * @return a DefaultFormatter
     * @throws UnsupportedOperationException
     */
    protected Formatter getPointFormatter(ColumnDesc columnDesc)
    {
        throw new UnsupportedOperationException("no formatter for column " + columnDesc.columnName);
    }

    /**
     * @param columnDesc
     * @return a DefaultFormatter
     * @throws UnsupportedOperationException
     */
    protected Formatter getRegionFormatter(ColumnDesc columnDesc)
    {
        throw new UnsupportedOperationException("no formatter for column " + columnDesc.columnName);
    }

    /**
     * @param columnDesc
     * @return a DefaultFormatter
     * @throws UnsupportedOperationException
     */
    protected Formatter getClobFormatter(ColumnDesc columnDesc)
    {
        return getDefaultFormatter();
    }

}
