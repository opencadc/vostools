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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.Top;

import org.apache.log4j.Logger;

import ca.nrc.cadc.tap.parser.ParserUtil;
import ca.nrc.cadc.tap.parser.PgsphereDeParser;
import ca.nrc.cadc.tap.parser.converter.AllColumnConverter;
import ca.nrc.cadc.tap.parser.converter.TableNameConverter;
import ca.nrc.cadc.tap.parser.converter.postgresql.PgFunctionNameConverter;
import ca.nrc.cadc.tap.parser.extractor.FunctionExpressionExtractor;
import ca.nrc.cadc.tap.parser.extractor.SelectListExpressionExtractor;
import ca.nrc.cadc.tap.parser.extractor.SelectListExtractor;
import ca.nrc.cadc.tap.parser.QuerySelectDeParser;
import ca.nrc.cadc.tap.parser.navigator.ExpressionNavigator;
import ca.nrc.cadc.tap.parser.navigator.FromItemNavigator;
import ca.nrc.cadc.tap.parser.navigator.ReferenceNavigator;
import ca.nrc.cadc.tap.parser.navigator.SelectNavigator;
import ca.nrc.cadc.tap.parser.schema.BlobClobColumnValidator;
import ca.nrc.cadc.tap.parser.schema.ExpressionValidator;
import ca.nrc.cadc.tap.parser.schema.TapSchemaTableValidator;
import ca.nrc.cadc.tap.schema.ParamDesc;
import ca.nrc.cadc.tap.schema.TableDesc;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.ParameterUtil;
import java.util.Set;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import net.sf.jsqlparser.util.deparser.SelectDeParser;

/**
 * TapQuery implementation for LANG=ADQL.
 * 
 * @author zhangsa
 *
 */
public class AdqlQuery implements TapQuery
{
    protected static Logger log = Logger.getLogger(AdqlQuery.class);

    protected TapSchema tapSchema;
    protected Map<String, TableDesc> extraTables;
    protected List<Parameter> paramList;
    protected List<String> queryStringList;
    protected String queryString;
    protected Integer maxRows;

    protected Statement statement;
    protected List<ParamDesc> selectList = null;
    protected List<SelectNavigator> navigatorList = new ArrayList<SelectNavigator>();

    protected transient boolean navigated = false;

    public AdqlQuery()
    {
    }

    /**
     * Set up the List<SelectNavigator>. Subclasses should override this method to
     * add extra navigators that check or modify the parsed query statement. This
     * implementation creates: TapSchemaValidator, AllColumnConverter.
     */
    protected void init()
    {
        ExpressionNavigator en;
        ReferenceNavigator rn;
        FromItemNavigator fn;
        SelectNavigator sn;

        // Blob,Clob plus Default Validator
        en = new ExpressionValidator(tapSchema);
        rn = new BlobClobColumnValidator(tapSchema);
        fn = new TapSchemaTableValidator(tapSchema);
        sn = new SelectNavigator(en, rn, fn);
        navigatorList.add(sn);

        sn = new AllColumnConverter(tapSchema);
        navigatorList.add(sn);

        en = new SelectListExpressionExtractor(tapSchema);
        rn = null;
        fn = null;
        sn = new SelectListExtractor(en, rn, fn);
        navigatorList.add(sn);

        en = new PgFunctionNameConverter();
        rn = new ReferenceNavigator();
        fn = null;
        sn = new FunctionExpressionExtractor(en, rn, fn);
        navigatorList.add(sn);

        // Used for file uploads to map the upload table name to the query table name.
        if (extraTables != null && !extraTables.isEmpty())
        {
            TableNameConverter tnc = new TableNameConverter(true);
            Set<Map.Entry<String, TableDesc>> entries = extraTables.entrySet();
            for (Map.Entry entry : entries)
            {
                String newName = (String) entry.getKey();
                TableDesc tableDesc = (TableDesc) entry.getValue();
                tnc.put(tableDesc.tableName, newName);
                log.debug("TableNameConverter " + tableDesc.tableName + " -> " + newName);
            }
            en = new ExpressionNavigator();
            sn = new SelectNavigator(en, new ReferenceNavigator(), tnc);
            navigatorList.add(sn);
        }
    }

    protected void doNavigate()
    {
        if (navigated) // idempotent
            return;

        init();

        try
        {
            log.debug("parsing query: " + queryString);
            statement = ParserUtil.receiveQuery(queryString);
        }
        catch (JSQLParserException e)
        {
            log.debug("parse failed", e);
            throw new IllegalArgumentException(e);
        }

        // if maxRows has been set, update top
        if (maxRows != null && statement instanceof Select)
        {
            Select select = (Select) statement;
            SelectBody selectBody = select.getSelectBody();
            if (selectBody instanceof PlainSelect)
            {
                PlainSelect plainSelect = (PlainSelect) selectBody;
                Top top = plainSelect.getTop();
                if (top == null)
                {
                    top = new Top();
                    top.setRowCount(new Long(maxRows));
                    log.debug("added TOP " + maxRows);
                }
                else
                {
                    if (maxRows < top.getRowCount())
                    {
                        log.debug("updated TOP " + top.getRowCount() + " to TOP " + maxRows);
                        top.setRowCount(maxRows);
                    }
                }
                plainSelect.setTop(top);
            }
        }

        // run all the navigators
        navigateStatement(statement);

        navigated = true;
    }

    /**
     * Run all navigators on a statement.
     * 
     * @param statement
     */
    protected void navigateStatement(Statement statement)
    {
        for (SelectNavigator sn : navigatorList)
        {
            log.debug("Navigated by: " + sn.getClass().getName());

            ParserUtil.parseStatement(statement, sn);

            if (sn instanceof SelectListExtractor)
            {
                SelectListExpressionExtractor slen = (SelectListExpressionExtractor) sn.getExpressionNavigator();
                selectList = slen.getSelectList();
            }
        }
    }

    public void setTapSchema(TapSchema tapSchema)
    {
        this.tapSchema = tapSchema;
    }

    /**
     * 
//     * @deprecated 
     */
    public void setExtraTables(Map<String, TableDesc> extraTables)
    {
        this.extraTables = extraTables;
    }

    public void setParameterList(List<Parameter> paramList)
    {
        this.paramList = paramList;
        this.queryStringList = ParameterUtil.findParameterValues("QUERY", paramList);
        // Tested; when no query is provided, the obj is null
        if (queryStringList == null) throw new IllegalArgumentException("parameter not found: QUERY");
        this.queryString = queryStringList.get(0);
        if (queryString == null) throw new IllegalArgumentException("QUERY is empty");
    }

    public void setMaxRowCount(Integer count)
    {
        this.maxRows = count;
    }

    public Integer getMaxRowCount()
    {
        return maxRows;
    }

    public String getSQL()
    {
        if (queryString == null)
            throw new IllegalStateException();
        
        doNavigate();
        log.debug("getSQL statement: " + statement);

        StringBuffer sb = new StringBuffer();
        SelectDeParser deParser = new QuerySelectDeParser();
        deParser.setBuffer(sb);
        ExpressionDeParser expressionDeParser = new PgsphereDeParser(deParser, sb);
        deParser.setExpressionVisitor(expressionDeParser);
        Select select = (Select) statement;
        select.getSelectBody().accept(deParser);
        return deParser.getBuffer().toString();
    }

    public List<ParamDesc> getSelectList()
    {
        if (queryString == null)
            throw new IllegalStateException();

        doNavigate();
        return selectList;
    }

    public String getInfo()
    {
        // TODO: format nicely?
        return queryString;
    }
}
