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

package ca.nrc.cadc.conformance.uws;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestProperties
{
    public static final String NEW_LINE = System.getProperty("line.separator");

    public String filename;
    public Map<String, List<String>> parameters;
    public Map<String, List<String>> preconditions;
    public Map<String, List<String>> expectations;

    public TestProperties()
    {
        super();
    }

    public void load(Reader reader, String propertiesFilename) throws IOException
    {
        String strLine, key, value;
        char firstChar;
        List<String> valueList;
        int idxColon, idxEquals, lineLength;
        Map<String, List<String>> targetMap = null;

        parameters = new HashMap<String, List<String>>();
        preconditions = new HashMap<String, List<String>>();
        expectations = new HashMap<String, List<String>>();

        BufferedReader br = new BufferedReader(reader);
        //Read File Line By Line
        while ((strLine = br.readLine()) != null)
        {
            strLine = strLine.trim();
            targetMap = parameters;
            
            lineLength = strLine.length();
            if (lineLength == 0)
                continue;

            firstChar = strLine.charAt(0);
            if (firstChar == '#' || firstChar == '!') //comment line
                continue;
            
            idxEquals = strLine.indexOf('=');
            idxColon = strLine.indexOf(':');
            if (idxColon != -1 && idxColon < idxEquals) // precondition or expectation
            {
                if (strLine.startsWith("expect"))
                    targetMap = expectations;
                else if (strLine.startsWith("precond"))
                    targetMap = preconditions;
                strLine = strLine.substring(idxColon + 1);
                idxEquals = strLine.indexOf('=');
            }
            
            if (idxEquals == 0) // "=foo"
                continue;

            key = strLine.substring(0, idxEquals).trim();
            value = strLine.substring(idxEquals + 1).trim();

            valueList = targetMap.get(key);
            if (valueList == null) // the key is not in parameters yet 
            {
                valueList = new ArrayList<String>();
                targetMap.put(key, valueList);
            }
            valueList.add(value);
        }
        //Close the buffered reader
        br.close();

        filename = propertiesFilename;
    }

    public String toString()
    {
        List<String> valueList;

        StringBuilder sb = new StringBuilder();
        List<String> keyList = new ArrayList<String>(parameters.keySet());
        for (String key : keyList)
        {
            valueList = parameters.get(key);
            for (String value : valueList)
            {
                sb.append(key).append('=').append(value).append(NEW_LINE);
            }
        }
        return sb.toString();
    }

}
