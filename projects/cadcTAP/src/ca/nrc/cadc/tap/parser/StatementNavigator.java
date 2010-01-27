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

import net.sf.jsqlparser.statement.StatementVisitor;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.replace.Replace;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectVisitor;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;

import org.apache.log4j.Logger;

/**
 * A wrapper class to hold the select visitor.
 * 
 * @author zhangsa
 *
 */
class StatementNavigator implements StatementVisitor
{
    private static Logger log = Logger.getLogger(StatementNavigator.class);

    protected SelectVisitor selectVisitor;

    public StatementNavigator(SelectVisitor selectVisitor)
    {
        this.selectVisitor = selectVisitor;
    }

    public void visit(Select s)
    {
        log.debug("visit(Select)");
        s.getSelectBody().accept(selectVisitor);
    }

    /**
     * Throws an UnsupportedOperationException
     * 
     * @param arg0
     * @throws UnsupportedOperationException
     */
    public void visit(Delete arg0)
    {
        log.debug("visit(Delete)");
        throw new UnsupportedOperationException("Delete");
    }

    /**
     * Throws an UnsupportedOperationException
     * 
     * @param arg0
     * @throws UnsupportedOperationException
     */
    public void visit(Update arg0)
    {
        log.debug("visit(Update)");
        throw new UnsupportedOperationException("Update");
    }

    /**
     * Throws an UnsupportedOperationException
     * 
     * @param arg0
     * @throws UnsupportedOperationException
     */
    public void visit(Insert arg0)
    {
        log.debug("visit(Insert)");
        throw new UnsupportedOperationException("Insert");
    }

    /**
     * Throws an UnsupportedOperationException
     * 
     * @param arg0
     * @throws UnsupportedOperationException
     */
    public void visit(Replace arg0)
    {
        log.debug("visit(Replace)");
        throw new UnsupportedOperationException("Replace");
    }

    /**
     * Throws an UnsupportedOperationException
     * 
     * @param arg0
     * @throws UnsupportedOperationException
     */
    public void visit(Drop arg0)
    {
        log.debug("visit(Drop)");
        throw new UnsupportedOperationException("Drop");
    }

    /**
     * Throws an UnsupportedOperationException
     * 
     * @param arg0
     * @throws UnsupportedOperationException
     */
    public void visit(Truncate arg0)
    {
        log.debug("visit(Truncate)");
        throw new UnsupportedOperationException("Truncate");
    }

    /**
     * Throws an UnsupportedOperationException
     * 
     * @param arg0
     * @throws UnsupportedOperationException
     */
    public void visit(CreateTable arg0)
    {
        log.debug("visit(CreateTable)");
        throw new UnsupportedOperationException("CreateTable");
    }
}
