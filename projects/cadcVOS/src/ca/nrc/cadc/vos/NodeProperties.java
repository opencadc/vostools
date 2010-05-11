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

package ca.nrc.cadc.vos;

import java.util.Collection;
import java.util.HashSet;

/**
 * Extension of HashSet that overrides equals, contains, and remove.
 * 
 * @author majorb
 *
 * @param <E>
 */
public class NodeProperties<E> extends HashSet<E>
{
    
    private static final long serialVersionUID = 6461983219785873279L;

    /**
     * No argument constructor.
     */
    NodeProperties()
    {
        super();
    }
    
    /**
     * Contructor with existing collection
     * @param c
     */
    NodeProperties(Collection<? extends E> c)
    {
        super(c);
    }
    
    /**
     * Returns true if the size of the set is the same and
     * each has the same NodeProperty members.
     */
    public boolean equals(Object o)
    {
        if (o instanceof NodeProperties)
        {
            NodeProperties<NodeProperty> np = ((NodeProperties<NodeProperty>) o);
            if (np.size() == this.size())
            {
                for (Object o1 : this)
                {
                    NodeProperty n1 = (NodeProperty) o1;
                    boolean propertyFound = false;
                    for (Object o2 : np)
                    {
                        NodeProperty n2 = (NodeProperty) o2;
                        if (n1.equals(n2))
                        {
                            propertyFound = true;
                        }
                    }
                    if (!propertyFound)
                    {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }
    
    /**
     * Returns true if a node property with the same
     * URI exists.
     */
    public boolean contains(Object o2)
    {
        if (o2 instanceof NodeProperty)
        {
            NodeProperty n2 = (NodeProperty) o2;
            for (Object o1 : this)
            {
                if (o1 instanceof NodeProperty)
                {
                    NodeProperty n1 = (NodeProperty) o1;
                    if (n1.equals(n2))
                    {
                        return true;
                    }
                }
            }
            return false;
        }
        return super.contains(o2);
    }
    
    /**
     * Removes the property with the same URI
     * as in o2.
     */
    public boolean remove(Object o2)
    {
        if (o2 instanceof NodeProperty)
        {
            NodeProperty n2 = (NodeProperty) o2;
            Object toRemove = null;
            for (Object o1: this)
            {
                if (o1 instanceof NodeProperty)
                {
                    NodeProperty n1 = (NodeProperty) o1;
                    if (n1.equals(n2))
                    {
                        toRemove = o1;
                    }
                }
            }
            if (toRemove == null)
            {
                return false;
            }
            else
            {
                return super.remove(toRemove);
            }
        }
        return false;
    }
    
    /**
     * Lists the contents of this set.
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        
        for (Object o : this)
        {
            NodeProperty p = (NodeProperty) o;
            sb.append(p);
            sb.append(", ");
        }
        if (this.size() > 0)
        {
            sb.delete(sb.length() - 2, sb.length());
        }
        sb.append("]");
        return sb.toString();
    }

}
