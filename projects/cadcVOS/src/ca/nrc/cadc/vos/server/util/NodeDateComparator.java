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
package ca.nrc.cadc.vos.server.util;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Class to compare two Nodes based on their LastModified property. Nodes are
 * sorted in descending Date order, with the most recent Dates first.
 *
 * @author jburke
 */
public class NodeDateComparator implements Comparator
{
    private DateFormat df = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);

    /**
     * Compare two Nodes based on their LastModified property.
     *
     * @param o1 The first Node to compare.
     * @param o2 The second Node to compare.
     * @return 
     */
    public int compare(Object o1, Object o2)
    {
        // Get the Nodes.
        Node n1 = (Node) o1;
        Node n2 = (Node) o2;

        // Properties for each Node.
        List<NodeProperty> nps1 = n1.getProperties();
        List<NodeProperty> nps2 = n2.getProperties();

        // LastModifed date for each Node.
        String np1 = null;
        for (NodeProperty nodeProperty : nps1)
        {
            if (nodeProperty.getPropertyURI().equals(VOS.PROPERTY_URI_DATE))
                np1 = nodeProperty.getPropertyValue();

        }
        String np2 = null;
        for (NodeProperty nodeProperty : nps2)
        {
            if (nodeProperty.getPropertyURI().equals(VOS.PROPERTY_URI_DATE))
                np2 = nodeProperty.getPropertyValue();

        }

        // If Node A does not have a LastModified property while Node B does
        // have a LastModified property, then Node A is considered to be
        // less than Node B. If neither Node's have a LastModified property,
        // then the Nodes are considered to be equal.
        if (np1 == null && np2 != null)
            return -1;
        if (np1 != null && np2 == null)
            return 1;
        if (np1 == null && np2 == null)
            return 0;

        Date d1 = null;

        try
        {
            d1 = df.parse(np1);
        }
        catch (ParseException ignore) { }

        Date d2 = null;
        try
        {
            d2 = df.parse(np2);
        }
        catch (ParseException ignore) { }

        // Dates are handled the same as the LastModified property. A Node with
        // a null Date is considered to be less than a Node whose Date is not null.
        if (d1 == null && d2 != null)
            return -1;
        if (d1 != null && d2 == null)
            return 1;
        if (d1 == null && d2 == null)
            return 0;

        return d2.compareTo(d1);
    }

}
