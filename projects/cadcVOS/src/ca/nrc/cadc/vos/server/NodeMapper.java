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

package ca.nrc.cadc.vos.server;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.log4j.Logger;
import org.springframework.jdbc.core.RowMapper;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.util.HexUtil;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.LinkNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOS.NodeBusyState;
import ca.nrc.cadc.vos.VOSURI;

/**
 * Class to map a result set into a Node object.
 */
public class NodeMapper implements RowMapper
{
    private static Logger log = Logger.getLogger(NodeMapper.class);
    
    private DateFormat dateFormat;
    private Calendar cal;

    private String authority;
    private String basePath;

    public NodeMapper(String authority, String basePath)
    {
        this.authority = authority;
        this.basePath = basePath;
        this.dateFormat = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
        this.cal = Calendar.getInstance(DateUtil.UTC);
    }

    /**
     * Map the row to the appropriate type of node object.
     * @param rs
     * @param row
     * @return a Node
     * @throws SQLException
     */
    public Object mapRow(ResultSet rs, int row)
        throws SQLException
    {

        long nodeID = rs.getLong("nodeID");
        String name = rs.getString("name");
        String type = rs.getString("type");
        String busyString = rs.getString("busyState");
        String groupRead = rs.getString("groupRead");
        String groupWrite = rs.getString("groupWrite");
        boolean isPublic = rs.getBoolean("isPublic");
        boolean isLocked = rs.getBoolean("isLocked");

        Object ownerObject = rs.getObject("ownerID");
        String contentType = rs.getString("contentType");
        String contentEncoding = rs.getString("contentEncoding");
        String link = null;

        Long contentLength = null;
        Object o = rs.getObject("contentLength");
        if (o != null)
        {
            Number n = (Number) o;
            contentLength = new Long(n.longValue());
        }
        log.debug("readNode: contentLength = " + contentLength);

        Object contentMD5 = rs.getObject("contentMD5");
        Date lastModified = rs.getTimestamp("lastModified", cal);
        
        String path = basePath + "/" + name;
        VOSURI vos;
        try { vos = new VOSURI(new URI("vos", authority, path, null, null)); }
        catch(URISyntaxException bug)
        {
            throw new RuntimeException("BUG - failed to create vos URI", bug);
        }

        Node node;
        if (NodeDAO.NODE_TYPE_CONTAINER.equals(type))
        {
            node = new ContainerNode(vos);
        }
        else if (NodeDAO.NODE_TYPE_DATA.equals(type))
        {
            node = new DataNode(vos);
            ((DataNode) node).setBusy(NodeBusyState.getStateFromValue(busyString));
        }
        else if (NodeDAO.NODE_TYPE_LINK.equals(type))
        {
            link = rs.getString("link");
            try { node = new LinkNode(vos, new URI(link)); }
            catch(URISyntaxException bug)
            {
                throw new RuntimeException("BUG - failed to create link URI", bug);
            }
        }
        else
        {
            throw new IllegalStateException("Unknown node database type: "
                    + type);
        }

        NodeID nid = new NodeID();
        nid.id = nodeID;
        nid.ownerObject = ownerObject;
        node.appData = nid;

        if (contentType != null && contentType.trim().length() > 0)
        {
            node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_TYPE, contentType));
        }

        if (contentEncoding != null && contentEncoding.trim().length() > 0)
        {
            node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTENCODING, contentEncoding));
        }

        if (contentLength != null)
                node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, contentLength.toString()));
        else
            node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, "0"));

        if (contentMD5 != null && contentMD5 instanceof byte[])
        {
            byte[] md5 = (byte[]) contentMD5;
            if (md5.length < 16)
            {
                byte[] tmp = md5;
                md5 = new byte[16];
                System.arraycopy(tmp, 0, md5, 0, tmp.length);
                // extra space is init with 0
            }
            String contentMD5String = HexUtil.toHex(md5);
            node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTMD5, contentMD5String));
        }
        if (lastModified != null)
        {
            node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_DATE, dateFormat.format(lastModified)));
        }
        if (groupRead != null && groupRead.trim().length() > 0)
        {
            node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_GROUPREAD, groupRead));
        }
        if (groupWrite != null && groupWrite.trim().length() > 0)
        {
            node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_GROUPWRITE, groupWrite));
        }
        node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, isPublic ? "true" : "false"));
        
        if (isLocked)
            node.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_ISLOCKED, isLocked ? "true" : "false"));
        
        // set the read-only flag on the properties
        for (String propertyURI : VOS.READ_ONLY_PROPERTIES)
        {
            int propertyIndex = node.getProperties().indexOf(new NodeProperty(propertyURI, ""));
            if (propertyIndex != -1)
            {
                node.getProperties().get(propertyIndex).setReadOnly(true);
            }
        }
        log.debug("read: " + node.getUri() + "," + node.appData);
        return node;
    }

}
