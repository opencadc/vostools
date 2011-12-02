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

package ca.nrc.cadc.tap.parser.navigator;

import java.util.List;
import java.util.Stack;

import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.ColumnReference;
import net.sf.jsqlparser.statement.select.Distinct;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SelectVisitor;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.statement.select.Top;
import net.sf.jsqlparser.statement.select.Union;

import org.apache.log4j.Logger;

/**
 * Basic SelectVisitor implementation. 
 * It holds three other navigators: ExpressionNavigator, ReferenceNavigator, and FromItemNavigator. 
 * 
 * 
 * @author pdowler, Sailor Zhang
 */
// Prototype: AdqlSelectVisitorProto
public class SelectNavigator implements SelectVisitor
{
    private static Logger log = Logger.getLogger(SelectNavigator.class);

    /**
     * Type of plainSelect.
     * 
     * @author zhangsa
     *
     */
    public enum PlainSelectType {
        ROOT_SELECT, FROM_SUB_SELECT, WHERE_SUB_SELECT, HAVING_SUB_SELECT
    }

    /**
     * Used to indicate which part the visitor is visiting.
     * 
     * @author zhangsa
     *
     */
    public enum VisitingPart {
        SELECT_ITEM, FROM, WHERE, HAVING, ORDER_BY, GROUP_BY
    }

    protected PlainSelectType plainSelectType;
    protected VisitingPart visitingPart;

    protected boolean toStop = false;
    protected PlainSelect plainSelect;
    protected Stack<PlainSelect> psStack = new Stack<PlainSelect>();
    protected Stack<VisitingPart> visitingPartStack = new Stack<VisitingPart>();

    // Other navigators controlled by SelectNavigator
    protected ExpressionNavigator expressionNavigator;
    protected ReferenceNavigator referenceNavigator;
    protected FromItemNavigator fromItemNavigator;

    protected SelectNavigator()
    {
    }

    public SelectNavigator(ExpressionNavigator en, ReferenceNavigator rn, FromItemNavigator fn)
    {
        this.expressionNavigator = en;
        this.referenceNavigator = rn;
        this.fromItemNavigator = fn;

        if (en != null) en.setSelectNavigator(this);
        if (rn != null) rn.setSelectNavigator(this);
        if (fn != null) fn.setSelectNavigator(this);
    }

    protected void enterPlainSelect(PlainSelect plainSelect)
    {
        if (this.visitingPart != null) this.visitingPartStack.push(this.visitingPart);
        this.plainSelect = plainSelect;
        this.psStack.push(this.plainSelect);

    }

    protected void leavePlainSelect()
    {
        if (!this.visitingPartStack.empty()) this.visitingPart = this.visitingPartStack.pop();

        this.psStack.pop();
        if (!this.psStack.empty()) this.plainSelect = this.psStack.peek();
    }

    @SuppressWarnings("unchecked")
    public void visit(PlainSelect plainSelect)
    {
        log.debug("visit(PlainSelect) " + plainSelect);
        enterPlainSelect(plainSelect);

        this.visitingPart = VisitingPart.FROM;
        navigateFromItem();
        if (isToStop()) return;
        navigateJoins();
        if (isToStop()) return;

        this.visitingPart = VisitingPart.SELECT_ITEM;
        List<SelectItem> selectItems = this.plainSelect.getSelectItems();
        if (selectItems != null) for (SelectItem s : selectItems)
            s.accept(this.expressionNavigator);

        this.visitingPart = VisitingPart.WHERE;
        if (this.plainSelect.getWhere() != null) this.plainSelect.getWhere().accept(this.expressionNavigator);

        this.visitingPart = VisitingPart.GROUP_BY;
        List<ColumnReference> crs = this.plainSelect.getGroupByColumnReferences();
        if (crs != null) for (ColumnReference cr : crs)
            cr.accept(this.referenceNavigator);

        this.visitingPart = VisitingPart.ORDER_BY;
        navigateOrderBy();

        this.visitingPart = VisitingPart.HAVING;
        if (this.plainSelect.getHaving() != null) this.plainSelect.getHaving().accept(this.expressionNavigator);

        // other SELECT options
        navigateOthers();

        log.debug("visit(PlainSelect) done "  + plainSelect);

        leavePlainSelect();
    }


    private void navigateFromItem()
    {
        FromItem fromItem = this.plainSelect.getFromItem();
        if (fromItem instanceof Table)
            fromItem.accept(this.fromItemNavigator);
        else if (fromItem instanceof SubSelect)
            throw new UnsupportedOperationException("sub-select not supported in FROM clause.");
    }

    @SuppressWarnings("unchecked")
    protected void navigateJoins()
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
                    rightTable.accept(this.fromItemNavigator);

                    if (join.getOnExpression() != null) join.getOnExpression().accept(this.expressionNavigator);

                    List<Column> columns = join.getUsingColumns();
                    if (columns != null) for (Column column : columns)
                        column.accept(this.expressionNavigator);
                }
                else if (fromItem instanceof SubSelect)
                    throw new UnsupportedOperationException("sub-select not supported in FROM clause.");
            }
        }
    }

    private void navigateOrderBy()
    {
        List<OrderByElement> obes = this.plainSelect.getOrderByElements();
        if (obes != null)
        {
            for (OrderByElement obe : obes)
            {
                ColumnReference cr = obe.getColumnReference();
                if (cr != null) cr.accept(this.referenceNavigator);
            }
        }
    }

    private void navigateOthers()
    {
        if (this.plainSelect.getLimit() != null) handleLimit(this.plainSelect.getLimit());
        if (this.plainSelect.getDistinct() != null) handleDistinct(this.plainSelect.getDistinct());
        if (this.plainSelect.getInto() != null) handleInto(this.plainSelect.getInto());
        if (this.plainSelect.getTop() != null) handleTop(this.plainSelect.getTop());
    }

    /*
     * Setters and Getters -------------------------------------------------
     */

    public final PlainSelectType getPlainSelectType()
    {
        return this.plainSelectType;
    }

    public final void setPlainSelectType(PlainSelectType type)
    {
        this.plainSelectType = type;
    }

    public final VisitingPart getVisitingPart()
    {
        return this.visitingPart;
    }

    public final void setVisitingPart(VisitingPart visitingPart)
    {
        this.visitingPart = visitingPart;
    }

    public boolean isToStop()
    {
        return toStop;
    }

    public void setToStop(boolean toStop)
    {
        this.toStop = toStop;
    }

    /**
     * Handle use of the TOP construct. The implementation logs.
     */
    protected void handleTop(Top top)
    {
        log.debug("handleTop: " + top);
    }

    /**
     * Handle use of the LIMIT construct. The implementation logs.
     */
    protected void handleLimit(Limit limit)
    {
        log.debug("handleLimit: " + limit);
    }

    /**
     * Handle use of the DISTINCT construct. The implementation logs and visits explicit expressions (itself) in the optional
     * ON(...) since they are not part of the select list.
     */
    @SuppressWarnings("unchecked")
    protected void handleDistinct(Distinct distinct)
    {
        log.debug("handleDistinct: " + distinct);
        List<SelectItem> onSelectItems = distinct.getOnSelectItems();
        if (onSelectItems != null) for (SelectItem si : onSelectItems)
        {
            if (si != null) si.accept(this.expressionNavigator);
        }
    }

    /**
     * Handle use of SELECT INTO. The implementation logs and throws an Exception.
     */
    protected void handleInto(Table dest)
    {
        log.debug("handleInto: " + dest);
        throw new UnsupportedOperationException("SELECT INTO is not supported.");
    }

    public PlainSelect getPlainSelect()
    {
        return this.plainSelect;
    }

    public void setPlainSelect(PlainSelect plainSelect)
    {
        this.plainSelect = plainSelect;
    }

    public ExpressionNavigator getExpressionNavigator()
    {
        return this.expressionNavigator;
    }

    public void setExpressionNavigator(ExpressionNavigator expressionNavigator)
    {
        this.expressionNavigator = expressionNavigator;
    }

    public ReferenceNavigator getReferenceNavigator()
    {
        return this.referenceNavigator;
    }

    public void setReferenceNavigator(ReferenceNavigator referenceNavigator)
    {
        this.referenceNavigator = referenceNavigator;
    }

    public FromItemNavigator getFromItemNavigator()
    {
        return this.fromItemNavigator;
    }

    public void setFromItemNavigator(FromItemNavigator fromItemNavigator)
    {
        this.fromItemNavigator = fromItemNavigator;
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.statement.select.SelectVisitor#visit(net.sf.jsqlparser.statement.select.Union)
     */
    @Override
    public void visit(Union union)
    {
        log.debug("visit(union) " + union);
        throw new UnsupportedOperationException("UNION is not supported.");
    }

}
