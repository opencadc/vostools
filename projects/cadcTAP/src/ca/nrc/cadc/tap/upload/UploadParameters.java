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
package ca.nrc.cadc.tap.upload;

import ca.nrc.cadc.tap.UploadManager;
import ca.nrc.cadc.util.StringUtil;
import ca.nrc.cadc.uws.Parameter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;

/**
 * Processes the List of TAP upload parameters and creates a UploadTable for
 * each table in the List.
 *
 * @author jburke
 */
public class UploadParameters
{
    private static final Logger log = Logger.getLogger(UploadParameters.class);

    /**
     * List
     */
    public List<UploadTable> uploadTables;

    /**
     * Processes the list of upload parameters and creates a list of
     * UploadTable to describe each upload table.
     *
     * @param parameters List of upload parameters.
     * @param jobID the UWS Job ID.
     */
    public UploadParameters(List<Parameter> parameters, String jobID)
    {
        uploadTables = new ArrayList<UploadTable>();

        process(parameters, jobID);
    }
    
    /**
     * Parse each parameter in the List and create a List of UploadTable
     * to describe each upload table.
     *
     * @param parameters List of upload parameters.
     * @param jobID the UWS Job ID.
     */
    protected void process(List<Parameter> parameters, String jobID)
    {
        if (parameters == null || parameters.isEmpty())
            throw new UnsupportedOperationException("UPLOAD parameter is missing");

        // Check each parameter in the list.
        log.debug("process: " + parameters.size() + " params");
        for (Parameter parameter : parameters)
        {
            log.debug("parameter: " + parameter);
            // Skip if parameter isn't named UPLOAD.
            if (parameter == null || !parameter.getName().equals(UploadManager.UPLOAD))
                continue;

            // Throw an exception if a UPLOAD parameter doesn't have a value.
            if (parameter.getValue() == null || parameter.getValue().isEmpty())
                throw new UnsupportedOperationException("UPLOAD parameter is empty " + parameter.getName());

            // Parameter values can be semicolon delimited.
            String[] values = parameter.getValue().split(";");
            for (String value : values)
            {
                log.debug("value: " + value);
                // Table name and uri or param are comma delimited.
                String[] tableNameUri = value.split(",");
                String tableName = validateTableName(parameter, tableNameUri);
                URI uri = validateURI(parameter, tableNameUri);
                UploadTable uploadTable = new UploadTable(tableName, jobID, uri);
                uploadTables.add(uploadTable);
            }
        }
    }
    
    /**
     * Validates that the table name in the UPLOAD parameter is a valid ADQL
     * table name.
     * 
     * @param parameter a single UPLOAD parameter.
     * @param tableNameUri String[] containing table name and VOTable URI string.
     * @return validated table name.
     * @throws UnsupportedOperationException if the table name is invalid.
     */
    protected String validateTableName(Parameter parameter, String[] tableNameUri)
        throws UnsupportedOperationException
    {
        log.debug("validateTableName: " + parameter + " " + StringUtil.toString(tableNameUri));
        
        String tableName;
        try
        {
            tableName = tableNameUri[0];
        }
        catch (IndexOutOfBoundsException e)
        {
            throw new UnsupportedOperationException("UPLOAD table name is missing: " + parameter);
        }
        
        // Does table name start with a valid schema.
        int index = tableName.indexOf('.');
        if (index >= 0)
        {
            String schema = tableName.substring(0, index);
            if (!schema.equalsIgnoreCase(UploadManager.SCHEMA))
                throw new UnsupportedOperationException("UPLOAD table schema name is invalid: " + tableName);
            if (tableName.length() == index + 1)
                throw new UnsupportedOperationException("UPLOAD table name is missing: " + tableName);
            tableName = tableName.substring(index + 1);
        }
        
        try
        {
            UploadUtil.isValidateIdentifier(tableName);
        }
        catch (ADQLIdentifierException e)
        {
            throw new UnsupportedOperationException("UPLOAD table name not a valid ADQL identifier: " + tableName, e);
        }
        
        // duplicate table names are not allowed.
        for (UploadTable uploadTable : uploadTables)
        {
            if (uploadTable.tableName.equals(tableName))
                throw new UnsupportedOperationException("UPLOAD table name is a duplicate: " + tableName);
        }
        return tableName;
    }
    
    /**
     * Validates that the URI string in the UPLOAD parameter is a valid URI and
     * is a supported protocol.
     * 
     * @param parameter a single UPLOAD parameter.
     * @param tableNameUri String[] containing table name and VOTable URI string.
     * @return valid URI to the VOTable.
     * @throws IllegalStateException if the URI is invalid.
     */
    protected URI validateURI(Parameter parameter, String[] tableNameUri)
        throws UnsupportedOperationException
    {
        URI uri;        
        try
        {
            uri = new URI(tableNameUri[1]);
        }
        catch (IndexOutOfBoundsException e)
        {
            throw new UnsupportedOperationException("UPLOAD URI is missing: " + parameter);
        }
        catch (URISyntaxException e)
        {
            throw new UnsupportedOperationException("UPLOAD URI is invalid: " + tableNameUri[1]);
        }
        if (!uri.getScheme().equals("http"))
            throw new UnsupportedOperationException("UPLOAD URI protocol is not supported: " + uri.toString());
        return uri;
    }
    
}
