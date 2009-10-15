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

/**
 * 
 */
package ca.nrc.cadc.tap.parser.adql.impl.postgresql.sql;

import java.util.MissingResourceException;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import ca.nrc.cadc.tap.parser.adql.config.AdqlConfig;
import ca.nrc.cadc.tap.parser.adql.config.meta.ColumnMeta;
import ca.nrc.cadc.tap.parser.adql.config.meta.FunctionMeta;
import ca.nrc.cadc.tap.parser.adql.config.meta.TableMeta;
import ca.nrc.cadc.tap.schema.Column;
import ca.nrc.cadc.tap.schema.Schema;
import ca.nrc.cadc.tap.schema.Table;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.tap.schema.TapSchemaDAO;

/**
 * @author zhangsa
 * 
 */
public class AdqlConfigImpl extends AdqlConfig {
	private static final String JDBC_DRIVER = "org.postgresql.Driver"; 
   private static final String JDBC_URL = "jdbc:postgresql://cvodb0/cvodb";
   private static final String USERNAME = "cadcuser51";
   private static final String PASSWORD = "MhU7nuvP5/67A:31:30";
   private static final boolean SUPPRESS_CLOSE = false;

   private static final String TAP_SCHEMA_NAME = "TAP_SCHEMA";

   private TapSchema _tapSchema;

	public AdqlConfigImpl() {
		super();
		
      try {
			Class.forName(JDBC_DRIVER);
		} catch (ClassNotFoundException e) {
			String msg = "JDBC Driver not found.";
			log.error(msg, e);
			throw new MissingResourceException(msg, JDBC_DRIVER, null);
		}
      DataSource ds = new SingleConnectionDataSource(JDBC_URL, USERNAME, PASSWORD, SUPPRESS_CLOSE);
      TapSchemaDAO dao = new TapSchemaDAO(ds);
      _tapSchema = dao.get();

		configName = "CADC PostgreSQL SQL";
		initFunctionMeta();
		initTableMeta();

		allowJoins = true; // Allow multiple tables in FROM clause (including JOIN). Default: true.
		allowUnion = true; // Allow UNION. Default: true.
		allowGroupBy = true; // Allow GROUP BY. Default: true.
		allowOrderBy = true; // Allow ORDER BY. Default: true.
		allowLimit = false; // Allow LIMIT. Default: false (not an ADQL construct)
		allowTop = true; // Allow TOP. Default: true.
		allowDistinct = true; // Allow DISTINCT. Default: true.
		allowInto = false; // Allow SELECT INTO. Default: false (not an ADQL construct)
		// caseSensitive = false; // Whether column, table, and schema names are case sensitive. -sz 2009-09-10

	}

	private void initFunctionMeta() {
		// functions not available for standard SQL parser.
	}

	private void initTableMeta() {
		TableMeta tableMeta;

		for (Schema schema : _tapSchema.getSchemas()) {
          if (schema.getSchemaName().equals(TAP_SCHEMA_NAME)) {
              for (Table table : schema.getTables()) {
                  tableMeta = new TableMeta(table.getSchemaName(), table.getSimpleTableName());
                  for (Column column : table.getColumns()) {
               		tableMeta.addColumnMeta(new ColumnMeta(column.getColumnName(), column.getDatatype(),
               				column.getUcd(), column.getUnit()));
                  }
            		tableMetas.add(tableMeta);
              }
          }
      }
	}
}
