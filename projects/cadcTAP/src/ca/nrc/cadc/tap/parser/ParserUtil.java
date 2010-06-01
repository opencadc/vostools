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

package ca.nrc.cadc.tap.parser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;

import org.apache.log4j.Logger;

import ca.nrc.cadc.stc.Box;
import ca.nrc.cadc.tap.parser.navigator.SelectNavigator;

/**
 * Utility class for the use of Tap Parser.
 * 
 * @author zhangsa
 *
 *
 */
public class ParserUtil
{
    private static Logger log = Logger.getLogger(ParserUtil.class);

    /**
     * parse a Statement using given SelectNavigator.
     */
    public static void parseStatement(Statement statement, SelectNavigator sn)
    {
        StatementNavigator statementNavigator = new StatementNavigator(sn);
        statement.accept(statementNavigator);
        return;
    }

    /**
     * Parse a SQL/ADQL string using JSqlParser, return the result Statement.
     * 
     * @param query
     * @return
     * @throws JSQLParserException SQL syntax error
     */
    public static Statement receiveQuery(String query) throws JSQLParserException
    {
        log.debug(query);
        Statement statement = null;
        StringReader sr = new StringReader(query);
        CCJSqlParserManager sqlParser = new CCJSqlParserManager();
        statement = sqlParser.parse(sr);
        return statement;
    }

    /**
     * Extract a list of Table from the FROM part of query.
     *  
     */
    @SuppressWarnings("unchecked")
    public static List<Table> getFromTableList(PlainSelect ps)
    {
        List<Table> fromTableList = new ArrayList<Table>();

        FromItem fromItem = ps.getFromItem();
        if (fromItem instanceof Table)
        {
            fromTableList.add((Table) fromItem);
        }

        List<Join> joins = ps.getJoins();
        if (joins != null)
        {
            for (Join join : joins)
            {
                fromItem = join.getRightItem();
                if (fromItem instanceof Table)
                {
                    Table rightTable = (Table) join.getRightItem();
                    fromTableList.add(rightTable);
                }
            }
        }
        return fromTableList;
    }

    /**
     * Find "from Table" by table name or alias.
     * 
     * @param plainSelect
     * @param tableNameOrAlias
     * @return Table object
     */
    public static Table findFromTable(PlainSelect ps, String tableNameOrAlias)
    {
        Table rtn = null;
        List<Table> fromTableList = getFromTableList(ps);
        for (Table table : fromTableList)
        {
            if (tableNameOrAlias.equalsIgnoreCase(table.getAlias()) || tableNameOrAlias.equalsIgnoreCase(table.getName()))
            {
                rtn = table;
                break;
            }
        }
        return rtn;
    }

    /**
     * find SelectItem by column name or alias.
     * 
     * @param plainSelect
     * @param columnNameOrAlias
     * @return SelectItem object
     */
    @SuppressWarnings("unchecked")
    public static SelectItem findSelectItemByAlias(PlainSelect plainSelect, String columnNameOrAlias)
    {
        SelectItem rtn = null;
        List<SelectItem> siList = plainSelect.getSelectItems();
        for (SelectItem si : siList)
        {
            if (si instanceof SelectExpressionItem)
            {
                SelectExpressionItem sei = (SelectExpressionItem) si;
                if (columnNameOrAlias.equalsIgnoreCase(sei.getAlias()))
                {
                    rtn = sei;
                    break;
                }
            }
        }
        return rtn;
    }

    /**
     * count number of SelectItems in a plainSelect.
     * 
     * @param plainSelect
     * @return int
     */
    public static int countSelectItems(PlainSelect plainSelect)
    {
        return plainSelect.getSelectItems().size();
    }

    /**
     * Find the column in select item which matches alias.
     * return null if not found, or if not column type.
     * 
     * @param plainSelect 
     * @param alias
     * @return Column
     */
    public static Column findSelectItemColumn(PlainSelect plainSelect, String alias)
    {
        Column rtn = null;
        SelectItem si = findSelectItemByAlias(plainSelect, alias);
        if (si != null || si instanceof SelectExpressionItem)
        {
            Expression ex = ((SelectExpressionItem) si).getExpression();
            if (ex instanceof Column) rtn = (Column) ex;
        }
        return rtn;
    }

    /**
     * Determine whether Expression parameter is a binary value (0 or 1).
     * 
     * @param Expression
     * @return true if parameter is 0/1, false for others.
     */
    public static boolean isBinaryValue(Expression expr)
    {
        boolean rtn = false;
        if (expr instanceof LongValue)
        {
            long l = ((LongValue) expr).getValue();
            rtn = ((l == 0) || (l == 1));
        }
        return rtn;
    }

    /**
     * Convert  ADQL BOX function to STC Box .
     * 
     * @param adqlFunction ADQL Function, such as: BOX('ICRS GEOCENTER', 10, 20, 30, 40)
     */
    @SuppressWarnings("unchecked")
    public static Box convertToStcBox(Function adqlFunction)
    {
        Box box = null;
        if (RegionFinder.BOX.equalsIgnoreCase(adqlFunction.getName()))
        {
            List<Expression> adqlParams = adqlFunction.getParameters().getExpressions();
            int size = adqlParams.size();
            if (size != 5) throw new IllegalArgumentException("Not recognized as a valid BOX function: " + adqlFunction);
            String coordsys = parseToString(adqlParams.get(0));
            log.debug("coordsys=" + coordsys);
            String frame = null;
            String refpos = null;
            String flavor = null;
            if (coordsys != null)
            {
                coordsys = coordsys.trim();
                if (coordsys.length() > 0)
                {
                    String[] parts = coordsys.split(" ");
                    frame = parts[0];
                    if (parts.length > 1) refpos = parts[1];
                    if (parts.length > 2) flavor = parts[2];
                }
            }
            log.debug("frame=" + frame + " refpos=" + refpos + " flavor=" + flavor);

            double ra = parseToDouble(adqlParams.get(1));
            double dec = parseToDouble(adqlParams.get(2));
            double width = parseToDouble(adqlParams.get(3));
            double height = parseToDouble(adqlParams.get(4));

            box = new Box(frame, refpos, flavor, ra, dec, width, height);
        }
        else
            throw new IllegalArgumentException("Not recognized as a BOX function: " + adqlFunction);
        return box;
    }

    /**
     * Parse a jSql Expression as Double object.
     */
    public static double parseToDouble(Expression param)
    {
        double rtn;
        if (param instanceof DoubleValue || param instanceof LongValue)
        {
            String sv = param.toString();
            rtn = Double.parseDouble(sv);
        }
        else
            throw new IllegalArgumentException("Cannot be parsed as double value: " + param);
        return rtn;
    }

    /**
     * Parse a jSql Expression as String object.
     */
    public static String parseToString(Expression param)
    {
        if (param instanceof NullValue) return null;

        if (param instanceof StringValue) return ((StringValue) param).getNotExcapedValue();

        throw new IllegalArgumentException("Cannot be parsed as double value: " + param);
    }
}
