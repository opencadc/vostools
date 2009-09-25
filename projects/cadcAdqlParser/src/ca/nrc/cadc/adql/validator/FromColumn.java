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
package ca.nrc.cadc.adql.validator;

import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

/**
 * @author zhangsa
 * 
 */
public class FromColumn {

	private String tableAlias_;
	private String schemaName_;
	private String tableName_;
	private String columnName_;
	private String columnAlias_;

	public boolean matches(Column c1) {
		String c1Name = c1.getColumnName();
		if (c1Name == null || c1Name.equals(""))
			return false;
		
		boolean rtn = false;
		if (c1Name.equals(columnAlias_) || c1Name.equals(columnName_)) {
			Table t1 = c1.getTable();
			if (t1 != null && t1.getName() != null && !t1.getName().equals("")) {
				String t1Name = t1.getName();
				String s1Name = t1.getSchemaName();
				if (t1Name.equals(tableAlias_) || t1Name.equals(tableName_)) {
					if (s1Name != null && !s1Name.equals("")) {
						if (s1Name.equals(schemaName_))
							rtn = true;
					} else
						rtn = true;
				}
			} else
				rtn = true;
		}
		return rtn;
	}
	
	@Override
	public String toString() {
		return "FromColumn [columnAlias=" + columnAlias_ + ", columnName=" + columnName_ + ", schemaName=" + schemaName_
				+ ", tableAlias=" + tableAlias_ + ", tableName=" + tableName_ + "]\r\n";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((columnAlias_ == null) ? 0 : columnAlias_.hashCode());
		result = prime * result + ((columnName_ == null) ? 0 : columnName_.hashCode());
		result = prime * result + ((schemaName_ == null) ? 0 : schemaName_.hashCode());
		result = prime * result + ((tableAlias_ == null) ? 0 : tableAlias_.hashCode());
		result = prime * result + ((tableName_ == null) ? 0 : tableName_.hashCode());
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
		FromColumn other = (FromColumn) obj;
		if (columnAlias_ == null) {
			if (other.columnAlias_ != null)
				return false;
		} else if (!columnAlias_.equals(other.columnAlias_))
			return false;
		if (columnName_ == null) {
			if (other.columnName_ != null)
				return false;
		} else if (!columnName_.equals(other.columnName_))
			return false;
		if (schemaName_ == null) {
			if (other.schemaName_ != null)
				return false;
		} else if (!schemaName_.equals(other.schemaName_))
			return false;
		if (tableAlias_ == null) {
			if (other.tableAlias_ != null)
				return false;
		} else if (!tableAlias_.equals(other.tableAlias_))
			return false;
		if (tableName_ == null) {
			if (other.tableName_ != null)
				return false;
		} else if (!tableName_.equals(other.tableName_))
			return false;
		return true;
	}

	public final String getTableAlias() {
		return tableAlias_;
	}

	public final void setTableAlias(String tableAlias) {
		this.tableAlias_ = tableAlias;
	}

	public final String getSchemaName() {
		return schemaName_;
	}

	public final void setSchemaName(String schemaName) {
		this.schemaName_ = schemaName;
	}

	public final String getTableName() {
		return tableName_;
	}

	public final void setTableName(String tableName) {
		this.tableName_ = tableName;
	}

	public final String getColumnName() {
		return columnName_;
	}

	public final void setColumnName(String columnName) {
		this.columnName_ = columnName;
	}

	public final String getColumnAlias() {
		return columnAlias_;
	}

	public final void setColumnAlias(String columnAlias) {
		this.columnAlias_ = columnAlias;
	}

	public FromColumn(String tableAlias, String schemaName, String tableName, String columnName, String columnAlias) {
		super();
		this.tableAlias_ = tableAlias;
		this.schemaName_ = schemaName;
		this.tableName_ = tableName;
		this.columnName_ = columnName;
		this.columnAlias_ = columnAlias;
	}

}
