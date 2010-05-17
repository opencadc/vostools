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

package ca.nrc.cadc.vos.util;

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.log4j.Logger;

import ca.nrc.cadc.util.FileMetadata;
import ca.nrc.cadc.util.FileMetadataSource;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodePersistence;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.dao.SearchNode;

public class VOSpaceFileMetadataSource implements FileMetadataSource
{
    
    private static Logger log = Logger.getLogger(VOSpaceFileMetadataSource.class);
    
    private NodePersistence nodePersistence;
    
    public VOSpaceFileMetadataSource()
    {
    }

    @Override
    public FileMetadata get(URI resource) throws FileNotFoundException,
            IllegalArgumentException
    {
        if (nodePersistence == null)
        {
            throw new IllegalStateException("NodePersistence not set.");
        }
        try
        {
            Node searchNode = new SearchNode(new VOSURI(resource));
            Node persistentNode = NodeUtil.iterateStack(searchNode, null, nodePersistence);
            
            if (! (persistentNode instanceof DataNode))
            {
                throw new FileNotFoundException("Node at " + resource + " is not a data node.");
            }
            
            FileMetadata fileMetadata = new FileMetadata();
            
            // fileName
            fileMetadata.setFileName(persistentNode.getName());
            
            // contentEncoding
            NodeProperty contentEncoding = persistentNode.getProperties().getProperty(VOS.PROPERTY_URI_CONTENTENCODING);
            if (contentEncoding != null)
            {
                fileMetadata.setContentEncoding(contentEncoding.getPropertyValue());
            }
            
            // contentLength
            NodeProperty contentLength = persistentNode.getProperties().getProperty(VOS.PROPERTY_URI_CONTENTLENGTH);
            if (contentLength != null)
            {
                try
                {
                    fileMetadata.setContentLength(new Long(contentLength.getPropertyValue()));
                }
                catch (NumberFormatException e) {
                    log.warn("Content Length is not a number for resource: " + resource);
                }
            }
            
            // contentType
            NodeProperty contentType = persistentNode.getProperties().getProperty(VOS.PROPERTY_URI_CONTENTTYPE);
            if (contentType != null)
            {
                fileMetadata.setContentType(contentType.getPropertyValue());
            }
            
            // md5Sum
            NodeProperty md5Sum = persistentNode.getProperties().getProperty(VOS.PROPERTY_URI_CONTENTMD5);
            if (md5Sum != null)
            {
                fileMetadata.setMd5Sum(md5Sum.getPropertyValue());
            }
            
            return fileMetadata;
        }
        catch (URISyntaxException e)
        {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        catch (NodeNotFoundException e)
        {
            throw new FileNotFoundException(resource.toString());
        }
    }

    @Override
    public void set(URI resource, FileMetadata meta)
            throws FileNotFoundException, IllegalArgumentException
    {
        // TODO Auto-generated method stub
        
    }
    
    public void setNodePersistence(NodePersistence nodePersistence)
    {
        this.nodePersistence = nodePersistence;
    }
    
    public NodePersistence getNodePersistence()
    {
        return nodePersistence;
    }

}
