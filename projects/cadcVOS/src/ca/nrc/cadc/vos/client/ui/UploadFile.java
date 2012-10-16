/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2012.                            (c) 2012.
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
*  with OpenCADC.  If not, sesrc/jsp/index.jspe          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 4 $
*
************************************************************************
*/

package ca.nrc.cadc.vos.client.ui;

import java.io.File;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Direction;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.Protocol;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.client.ClientTransfer;
import ca.nrc.cadc.vos.client.VOSpaceClient;
import ca.nrc.cadc.vos.client.VOSpaceTransferListener;

/**
 * Class to upload the supplied file to vospace as the DataNode.
 * 
 * @author majorb
 *
 */
public class UploadFile implements VOSpaceCommand
{
    
    protected static final Logger log = Logger.getLogger(UploadFile.class);
    
    private DataNode dataNode;
    private File file;
    private DateFormat dateFormat = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
    
    public UploadFile(DataNode dataNode, File file)
    {
        if (dataNode == null)
            throw new IllegalArgumentException("dataNode cannot be null.");
        if (file == null)
            throw new IllegalArgumentException("file cannot be null.");
        if (!file.isFile())
            throw new IllegalArgumentException("not a file.");
        if (!file.canRead())
            throw new IllegalArgumentException("cannot read file.");
        this.dataNode = dataNode;
        this.file = file;
    }

    @Override
    public void execute(VOSpaceClient vospaceClient) throws Exception
    {
        boolean upload = false;
        try
        {
            // see if the file exists
            Node serverNode = vospaceClient.getNode(dataNode.getUri().getPath());
            log.debug("Server dataNode already exists, checking metadata.");
            
            // See if a MD5 value for the file exists
            String clientMD5 = dataNode.getPropertyValue(VOS.PROPERTY_URI_CONTENTMD5);
            String serverMD5 = serverNode.getPropertyValue(VOS.PROPERTY_URI_CONTENTMD5);
            if (clientMD5 != null && serverMD5 != null)
            {
                if (clientMD5.equals(serverMD5))
                {
                    log.debug("MD5 values don't match, uploading.");
                    upload = true;
                }
            }
            else
            {
                // compare the sizes and date
                boolean sizesMatch = false;
                boolean datesMatch = false;
                
                String serverSize = serverNode.getPropertyValue(VOS.PROPERTY_URI_CONTENTLENGTH);
                long clientSize = file.length();
                if (Long.parseLong(serverSize) == clientSize)
                {
                    sizesMatch = true;
                }
                
                try
                {
                    Date serverDate = dateFormat.parse(serverNode.getPropertyValue(VOS.PROPERTY_URI_DATE));
                    Date clientDate = new Date(file.lastModified());
                    if (serverDate.equals(clientDate))
                    {
                        datesMatch = true;
                    }
                }
                catch (Exception e)
                {
                    log.debug("Couldn't determine file dates: " + e);
                }
                
                if (!sizesMatch || !datesMatch)
                {
                    log.debug("Size and dates don't match, uploading.");
                    upload = true;
                }
            }
        }
        catch (NodeNotFoundException e)
        {
            // not found = do upload
            upload = true;
        }
        
        if (upload)
        {
            log.debug("Uploading file: " + file.getName() + " to " + dataNode.getUri());
            List<Protocol> protocols = new ArrayList<Protocol>();
            
            AccessControlContext acContext = AccessController.getContext();
            Subject subject = Subject.getSubject(acContext);
            
            if (subject != null)
                protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_PUT));
            else
                protocols.add(new Protocol(VOS.PROTOCOL_HTTP_PUT));

            Transfer transfer = new Transfer(dataNode.getUri(), Direction.pushToVoSpace, null, protocols);
            ClientTransfer clientTransfer = vospaceClient.createTransfer(transfer);
            
            clientTransfer.setMaxRetries(Integer.MAX_VALUE);
            clientTransfer.setTransferListener(new VOSpaceTransferListener(false));
            clientTransfer.setSSLSocketFactory(vospaceClient.getSslSocketFactory());
            clientTransfer.setFile(file);
            
            clientTransfer.runTransfer();

            ExecutionPhase ep = clientTransfer.getPhase();
            if ( ExecutionPhase.ERROR.equals(ep) )
            {
                ErrorSummary es = clientTransfer.getServerError();
                throw new RuntimeException("Internal error: " + es.getSummaryMessage());
            }
            else if ( ExecutionPhase.ABORTED.equals(ep) )
                throw new RuntimeException("Upload aborted");
            else if ( !ExecutionPhase.COMPLETED.equals(ep) )
                throw new RuntimeException("Unexpected upload state: " + ep.name());
        }
    }
    
    @Override
    public String toString()
    {
        return "Upload file " + dataNode.getUri();
    }

}
