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
 * @author zhangsa
 *
 * Please see the section 3.7 of the specification document here:
 * http://www.ivoa.net/Documents/VOSpace/20101112/WD-VOSpace-2.0-20101112.html
 *
 * This will lay out details of the fields.
 *
 * Field names have been kept consistent with the document for simplicity and
 * conformity.
 */
public class Search
{
    // Supported query parameters for a search.
    public static final String[] SUPPORTED_PARAMETERS =
            {
                    "detail"
            };

    private String uri;
    private Results results;
    private String matches;
    private Collection<VOSURI> node;


    /**
     * Basic empty constructor.
     */
    public Search()
    {
    }

    /**
     * Complete constructor.
     *
     * @param uri       An OPTIONAL identifier indicating from which item to
     *                  continue a search.
     * @param results   Search results configurations.
     * @param matches   An OPTIONAL search string consisting of properties and
*                  values to match against and joined in conjunction (and)
*                  or disjunction (or).
     * @param node      An OPTIONAL URI(s) identifying the target URIs to be
     */
    public Search(final String uri, final Results results,
                  final String matches, final Collection<VOSURI> node)
    {
        this.uri = uri;
        this.results = results;
        this.matches = matches;
        this.node = node;
    }


    public String getUri()
    {
        return uri;
    }

    public void setUri(final String uri)
    {
        this.uri = uri;
    }

    public Results getResults()
    {
        if (results == null)
        {
            setResults(new Results());
        }

        return results;
    }

    public void setResults(final Results results)
    {
        this.results = results;
    }

    public String getMatches()
    {
        return matches;
    }

    public void setMatches(final String matches)
    {
        this.matches = matches;
    }

    /**
     * An OPTIONAL URI(s) identifying the target URIs to be found.
     *
     * @return  Collection of Node URIs, or empty Collection.  Never null.
     */
    public Collection<VOSURI> getNode()
    {
        if (node == null)
        {
            setNode(new HashSet<VOSURI>());
        }

        return node;
    }

    public void setNode(final Collection<VOSURI> node)
    {
        this.node = node;
    }


    /**
     * Inner class for constraining/formatting search results.
     */
    public static class Results
    {
        private Integer limit;
        private Detail detail;


        public Results()
        {
        }

        public Results(final Integer limit, final Detail detail)
        {
            this.limit = limit;
            this.detail = detail;
        }


        public Results(final Integer limit)
        {
            this(limit, null);
        }

        public Results(final Detail detail)
        {
            this(null, detail);
        }


        public Integer getLimit()
        {
            return limit;
        }

        public void setLimit(final Integer limit)
        {
            this.limit = limit;
        }

        public Detail getDetail()
        {
            return detail;
        }

        public void setDetail(Detail detail)
        {
            this.detail = detail;
        }


        public enum Detail
        {
            MIN, MAX, PROPERTIES
        }
    }
}
