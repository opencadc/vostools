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
package ca.nrc.cadc.tap.schema;

public class ParamDesc
{
    public ColumnDesc columnDesc;

     /**
     * The name (not null).
     */
    public String name;

    /**
     * The alias of the param (can be null).
     */
    public String alias;

    /**
     * Describes the param (can be null).
     */
    public String description;

    /**
     * The utype of the param (can be null).
     */
    public String utype;

    /**
     * The UCD of param (can be null).
     */
    public String ucd;

    /**
     * The unit used for param values (can be null).
     */
    public String unit;

    /**
     * The ADQL datatype of param (not null).
     */
    public String datatype;

    /**
     * The param datatype size (Column width) (not null).
     */
    public Integer size;

    public ParamDesc(ColumnDesc columnDesc, String alias)
    {
        this.columnDesc = columnDesc;
        this.name = columnDesc.columnName;
        this.description = columnDesc.description;
        this.utype = columnDesc.utype;
        this.ucd = columnDesc.ucd;
        this.unit = columnDesc.unit;
        this.datatype = columnDesc.datatype;
        this.size = columnDesc.size;
        this.alias = alias;
    }

    public ParamDesc(FunctionDesc functionDesc, String alias)
    {
        this.columnDesc = null;
        this.name = functionDesc.name;
        this.description = null;
        this.utype = null;
        this.ucd = null;
        this.unit = functionDesc.unit;
        this.datatype = functionDesc.datatype;
        this.size = null;
        this.alias = alias;
    }

    public ParamDesc(String name, String alias, String datatype)
    {
        this.columnDesc = null;
        this.name = name;
        this.alias = alias;
        this.datatype = null;
        this.size = null;
        this.description = null;
        this.utype = null;
        this.ucd = null;
        this.unit = null;
        
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("ParamDesc[");
        sb.append(name).append(",");
        sb.append(alias == null ? "" : alias).append(",");
        sb.append(description == null ? "" : description).append(",");
        sb.append(utype == null ? "" : utype).append(",");
        sb.append(ucd == null ? "" : ucd).append(",");
        sb.append(unit == null ? "" : unit).append(",");
        sb.append(datatype).append(",");
        sb.append(size == null ? "" : size);
        sb.append("]");
        return sb.toString();
    }
    
}
