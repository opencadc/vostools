/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2011.                            (c) 2011.
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
*  $Revision: 5 $
*
************************************************************************
*/

package ca.nrc.cadc.tap.parser.converter;

import ca.nrc.cadc.tap.parser.navigator.ReferenceNavigator;
import ca.nrc.cadc.util.CaseInsensitiveStringComparator;
import java.util.Map;
import java.util.TreeMap;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.apache.log4j.Logger;

/**
 * Converts table names foudn in references (e.g. fully qualified column references).
 * You need to use both this and the TableNameConverter with the same underlying map
 * to convert all places a table name can be used.
 * 
 * @author pdowler
 */
public class TableNameReferenceConverter extends ReferenceNavigator
{
    protected static Logger log = Logger.getLogger(TableNameReferenceConverter.class);

    public Map<String, Table> map;

    public TableNameReferenceConverter(boolean ignoreCase)
    {
        if (ignoreCase)
            this.map = new TreeMap<String,Table>(new CaseInsensitiveStringComparator());
        else
            this.map = new TreeMap<String,Table>();
    }

    public TableNameReferenceConverter(Map<String, Table> map)
    {
        super();
        this.map = map;
    }

    /**
     * Add new entries to the table name map.
     *
     * @param originalName a table name that should be replaced
     * @param newName the value that originalName should be replaced with
     */
    public void put(String originalName, String newName)
    {
        Table t = new Table();
        String[] parts = newName.split("[.]");
        if (parts.length == 1)
            t.setName(parts[0]);
        else if (parts.length == 2)
        {
            t.setSchemaName(parts[0]);
            t.setName(parts[1]);
        }
        else
            throw new IllegalArgumentException("expected new table name to have 1-2 parts, found " + parts.length);

        map.put(originalName, t);
    }

    @Override
    public void visit(Column column)
    {
        log.debug("visit(column)" + column);
        Table table = column.getTable();
        log.debug("table: " + table);
        if (table != null && table.getName() != null)
        {
            String tabName = table.getWholeTableName();
            log.debug("looking for " + tabName + " in conversion map...");
            Table ntab = map.get(tabName);
            log.debug("found: " + ntab);
            if (ntab != null)
            {
                log.debug("convert: " + table.getSchemaName() + "." + table.getName()
                        + " -> " + ntab.getSchemaName() + "." + ntab.getName());
                table.setName(ntab.getName());
                table.setSchemaName(ntab.getSchemaName());
                // leave alias intact
            }
        }
    }
}
