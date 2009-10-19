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
package ca.nrc.cadc.tap.parser.adql.impl.postgresql.sql.validator;

import java.util.Iterator;
import java.util.List;

import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.ColumnReference;
import net.sf.jsqlparser.statement.select.Distinct;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SubJoin;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.statement.select.Top;
import net.sf.jsqlparser.statement.select.Union;
import ca.nrc.cadc.tap.parser.adql.AdqlUtil;
import ca.nrc.cadc.tap.parser.adql.exception.AdqlValidateException;
import ca.nrc.cadc.tap.parser.adql.validator.SelectValidator;

/**
 * @author zhangsa
 * 
 */
public class SelectValidatorImpl extends SelectValidator
{

    /**
     * Visit a SELECT. This method visits the items in the select list (itself), the items in the FROM (itself), the right-hand-side
     * of any JOIN/ON, the WHERE clause, and the HAVING clause (using the configured ExpressionVisitor). It calls handleDistinct(),
     * handleLimit(), and handleInto() if these appear in the SELECT.
     * 
     * @param plainSelect
     */
    public void visit(PlainSelect plainSelect)
    {
        log.debug("visit(PlainSelect): " + plainSelect);
        super.visit(plainSelect);

        try
        {
            initialAnalysis(plainSelect);
        } catch (AdqlValidateException ex)
        {
            addException(ex);
            setToStop(true);
        }

        if (isToStop())
            return;

        this.visitingPart = VisitingPart.FROM;
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem instanceof Table)
            fromItem.accept(this);
        else if (fromItem instanceof SubSelect)
        {
            handleSubSelect((SubSelect) fromItem, PlainSelectType.FROM_SUB_SELECT);
        }

        if (isToStop())
            return;

        validateJoins();
        if (isToStop())
            return;

        validateQueryBody();

        this.visitingPart = VisitingPart.SELECT_ITEM;
        List<SelectItem> selectItems = plainSelect.getSelectItems();
        if (selectItems != null)
        {
            for (SelectItem s : selectItems)
            {
                s.accept(this);
            }
        }

        this.visitingPart = VisitingPart.WHERE;
        if (plainSelect.getWhere() != null)
        {
            plainSelect.getWhere().accept(this.expressionValidator);
        }

        this.visitingPart = VisitingPart.GROUP_BY;
        List<ColumnReference> crs = plainSelect.getGroupByColumnReferences();
        if (crs != null)
        {
            for (ColumnReference cr : crs)
            {
                cr.accept(this.columnReferenceValidator);
            }
        }

        this.visitingPart = VisitingPart.ORDER_BY;
        List<OrderByElement> obes = plainSelect.getOrderByElements();
        if (obes != null)
        {
            for (OrderByElement obe : obes)
            {
                ColumnReference cr = obe.getColumnReference();
                cr.accept(this.columnReferenceValidator);
            }
        }

        this.visitingPart = VisitingPart.HAVING;
        if (plainSelect.getHaving() != null)
        {
            plainSelect.getHaving().accept(this.expressionValidator);
        }

        // other SELECT options

        if (plainSelect.getLimit() != null)
        {
            log.debug("limit: " + plainSelect.getLimit());
            handleLimit(plainSelect.getLimit());
        }

        if (plainSelect.getDistinct() != null)
            handleDistinct(plainSelect.getDistinct());

        if (plainSelect.getInto() != null)
            handleInto(plainSelect.getInto());

        if (plainSelect.getTop() != null)
        {
            log.debug("top: " + plainSelect.getTop());
            handleTop(plainSelect.getTop());
        }

        log.debug("visit(PlainSelect) done");
    }

    private void handleSubSelect(SubSelect subSelect, PlainSelectType plainSelectType)
    {
        SelectValidator subSelectValidator = this.validator.newSelectValidator();
        subSelectValidator.init(this.validator.getManager(), plainSelectType, this);
        subSelect.getSelectBody().accept(subSelectValidator);
    }

    /**
     * Visits a UNION. This method visits each SELECT within the UNION as described above.
     * 
     * @param u
     */
    public void visit(Union u)
    {
        log.debug("visit(Union): " + u);
        addException(new AdqlValidateException("UNION is not supported"));
        log.debug("visit(Union) done ");
    }

    /**
     * Visit a table that is refererred to in the FROM clause. The implementation simply logs the visit at debug mode.
     * 
     * @param t
     */
    public void visit(Table t)
    {
        String wholeName = t.getWholeTableName();
        log.debug("visit(Table): " + wholeName);

        if (this.config.isTableAmbiguous(t))
        {
            addException(new AdqlValidateException(wholeName + " is ambiguous. Schema prefix is needed."));
            toStop = true;
        } else if (!this.config.isTableValid(t))
        {
            addException(new AdqlValidateException(wholeName + " is not a supported table."));
        }
    }

    /**
     * In the WHERE or HAVING part, a sub-select may use columns of the parent select. These scenarios are taken care of here.
     * 
     * In the FROM part, a sub-select should not access the parent select. It is not included here.
     * 
     * @param subSelect
     */
    public void visit(SubSelect subSelect)
    {
        log.debug("visit(SubSelect): " + subSelect);
        switch (this.visitingPart)
        {
        case WHERE:
            handleSubSelect(subSelect, PlainSelectType.WHERE_SUB_SELECT);
            break;
        case HAVING:
            handleSubSelect(subSelect, PlainSelectType.WHERE_SUB_SELECT);
            break;
        default:
            addException(new AdqlValidateException("Sub-select is not supported:" + subSelect));
        }
    }

    /**
     * Visit the occurance of all-columns in a select list. For example, this method is called when the <code>*</code> in a query
     * like <code>SELECT * FROM ...</code> is visited.
     * 
     * If there are more than one FROM table, an asteric is not allowed.
     * 
     * All-columns should not come together with other select item.
     * 
     * @param allColumns
     */
    public void visit(AllColumns allColumns)
    {
        log.debug("visit(AllColumns): " + allColumns);
        if (this.plainSelect.getSelectItems().size() > 1)
            addException(new AdqlValidateException(allColumns.toString() + " cannot be placed when other select item exists."));
        if (AdqlUtil.extractSelectFromTables(this.plainSelect).size() > 1)
            addException(new AdqlValidateException(allColumns.toString()
                    + " is ambigious when there are more that one select-from tables."));
    }

    /**
     * Validate that an all-table-columns selectItem uses a valid table name.
     * 
     * @param allTableColumns
     */
    public void visit(AllTableColumns atc)
    {
        log.debug("visit(AllTableColumns): " + atc);
        boolean isValid = false;
        if (AdqlUtil.extractAliases(this.plainSelect).contains(atc.getTable().getName()))
            isValid = true;
        else if (AdqlUtil.isTableInSelectFromTables(this.plainSelect, atc.getTable()))
            isValid = true;
        else
            addException(new AdqlValidateException(atc.toString() + " is not in the from table or join tables."));
        return;
    }

    /**
     * Visit an expression in the SELECT. This implementation logs the visit and visits the expression with the configured
     * ExpresssionVisitor.
     * 
     * @param ei
     */
    public void visit(SelectExpressionItem ei)
    {
        log.debug("visit(SelectExpressionItem): " + ei);
        ei.getExpression().accept(this.expressionValidator);
    }

    /**
     * Handle use of the TOP construct. The implementation logs.
     * 
     * @param top
     */
    protected void handleTop(Top top)
    {
        log.debug("handleTop: " + top);
    }

    /**
     * Handle use of the LIMIT construct. The implementation logs.
     * 
     * @param limit
     */
    protected void handleLimit(Limit limit)
    {
        log.debug("handleLimit: " + limit);
        addException(new AdqlValidateException("LIMIT"));
    }

    /**
     * Handle use of the DISTINCT construct. The implementation logs and visits explicit expressions (itself) in the optional
     * ON(...) since they are not part of the select list.
     * 
     * @param limit
     */
    protected void handleDistinct(Distinct distinct)
    {
        log.debug("handleDistinct: " + distinct);
        if (distinct.getOnSelectItems() != null)
        {
            Iterator i = distinct.getOnSelectItems().iterator();
            while (i.hasNext())
            {
                SelectItem si = (SelectItem) i.next();
                si.accept(this);
            }
        }
    }

    /**
     * Handle use of SELECT INTO. The implementation logs and throws an AdqlValidateException.
     * 
     * @param limit
     */
    protected void handleInto(Table dest)
    {
        log.debug("handleInto: " + dest);
        addException(new AdqlValidateException("SELECT INTO is not supported."));
    }

    @Override
    public void visit(SubJoin arg0)
    {
        // TODO:sz: Auto-generated method stub

    }

    private void validateJoins()
    {
        PlainSelect ps = this.plainSelect;
        List<Join> joins = ps.getJoins();
        if (joins != null)
        {
            for (Join join : joins)
            {
                FromItem fromItem = join.getRightItem();
                if (fromItem instanceof Table)
                {
                    Table rightTable = (Table) join.getRightItem();
                    rightTable.accept(this);

                    if (join.getOnExpression() != null)
                        join.getOnExpression().accept(this.expressionValidator);

                    List<Column> columns = join.getUsingColumns();
                    if (columns != null)
                    {
                        for (Column column : columns)
                        {
                            // Validate join
                            FromItem selectFromItem = this.plainSelect.getFromItem();
                            if (!(selectFromItem instanceof Table))
                                addException(new AdqlValidateException(selectFromItem.toString() + " is not a table."));
                            else
                            {
                                Table selectFromTable = (Table) selectFromItem;
                                if (!this.config.isColumnInTable(column, selectFromTable))
                                    addException(new AdqlValidateException("" + column + " is not found in table "
                                            + selectFromTable));
                                if (!this.config.isColumnInTable(column, rightTable))
                                    addException(new AdqlValidateException("" + column + " is not found in table " + rightTable));
                            }
                        }
                    }
                } else if (fromItem instanceof SubSelect)
                {
                    SubSelect subSelect = (SubSelect) fromItem;
                    handleSubSelect(subSelect, PlainSelectType.FROM_SUB_SELECT);
                }
            }
        }
    }

    private void validateQueryBody()
    {
        PlainSelect ps = this.plainSelect;
        if (!config.isAllowJoins() && ps.getJoins() != null)
            addException(new AdqlValidateException("multiple tables not allowed."));

        if (!config.isAllowGroupBy() && ps.getGroupByColumnReferences() != null)
            addException(new AdqlValidateException("GROUP BY is not supported"));

        if (ps.getHaving() != null && ps.getGroupByColumnReferences() == null)
            addException(new AdqlValidateException("HAVING cannot be used without GROUP BY."));

        if (!config.isAllowOrderBy() && ps.getOrderByElements() != null)
            addException(new AdqlValidateException("ORDER BY is not supported"));

        if (!config.isAllowLimit() && ps.getLimit() != null)
            addException(new AdqlValidateException("LIMIT is not supported"));

        if (!config.isAllowDistinct() && ps.getDistinct() != null)
            addException(new AdqlValidateException("DISTINCT is not supported"));

        if (!config.isAllowInto() && ps.getInto() != null)
            addException(new AdqlValidateException("INTO is not supported"));

        if (!config.isAllowTop() && ps.getTop() != null)
            addException(new AdqlValidateException("TOP is not supported"));
    }

}
