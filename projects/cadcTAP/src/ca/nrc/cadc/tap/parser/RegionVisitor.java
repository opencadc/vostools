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
*  $Revision: 1 $
*
************************************************************************
*/

package ca.nrc.cadc.tap.parser;

import net.sf.jsqlparser.expression.Expression;
import org.apache.log4j.Logger;

/**
 * This visitor finds all occurances of ADQL geometry constructs. The default
 * implementation of the protected <code>handle</code> methods throw an
 * UnsupportedOperationException.
 * 
 * @author pdowler
 */
public class RegionVisitor 
{
    private static Logger log = Logger.getLogger(RegionVisitor.class);
    
    public RegionVisitor() { }
    
    /**
     * This method is called when a CONTAINS is found outside of a predicate.
     * This could occurr if the query had CONTAINS(...) in the select list or as
     * part of an arithmetic expression or aggregate function (since CONTAINS 
     * returns a numeric value). 
     * 
     * @param ex the CONTAINS expression
     */
    protected void handleContains(Expression ex)
    {
        throw new UnsupportedOperationException("CONTAINS not supported");
    }
    
    /**
     * This method is called when CONTAINS is one of the arguments in a predicate.
     * 
     * @param ex the predicate with a CONTAINS argument
     */
    protected void handleContainsPredicate(Expression ex)
    {
        throw new UnsupportedOperationException("CONTAINS not supported");
    }
    
    /**
     * This method is called when a INTERSECTS is found outside of a predicate.
     * This could occurr if the query had INTERSECTS(...) in the select list or as
     * part of an arithmetic expression or aggregate function (since INTERSECTS 
     * returns a numeric value). 
     * 
     * @param ex the CONTAINS expression
     */
    protected void handleIntersects(Expression ex)
    {
        throw new UnsupportedOperationException("INTERSECTS not supported");
    }
    
    /**
     * This method is called when INTERSECTS is one of the arguments in a predicate.
     * 
     * @param ex the predicate with a INTERSECTS argument
     */
    protected void handleIntersectsPredicate(Expression ex)
    {
        throw new UnsupportedOperationException("INTERSECTS not supported");
    }
    
    /**
     * This method is called when a POINT geometry value is found.
     * 
     * @param ex the POINT expression
     */
    protected void handlePoint(Expression ex)
    {
        throw new UnsupportedOperationException("POINT not supported");
    }
    
    /**
     * This method is called when a CIRCLE geometry value is found.
     * 
     * @param ex the CIRCLE expression
     */
    protected void handleCircle(Expression ex)
    {
        throw new UnsupportedOperationException("CIRCLE not supported");
    }
    
    /**
     * This method is called when a BOX geometry value is found.
     * 
     * @param ex the BOX expression
     */
    protected void handleBox(Expression ex)
    {
        throw new UnsupportedOperationException("BOX not supported");
    }
    
    /**
     * This method is called when a POLYGON geometry value is found.
     * 
     * @param ex the POLYGON expression
     */
    protected void handlePolygon(Expression ex)
    {
        throw new UnsupportedOperationException("POLYGON not supported");
    }
    
    /**
     * This method is called when a REGION geometry value is found.
     * 
     * @param ex the REGION expression
     */
    protected void handleRegion(Expression ex)
    {
        throw new UnsupportedOperationException("REGION not supported");
    }

}
