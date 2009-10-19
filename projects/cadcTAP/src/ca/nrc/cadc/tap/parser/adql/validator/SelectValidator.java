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

package ca.nrc.cadc.tap.parser.adql.validator;

import java.util.ArrayList;
import java.util.List;

import javax.management.RuntimeErrorException;

import net.sf.jsqlparser.statement.select.FromItemVisitor;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItemVisitor;
import net.sf.jsqlparser.statement.select.SelectVisitor;

import org.apache.log4j.Logger;

import ca.nrc.cadc.tap.parser.adql.AdqlManager;
import ca.nrc.cadc.tap.parser.adql.exception.AdqlValidateException;

/**
 * Basic SelectVisitor implementation. This class implements FromItemVisitor to handle references to tables and subselects in a
 * simple fashion. It implements SelectItemVisitor in order to process the expressions in the select list itself.
 * 
 * 
 * @author pdowler, Sailor Zhang
 */
// Prototype: AdqlSelectVisitorProto
public abstract class SelectValidator extends AdqlValidatorVisitor implements SelectVisitor, SelectItemVisitor, FromItemVisitor
{
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

    public final PlainSelectInfo getPlainSelectInfo()
    {
        return plainSelectInfo;
    }

    public final void setPlainSelectInfo(PlainSelectInfo plainSelectInfo)
    {
        this.plainSelectInfo = plainSelectInfo;
    }

    public void init(AdqlManager manager, PlainSelectType type, SelectValidator parentSelectValidator)
    {
        this.plainSelectType = type;
        super.init(manager);
        Validator validator = manager.getValidator();

        this.expressionValidator = validator.newExpressionValidator();
        this.expressionValidator.init(manager, this);

        this.columnReferenceValidator = validator.newColumnReferenceValidator();
        this.columnReferenceValidator.init(manager, this);

        this.plainSelectInfo = new PlainSelectInfo();

        if (type == PlainSelectType.ROOT_SELECT)
        {
            this.parentSelectValidator = null;
        } else
        {
            this.parentSelectValidator = parentSelectValidator;
            if (parentSelectValidator != null)
            {
                switch (type)
                {
                case FROM_SUB_SELECT:
                    parentSelectValidator.addFromSubSelectValidator(this);
                    break;
                case WHERE_SUB_SELECT:
                    // TODO:sz:
                    break;
                case HAVING_SUB_SELECT:
                    // TODO:sz:
                    break;
                }
            } else
                throw new RuntimeErrorException(new Error("Parent SelectValidator not provided."));
        }
    }

    private void addFromSubSelectValidator(SelectValidator selectValidator)
    {
        if (this.fromSubSelectValidators == null)
            this.fromSubSelectValidators = new ArrayList<SelectValidator>();
        this.fromSubSelectValidators.add(selectValidator);
    }

    public void visit(PlainSelect ps)
    {
        this.setPlainSelect(ps);
        this.expressionValidator.setPlainSelect(ps);
        this.columnReferenceValidator.setPlainSelect(ps);
    }

    public final boolean isRoot()
    {
        return (parentSelectValidator == null);
    }

    protected void initialAnalysis(PlainSelect plainSelect) throws AdqlValidateException
    {
        this.config.populatePlainSelectInfo(this, plainSelect);
    }

    /*
     * Setters and Getters -------------------------------------------------
     */

    public final PlainSelectType getPlainSelectType()
    {
        return plainSelectType;
    }

    public final void setPlainSelectType(PlainSelectType type)
    {
        this.plainSelectType = type;
    }

    public final ExpressionValidator getExpressionValidator()
    {
        return expressionValidator;
    }

    public final ColumnReferenceValidator getColumnReferenceValidator()
    {
        return columnReferenceValidator;
    }

    public final List<SelectValidator> getFromSubSelectValidators()
    {
        return fromSubSelectValidators;
    }

    public final SelectValidator getParentSelectValidator()
    {
        return parentSelectValidator;
    }

    public final VisitingPart getVisitingPart()
    {
        return visitingPart;
    }

    public final void setVisitingPart(VisitingPart visitingPart)
    {
        this.visitingPart = visitingPart;
    }
}
