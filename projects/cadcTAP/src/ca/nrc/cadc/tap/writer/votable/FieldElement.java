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

package ca.nrc.cadc.tap.writer.votable;

import ca.nrc.cadc.tap.parser.adql.TapSelectItem;
import ca.nrc.cadc.tap.schema.ColumnDesc;
import org.jdom.Element;

public class FieldElement extends Element
{
    /**
     * Builds a FIELD Element from a Column.
     *
     * @param selectItem
     * @param columnDesc 
     */
    public FieldElement(TapSelectItem selectItem, ColumnDesc columnDesc)
    {
        super("FIELD");
        setFieldName(selectItem.getAlias(), columnDesc.columnName);
        setFieldAttribute("utype", columnDesc.utype);
        setFieldAttribute("ucd", columnDesc.ucd);
        setFieldAttribute("unit", columnDesc.unit);
        setFieldAttribute("xtype", columnDesc.datatype);
        setDescription(columnDesc.description);
        setDatatypeAndWidth(columnDesc.datatype, columnDesc.size);
     }

    // Set the name using the alias first, then the column name.
    private void setFieldName(String alias, String name)
    {
        if (alias != null)
            setAttribute("name", alias);
        else if (name != null)
            setAttribute("name", name);
    }

    // Set a String or Integer FIELD attribute.
    private void setFieldAttribute(String name, Object value)
    {
        if (value != null)
        {
            if (value instanceof String)
                setAttribute(name, (String) value);
            else if (value instanceof Integer)
                setAttribute(name, String.valueOf((Integer) value));
        }
    }

    // Add a DESCRIPTION Element to the FIELD.
    private void setDescription(String description)
    {
        if (description != null)
        {
            Element element = new Element("DESCRIPTION");
            element.setText(description);
            addContent(element);
        }
    }

    // Set the datatype and Width attributes.
    private void setDatatypeAndWidth(String datatype, Integer size)
    {
        if (datatype == null)
            return;

        String width = size == null ? null : String.valueOf(size);

        if (datatype.equals("adql:SMALLINT"))
        {
            setAttribute("datatype", "short");
        }
        else if (datatype.equals("adql:INTEGER"))
        {
            setAttribute("datatype", "int");
        }
        else if (datatype.equals("adql:BIGINT"))
        {
            setAttribute("datatype", "long");
        }
        else if (datatype.equals("adql:REAL"))
        {
            setAttribute("datatype", "float");
        }
        else if (datatype.equals("adql:DOUBLE"))
        {
            setAttribute("datatype", "double");
        }
        else if (datatype.equals("adql:VARBINARY"))
        {
            setAttribute("datatype", "unsignedByte");
            setAttribute("width", width == null ? "*" : width + "*");
        }
        else if (datatype.equals("adql:CHAR"))
        {
            setAttribute("datatype", "char");
            setAttribute("width", width == null ? "*" : width);
        }
        else if (datatype.equals("adql:VARCHAR"))
        {
            setAttribute("datatype", "char");
            setAttribute("width", width == null ? "*" : width + "*");
        }
        else if (datatype.equals("adql:BINARY"))
        {
            setAttribute("datatype", "unsignedByte");
            setAttribute("width", width == null ? "*" : width);
        }
        else if (datatype.equals("adql:BLOB"))
        {
            setAttribute("datatype", "unsignedByte");
            setAttribute("width", width == null ? "*" : width);
        }
        else if (datatype.equals("adql:CLOB"))
        {
            setAttribute("datatype", "char");
            setAttribute("width", width == null ? "*" : width);
        }
        else if (datatype.equals("adql:TIMESTAMP"))
        {
            setAttribute("datatype", "char");
            setAttribute("width", width == null ? "*" : width + "*");
        }
        else if (datatype.equals("adql:POINT"))
        {
            setAttribute("datatype", "char");
            setAttribute("width", "*");
        }
        else if (datatype.equals("adql:CIRCLE"))
        {

        }
        else if (datatype.equals("adql:POLYGON"))
        {

        }
        else if (datatype.equals("adql:REGION"))
        {
            setAttribute("datatype", "char");
            setAttribute("width", "*");
        }
    }

}
