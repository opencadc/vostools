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
package ca.nrc.cadc.tap;

import ca.nrc.cadc.tap.schema.ColumnDesc;
import ca.nrc.cadc.tap.schema.ParamDesc;
import ca.nrc.cadc.tap.schema.SchemaDesc;
import ca.nrc.cadc.tap.schema.TableDesc;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.ParameterUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 *
 * @author jburke
 */
public abstract class PqlQuery implements TapQuery
{
    protected static Logger log = Logger.getLogger(PqlQuery.class);

    /*
     * The TapSchema content.
     */
    protected TapSchema tapSchema;

    /*
     * Temporary tables not described in the TapSchema.
     */
    protected Map<String, TableDesc> extraTables;

    /*
     * List of request parameters.
     */
    protected List<Parameter> paramList;

    /*
     * Maximum number of rows returned from query.
     */
    protected Integer maxRows;

    /*
     * Request parameters from the TapSchema and their values.
     */
    protected Map<TableDesc, String[]> tapSchemaParameters;

    protected transient boolean navigated = false;

    /**
     * Default no-arg constructor.
     */
    public PqlQuery() { }

    /**
     * Caller provides the complete TapSchema content.
     *
     * @param ts
     */
    public void setTapSchema(TapSchema ts)
    {
        this.tapSchema = ts;
    }

    /**
     * Caller provides the original table names and metadata for temporary tables not
     * described in TapSchema.
     *
     * @param extraTables
     */
    public void setExtraTables(Map<String, TableDesc> extraTables)
    {
        this.extraTables = extraTables;
    }

    /**
     * Set the parameter list. Calling this method clears all previous
     * parsing state.
     *
     * @param params
     */
    public void setParameterList(List<Parameter> params)
    {
        this.paramList = params;    
    }

    /**
     * @return the SQL query to execute
     */
    public abstract String getSQL();

    /**
     * @return the metadata for columns in the result set
     */
    public abstract List<ParamDesc> getSelectList();

    /**
     * Limit number of table rows.
     *
     * @param count
     */
    public void setMaxRowCount(Integer count)
    {
        this.maxRows = count;
    }

    /**
     * Get the effective row count limit. The QueryRunner class will use this to
     * limit output to the lesser of this value and the user-specified MAXREC and
     * will actually write one row less than this along with an overflow indicator.
     *
     * @return max number of rows the query will return, null means unlimited
     */
    public Integer getMaxRowCount()
    {
        return maxRows;
    }

    /**
     * Given the List of request Parameters, builds a Map of TableDesc to
     * String array. If a request parameter name matches a TapSchema
     * [schema.]table.column name, create a new TableDesc and adds it to
     * the Map with the associated request parameter value.
     *
     * It is expected that setParameterList(List<Parameter> params) and
     * setTapSchema(TapSchema ts) will be invoked before calling this method.
     * 
     */
    protected void setTapSchemaParameters()
    {        
        if (paramList == null)
            throw new IllegalStateException("setParameterList(List<Parameter> params) must be called before using this method");

        if (tapSchema == null)
            throw new IllegalStateException("setTapSchema(TapSchema ts) must be called before using this method");

        // Check for request parameters that match tap_schema names.
        List<SchemaDesc> schemaDescs = tapSchema.getSchemaDescs();
        for (SchemaDesc schemaDesc : schemaDescs)
        {
            // Skip the tap_schema schema.
            if (schemaDesc.schemaName != null && schemaDesc.schemaName.equals("tap_schema"))
                continue;

            // Skip schemas with no tables.
            if (schemaDesc.tableDescs == null || schemaDesc.tableDescs.isEmpty())
                continue;

            // For each schema get the list of tables.
            for (TableDesc tableDesc : schemaDesc.tableDescs)
            {
                // Skip empty tables.
                if (tableDesc.columnDescs == null || tableDesc.columnDescs.isEmpty())
                    continue;

                // For each table get the list of columns.
                for (ColumnDesc columnDesc : tableDesc.columnDescs)
                {
                    // Skip empty columns, necessary?
                    if (columnDesc.columnName == null || columnDesc.columnName.isEmpty())
                        continue;

                    // Build the fully qualified name for this column.
                    StringBuilder sb = new StringBuilder();
                    if (schemaDesc.schemaName != null && !schemaDesc.schemaName.isEmpty())
                    {
                        sb.append(schemaDesc.schemaName);
                        sb.append(".");
                    }
                    sb.append(tableDesc.getSimpleTableName());
                    sb.append(".");
                    sb.append(columnDesc.columnName);
                    String fqn = sb.toString();

                    // Check if the paramList contains the fully qualified column name.
                    List<String> values = ParameterUtil.findParameterValues(fqn, paramList);
                    if (values == null)
                        continue;

                    // Create a new TableDesc and add with the values to the tableParameters.
                    TableDesc newTableDesc = new TableDesc(tableDesc.schemaName,
                                                           tableDesc.getSimpleTableName(),
                                                           tableDesc.description,
                                                           tableDesc.utype);
                    ColumnDesc newColumnDesc = new ColumnDesc(columnDesc.tableName,
                                                              columnDesc.columnName,
                                                              columnDesc.description,
                                                              columnDesc.utype,
                                                              columnDesc.ucd,
                                                              columnDesc.unit,
                                                              columnDesc.datatype,
                                                              columnDesc.size);
                    List<ColumnDesc> newColumnDescs = new ArrayList<ColumnDesc>();
                    newColumnDescs.add(newColumnDesc);
                    newTableDesc.columnDescs = newColumnDescs;
                    if (tapSchemaParameters == null)
                        tapSchemaParameters = new HashMap<TableDesc, String[]>();
                    tapSchemaParameters.put(newTableDesc, values.toArray(new String[0]));
                    log.debug("parameter " + fqn + " = " + values.toString());
                }
            }
        }
    }

}
