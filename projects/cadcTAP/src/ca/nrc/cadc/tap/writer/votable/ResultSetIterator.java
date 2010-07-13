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

package ca.nrc.cadc.tap.writer.votable;

import ca.nrc.cadc.tap.writer.formatter.Formatter;
import ca.nrc.cadc.tap.writer.formatter.ResultSetFormatter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.apache.log4j.Logger;
import org.jdom.Element;
import org.jdom.Namespace;

/**
 * Class that iterates through a ResultSet. The Iterator returns a JDOM Element
 * containing a HTML table row representation of the ResultSet.
 */
public class ResultSetIterator implements Iterator
{
    private static Logger log = Logger.getLogger(ResultSetIterator.class);

    // ResultSet for iteration.
    private ResultSet resultSet;

    // List of Formatter's in selectList order.
    private List<Formatter> formatters;

    // Namespace of the element.
    private Namespace namespace;

    // Indicates if the ResultSet has another element
    // past the current position in the ResultSet.
    private boolean hasNext;

    private int numRows = 0;

    /**
     * Constructor.
     *
     * @param resultSet
     * @param formatters
     * @param namespace 
     */
    public ResultSetIterator(ResultSet resultSet, List<Formatter> formatters, Namespace namespace)
    {
        this.resultSet = resultSet;
        this.formatters = formatters;
        this.namespace = namespace;
        if (resultSet == null)
        {
            this.hasNext = false;
            return;
        }
        try
        {
            this.hasNext = resultSet.next();
        }
        catch(SQLException e)
        {
            hasNext = false;
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     *
     * @return true if the iteration has more elements.
     */
    public boolean hasNext()
    {
        return hasNext;
    }

    /**
     * Gets the next row in the ResultSet, and builds a JDOM Element
     * containing a single HTML table row, with table data elements for
     * each column in the row.
     * 
     * @return the next element in the iteration.
     * @throws NoSuchElementException iteration has no more elements.
     */
    public Object next()
        throws NoSuchElementException
    {
        try
        {
            // If no more rows in the ResultSet throw a NoSuchElementException.
            if (!hasNext)
                throw new NoSuchElementException("No more rows in the ResultSet");

            // Create the TR element.
            Element tableRow = new Element("TR", namespace);

            // Loop through the ResultSet adding the table data elements.
            for (int columnIndex = 1; columnIndex <= resultSet.getMetaData().getColumnCount(); columnIndex++)
            {
                Formatter formatter = formatters.get(columnIndex - 1);
                Element tableData = new Element("TD", namespace);
                if (formatter instanceof ResultSetFormatter)
                    tableData.setText(((ResultSetFormatter) formatter).format(resultSet, columnIndex));
                else
                    tableData.setText(formatter.format(resultSet.getObject(columnIndex)));
                tableRow.addContent(tableData);
            }
            // Get the next row.
            hasNext = resultSet.next();

            return tableRow;
        }
        catch (SQLException e)
        {
            hasNext = false;
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * @throws UnsupportedOperationException
     */
    public void remove()
    {
        throw new UnsupportedOperationException();
    }

}
