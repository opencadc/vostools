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
package ca.nrc.cadc.tap.parser;

import static org.junit.Assert.fail;

import java.util.MissingResourceException;

import javax.sql.DataSource;

import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SubJoin;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.statement.select.Union;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import ca.nrc.cadc.tap.TapProperties;
import ca.nrc.cadc.tap.parser.ParserUtil;
import ca.nrc.cadc.tap.parser.adql.AdqlManager;
import ca.nrc.cadc.tap.parser.adql.AdqlParser;
import ca.nrc.cadc.tap.parser.adql.exception.AdqlException;
import ca.nrc.cadc.tap.parser.adql.impl.postgresql.sql.AdqlManagerImpl;
import ca.nrc.cadc.tap.parser.adql.impl.postgresql.sql.SqlPropertiesFactory;
import ca.nrc.cadc.tap.parser.navigator.ExpressionNavigator;
import ca.nrc.cadc.tap.parser.navigator.FromItemNavigator;
import ca.nrc.cadc.tap.parser.navigator.ReferenceNavigator;
import ca.nrc.cadc.tap.parser.navigator.SelectNavigator;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.tap.schema.TapSchemaDAO;
import ca.nrc.cadc.util.LoggerUtil;

/**
 * 
 * @author Sailor Zhang
 *
 */
public class TestUtil
{
    private static String PROPERTY_FILE = "postgresql_sql.properties";

    public static TapProperties getPropertiesInstance() throws Exception
    {
        TapProperties prop;
        try
        {
            prop = new TapProperties(PROPERTY_FILE);
        } catch (Exception e)
        {
            throw e;
        }
        return prop;
    }

    public static TapSchema loadDefaultTapSchema()
    {
        TapSchema rtn = null;
        try
        {
            TapProperties properties = TestUtil.getPropertiesInstance();

            String JDBC_DRIVER = properties.getProperty("JDBC_DRIVER");
            String JDBC_URL = properties.getProperty("JDBC_URL");
            String USERNAME = properties.getProperty("USERNAME");
            String PASSWORD = properties.getProperty("PASSWORD");
            boolean SUPPRESS_CLOSE = properties.getPropertyBooloean("SUPPRESS_CLOSE");

            try
            {
                Class.forName(JDBC_DRIVER);
            } catch (ClassNotFoundException e)
            {
                String msg = "JDBC Driver not found.";
                throw new MissingResourceException(msg, JDBC_DRIVER, null);
            }
            DataSource ds = new SingleConnectionDataSource(JDBC_URL, USERNAME, PASSWORD, SUPPRESS_CLOSE);
            TapSchemaDAO dao = new TapSchemaDAO(ds);
            rtn = dao.get();
        } catch (Exception ex)
        {
            throw new UnsupportedOperationException(ex);
        }
        return rtn;
    }
}
