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

package ca.nrc.cadc.vos.client;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessControlException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.security.auth.Subject;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import ca.nrc.cadc.auth.CertCmdArgUtil;
import ca.nrc.cadc.auth.RunnableAction;
import ca.nrc.cadc.auth.X509CertificateChain;
import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.net.NetUtil;
import ca.nrc.cadc.reg.client.RegistryClient;
import ca.nrc.cadc.util.ArgumentMap;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.util.StringUtil;
import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Direction;
import ca.nrc.cadc.vos.LinkNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeLockedException;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.Protocol;
import ca.nrc.cadc.vos.StructuredDataNode;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.UnstructuredDataNode;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.View;
import ca.nrc.cadc.vos.View.Parameter;

/**
 * Main method for the command-line VOSpace client.
 * 
 * @author pdowler, zhangsa
 */
public class Main implements Runnable
{
    public static final String ARG_HELP = "help";
    public static final String ARG_VERBOSE = "verbose";
    public static final String ARG_DEBUG = "debug";
    public static final String ARG_H = "h";
    public static final String ARG_V = "v";
    public static final String ARG_D = "d";
    public static final String ARG_XSV = "xsv";
    public static final String ARG_NO_RETRY = "noretry";
    public static final String ARG_VIEW = "view";
    public static final String ARG_CREATE = "create";
    public static final String ARG_DELETE = "delete";
    public static final String ARG_SET = "set";
    public static final String ARG_COPY = "copy";
    public static final String ARG_MOVE = "move";
    public static final String ARG_TARGET = "target";
    public static final String ARG_PUBLIC = "public";
    public static final String ARG_GROUP_READ = "group-read";
    public static final String ARG_GROUP_WRITE = "group-write";
    public static final String ARG_PROP = "prop";
    public static final String ARG_SRC = "src";
    public static final String ARG_LINK = "link";
    public static final String ARG_DEST = "dest";
    public static final String ARG_CONTENT_TYPE = "content-type";
    public static final String ARG_CONTENT_ENCODING = "content-encoding";
    public static final String ARG_CONTENT_MD5 = "content-md5";
    public static final String ARG_RECURSIVE = "recursive";
    public static final String ARG_LOCK = "lock";
    public static final String ARG_QUICK = "quick";

    public static final String VOS_PREFIX = "vos://";

    private static Logger log = Logger.getLogger(Main.class);
    private static final int INIT_STATUS = 1; // exit code for initialisation failure
    private static final int NET_STATUS = 2;  // exit code for client-server failures
    
    private static final int MAX_CHILD_SIZE = 1000;
    
    /**
     * Supported node type
     */
    public enum NodeType
    {
    	CONTAINER_NODE, LINK_NODE, STRUCTURED_DATA_NODE, UNSTRUCTURED_DATA_NODE
    }
    /**
     * Operations of VoSpace Client.
     */
    public enum Operation
    {
        VIEW, CREATE, DELETE, SET, COPY, MOVE
    }

    private NodeType nodeType;
    private Operation operation;
    private VOSURI target;
    private List<NodeProperty> properties;
    private String contentType;
    private String contentEncoding;
    
    private URI source;
    private URI destination;
    private Direction transferDirection = null;
    private String baseUrl = null;
    private VOSpaceClient client = null;
    private Subject subject;
    private boolean retryEnabled = false;
    private boolean quickTransfer = false;

    private boolean recursiveMode = false;
    
    // target of a LinkNode
    private URI link;

    /**
     * @param args  The arguments passed into this command.
     */
    public static void main(String[] args)
    {
        ArgumentMap argMap = new ArgumentMap(args);
        Main command = new Main();

        if (argMap.isSet(ARG_HELP) || argMap.isSet(ARG_H))
        {
            usage();
            System.exit(0);
        }

        // Set debug mode
        if (argMap.isSet(ARG_DEBUG) || argMap.isSet(ARG_D))
        {
            Log4jInit.setLevel("ca.nrc.cadc.vos.client", Level.DEBUG);
            Log4jInit.setLevel("ca.nrc.cadc.net", Level.DEBUG);
        }
        else if (argMap.isSet(ARG_VERBOSE) || argMap.isSet(ARG_V))
        {
            Log4jInit.setLevel("ca.nrc.cadc.vos.client", Level.INFO);
        }
        else
            Log4jInit.setLevel("ca", Level.WARN);

        try
        {
            command.validateCommand(argMap);
            command.validateCommandArguments(argMap);
        }
        catch (IllegalArgumentException ex)
        {
            msg("illegal argument(s): " + ex.getMessage());
            msg("");
            usage();
            System.exit(INIT_STATUS);
        }

        try
        {
            command.init(argMap);
            if (command.subject != null)
                Subject.doAs(command.subject, new RunnableAction(command));
            else
                command.run(); // anon
        }
        catch (IllegalArgumentException ex)
        {
            msg("illegal arguments(s): " + ex.getMessage());
            msg("");
            System.exit(INIT_STATUS);
        }
        catch(Throwable t)
        {
            log.error("unexpected failure", t);
            System.exit(NET_STATUS);
        }
        System.exit(0);
    }

    // encapsulate all messages to console here
    private static void msg(String s)
    {
        System.out.println(s);
    }

    public void run()
    {
        log.debug("run - START");
        if (this.operation.equals(Operation.CREATE))
        {
            doCreate();
        }
        else if (this.operation.equals(Operation.DELETE))
        {
            doDelete();
        }
        else if (this.operation.equals(Operation.VIEW))
        {
            doView();
        }
        else if (this.operation.equals(Operation.COPY))
        {
            doCopy();
        }
        else if (this.operation.equals(Operation.MOVE))
        {
            doMove();
        }
        else if (this.operation.equals(Operation.SET))
        {
            if (recursiveMode)
                doRecursiveSet();
            else
                doSet();
        }
        log.debug("run - DONE");
    }

    private void doSet()
    {
        log.debug("doSet");
        try
        {
            log.debug("target.getPath()" + this.target.getPath());
            // TODO: here we get the node in order to figure out the type, but
            // maybe we could just POST a vanilla Node object?
            Node n = this.client.getNode(this.target.getPath(), "limit=0");
            Node up = null;
            if (n instanceof ContainerNode)
                up = new ContainerNode(target, properties);
            else if (n instanceof DataNode)
                up = new DataNode(target, properties);
            else if (n instanceof LinkNode)
            {
                URI link = ((LinkNode) n).getTarget();
                up = new LinkNode(target, properties, link);
            }
            else
                throw new UnsupportedOperationException("unexpected node type: " + n.getClass().getName());

            this.client.setNode(up);
            log.info("updated properties: " + this.target);
        }
        catch(NodeNotFoundException ex)
        {
            msg("not found: " + target);
            System.exit(NET_STATUS);
        }
        catch(Throwable t)
        {
            msg("failed to set properties on node: " + target);
            if (t.getMessage() != null)
                msg("          reason: " + t.getMessage());
            else
                msg("          reason: " + t);
            if (t.getCause() != null)
                msg("          reason: " + t.getCause());
            System.exit(NET_STATUS);
        }
    }

    private void doDelete()
    {
        log.debug("doDelete");
        try
        {
            log.debug("target.getPath()" + this.target.getPath());
            this.client.deleteNode(this.target.getPath());
            log.info("deleted: " + this.target);
        }
        catch(NodeLockedException nlex)
        {
            msg("node locked: " + target);	
            System.exit(NET_STATUS);
        }
        catch(Throwable t)
        {
            msg("failed to delete: " + target);
            if (t.getMessage() != null)
                msg("          reason: " + t.getMessage());
            else
                msg("          reason: " + t);
            if (t.getCause() != null)
                msg("          reason: " + t.getCause());
            System.exit(NET_STATUS);
        }
    }

    private void doCopy()
    {
        log.debug("doCopy");
        try
        {
            if (this.transferDirection.equals(Direction.pushToVoSpace))
            {
                copyToVOSpace();
            }
            else if (this.transferDirection.equals(Direction.pullFromVoSpace))
            {
                copyFromVOSpace();
            }
        }
        catch(NullPointerException ex)
        {
            log.error("BUG", ex);
            System.exit(NET_STATUS);
        }
        catch(Throwable t)
        {
            log.debug(t);
            if (t instanceof IllegalArgumentException)
            {
                throw (IllegalArgumentException) t;
            }
            else
            {
                msg("failed to copy: " + source + " -> " + destination);
                if (t.getCause() != null)
                {
                    if (t.getCause().getMessage() != null)
                        msg("          reason: " + t.getCause().getMessage());
                    else
                        msg("          reason: " + t.getCause());
                }
                else
                {
                    if (t.getMessage() != null)
                        msg("          reason: " + t.getMessage());
                    else
                        msg("          reason: " + t);
                }
            }

            System.exit(NET_STATUS);
        }
    }
    
    private void doMove()
    {
        log.debug("doMove");
        try
        {
            if (Direction.pushToVoSpace.equals(transferDirection))
            {
                moveToVOSpace();
            }
            else if (Direction.pullFromVoSpace.equals(transferDirection))
            {
                moveFromVOSpace();
                // TODO: cofirm copy worked by checking MD5, length
                // TODO: delete src file from VOSpace, see TODO below
            }
            else //if (this.transferDirection.getValue().startsWith(VOS_PREFIX))
            {
                moveWithinVOSpace();
            }
        }
        catch(NullPointerException ex)
        {
            log.error("BUG", ex);
            System.exit(NET_STATUS);
        }
        /* TODO: Add this catch when we add delete src file from VOSpace for
         *       Direction.pullFromVOSpace (see TODO above)
        catch(NodeLockedException nlex)
        {
            if (destination == null)
                msg("alinga-- failed to move: " + source + " -> " + transferDirection.getValue());
            else
                msg("alinga-- failed to move: " + source + " -> " + destination);
            msg("          reason: " + nlex.getMessage());    	
            System.exit(NET_STATUS);
        }
        */
        catch(Throwable t)
        {
            if (t instanceof IllegalArgumentException)
            {
                throw (IllegalArgumentException) t;
            }
            else
            {
                if (destination == null)
                    msg("failed to move: " + source + " -> " + transferDirection.getValue());
                else
                    msg("failed to move: " + source + " -> " + destination);
                if (t.getCause() != null)
                {
                    if (t.getCause().getMessage() != null)
                        msg("          reason: " + t.getCause().getMessage());
                    else
                        msg("          reason: " + t.getCause());
                }
                else
                {
                    if (t.getMessage() != null)
                        msg("          reason: " + t.getMessage());
                    else
                        msg("          reason: " + t);
                }
            }

            System.exit(NET_STATUS);
        }
    }

    
    private void doCreate()
    {
        try
        {
            Node node;
            switch (this.nodeType)
            {
                case CONTAINER_NODE:
                    node = new ContainerNode(this.target, this.properties);
                    break;
                case LINK_NODE:
                    node = new LinkNode(this.target, this.properties, this.link);
                    break;
                case STRUCTURED_DATA_NODE:
                    node = new StructuredDataNode(this.target, this.properties);
                    break;
                case UNSTRUCTURED_DATA_NODE:
                    node = new UnstructuredDataNode(this.target, this.properties);
                    break;
                default:
                    throw new RuntimeException("BUG. Unsupported node type " + this.nodeType);
            }
        	
            Node nodeRtn = client.createNode(node);
            log.info("created: " + nodeRtn.getUri());
        }
        catch(Throwable t)
        {
            msg("failed to create: " + target);
            if (t.getMessage() != null)
                msg("          reason: " + t.getMessage());
            else
                msg("          reason: " + t);
            if (t.getCause() != null)
                msg("          reason: " + t.getCause());
            System.exit(NET_STATUS);
        }
    }

    private void doView()
    {   
        // if the user isn't controlling paging, add a child
        // limit of MAX_CHILD_SIZE (1000)
        boolean explicitPaging = false;
        String queryString = target.getQuery();
        if (StringUtil.hasText(queryString))
        {
            String[] queries = queryString.split("&");
            for (String query : queries)
                if (query.startsWith("limit=") || query.startsWith("uri="))
                    explicitPaging = true;
            if (!explicitPaging)
                queryString += "&limit=" + MAX_CHILD_SIZE;
        }
        else
            queryString = "limit=" + MAX_CHILD_SIZE;
        log.debug("explicit paging control: " + explicitPaging);
        log.debug("view query string: " + queryString);
        
        try
        {
            Node n = client.getNode(target.getPath(), queryString);

            msg(getType(n) + ": " + n.getUri());
            msg("creator: " + safePropertyRef(n, VOS.PROPERTY_URI_CREATOR));
            msg("is locked: " + safePropertyRef(n, VOS.PROPERTY_URI_ISLOCKED));
            msg("last modified: " + safePropertyRef(n, VOS.PROPERTY_URI_DATE));
            msg("readable by anyone: " + safePropertyRef(n, VOS.PROPERTY_URI_ISPUBLIC));
            msg("readable by: " + safePropertyRef(n, VOS.PROPERTY_URI_GROUPREAD));
            msg("readable and writable by: " + safePropertyRef(n, VOS.PROPERTY_URI_GROUPWRITE));
            msg("size: " + getContentLength(n,true));

            if (n instanceof ContainerNode)
            {
                final String quotaSize =
                        safePropertyRef(n, VOS.PROPERTY_URI_QUOTA);

                if (StringUtil.hasText(quotaSize))
                {
                    msg("quota size: "
                        + FileSizeType.getHumanReadableSize(
                            Long.parseLong(quotaSize)) + " (" + quotaSize
                        + " bytes)");
                }

                ContainerNode cn = (ContainerNode) n;
                if (cn.getNodes().size() > 0)
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append(pad("child nodes: ", 32));
                    sb.append(pad("size",12));
                    sb.append(pad("public",8));
                    sb.append(pad("last modified",26));
                    sb.append("URI");
                    msg(sb.toString());
                }
                
                log.debug("get container node returned : " + cn.getNodes().size() + " children.");
                printChildList(n, cn.getNodes());
                
                // get remaining children if the user isn't explicitly controlling paging
                if (!explicitPaging)
                {
                    VOSURI uriQueryObj = null;
                    String uriQueryParam = null;
                    while (cn.getNodes().size() > 0)
                    {
                        log.debug("Getting next set of children.");
                        uriQueryObj = cn.getNodes().get(cn.getNodes().size() - 1).getUri();
                        uriQueryParam = "uri=" + NetUtil.encode(uriQueryObj.toString());
                        cn = null;
                        
                        if (StringUtil.hasText(queryString))
                            n = client.getNode(target.getPath(), queryString + "&" + uriQueryParam);
                        else
                            n = client.getNode(target.getPath(), uriQueryParam);
                        if (!(n instanceof ContainerNode))
                            throw new IllegalStateException("inconsistent node state.");
                        cn = (ContainerNode) n;
                        
                        log.debug("next set has : " + cn.getNodes().size() + " children.");
                        
                        // remove the first child if it is the one matching the uri parameter
                        if (cn.getNodes().size() > 0 && cn.getNodes().get(0).getUri().equals(uriQueryObj))
                        {
                            cn.getNodes().remove(0);
                        }
                        printChildList(n, cn.getNodes());
                    }
                }
            }
            else if (n instanceof DataNode)
            {
                msg("type: " + safePropertyRef(n, VOS.PROPERTY_URI_TYPE));
                msg("encoding: " + safePropertyRef(n, VOS.PROPERTY_URI_CONTENTENCODING));
                msg("md5sum: " + safePropertyRef(n, VOS.PROPERTY_URI_CONTENTMD5));
            }
            else if (n instanceof LinkNode)
            {
                msg("link uri: " + ((LinkNode) n).getTarget());
            }
            else
            {
                log.debug("class of returned node: " + n.getClass().getName());
            }
        }
        catch(AccessControlException ex)
        {
            msg("permission denied: " + target);
            System.exit(NET_STATUS);
        }
        catch(NodeNotFoundException ex)
        {
            msg("not found: " + target);
            System.exit(NET_STATUS);
        }
    }
    
    private void printChildList(Node n, List<Node> children)
    {
        StringBuilder sb = null;
        for (Node child : children)
        {
            sb = new StringBuilder();
            String name = child.getName();
            if (child instanceof ContainerNode)
                name += "/";
            sb.append(pad(name,32));
            sb.append(pad(getContentLength(child,true),12));
            sb.append(pad(safePropertyRef(child, VOS.PROPERTY_URI_ISPUBLIC),8));
            sb.append(pad(safePropertyRef(child, VOS.PROPERTY_URI_DATE),26));
            sb.append(child.getUri().toString());
            msg(sb.toString());
        }
    }

    private String pad(String s, int len)
    {
        if (s.length() >= len)
            len = s.length() + 1;
        StringBuilder sb = new StringBuilder();
        sb.append(s);
        for (int i=s.length(); i<len; i++)
            sb.append(" ");
        return sb.toString();
    }

    private String getContentLength(Node n, boolean simple)
    {
        String contentLength = safePropertyRef(n, VOS.PROPERTY_URI_CONTENTLENGTH);
        if (!StringUtil.hasText(contentLength) )
            return "0";
        if (simple)
            return contentLength;
        return FileSizeType.getHumanReadableSize(Long.parseLong(contentLength))
                + " (" + contentLength + " bytes)";
    }
    
    private void copyToVOSpace()
        throws Throwable
    {
        URI originalDestination = null;
        if (StringUtil.hasText(destination.getQuery()))
        {
            originalDestination = new URI(destination.toString());
            destination = new URI(destination.toString().replace("?" + destination.getQuery(), ""));
        }
        //DataNode dnode = new DataNode(new VOSURI(destination));
        VOSURI dest = new VOSURI(destination);
        
        View view = null;
        if (originalDestination != null)
        {
            view = createAcceptsView(new VOSURI(originalDestination), null);
        }
        if (view == null)
        {
            view = new View(new URI(VOS.VIEW_DEFAULT));
        }

        
        List<Protocol> protocols = new ArrayList<Protocol>();
        if (subject != null)
            protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_PUT));
        else
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_PUT));

        Transfer transfer = new Transfer(dest, Direction.pushToVoSpace, view, protocols);
        transfer.setQuickTransfer(this.quickTransfer);
        ClientTransfer clientTransfer = client.createTransfer(transfer);

        // set http headers for put
        if (contentType != null)
        {
            log.debug("copyToVOSpaceFast: setting content-type = " + contentType);
            clientTransfer.setRequestProperty("Content-Type", contentType);
        }
        if (contentEncoding != null)
        {
            log.debug("copyToVOSpaceFast: setting content-encoding = " + contentEncoding);
            clientTransfer.setRequestProperty("Content-Encoding", contentEncoding);
        }
        
        log.debug("this.source: " + source);
        File fileToUpload = new File(source);
        
        if (retryEnabled)
            clientTransfer.setMaxRetries(Integer.MAX_VALUE);
        clientTransfer.setTransferListener(new VOSpaceTransferListener(false));
        clientTransfer.setSSLSocketFactory(client.getSslSocketFactory());
        clientTransfer.setFile(fileToUpload);
        
        clientTransfer.runTransfer();
        if (!quickTransfer)
        {
        	checkPhase(clientTransfer);
        }
        Node node = client.getNode(dest.getPath());

        boolean checkProps = contentType != null || contentEncoding != null || properties.size() > 0;
        if (checkProps || log.isDebugEnabled())
        {
            log.debug("clientTransfer getTarget: " + node);
            Node cur = this.client.getNode(node.getUri().getPath());
            log.debug("Node returned from getNode, after doUpload: " + VOSClientUtil.xmlString(cur));
            if (checkProps)
            {
                log.debug("checking properties after put: " + cur.getUri());
                boolean updateProps = false;
                for (NodeProperty np : properties)
                    updateProps = updateProps || updateProperty(cur, np.getPropertyURI(), np.getPropertyValue());
                if (updateProps)
                {
                    log.debug("updating properties after put: " + cur.getUri());
                    client.setNode(cur);
                }
            }
        }
    }
    
    private boolean updateProperty(Node n, String propURI, String propValue)
    {
        log.debug("checking property: " + propURI + " vs " + propValue);
        boolean ret = false;
        if (propValue != null)
        {
            NodeProperty cur = n.findProperty(propURI);
            if ( cur == null)
            {
                log.debug("adding property: " + propURI + " = " + propValue);
                n.getProperties().add(new NodeProperty(propURI, propValue));
                ret = true;
            }
            else if ( !propValue.equals(cur.getPropertyValue()) )
            {
                log.debug("setting property: " + propURI + " = '" + propValue + "', was '" + cur.getPropertyValue() + "'");
                cur.setValue(propValue);
                ret = true;
            }
        }
        return ret;
    }

    private void copyFromVOSpace()
        throws Throwable
    {
        View view = null;
        if (StringUtil.hasText(source.getQuery()))
        {
            view = createProvidesView(new VOSURI(source), null);
            source = new URI(source.toString().replace("?" + source.getQuery(), ""));
        }
        if (view == null)
        {
            view = new View(new URI(VOS.VIEW_DEFAULT));
        }
        VOSURI src = new VOSURI(source);

        List<Protocol> protocols = new ArrayList<Protocol>();
        if (subject != null)
            protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_GET));
        else
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_GET));

        Transfer transfer = new Transfer(src, Direction.pullFromVoSpace, view, protocols);
        transfer.setQuickTransfer(this.quickTransfer);
        ClientTransfer clientTransfer = client.createTransfer(transfer);

        log.debug("this.source: " + source);
        File fileToSave = new File(destination);
        if (fileToSave.exists())
            log.info("overwriting existing file: " + destination);
        
        if (retryEnabled)
            clientTransfer.setMaxRetries(Integer.MAX_VALUE);
        clientTransfer.setTransferListener(new VOSpaceTransferListener(true));
        clientTransfer.setSSLSocketFactory(client.getSslSocketFactory());
        clientTransfer.setFile(fileToSave);
        
        clientTransfer.runTransfer();
        if (!quickTransfer)
        {
        	checkPhase(clientTransfer);
        }
    }
    
    private void moveToVOSpace()
        throws Throwable
    {
        File sourceFile = new File(source);
        if (!sourceFile.isFile())
        {
            msg("Cannot move local directories to VOSpace.");
            System.exit(-1);
        }
        copyToVOSpace();
        log.debug("copied local file " + source + " to " + this.destination.getPath());
        Node uploadedNode = this.client.getNode(this.destination.getPath(), "limit=0");
        if (uploadedNode == null)
        {
            msg("Failed to upload, keeping local file.");
            System.exit(-1);
        }
        NodeProperty uploadedSizeProp = uploadedNode.findProperty(VOS.PROPERTY_URI_CONTENTLENGTH);
        long uploadedSize = Long.parseLong(uploadedSizeProp.getPropertyValue());
        log.debug("uploaded size: " + uploadedSize);
        log.debug("original size: " + sourceFile.length());
        if (uploadedSize != sourceFile.length())
        {
            msg("Uploaded file size does not match that of local file, keeping local file.");
            System.exit(-1);
        }
        sourceFile.delete();
        log.debug("deleted local file: " + source);
    }
    
    private void moveFromVOSpace()
        throws Throwable
    {
        Node sourceNode = this.client.getNode(this.source.getPath(), "limit=0");
        if (sourceNode instanceof ContainerNode)
        {
            msg("Cannot move containers from VOSpace to local file system.");
            System.exit(-1);
        }
        copyFromVOSpace();
        log.debug("copied " + this.destination.getPath() + " to local file " + source);
        
        NodeProperty downloadedSizeProp = sourceNode.findProperty(VOS.PROPERTY_URI_CONTENTLENGTH);
        File destFile = new File(destination);
        long downloadedSize = Long.parseLong(downloadedSizeProp.getPropertyValue());
        log.debug("downloaded size: " + downloadedSize);
        log.debug("original size: " + destFile.length());
        if (downloadedSize != destFile.length())
        {
            msg("Downloaded file size does not match that of local file, keeping remote file.");
            System.exit(-1);
        }
        this.client.deleteNode(this.source.getPath());
        log.debug("deleted vos file: " + this.source);
    }
    
    private void doRecursiveSet()
    {
        try
        {
            log.debug("target.getPath()" + this.target.getPath());
            // TODO: here we get the node in order to figure out the type, but
            // maybe we could just POST a vanilla Node object?
            Node n = this.client.getNode(this.target.getPath(), "limit=0");
            Node up = null;
            if (n instanceof ContainerNode)
                up = new ContainerNode(target, properties);
            else if (n instanceof DataNode)
                up = new DataNode(target, properties);
            else if (n instanceof LinkNode)
            {
                URI link = ((LinkNode) n).getTarget();
                up = new LinkNode(target, properties, link);
            }
            else
                throw new UnsupportedOperationException("unexpected node type: " + n.getClass().getName());

            ClientRecursiveSetNode recSetNode = client.setNodeRecursive(up);

            Thread abortThread = new ClientAbortThread(recSetNode.getJobURL());
            Runtime.getRuntime().addShutdownHook(abortThread);
            recSetNode.setMonitor(true);
            recSetNode.run();
            Runtime.getRuntime().removeShutdownHook(abortThread);
            checkPhase(recSetNode);
            
            log.info("updated properties recursively: " + this.target);
        }
        catch(NodeNotFoundException ex)
        {
            msg("not found: " + target);
            System.exit(NET_STATUS);
        }
        catch(Throwable t)
        {
            msg("failed to set properties recursively on node: " + target);
            if (t.getMessage() != null)
                msg("          reason: " + t.getMessage());
            else
                msg("          reason: " + t);
            if (t.getCause() != null)
                msg("          reason: " + t.getCause());
            System.exit(NET_STATUS);
        }

    }
    
    private void moveWithinVOSpace()
        throws Throwable
    {
        VOSURI src = new VOSURI(source);
        VOSURI dest = new VOSURI(destination);
        Transfer transfer = new Transfer(src, dest, false);
        ClientTransfer trans = client.createTransfer(transfer);

        Thread abortThread = new ClientAbortThread(trans.getJobURL());
        Runtime.getRuntime().addShutdownHook(abortThread);
        trans.setMonitor(true);
        trans.runTransfer();
        Runtime.getRuntime().removeShutdownHook(abortThread);
        checkPhase(trans);
    }
    
    private void checkPhase(ClientTransfer trans)
            throws IOException, RuntimeException
    {
        ExecutionPhase ep = trans.getPhase();
        if ( ExecutionPhase.ERROR.equals(ep) )
        {
            ErrorSummary es = trans.getServerError();
            throw new RuntimeException(es.getSummaryMessage());
        }
        else if ( ExecutionPhase.ABORTED.equals(ep) )
            throw new RuntimeException("transfer aborted by service");
        else if ( !ExecutionPhase.COMPLETED.equals(ep) )
            throw new RuntimeException("unexpected job state: " + ep.name());
    }

    private void checkPhase(ClientRecursiveSetNode recSetNode)
        throws IOException, RuntimeException
    {
        ExecutionPhase ep = recSetNode.getPhase();
        if ( ExecutionPhase.ERROR.equals(ep) )
        {
            ErrorSummary es = recSetNode.getServerError();
            throw new RuntimeException(es.getSummaryMessage());
        }
        else if ( ExecutionPhase.ABORTED.equals(ep) )
            throw new RuntimeException("recursive set node aborted by service");
        else if ( !ExecutionPhase.COMPLETED.equals(ep) )
            throw new RuntimeException("unexpected job state: " + ep.name());
    }
    
    /**
     * Create a view used in node.accepts based on the query string in the vosuri.
     * 
     * @param vosuri
     * @param node The node object if available.
     * @return
     * @throws NodeNotFoundException
     * @throws URISyntaxException
     */
    private View createAcceptsView(VOSURI vosuri, Node node) throws URISyntaxException
    {
        AcceptsProvidesAbstraction nodeViewWrapper = new AcceptsProvidesAbstraction()
        {
            public List<URI> getViews(Node node) { return node.accepts(); }
        };
        return createView(vosuri, nodeViewWrapper, node);
    }
    
    /**
     * Create a view used in node.provides based on the query string in the vosuri.
     * 
     * @param vosuri
     * @param node The node object if available.
     * @return
     * @throws NodeNotFoundException
     * @throws URISyntaxException
     */
    private View createProvidesView(VOSURI vosuri, Node node) throws URISyntaxException
    {
        AcceptsProvidesAbstraction nodeViewWrapper = new AcceptsProvidesAbstraction()
        {
            public List<URI> getViews(Node node) { return node.provides(); }
        };
        return createView(vosuri, nodeViewWrapper, node);
    }
    
    /**
     * Createa a view based on the query string in the vosuri.
     * 
     * @param vosuri
     * @param acceptsOrProvides
     * @param node The node object if available.
     * @return
     * @throws NodeNotFoundException
     * @throws URISyntaxException
     */
    private View createView(VOSURI vosuri, AcceptsProvidesAbstraction acceptsOrProvides, Node node)
            throws URISyntaxException
    {
        // parse the query string
        String queryString = vosuri.getQuery();
        final String viewKey = "view="; 
        String[] queries = queryString.split("&");
        String viewRef = null;
        List<String> params = new ArrayList<String>();
        for (String query : queries)
        {
            if (query.startsWith(viewKey))
            {
                if (viewRef != null)
                {
                    throw new IllegalArgumentException("Too many view references.");
                }
                viewRef = query.substring(viewKey.length());
            }
            else
            {
                params.add(query);
            }
        }
        if (viewRef == null)
        {
            log.debug("View not found in query string, using default view");
            return null;
        }
        
        // get the node object if necessary
        if (node == null)
        {
            try
            {
                node = client.getNode(vosuri.getPath(), "limit=0");
            }
            catch (NodeNotFoundException e)
            {
                throw new IllegalArgumentException(
                        "Node " + vosuri.getPath() + " not found.");
            }
        }
        
        // determine if the view is supported
        URI viewURI = null;
        for (URI uri : acceptsOrProvides.getViews(node))
        {
            if (viewRef.equals(uri.getFragment()))
            {
                viewURI = uri;
            }
        }
        
        if (viewURI == null)
        {
            throw new IllegalArgumentException(
                    "View '" + viewRef + "' not supported by node " +
                    node.getUri().toString());
        }
        
        // add the view parameters
        View view = new View(viewURI);
        if (params.size() > 0)
        {
            String viewURIFragment = viewURI.getFragment();
            String paramURIBase = viewURI.toString().replace("#" + viewURIFragment, "");
            for (String param : params)
            {
                int eqIndex = param.indexOf('=');
                if (eqIndex > 0)
                {
                    String key = param.substring(0, eqIndex);
                    URI paramURI = new URI(paramURIBase + "#" + key);
                    Parameter viewParam = new Parameter(paramURI, param.substring(eqIndex + 1));
                    view.getParameters().add(viewParam);
                }
            }
        }
        return view;
    }

    private static String ZERO_LENGTH = "";
    private String safePropertyRef(Node n, String key)
    {
        if (n == null || key == null)
            return ZERO_LENGTH;
        NodeProperty p = n.findProperty(key);
        if (p == null)
            return ZERO_LENGTH;
        String ret = p.getPropertyValue();
        if (ret == null)
            return ZERO_LENGTH;
        return ret;
    }
    private String getType(Node n)
    {
        if (n instanceof ContainerNode)
            return "container";
        if (n instanceof DataNode)
            return "data";
        if (n instanceof LinkNode)
            return "link";
        return ZERO_LENGTH;
    }

    /**
     * Initialize command member variables based on arguments passed in.
     * 
     * @param argMap    The parsed arguments to this command.
     */
    private void init(ArgumentMap argMap)
    {
        URI serverUri = null;
        try
        {
            this.subject = CertCmdArgUtil.initSubject(argMap, true);
            
            // check that loaded certficate chain is valid right now
            // TODO: should this be moved into CertCmdArgUtil?
            if (subject != null)
            {
                Set<X509CertificateChain> certs = subject.getPublicCredentials(X509CertificateChain.class);
                if (certs.size() == 0)
                {
                    // subject without certs means something went wrong above
                    throw new RuntimeException("BUG: failed to load certficate");
                }
                DateFormat df = DateUtil.getDateFormat(DateUtil.ISO_DATE_FORMAT, DateUtil.LOCAL);
                X509CertificateChain chain = certs.iterator().next(); // the first one
                Date start = null;
                Date end = null;
                for (X509Certificate c : chain.getChain())
                {
                    try
                    {
                        start = c.getNotBefore();
                        end = c.getNotAfter();
                        c.checkValidity();
                    }
                    catch(CertificateNotYetValidException exp)
                    {
                        log.error("certificate is not valid yet (valid from " + df.format(start) + " to " + df.format(end) + ")");
                        System.exit(INIT_STATUS);
                    }
                    catch (CertificateExpiredException exp)
                    {
                        log.error("certificate has expired (valid from " + df.format(start) + " to " + df.format(end) + ")");
                        System.exit(INIT_STATUS);
                    }
                }
            }
        }
        catch(Exception ex)
        {
            log.error("failed to load certificates: " + ex.getMessage());
            System.exit(INIT_STATUS);
        }


        try
        {
            if (this.operation.equals(Operation.COPY) || this.operation.equals(Operation.MOVE))
            {
            	if (this.operation.equals(Operation.COPY) && argMap.isSet(ARG_QUICK))
            	{
            		this.quickTransfer = true;
            	}
                String strSrc = argMap.getValue(ARG_SRC);
                String strDest = argMap.getValue(ARG_DEST);
                if (!strSrc.startsWith(VOS_PREFIX) && strDest.startsWith(VOS_PREFIX))
                {
                    this.transferDirection = Direction.pushToVoSpace;
                    try
                    {
                        this.destination = new URI(strDest);
                        serverUri = new VOSURI(strDest).getServiceURI();
                    }
                    catch (URISyntaxException e)
                    {
                        throw new IllegalArgumentException("Invalid VOS URI: " + strDest);
                    }
                    File f = new File(strSrc);
                    if (!f.exists() || !f.canRead())
                        throw new IllegalArgumentException("Source file " + strSrc + " does not exist or cannot be read.");
                    
                    // Predetermine delete permission on file -- fail on attempt for now
                    // if (this.operation.equals(Operation.MOVE))
                    // {
                    //     try
                    //     {
                    //         SecurityManager sm = System.getSecurityManager();
                    //         sm.checkDelete(strSrc);
                    //     }
                    //     catch (SecurityException secEx)
                    //     {
                    //         throw new IllegalArgumentException("Permission to remove source file " + strSrc + " denied.");
                    //     }
                    // }
                    
                    try
                    {
                        this.source = new URI("file", f.getAbsolutePath(), null);
                    }
                    catch (URISyntaxException e)
                    {
                        throw new IllegalArgumentException("Invalid file path: " + strSrc);
                    }
                }
                else if (strSrc.startsWith(VOS_PREFIX) && !strDest.startsWith(VOS_PREFIX))
                {
                    this.transferDirection = Direction.pullFromVoSpace;
                    try
                    {
                        serverUri = new VOSURI(strSrc).getServiceURI();
                        this.source = new URI(strSrc);
                    }
                    catch (URISyntaxException e)
                    {
                        throw new IllegalArgumentException("Invalid VOS URI: " + strSrc);
                    }
                    File f = new File(strDest);
                    if (f.exists())
                    {
                        if (!f.canWrite()) throw new IllegalArgumentException("Destination file " + strDest + " is not writable.");
                    }
                    else
                    {
                        File parent = f.getParentFile();
                        if (parent == null)
                        {
                            String cwd = System.getProperty("user.dir");
                            parent = new File(cwd);
                        }
                        if (parent.isDirectory())
                        {
                            if (!parent.canWrite())
                                throw new IllegalArgumentException("The parent directory of destination file " + strDest
                                        + " is not writable.");
                        }
                        else
                            throw new IllegalArgumentException("Destination file " + strDest + " is not within a directory.");
                    }
                    this.destination = f.toURI();
                }
                else if (!strSrc.startsWith(VOS_PREFIX) && !strDest.startsWith(VOS_PREFIX))
                {
                    // local copy/move
                    throw new IllegalArgumentException("Local copy and move operations not yet supported.");
                }
                else
                {   
                    // server to server copy/move
                    if (this.operation.equals(Operation.COPY))
                    {
                        throw new IllegalArgumentException("Copy within vospace is not yet supported.");
                    }   
                    
                    URI destServerUri = null;
                    try
                    {
                        this.source = new URI(strSrc);
                        serverUri = new VOSURI(source).getServiceURI();
                    }
                    catch (URISyntaxException e)
                    {
                        throw new IllegalArgumentException("Invalid VOS URI: " + strSrc);
                    }
                    try
                    {
                        this.destination = new URI(strDest);
                        destServerUri = new VOSURI(destination).getServiceURI();
                    }
                    catch (URISyntaxException e)
                    {
                        throw new IllegalArgumentException("Invalid VOS URI: " + strDest);
                    }
                    
                    if (!serverUri.equals(destServerUri))
                    {
                        throw new IllegalArgumentException("Move between two vospace services is not yet supported.");
                    }
                }
            }
            else
            {
                String strTarget = argMap.getValue(ARG_TARGET);
                try
                {
                    this.target = new VOSURI(strTarget);
                    serverUri = this.target.getServiceURI();
                }
                catch (URISyntaxException e)
                {
                    throw new IllegalArgumentException("Invalid VOS URI: " + strTarget);
                }
            }
        }
        catch(NullPointerException nex)
        {
            log.error("BUG", nex);
            System.exit(-1);
        }
        catch(Exception ex)
        {
            log.error(ex.toString());
            System.exit(INIT_STATUS);
        }

        try
        {
            RegistryClient reg = new RegistryClient();
            String protocol = "https";
            if (this.subject == null)
                protocol = "http";
            URL baseURL = reg.getServiceURL(serverUri, protocol);
            if (baseURL == null)
            {
                log.error("failed to find service URL for " + serverUri);
                System.exit(INIT_STATUS);
            }
            this.baseUrl = baseURL.toString();
        }
        catch (MalformedURLException e)
        {
            log.error("failed to find service URL for " + serverUri);
            log.error("reason: " + e.getMessage());
            System.exit(INIT_STATUS);
        }

        // check if schema validation should be disabled
        boolean doVal = true;
        String schemaVal = argMap.getValue(ARG_XSV);
        if (schemaVal != null && "off".equals(schemaVal))
        {
            doVal = false;
            log.info("XML schema validation: disabled");
        }

        this.client = new VOSpaceClient(baseUrl, doVal);

        this.retryEnabled = !argMap.isSet(ARG_NO_RETRY);

        log.info("server uri: " + serverUri);
        log.info("base url: " + this.baseUrl);
    }

    /**
     * @param argMap  The parsed arguments to this command.
     * @throws IllegalArgumentException
     *                  If more or less than one operation was requested.
     */
    private void validateCommand(ArgumentMap argMap)
            throws IllegalArgumentException
    {
        int numOp = 0;
        if (argMap.isSet(ARG_VIEW))
        {
            numOp++;
            this.operation = Operation.VIEW;
        }
        if (argMap.isSet(ARG_CREATE))
        {
            numOp++;
            this.operation = Operation.CREATE;
        }
        if (argMap.isSet(ARG_DELETE))
        {
            numOp++;
            this.operation = Operation.DELETE;
        }
        if (argMap.isSet(ARG_SET))
        {
            numOp++;
            this.operation = Operation.SET;
        }
        if (argMap.isSet(ARG_COPY))
        {
            numOp++;
            this.operation = Operation.COPY;
        }
        if (argMap.isSet(ARG_MOVE))
        {
            numOp++;
            this.operation = Operation.MOVE;
        }

        if (numOp == 0)
        {
            throw new IllegalArgumentException("At least one operation must be defined.");
        }
        else if (numOp > 1)
        {
            throw new IllegalArgumentException("Only one operation may be defined.");
        }
    }

    /**
     * @param argMap        The parsed out arguments to this command.
     * @throws IllegalArgumentException  For any required missing arguments.
     */
    private void validateCommandArguments(ArgumentMap argMap)
        throws IllegalArgumentException
    {
        if (this.operation.equals(Operation.COPY) || this.operation.equals(Operation.MOVE))
        {
            String strSrc = argMap.getValue(ARG_SRC);
            if (strSrc == null) throw new IllegalArgumentException("Argument src is required for " + this.operation);

            String strDest = argMap.getValue(ARG_DEST);
            if (strDest == null) throw new IllegalArgumentException("Argument dest is required for " + this.operation);
        }
        else
        {
            String strTarget = argMap.getValue(ARG_TARGET);
            if (strTarget == null) throw new IllegalArgumentException("Argument target is required for " + this.operation);
            
            if (this.operation.equals(Operation.CREATE))
            {
                String strNodeType = argMap.getValue(ARG_CREATE);
                if (("true".equalsIgnoreCase(strNodeType)) || ("ContainerNode".equalsIgnoreCase(strNodeType)))
                    this.nodeType = NodeType.CONTAINER_NODE;
                else if (("DataNode".equalsIgnoreCase(strNodeType)) || ("UnstructuredDataNode".equalsIgnoreCase(strNodeType)))
                    this.nodeType = NodeType.UNSTRUCTURED_DATA_NODE;
                else if ("LinkNode".equalsIgnoreCase(strNodeType))
                {
                    String strLink = argMap.getValue(ARG_LINK);
                    if (strLink == null) throw new IllegalArgumentException("Argument link is required for node type " + strNodeType);
                    
                    try
                    {
                        this.link = new URI(strLink);
                    }
                    catch (URISyntaxException e)
                    {
                        throw new IllegalArgumentException("Invalid URI: " + strLink);
                    }  
                    
                    this.nodeType = NodeType.LINK_NODE;
                }
                else if ("StructuredDataNode".equalsIgnoreCase(strNodeType))
                    this.nodeType = NodeType.STRUCTURED_DATA_NODE;
                else 
                    throw new IllegalArgumentException("Unsupported node type: " + strNodeType);
            }
        }

        // optional properties
        this.properties = new ArrayList<NodeProperty>();
        
        String propFile = argMap.getValue(ARG_PROP);
        if (propFile != null)
        {
            File f = new File(propFile);
            if (f.exists())
            {
                if (f.canRead())
                {
                    try
                    {
                        Properties p = new Properties();
                        p.load(new FileReader(f));
                        for ( String key : p.stringPropertyNames())
                        {
                            String val = p.getProperty(key);
                            properties.add(new NodeProperty(key, val));
                        }
                    }
                    catch(IOException ex)
                    {
                        log.info("failed to read properties file: "
                                + f.getAbsolutePath()
                                + "(" + ex.getMessage() + ", skipping)");
                    }
                }
                else
                    log.info("cannot read properties file: "
                            + f.getAbsolutePath() + " (permission denied, skipping)");
            }
            else
                log.info("cannot read properties file: "
                        + f.getAbsolutePath() + " (does not exist, skipping)");
        }

        this.contentType = argMap.getValue(ARG_CONTENT_TYPE);
        this.contentEncoding = argMap.getValue(ARG_CONTENT_ENCODING);
        String contentMD5 = argMap.getValue(ARG_CONTENT_MD5);
        String groupRead = argMap.getValue(ARG_GROUP_READ);
        String groupWrite = argMap.getValue(ARG_GROUP_WRITE);

        // support --public and --public=true; everything else sets it to false
        boolean isPublicSet = argMap.isSet(ARG_PUBLIC);
        boolean isPublicValue = true;
        if (isPublicSet)
        {
            String s = argMap.getValue(ARG_PUBLIC);
            if (s != null  && s.trim().length() > 0 && !s.trim().equalsIgnoreCase("true"))
            {
                if (s.equalsIgnoreCase("false"))
                {
                    isPublicValue = false;
                }
                else
                {
                    isPublicSet = false;
                    log.info("--public value not recognized: " + s.trim() + ".  Ignoring.");
                }
            }
        }

        // support --lock and --lock=true; everything else sets it to false
        boolean isLockSet = argMap.isSet(ARG_LOCK);
        boolean isLockValue = true;
        if (isLockSet)
        {
            String s = argMap.getValue(ARG_LOCK);
            if (s != null  && s.trim().length() > 0 && !s.trim().equalsIgnoreCase("true"))
            {
                if (s.equalsIgnoreCase("false"))
                {
                    isLockValue = false;
                }
                else
                {
                    isLockSet = false;
                    log.info("--lock value not recognized: " + s.trim() + ".  Ignoring.");
                }
            }
        }

        this.recursiveMode = argMap.isSet(ARG_RECURSIVE);

        if (contentType != null)
            properties.add(new NodeProperty(VOS.PROPERTY_URI_TYPE, contentType));
        if (contentEncoding != null)
            properties.add(new NodeProperty(VOS.PROPERTY_URI_CONTENTENCODING, contentEncoding));

        if (contentMD5 != null)
            properties.add(new NodeProperty(VOS.PROPERTY_URI_CONTENTMD5, contentMD5));
        
        if (groupRead != null)
            properties.add(new NodeProperty(VOS.PROPERTY_URI_GROUPREAD, groupRead));
        if (groupWrite != null)
            properties.add(new NodeProperty(VOS.PROPERTY_URI_GROUPWRITE, groupWrite));
        if (isLockSet)
            properties.add(new NodeProperty(VOS.PROPERTY_URI_ISLOCKED, Boolean.toString(isLockValue)));
        if (isPublicSet)
            properties.add(new NodeProperty(VOS.PROPERTY_URI_ISPUBLIC, Boolean.toString(isPublicValue)));
    }

    /**
     * Print the usage report for this command.
     */
    public static void usage()
    {
        String[] um = {
        /*
         * Note: When using "Format" in Eclipse, shorter lines in this string array are squeezed into one line.
         * This makes it hard to read or edit.
         * 
         * A workaround is, lines are purposely extended with blank spaces to a certain length,
         * where the EOL is at about column 120 (including leading indenting spaces).
         * 
         * In this way, it's still easy to read and edit and the formatting operation does not change it's layout.
         * 
         */
            "",
            "Usage: java -jar cadcVOSClient.jar [-v|--verbose|-d|--debug] [--xsv=off]                          ",
            CertCmdArgUtil.getCertArgUsage(),
            "",
            "  Note: --xsv=off disables XML schema validation; use at your own risk                            ",
            "",
            "Help:                                                                                             ",
            "    <-h | --help>                                                                                 ",
            "",
            "Create node:                                                                                      ",
            "",
            "    --create[=<ContainerNode|LinkNode|StructuredDataNode|UnstructuredDataNode>]                   ",
            "    --target=<node URI>                                                                           ",
            "    [--link=<link URI>]                                                                           ",
            "    [--prop=<properties file>]                                                                    ",
            "",
            "  Note: --create defaults to creating a ContainerNode (directory).                                ",
            "",
            "  Note: --link is only required when creating a LinkNode.  It is the URI to which                 ",
            "          the LinkNode is pointing.                                                               ",
            "",
            "View node:                                                                                        ",
            "",
            "    --view --target=<target URI>                                                                  ",
            "",
            "Delete node:                                                                                      ",
            "",
            "    --delete --target=<target URI>                                                                ",
            "",
            "Set node:                                                                                         ",
            "",
            "    --set --target=<target URI>                                                                   ",
            "    [--content-type=<mimetype of source>]                                                         ",
            "    [--content-encoding=<encoding of source>]                                                     ",
            "    [--group-read=<group URIs (in double quotes, space separated, 4 maximum)>]                    ",
            "    [--group-write=<group URIs (in double quotes, space separated, 4 maximum)>]                   ",
            "    [--lock]                                                                                      ",
            "    [--public]                                                                                    ",
            "    [--prop=<properties file>]                                                                    ",
            "    [--recursive]                                                                                 ",
            "",
            "Copy file:                                                                                        ",
            "",
            "    --copy --src=<source URI> --dest=<destination URI>                                            ",
            "    [--content-type=<mimetype of source>]                                                         ",
            "    [--content-encoding=<encoding of source>]                                                     ",
            "    [--prop=<properties file>]                                                                    ",
            "    [--noretry]                                                                                   ",
            "    [--quick]                                                                                     ",
            "",
            "  Note: --noretry disables the retry of failed transfers (when the server indicates it was        ",
            "  temporary)                                                                                      ",
            "",
            "  Note: One of --src and --target may be a \"vos\" URI and the other may be an                    ",
            "  absolute or relative path to a file.  If the target node does not exist, a                      ",
            "  DataNode is created and data copied.  If it does exist, the data and                            ",
            "  properties are overwritten.                                                                     ",
            "",
            "  Note: Source and destination URIs may include HTTP-like query parameters, some of which will    ",
            "  result in additional operations being performed.                                                ",
            "",
            "  Note: If the --quick options is supplied, and a download is being performed, transfer           ",
            "  negotiation will be replaced with an optimized download process.                                ",
            "",
            "Move file/node:                                                                                   ",
            "",
            "    --move --src=<source URI> --dest=<destination URI>                                            ",
            "",
            "  Note: If the source URI refers to a VOSpace node, then move is a recursive operation:  the      ",
            "  source nodes, and all subnodes, are moved.                                                      ",
            "",
            "  Note: Only files can be moved from the local file system to VOSpace.  Similarly, only files     ",
            "  can be moved from VOSpace to the local file system.                                             ",
            "",
            "  Note: If the destination URI referes to a VOSpace node, that node must be a directory.  If the  ",
            "  directory exists, the source URI will be moved into that directory.  If the directory doesn't   ",
            "  exist, the source URI will be moved into the parent directory and will be renamed to the name   ",
            "  specified in destination URI.                                                                   "

        };
        for (String line : um)
            msg(line);
    }
    
    /**
     * Interface to allow abstraction between accepts and provides views.
     */
    private interface AcceptsProvidesAbstraction
    {
        List<URI> getViews(Node node);
    }
}
