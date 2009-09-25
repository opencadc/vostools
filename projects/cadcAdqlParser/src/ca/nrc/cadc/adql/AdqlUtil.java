/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÃES ASTRONOMIQUES  **************
*
*  (c) 2009.                            (c) 2009.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits rÃ©servÃ©s
*                                       
*  NRC disclaims any warranties,        Le CNRC dÃ©nie toute garantie
*  expressed, implied, or               Ã©noncÃ©e, implicite ou lÃ©gale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           Ãªtre tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou gÃ©nÃ©ral,
*  arising from the use of the          accessoire ou fortuit, rÃ©sultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        Ãªtre utilisÃ©s pour approuver ou
*  products derived from this           promouvoir les produits dÃ©rivÃ©s
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  prÃ©alable et particuliÃ¨re
*                                       par Ã©crit.
*                                       
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*                                       
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la âGNU Affero General Public
*  License as published by the          Licenseâ telle que publiÃ©e
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (Ã  votre grÃ©)
*  any later version.                   toute version ultÃ©rieure.
*                                       
*  OpenCADC is distributed in the       OpenCADC est distribuÃ©
*  hope that it will be useful,         dans lâespoir quâil vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans mÃªme la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÃ
*  or FITNESS FOR A PARTICULAR          ni dâADÃQUATION Ã UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           GÃ©nÃ©rale Publique GNU Affero
*  more details.                        pour plus de dÃ©tails.
*                                       
*  You should have received             Vous devriez avoir reÃ§u une
*  a copy of the GNU Affero             copie de la Licence GÃ©nÃ©rale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce nâest
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 4 $
*
************************************************************************
*/
/**
 * 
 */
package ca.nrc.cadc.adql;

import java.util.ArrayList;
import java.util.List;

import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SubSelect;

/**
 * @author zhangsa
 * 
 */

public class AdqlUtil {

	public static String detailOf(Column c) {
		StringBuffer sb = new StringBuffer();
		Table t = c.getTable();
		String dot = ".";
		sb.append(t.getSchemaName()).append(dot).append(t.getName()).append(dot).append(c.getColumnName());
		sb.append(" ::").append(c.getWholeColumnName());
		return sb.toString();
	}
	
	@SuppressWarnings("unchecked")
	public static List<Table> extractSelectFromTables(PlainSelect ps) {
		List<Table> tables = new ArrayList<Table>();
		if (ps.getFromItem() instanceof Table)
			tables.add((Table) ps.getFromItem());
		List<Join> joins = ps.getJoins();
		if (joins != null) {
			for (Join join : joins) {
				if (join.getRightItem() instanceof Table)
					tables.add((Table) join.getRightItem());
			}
		}
		return tables;
	}

	/**
	 * Return list of alias  in the from part
	 * 
	 * @param ps
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static List<String> extractAliases(PlainSelect ps) {
		List<String> aliases = new ArrayList<String>();
		aliases.add(ps.getFromItem().getAlias());
		List<Join> joins = ps.getJoins();
		if (joins != null) {
			for (Join join : joins) {
				aliases.add(join.getRightItem().getAlias());
			}
		}
		return aliases;
	}

	/**
	 * Determine whether a table is in the "From Tables" list. table can be from the prefix of a select item, or prefix of a column
	 * expression in where, having, group by, etc. From-tables refers to the from table and the join tables.
	 * 
	 * @param ps
	 * @param table
	 * @return
	 */
	public static boolean isTableInSelectFromTables(PlainSelect ps, Table table) {
		boolean rtn = false;
		String schemaName = table.getSchemaName();
		String tableName = table.getName();
		String fromSchemaName, fromTableName, fromAlias;
		List<Table> fromTables = extractSelectFromTables(ps);
		for (Table fromTable : fromTables) {
			fromSchemaName = fromTable.getSchemaName();
			fromTableName = fromTable.getName();
			fromAlias = fromTable.getAlias();

			if (schemaName != null) {
				// it's not using an alias. The table name is actual name
				if (schemaName.equals(fromSchemaName) && tableName.equals(fromTableName)) {
					rtn = true;
					break;
				}
			} else {
				// No schema name used; the table name could be an actual name, or an alias.
				if (tableName.equals(fromTableName) || tableName.equals(fromAlias)) {
					rtn = true;
					break;
				}
			}
		}
		return rtn;
	}

	public static boolean equal(FromItem fromItem, Table table) {
		boolean rtn = false;
		if (fromItem instanceof Table) {
			Table fromTable = (Table) fromItem;
			if (fromTable.equals(table))
				rtn = true;
		}
		return rtn;
	}

	// not used.
	public static boolean isTableInFromItemOrJoin(PlainSelect ps, Table table) {
		boolean rtn = false;
		FromItem fromItem = ps.getFromItem();
		List<Join> joins = ps.getJoins();

		rtn = AdqlUtil.equal(fromItem, table);
		if (rtn == false && joins != null) {
			for (Join join : joins) {
				fromItem = join.getRightItem();
				rtn = AdqlUtil.equal(fromItem, table);
				if (rtn == true)
					break;
			}
		}
		return rtn;
	}
}
