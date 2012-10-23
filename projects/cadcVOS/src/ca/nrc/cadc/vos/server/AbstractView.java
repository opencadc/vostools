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

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;
import org.restlet.data.Disposition;
import org.restlet.data.Encoding;
import org.restlet.data.MediaType;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.View;
import ca.nrc.cadc.vos.server.auth.VOSpaceAuthorizer;

/**
 * This abstract class defines the required behaviour of server side implementations
 * of a View.
 * 
 * Generally, subclasses should implement setNode(Node, String) and either:
 * 
 * 1) For views that result in a redirect:
 *     - getRedirectURL()
 *     
 *     or, the set of:
 * 
 * 2) For views that result in data returned: 
 *     - write(OutputStream)
 *     - getContentLength()
 *     - getMediaType()
 *     - getEncodings()
 *     - getContentMD5()
 *     
 * The VOSpaceAuthorizer and NodePersistence objects are available to view implementations.
 * 
 * Please note:  The node returned by getNode() has already gone through an authorization
 * check using the VOSpaceAuthorizer.
 * 
 * @author majorb
 *
 */
public abstract class AbstractView extends View
{
    
    protected static Logger log = Logger.getLogger(AbstractView.class);
    
    // The node for which to create the view
    protected Node node;

    protected URL requestURL;
    
    // The node authorizer available for use
    protected VOSpaceAuthorizer voSpaceAuthorizer;
    
    // The node permisistence available for use
    protected NodePersistence nodePersistence;
    
    // The subject in which the view can choose to operate.
    protected Subject subject;
    
    /**
     * AbstractView constructor.
     */
    protected AbstractView()
    {
        super();
        loadSubject();
    }
    
    /**
     * AbstractView constructor.
     * 
     * @param uri The view identifier.
     */
    public AbstractView(URI uri)
    {
        super(uri);
        loadSubject();
    }

    /**
     * AbstractView constructor for service-side.
     *
     * @param uri The view identifier.
     * @param original 
     */
    public AbstractView(URI uri, boolean original)
    {
        super(uri, original);
        loadSubject();
    }
    
    
    /**
     * Get the node for this view.
     */
    protected Node getNode()
    {
        return node;
    }
    
    /**
     * Get the VOSpaceAuthorizer
     * 
     * Note: The node available in getNode() has already gone
     * through an authorization check with this authorizer.
     */
    protected VOSpaceAuthorizer getVOSpaceAuthorizer()
    {
        return voSpaceAuthorizer;
    }
    
    /**
     * Get the NodePersistence
     */
    protected NodePersistence getNodePersistence()
    {
        return nodePersistence;
    }
    
    /**
     * Set the node to be used by the view.
     * 
     * @param node The node to be used.
     * @param viewReference The name used to reference this view.
     * @param requestURL the original URL
     * @throws UnsupportedOperationException If this view cannot be created for the given node.
     */
    public void setNode(Node node, String viewReference, URL requestURL)
        throws UnsupportedOperationException, TransientException
    {
        if (node == null)
            throw new UnsupportedOperationException("BUG: node for view is null.");
        if (requestURL == null)
            throw new UnsupportedOperationException("BUG: return requestURL for view is null.");
        this.node = node;
        this.requestURL = requestURL;
    }
    
    /**
     * Return true if the supplied node accepts this view.
     * The default implementation here always returns false.
     * @param node
     * @return
     */
    public boolean canAccept(Node node)
    {
        return false;
    }
    
    /**
     * Return true if the supplied node provides this view.
     * The default implementation here always returns false.
     * @param node
     * @return
     */
    public boolean canProvide(Node node)
    {
        return false;
    }
    
    /**
     * Return the list of protocols with the endpoints using information
     * in the parameters.
     * @param node The node for the transfer.
     * @param transfer The transfer document.
     * @param serverName The target server.
     * @param port The target server port.
     * @param queryString The query string to be included on the endpoints.
     * @return
     */
    //public List<Protocol> getTransferEndpointsX(Node node, Transfer transfer, String serverName, int port, String queryString)
    //{
    //    return null;
    //}

    /**
     * Return the redirect URL for this view, or null if a redirect is not
     * a result of the view.
     */
    public URL getRedirectURL()
    {
        return null;
    }
    
    /**
     * Write the view data to the outputStream if that is the result of the view.
     * @param outputStream The output stream on which to write.
     * @throws IOException If an I/O problem occurs.
     */
    public void write(OutputStream outputStream) throws IOException
    {
    }
    
    /**
     * Return the content length of the data for the view.
     */
    public long getContentLength()
    {
        return 0;
    }
    
    /**
     * Return the content type of the data for the view.
     */
    public MediaType getMediaType()
    {
        return null;
    }
    
    /**
     * Return the content disposition of the data for the view.
     */
    public Disposition getDisposition()
    {
        return null;
    }
    
    /**
     * Return the content encoding of the data for the view.
     */
    public List<Encoding> getEncodings()
    {
        return null;
    }
    
    /**
     * Return the MD5 Checksum of the data for the view.
     */
    public String getContentMD5()
    {
        return null;
    }
    
    /**
     * Return the last modification date of the Node.
     */
    public Date getLastModified()
    {
        NodeProperty modificationDate = node.findProperty(VOS.PROPERTY_URI_DATE);
        if (modificationDate != null)
        {
            try
            {
                DateFormat df = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
                return df.parse(modificationDate.getPropertyValue());
            }
            catch (ParseException e)
            {
                log.warn("Date " + modificationDate.getPropertyValue()
                        + " could not be parsed.");
            }
        }
        return null;
    }

    public void setVOSpaceAuthorizer(VOSpaceAuthorizer vospaceAuthorizer)
    {
        this.voSpaceAuthorizer = vospaceAuthorizer;
    }

    public void setNodePersistence(NodePersistence nodePersistence)
    {
        this.nodePersistence = nodePersistence;
    }
    
    private void loadSubject()
    {
        AccessControlContext acContext = AccessController.getContext();
        subject = Subject.getSubject(acContext);
    }

}
