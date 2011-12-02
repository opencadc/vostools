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

import org.apache.log4j.Logger;

import ca.nrc.cadc.tap.parser.ParserUtil;
import ca.nrc.cadc.tap.parser.converter.AllColumnConverter;
import ca.nrc.cadc.tap.parser.extractor.SelectListExpressionExtractor;
import ca.nrc.cadc.tap.parser.extractor.SelectListExtractor;
import ca.nrc.cadc.tap.parser.navigator.ExpressionNavigator;
import ca.nrc.cadc.tap.parser.navigator.FromItemNavigator;
import ca.nrc.cadc.tap.parser.navigator.ReferenceNavigator;
import ca.nrc.cadc.tap.parser.navigator.SelectNavigator;
import ca.nrc.cadc.tap.parser.schema.TapSchemaColumnValidator;
import ca.nrc.cadc.tap.parser.schema.TapSchemaTableValidator;
import ca.nrc.cadc.tap.schema.ParamDesc;
import ca.nrc.cadc.tap.schema.TableDesc;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.ParameterUtil;

/**
 * TapQuery implementation for LANG=SQL.
 * 
 * @author zhangsa
 *
 */
public class SqlQuery implements TapQuery
{
    protected static Logger log = Logger.getLogger(SqlQuery.class);

    protected TapSchema tapSchema;
    protected Map<String, TableDesc> extraTables;
    protected List<Parameter> paramList;
    protected String queryString;
    protected Integer maxRows;

    protected Statement statement;
    protected List<ParamDesc> selectList;
    protected List<SelectNavigator> navigatorList = new ArrayList<SelectNavigator>();

    protected transient boolean navigated = false;

    public SqlQuery()
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

        en = new ExpressionNavigator();
        rn = new TapSchemaColumnValidator(tapSchema);
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
    }

    protected void doNavigate()
    {
        if (navigated) // idempotent
            return;

        init();

        // parse for syntax
        try
        {
            statement = ParserUtil.receiveQuery(queryString);
        }
        catch (JSQLParserException e)
        {
            e.printStackTrace();
            throw new IllegalArgumentException(e);
        }

        // run all the navigators
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
        navigated = true;
    }

    public void setTapSchema(TapSchema tapSchema)
    {
        this.tapSchema = tapSchema;
    }

    public void setExtraTables(Map<String, TableDesc> extraTables)
    {
        this.extraTables = extraTables;
    }

    public void setParameterList(List<Parameter> paramList)
    {
        this.queryString = ParameterUtil.findParameterValue("QUERY", paramList);
        if (queryString == null) throw new IllegalArgumentException("parameter not found: QUERY");
    }

    /**
     * Not currently used for SQL queries.
     */
    public void setMaxRowCount(Integer count)
    {
        if (maxRows == null)
            this.maxRows = count;
        else if (count != null && count < maxRows) this.maxRows = count;
    }

    public Integer getMaxRowCount()
    {
        return maxRows;
    }

    public String getSQL()
    {
        if (queryString == null) throw new IllegalStateException();

        doNavigate();
        return statement.toString();
    }

    public List<ParamDesc> getSelectList()
    {
        if (queryString == null) throw new IllegalStateException();

        doNavigate();
        return selectList;
    }

    public String getInfo()
    {
        // TODO: format nicely?
        return queryString;
    }


}
