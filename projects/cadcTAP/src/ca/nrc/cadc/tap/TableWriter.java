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

import ca.nrc.cadc.tap.schema.ParamDesc;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.util.List;

import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.Parameter;

/**
 * Interface for classes that write tables in specific formats.
 * 
 * @see TableWriterFactory
 * @author pdowler
 */
public interface TableWriter
{
    /**
     * Provide the jobID to the TableWriter so it can include it with the output.
     * 
     * @param jobID
     * @deprecated since setJob(Job) was added
     */
    public void setJobID(String jobID);

    /**
     * The complete list of job parameters. This is here to allow implementations to
     * provide custom extensions such as client control of the output.
     *
     * @param params
     * @deprecated since setJob(Job) was added
     */
    public void setParameterList(List<Parameter> params);

    /**
     * Provide the job to the TableWriter.
     *
     * @param job
     */
    public void setJob(Job job);
    
    /**
     * Get the usual filename extension for this format.
     * 
     * @return filename extension
     */
    String getExtension();
    
    /**
     * Get the usual or requested content-type (mimetype).
     * 
     * @return content-type
     */
    String getContentType();

    /**
     * The ordered selected items from the query.
     * 
     * @param items
     */
    void setSelectList(List<ParamDesc> items);
    
    /**
     * The TapSchema for the target database.
     * 
     * @param schema
     * @deprecated not used since ParamDesc now contains the datatype.
     */
    void setTapSchema(TapSchema schema);
    
    /**
     * Set additional information or description of the result. The implementation may
     * include this text in the output (as a comment or wherever such descriptive text
     * is permitted by the format).
     * 
     * @see TapQuery.getInfo()
     * @param info
     */
    void setQueryInfo(String info);

    /**
     * Write ResultSet to the OutputStream.
     *
     * @param rs
     * @param out
     * @throws IOException
     */
    void write(ResultSet rs, OutputStream out)
        throws IOException;

    /**
     * Limit number of table rows.
     *
     * @param count
     */
    void setMaxRowCount(int count);
}
