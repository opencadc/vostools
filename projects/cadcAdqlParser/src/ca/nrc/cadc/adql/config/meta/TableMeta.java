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


package ca.nrc.cadc.adql.config.meta;

import java.util.*;

import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;

public class TableMeta {
	private String schemaName = ""; //default to empty
	private String tableName;
	protected List<ColumnMeta> columnMetas;
	
	private TableMeta() {
		this.columnMetas = new ArrayList<ColumnMeta>();
	}
	
	public TableMeta(String tableName) {
		this();
		this.tableName = tableName;
	}
	
	public TableMeta(String schemaName, String tableName) {
		this(tableName);
		this.schemaName = schemaName;
	}
	
	public void addColumnMeta(ColumnMeta columnMeta) {
		this.columnMetas.add(columnMeta);
	}
	
    @Override
    public String toString() {
    	String cr = "\r\n";
    	StringBuffer sb = new StringBuffer();
    	if (!schemaName.equals("")) {
    		sb.append(schemaName).append(".");
    	}
    	sb.append(tableName).append(cr);
    	for (ColumnMeta cm : this.columnMetas) {
    		sb.append("    ").append(cm).append(cr);
    	}
    	return sb.toString();
    }
	
	/**
	 * 
	 * @param withSchemaName
	 * @param withTableName
	 * @return string as one of the following:
	 * 
	 * schemaA.tableA.columnA, schemaA.tableA.columnB,schemaA.tableA.columnC
	 * 
	 * tableA.columnA,tableA.columnB,tableA.columnC
	 * 
	 * columnA,columnB,columnC
	 */
	public String getColumnListString(boolean withSchemaName, boolean withTableName) {
		String prefix = "";
		String deli;
		
		deli = ".";
		if (withSchemaName) withTableName = true; //if schemaName is used, tableName must be used.
		prefix = withSchemaName ? this.schemaName + deli : "";
		prefix = withTableName ? prefix + this.tableName + deli : "";
		
		deli = "" ;
		StringBuffer sb = new StringBuffer();
		for (ColumnMeta cm : this.columnMetas) {
			sb.append(deli).append(prefix).append(cm.getName());
			deli = ",";
		}
		return sb.toString();
	}
	
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((schemaName == null) ? 0 : schemaName.hashCode());
		result = prime * result
				+ ((tableName == null) ? 0 : tableName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TableMeta other = (TableMeta) obj;
		if (schemaName == null) {
			if (other.schemaName != null)
				return false;
		} else if (!schemaName.equals(other.schemaName))
			return false;
		if (tableName == null) {
			if (other.tableName != null)
				return false;
		} else if (!tableName.equals(other.tableName))
			return false;
		return true;
	}

	public final String getSchemaName() {
		return schemaName;
	}
	public final void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}
	public final String getTableName() {
		return tableName;
	}
	public final void setTableName(String tableName) {
		this.tableName = tableName;
	}
	public final List<ColumnMeta> getColumnMetas() {
		return columnMetas;
	}

	public List<SelectItem> getAllColumnAsSelectItems(Table table) {
		List<SelectItem> selectItems = new ArrayList<SelectItem>();
		Column column;
		SelectExpressionItem selectExpressionItem;
		for (ColumnMeta columnMeta : this.columnMetas) {
			column = new Column(table, columnMeta.getName());
            selectExpressionItem = new SelectExpressionItem();
            selectExpressionItem.setExpression(column);
            selectItems.add(selectExpressionItem);
		}
        return selectItems;
	}

}
