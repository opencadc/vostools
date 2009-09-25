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
package ca.nrc.cadc.adql.validator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.management.RuntimeErrorException;

import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.ColumnReference;
import net.sf.jsqlparser.statement.select.Distinct;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.FromItemVisitor;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.OrderByVisitor;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SelectItemVisitor;
import net.sf.jsqlparser.statement.select.SelectVisitor;
import net.sf.jsqlparser.statement.select.SubJoin;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.statement.select.Top;
import net.sf.jsqlparser.statement.select.Union;
import org.apache.log4j.Logger;

import ca.nrc.cadc.adql.config.AdqlConfig;
import ca.nrc.cadc.adql.config.meta.TableMeta;
import ca.nrc.cadc.adql.exception.AdqlException;
import ca.nrc.cadc.adql.exception.AdqlValidateException;
import ca.nrc.cadc.adql.*;

/**
 * Basic SelectVisitor implementation. This class implements FromItemVisitor to handle references to tables and subselects in a
 * simple fashion. It implements SelectItemVisitor in order to process the expressions in the select list itself.
 * 
 * 
 * @author pdowler, Sailor Zhang
 */
// Prototype: AdqlSelectVisitorProto
public abstract class SelectValidator extends AdqlValidatorVisitor implements SelectVisitor, SelectItemVisitor, FromItemVisitor,
		OrderByVisitor {
	protected static Logger log = Logger.getLogger(SelectValidator.class);

	public enum PlainSelectType {
		ROOT_SELECT, FROM_SUB_SELECT, WHERE_SUB_SELECT, HAVING_SUB_SELECT
	}

	public enum VisitingPart {
		SELECT_ITEM, FROM, WHERE, HAVING, ORDER_BY, GROUP_BY
	}

	protected PlainSelectType plainSelectType;
	protected VisitingPart visitingPart;

	protected ExpressionValidator expressionValidator;
	protected ColumnReferenceValidator columnReferenceValidator;

	// The following is for subselect feature.
	protected List<SelectValidator> fromSubSelectValidators; // Hold all subselects in the "FROM" part
	protected SelectValidator parentSelectValidator; // point to the parent selectValidator
	protected PlainSelectInfo plainSelectInfo;
	

	public final PlainSelectInfo getPlainSelectInfo() {
		return plainSelectInfo;
	}

	public final void setPlainSelectInfo(PlainSelectInfo plainSelectInfo) {
		this.plainSelectInfo = plainSelectInfo;
	}

	public void init(AdqlManager manager, PlainSelectType type, SelectValidator parentSelectValidator) {
		this.plainSelectType = type;
		super.init(manager);
		Validator validator = manager.getValidator();

		this.expressionValidator = validator.newExpressionValidator();
		this.expressionValidator.init(manager, this);

		this.columnReferenceValidator = validator.newColumnReferenceValidator();
		this.columnReferenceValidator.init(manager, this);
		
		this.plainSelectInfo = new PlainSelectInfo();

		if (type == PlainSelectType.ROOT_SELECT) {
			this.parentSelectValidator = null;
		} else {
			this.parentSelectValidator = parentSelectValidator;
			if (parentSelectValidator != null) {
				switch (type) {
				case FROM_SUB_SELECT:
					parentSelectValidator.addFromSubSelectValidator(this);
					break;
				case WHERE_SUB_SELECT:
					//TODO:sz:
					break;
				case HAVING_SUB_SELECT:
					//TODO:sz:
					break;
				}
			} else 
				throw new RuntimeErrorException(new Error("Parent SelectValidator not provided."));
		}
	}

	// public void init(AdqlManager manager, ExpressionValidator expressionValidator,
	// ColumnReferenceValidator columnReferenceValidator, SelectValidator parentSelectValidator) {
	// super.init(manager);
	// this.expressionValidator = expressionValidator;
	// this.columnReferenceValidator = columnReferenceValidator;
	//
	// this.parentSelectValidator = parentSelectValidator;
	// if (parentSelectValidator != null)
	// parentSelectValidator.addSubSelectValidator(this);
	// }

	private void addFromSubSelectValidator(SelectValidator selectValidator) {
		if (this.fromSubSelectValidators == null)
			this.fromSubSelectValidators = new ArrayList<SelectValidator>();
		this.fromSubSelectValidators.add(selectValidator);
	}

	public void visit(PlainSelect ps) {
		this.setPlainSelect(ps);
		this.expressionValidator.setPlainSelect(ps);
		this.columnReferenceValidator.setPlainSelect(ps);
	}

	public final boolean isRoot() {
		return (parentSelectValidator == null);
	}

	public final PlainSelectType getPlainSelectType() {
		return plainSelectType;
	}

	public final void setPlainSelectType(PlainSelectType type) {
		this.plainSelectType = type;
	}

	public final ExpressionValidator getExpressionValidator() {
		return expressionValidator;
	}

	public final ColumnReferenceValidator getColumnReferenceValidator() {
		return columnReferenceValidator;
	}

	public final List<SelectValidator> getFromSubSelectValidators() {
		return fromSubSelectValidators;
	}

	public final SelectValidator getParentSelectValidator() {
		return parentSelectValidator;
	}

	public final VisitingPart getVisitingPart() {
		return visitingPart;
	}

	public final void setVisitingPart(VisitingPart visitingPart) {
		this.visitingPart = visitingPart;
	}
}
