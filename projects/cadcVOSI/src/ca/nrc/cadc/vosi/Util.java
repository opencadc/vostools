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

package ca.nrc.cadc.vosi;

import org.jdom.Element;

/**
 * @author zhangsa
 *
 */
public class Util
{
    
    /**
     * find the part of a string that is before the first occurance of a sub-string named searched. 
     * 
     * e.g. getStringPartBefore("abcDEfghDE", "DE") returns "abc"
     * 
     * @param full: the input full string
     * @param searched: the string being searched
     * @return substring before the first occurance of searched.  If searched is not found, return the original full string.
     */
    public static String getStringPartBefore(String full, String searched)
    {
        String rtn = full;
        int idx = full.indexOf(searched);
        if (idx >= 0 )
            rtn = full.substring(0, idx);
        return rtn;
    }

    /**
     * find the part of a string that is AFTER the first occurance of a sub-string named searched. 
     * 
     * e.g. getStringPartBefore("abcDEfghDE", "DE") returns "fghDE"
     * 
     * @param full: the input full string
     * @param searched: the string being searched
     * @return substring after the first occurance of searched.  If searched is not found, return NULL.
     */
    public static String getStringPartAfter(String full, String searched)
    {
        String rtn = null;
        int idx = full.indexOf(searched);
        if (idx >= 0 )
            rtn = full.substring(idx + searched.length());
        return rtn;
    }

    /**
     * Add a child XML element
     * 
     * @param ele0 the parent element 
     * @param chdName name of the to-be-added element
     * @param chdText text of the to-be-added element
     * @return the added new child element
     */
    public static Element addChild(Element ele0, String chdName, String chdText)
    {
        Element ele = null;
        if (chdText != null && !chdText.equals(""))
        {
            ele = new Element(chdName);
            ele.setText(chdText);
            ele0.addContent(ele);
        }
        return ele;
    }

}
