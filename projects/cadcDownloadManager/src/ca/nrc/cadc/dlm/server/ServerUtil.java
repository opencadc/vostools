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


package ca.nrc.cadc.dlm.server;

import ca.nrc.cadc.dlm.DownloadUtil;
import ca.nrc.cadc.util.StringUtil;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;

/**
 * TODO.
 *
 * @author pdowler
 */
public class ServerUtil
{
    private static final Logger log = Logger.getLogger(ServerUtil.class);
    
    // public API for DownloadManager is to accept and interpret these two params
    static final String PARAM_URI = "uri";
    static final String PARAM_URILIST = "uris";
    static final String PARAM_PARAMLIST = "params";
    static final String PARAM_METHOD = "method";

    static final List<String> INTERNAL_PARAMS = new ArrayList<String>();
    static
    {
        INTERNAL_PARAMS.add(PARAM_URI);
        INTERNAL_PARAMS.add(PARAM_URILIST);
        INTERNAL_PARAMS.add(PARAM_PARAMLIST);
        INTERNAL_PARAMS.add(PARAM_METHOD);
    }
    private ServerUtil() { }

    public static String getCodebase(HttpServletRequest request)
    {
        try
        {
            URL req = new URL(request.getRequestURL().toString());
            String ret = req.getProtocol() + "://" + req.getHost();
            ret += request.getContextPath();
            return ret;
        }
        catch(Throwable oops)
        {
            log.error("failed to generate codebase URL", oops);
        }
        return null;
    }

    /**
     * Extract all download content related parameters from the request.
     *
     * @param request
     * @return
     */
    public static Map<String,List<String>> getParameters(HttpServletRequest request)
    {
        // internal repost
        String params = request.getParameter("params");
        if (params != null)
            return DownloadUtil.decodeParamMap(params);

        // original post
        Map<String,List<String>> paramMap = new TreeMap<String,List<String>>();
        Enumeration e = request.getParameterNames();
        while ( e.hasMoreElements() )
        {
            String key = (String) e.nextElement();
            if ( !INTERNAL_PARAMS.contains(key) )
            {
                String[] values = request.getParameterValues(key);
                if (values != null && values.length > 0)
                    paramMap.put(key, Arrays.asList(values));
            }
        }
        return paramMap;
    }

    /**
     * Extract all download content related parameters from the request.
     *
     * @param request
     * @return
     */
    public static List<String> getURIs(HttpServletRequest request)
    {
        // internal repost
        String uris = request.getParameter("uris");
        if (uris != null)
            return DownloadUtil.decodeListURI(uris);

        // original post
        String[] uriParams = request.getParameterValues(PARAM_URI);
        
        List<String> ret = new ArrayList<String>();

        if (uriParams != null)
        {
            for (String u : uriParams)
            {
                if ( StringUtil.hasText(u) )
                    ret.add(u);
            }
        }

       return ret;
    }

}
