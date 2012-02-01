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
 *  $Revision: 0 $
 *
 ************************************************************************
 */

package ca.nrc.cadc.tap.writer;

import ca.nrc.cadc.tap.TableWriter;
import ca.nrc.cadc.uws.Parameter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import ca.nrc.cadc.tap.schema.ParamDesc;
import ca.nrc.cadc.tap.schema.TapSchema;

import ca.nrc.cadc.tap.writer.formatter.DefaultFormatterFactory;
import ca.nrc.cadc.tap.writer.formatter.Formatter;
import ca.nrc.cadc.tap.writer.formatter.FormatterFactory;
import ca.nrc.cadc.tap.writer.formatter.ResultSetFormatter;
import ca.nrc.cadc.uws.Job;
import com.csvreader.CsvWriter;
import org.apache.log4j.Logger;

/**
 * Writes a ResultSet or Throwable as ASCII text to an OutputStream.
 * 
 * @author pdowler
 * @author Sailor Zhang
 */
public class AsciiTableWriter implements TableWriter
{
    // ASCII character set.
    public static final String US_ASCII = "US-ASCII";

    // Comma delimited format.
    public static final String CSV = "csv";

    // Tab delimited format.
    public static final String TSV = "tsv";

    // CSV format delimiter.
    public static final char CSV_DELI = ',';

    // TSV format delimiter.
    public static final char TSV_DELI = '\t';

    private static final Logger LOG = Logger.getLogger(AsciiTableWriter.class);

    // List of column names used in the select statement.
    protected List<ParamDesc> selectList;

    protected Job job;
    protected String info;

    // Maximum number of rows to write.
    protected int maxRows;

    private String format;
    private char delimeter;

    private AsciiTableWriter() { }

    /**
     * Create a new AsciiTableWriter using the specified format.
     * <p>
     * Allowed formats are:
     * <li>{@link AsciiTableWriter.CSV} - comma delimited.
     * <li>{@link AsciiTableWriter.TSV} - tab delmitited.
     * <p>
     *
     * @param format output format.
     * @throws IllegalArgumentException if the format is not one of the allowed
     * values.
     */
    public AsciiTableWriter(String format)
    {
        maxRows = Integer.MAX_VALUE;
        if (CSV.equalsIgnoreCase(format))
        {
            this.format = CSV;
            this.delimeter = CSV_DELI;
        }
        else if (TSV.equalsIgnoreCase(format))
        {
            this.format = TSV;
            this.delimeter = TSV_DELI;
        }
        else
            throw new IllegalArgumentException("illegal format: " + format);
    }

    /**
     * @deprecated
     */
    public void setJobID(String jobID) { }

    /**
     * @deprecated
     */
    public void setParameterList(List<Parameter> params) { }

    /**
     * @deprecated
     */
    public void setTapSchema(TapSchema schema) { }

    public void setJob(Job job)
    {
        this.job = job;
    }

    /**
     * The info is ignored by this class.
     * @param info
     */
    public void setQueryInfo(String info)
    {
        this.info = info;
    }

    public String getExtension()
    {
        return format;
    }

    public String getContentType()
    {
        if (format.equals(CSV))
            return "text/csv";
        return "text/tab-separated-values";
    }

    public void setSelectList(List<ParamDesc> items)
    {
        this.selectList = items;
    }

    public void setMaxRowCount(int count)
    {
        this.maxRows = count;
    }

    public void write(ResultSet rs, OutputStream out) throws IOException
    {
        if (selectList == null)
            throw new IllegalStateException("SelectList cannot be null, set using setSelectList()");

        FormatterFactory factory = DefaultFormatterFactory.getFormatterFactory();
        factory.setJobID(job.getID());
        factory.setParamList(job.getParameterList());
        List<Formatter> formatters = factory.getFormatters(selectList);

        LOG.debug("writing ResultSet, format: " + format);
        int numRows = 0;
        int numColumns = 0;
        CsvWriter writer = new CsvWriter(out, this.delimeter, Charset.forName(US_ASCII));
        try
        {
            // Add the metadata elements.
            for (ParamDesc paramDesc : selectList)
                writer.write(paramDesc.name);
            writer.endRecord();

            if (rs != null)
            {
                numColumns = rs.getMetaData().getColumnCount();
                while (rs.next())
                {
                    if (numRows >= maxRows)
                        break;

                    for (int i = 1; i <= numColumns; i++)
                    {
                        Formatter formatter = formatters.get(i - 1);
                        if (formatter instanceof ResultSetFormatter)
                            writer.write(((ResultSetFormatter) formatter).format(rs, i));
                        else
                            writer.write(formatter.format(rs.getObject(i)));
                    }
                    writer.endRecord();
                    numRows++;
                }
            }
            LOG.debug("wrote format: " + format
                    + " columns: " + numColumns+  " rows: " + numRows
                    + " [OK]");
            writer.flush();
        }
        catch (SQLException ex)
        {
            LOG.debug("wrote format: " + format
                    + " columns:" + numColumns+  " rows: " + numRows
                    + " [FAILED]");
            throw new IOException(ex);
        }
    }

}
