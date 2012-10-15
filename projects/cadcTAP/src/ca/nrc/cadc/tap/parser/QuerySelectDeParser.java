/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.nrc.cadc.tap.parser;

import java.util.Iterator;
import net.sf.jsqlparser.expression.ExpressionVisitor;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.ColumnReference;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.Top;
import net.sf.jsqlparser.util.deparser.SelectDeParser;
import org.apache.log4j.Logger;

/**
 * The methods in this class override JSQLParser SelectDeParser methods to
 * fix de-parsing bugs.
 *
 *
 * @author jburke
 */
public class QuerySelectDeParser extends SelectDeParser
{
    private static Logger log = Logger.getLogger(QuerySelectDeParser.class);

    public QuerySelectDeParser()
    {
        super();
    }

    public QuerySelectDeParser(ExpressionVisitor expressionVisitor, StringBuffer buffer)
    {
        super(expressionVisitor, buffer);
    }

    /**
     * The table alias, if it exists, was not appended to the table name.
     *
     * @param table
     */
    @Override
    public void visit(Table table)
    {
        log.debug("visit(Table) " + table);
        buffer.append(table.getWholeTableName());
        if (table.getAlias() != null)
        {
			buffer.append(" AS ");
            buffer.append(table.getAlias());
		}
    }

    /**
     * Incorrectly appends the From alias after the From has been appended to the query,
     * instead of allowing the From expression visitor to process the alias, i.e. Table.
     *
     * @param join
     */
    @Override
    public void deparseJoin(Join join)
    {
		if (join.isSimple())
			buffer.append(", ");
		else
		{
			if (join.isRight())
				buffer.append(" RIGHT");
			else if (join.isNatural())
				buffer.append(" NATURAL");
			else if (join.isFull())
				buffer.append(" FULL");
			else if (join.isLeft())
				buffer.append(" LEFT");

			if (join.isOuter())
				buffer.append(" OUTER");
			else if (join.isInner())
				buffer.append(" INNER");

			buffer.append(" JOIN ");
		}

		FromItem fromItem = join.getRightItem();
		fromItem.accept(this);
//		if (fromItem.getAlias() != null) {
//			buffer.append(" AS " + fromItem.getAlias());
//		}
		if (join.getOnExpression() != null) {
			buffer.append(" ON ");
			join.getOnExpression().accept(expressionVisitor);
		}
		if (join.getUsingColumns() != null) {
			buffer.append(" USING ( ");
			for (Iterator iterator = join.getUsingColumns().iterator(); iterator.hasNext();) {
				Column column = (Column) iterator.next();
				buffer.append(column.getWholeColumnName());
				if (iterator.hasNext()) {
					buffer.append(" ,");
				}
			}
			buffer.append(")");
		}
    }

    /**
     * TOP, if it exists, was not inserted into the query.
     *
     * @param plainSelect
     */
    @Override
    public void visit(PlainSelect plainSelect)
    {
        log.debug("visit(" +  plainSelect.getClass().getSimpleName() + ") " + plainSelect);
        buffer.append("SELECT ");
		Top top = plainSelect.getTop();
		if (top != null)
        {
            buffer.append("TOP ");
			buffer.append(top.getRowCount());
            buffer.append(" ");
        }
		if (plainSelect.getDistinct() != null) {
			buffer.append("DISTINCT ");
			if (plainSelect.getDistinct().getOnSelectItems() != null) {
				buffer.append("ON (");
				for (Iterator iter = plainSelect.getDistinct().getOnSelectItems().iterator(); iter.hasNext();) {
					SelectItem selectItem = (SelectItem) iter.next();
					selectItem.accept(this);
					if (iter.hasNext()) {
						buffer.append(", ");
					}
				}
				buffer.append(") ");
			}
		}

		for (Iterator iter = plainSelect.getSelectItems().iterator(); iter.hasNext();) {
			SelectItem selectItem = (SelectItem) iter.next();
			selectItem.accept(this);
			if (iter.hasNext()) {
				buffer.append(", ");
			}
		}

		buffer.append(" ");

		if (plainSelect.getFromItem() != null) {
			buffer.append("FROM ");
			plainSelect.getFromItem().accept(this);
		}

		if (plainSelect.getJoins() != null) {
			for (Iterator iter = plainSelect.getJoins().iterator(); iter.hasNext();) {
				Join join = (Join) iter.next();
				deparseJoin(join);
			}
		}

		if (plainSelect.getWhere() != null) {
			buffer.append(" WHERE ");
			plainSelect.getWhere().accept(expressionVisitor);
		}

		if (plainSelect.getGroupByColumnReferences() != null) {
			buffer.append(" GROUP BY ");
			for (Iterator iter = plainSelect.getGroupByColumnReferences().iterator(); iter.hasNext();) {
				ColumnReference columnReference = (ColumnReference) iter.next();
				columnReference.accept(this);
				if (iter.hasNext()) {
					buffer.append(", ");
				}
			}
		}

		if (plainSelect.getHaving() != null) {
			buffer.append(" HAVING ");
			plainSelect.getHaving().accept(expressionVisitor);
		}

		if (plainSelect.getOrderByElements() != null) {
			deparseOrderBy(plainSelect.getOrderByElements());
		}

		if (plainSelect.getLimit() != null) {
			deparseLimit(plainSelect.getLimit());
		}
    }

    /**
     * Incorrectly handles limit of 0 by setting the limit to BigInt.MAX when
     * the limit is 0 so hard code LIMIT 0.
     * 
     * @param limit
     */
    @Override
    public void deparseLimit(Limit limit)
    {
        log.debug("visit(" +  limit.getClass().getSimpleName() + ") " + limit);
        if (limit.getRowCount() == 0)
        {
            buffer.append(" LIMIT 0");
        }
        else
        {
            super.deparseLimit(limit);
        }
    }

}

    /**
     * The following are overridden for debugging purposes only.
     */
    /*
    @Override
    public void deparseLimit(Limit limit)
    {
        log.debug("visit(" +  limit.getClass().getSimpleName() + ") " + limit);
        super.deparseLimit(limit);
    }

    @Override
    public void deparseOrderBy(List orderByElements)
    {
        log.debug("visit(" +  orderByElements.getClass().getSimpleName() + ") " + orderByElements);
        super.deparseOrderBy(orderByElements);
    }

    @Override
    public void visit(Union union)
    {
        log.debug("visit(" +  union.getClass().getSimpleName() + ") " + union);
        super.visit(union);
    }

    @Override
    public void visit(OrderByElement orderBy)
    {
        log.debug("visit(" +  orderBy.getClass().getSimpleName() + ") " + orderBy);
        super.visit(orderBy);
    }

    @Override
    public void visit(Column column)
    {
        log.debug("visit(" +  column.getClass().getSimpleName() + ") " + column);
        super.visit(column);
    }

    @Override
    public void visit(ColumnIndex columnIndex)
    {
        log.debug("visit(" +  columnIndex.getClass().getSimpleName() + ") " + columnIndex);
        super.visit(columnIndex);
    }

    @Override
    public void visit(AllColumns allColumns)
    {
        log.debug("visit(" +  allColumns.getClass().getSimpleName() + ") " + allColumns);
        super.visit(allColumns);
    }

    @Override
    public void visit(AllTableColumns allTableColumns)
    {
        log.debug("visit(" +  allTableColumns.getClass().getSimpleName() + ") " + allTableColumns);
        super.visit(allTableColumns);
    }

    @Override
    public void visit(SelectExpressionItem selectExpressionItem)
    {
        log.debug("visit(" +  selectExpressionItem.getClass().getSimpleName() + ") " + selectExpressionItem);
        super.visit(selectExpressionItem);
    }

    @Override
    public void visit(SubSelect subSelect)
    {
        log.debug("visit(" +  subSelect.getClass().getSimpleName() + ") " + subSelect);
        super.visit(subSelect);
    }

    @Override
    public void visit(SubJoin subjoin)
    {
        log.debug("visit(" +  subjoin.getClass().getSimpleName() + ") " + subjoin);
        super.visit(subjoin);
    }
    */
