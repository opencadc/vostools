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

import java.io.FileNotFoundException;
import java.net.URI;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.util.FileMetadata;
import ca.nrc.cadc.util.FileMetadataSource;
import ca.nrc.cadc.uws.util.StringUtil;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;

/**
 * Class to get and set the meta data of vospace data nodes.  This class
 * requires that the NodePersistence be set before any calls are made.
 * 
 * @author majorb
 *
 */
public class VOSpaceFileMetadataSource implements FileMetadataSource
{
    protected static Logger log = Logger.getLogger(VOSpaceFileMetadataSource.class);
    
    private NodePersistence nodePersistence;

    public VOSpaceFileMetadataSource() { }

    /**
     * Get the current file metadata for the specified resource.
     * 
     * @param resource identifier for the target resource
     * @return a FileMetadata object, populated with available metadata
     * @throws FileNotFoundException if the specified resource is not found
     * @throws IllegalArgumentException if the specified resource is not a file
     */
    public FileMetadata get(URI resource) 
        throws FileNotFoundException, IllegalArgumentException
    {
        VOSURI vos = new VOSURI(resource);
        Node persistentNode = getPersistentNode(vos);
        return get(persistentNode);
    }
    
    /**
     * Get the current file metadata for the specified resource.
     * 
     * @param resource identifier for the target resource
     * @return a FileMetadata object, populated with available metadata
     * @throws FileNotFoundException if the specified resource is not found
     * @throws IllegalArgumentException if the specified resource is not a file
     */
    public FileMetadata get(Node persistentNode) 
        throws FileNotFoundException, IllegalArgumentException
    {
        // TODO: how to decide if we need to call NodePersistence.getProperties
        // except by knowing the props we want are already loaded by the impl?

        FileMetadata fileMetadata = new FileMetadata();
        
        // fileName
        fileMetadata.setFileName(persistentNode.getName());
        
        // contentEncoding
        String contentEncoding = persistentNode.getPropertyValue(VOS.PROPERTY_URI_CONTENTENCODING);
        if (contentEncoding != null)
        {
            fileMetadata.setContentEncoding(contentEncoding);
        }
        
        // contentLength
        String contentLength = persistentNode.getPropertyValue(VOS.PROPERTY_URI_CONTENTLENGTH);
        if (contentLength != null)
        {
            try
            {
                fileMetadata.setContentLength(new Long(contentLength));
            }
            catch (NumberFormatException e)
            {
                log.warn("Content Length is not a number for resource: " + persistentNode.getUri());
            }
        }
        
        // contentType
        String contentType = persistentNode.getPropertyValue(VOS.PROPERTY_URI_TYPE);
        if (contentType != null)
        {
            fileMetadata.setContentType(contentType);
        }
        
        // md5Sum
        String md5Sum = persistentNode.getPropertyValue(VOS.PROPERTY_URI_CONTENTMD5);
        if (md5Sum != null)
        {
            fileMetadata.setMd5Sum(md5Sum);
        }
        
        // lastModified
        String lastModified = persistentNode.getPropertyValue(VOS.PROPERTY_URI_DATE);
        if (lastModified != null)
        {
            try
            {
                DateFormat df = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
                fileMetadata.setLastModified(df.parse(lastModified));
            }
            catch (ParseException e)
            {
                log.warn("Couldn't convert date string "
                        + lastModified
                        + " to Date object.", e);
            }
        }

        return fileMetadata;
    }

    /** 
     * Set the current file metadata for the specified resource. 
     * 
     * @param resource identifier for the target resource
     * @param meta new metadata values to persist
     * @throws FileNotFoundException if the specified resource is not found
     * @throws IllegalArgumentException if the specified resource is not a file
     */
    public void set(final URI resource, final FileMetadata meta)
            throws FileNotFoundException, IllegalArgumentException
    {
        if (meta == null)
            throw new IllegalArgumentException("FileMetadata is null.");
        VOSURI uri = new VOSURI(resource);
        Node persistentNode = getPersistentNode(uri);
        List<NodeProperty> props = new ArrayList<NodeProperty>();
        

        String existingContentLengthString = 
            persistentNode.getPropertyValue(VOS.PROPERTY_URI_CONTENTLENGTH);
        long contentLengthDifference;

        if (StringUtil.hasText(existingContentLengthString))
        {
            long existingContentLength =
                    Long.parseLong(existingContentLengthString);

            if (meta.getContentLength() != null)
            {
                contentLengthDifference =
                        meta.getContentLength() - existingContentLength;
            }
            else
            {
                contentLengthDifference = 0;
            }
        }
        else if (meta.getContentLength() != null)
        {
            contentLengthDifference = meta.getContentLength();
        }
        else
        {
            contentLengthDifference = 0;
        }

        // fileName
        // Ignore file name property - this is the name of the node as specified
        // in the resource
        
        // contentEncoding
        if (meta.getContentEncoding() != null)
        {
            NodeProperty contentEncoding = new NodeProperty(VOS.PROPERTY_URI_CONTENTENCODING, meta.getContentEncoding());
            //persistentNode.getProperties().remove(contentEncoding);
            props.add(contentEncoding);
        }
        
        // contentLength
        if (meta.getContentLength() != null)
        {
            NodeProperty contentLength = new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, meta.getContentLength().toString());
            //persistentNode.getProperties().remove(contentLength);
            props.add(contentLength);
        }
        
        // contentType
        if (meta.getContentType() != null)
        {
            NodeProperty contentType = new NodeProperty(VOS.PROPERTY_URI_TYPE, meta.getContentType());
            //persistentNode.getProperties().remove(contentType);
            props.add(contentType);
        }
        
        // md5Sum
        if (meta.getMd5Sum() != null)
        {
            NodeProperty md5Sum = new NodeProperty(VOS.PROPERTY_URI_CONTENTMD5, meta.getMd5Sum());
            //persistentNode.getProperties().remove(md5Sum);
            props.add(md5Sum);
        }
        
        nodePersistence.updateProperties(persistentNode, props);
        
        // TODO: this API call easier to optimise
        //nodePersistence.setFileMetadata(persistentNode, meta);

        if (contentLengthDifference != 0)
        {
            ContainerNode parent = persistentNode.getParent();
            while (parent != null)
            {
                nodePersistence.updateContentLength(parent, contentLengthDifference);
                parent = parent.getParent();
            }
        }
    }

    /**
     * Return the persistent node at the specified resource.
     * @param resource      The URI to search for.
     * @return              Node at the given URI.
     * @throws FileNotFoundException        If a node with the given URI does
     *                                      not exist.
     */
    protected Node getPersistentNode(VOSURI resource)
            throws FileNotFoundException
    {
        if (nodePersistence == null)
        {
            throw new IllegalStateException("NodePersistence not set.");
        }
        try
        {
            Node persistentNode = nodePersistence.get(resource);
            if (! (persistentNode instanceof DataNode))
                throw new IllegalArgumentException("not a data node: " + resource.getURIObject().toASCIIString());
            return persistentNode;
        }
        catch(NodeNotFoundException ex)
        {
            throw new FileNotFoundException(resource.getURIObject().toASCIIString());
        }
    }
    
    /**
     * NodePersistence setter.
     * @param nodePersistence
     */
    public void setNodePersistence(NodePersistence nodePersistence)
    {
        this.nodePersistence = nodePersistence;
    }
    
    /**
     * NodePersistence getter.
     * @return
     */
    public NodePersistence getNodePersistence()
    {
        return nodePersistence;
    }

}
