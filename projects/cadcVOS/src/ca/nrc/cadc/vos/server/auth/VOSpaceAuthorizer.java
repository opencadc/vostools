/**
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2010.                            (c) 2010.
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
 ************************************************************************
 */
package ca.nrc.cadc.vos.server.auth;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Principal;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;

import org.apache.log4j.Logger;

import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.auth.Authorizer;
import ca.nrc.cadc.auth.SSLUtil;
import ca.nrc.cadc.auth.X509CertificateChain;
import ca.nrc.cadc.cred.AuthorizationException;
import ca.nrc.cadc.cred.client.priv.CredPrivateClient;
import ca.nrc.cadc.gms.client.GmsClient;
import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.reg.client.RegistryClient;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeLockedException;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.server.NodeID;
import ca.nrc.cadc.vos.server.NodePersistence;


/**
 * Authorization implementation for VO Space. 
 * 
 * Important:  This class cannot be re-used between HTTP Requests--A new
 * instance must be created each time.
 * 
 * The nodePersistence object must be set for every instance.
 * 
 * @author majorb
 */
public class VOSpaceAuthorizer implements Authorizer
{
    protected static final Logger LOG = Logger.getLogger(VOSpaceAuthorizer.class);

    // TODO: dynamically find the cred service associated with this VOSpace service
    // maybe from the capabilities?
    private static final String CRED_SERVICE_ID = "ivo://cadc.nrc.ca/cred";

    public static final String MODE_KEY = VOSpaceAuthorizer.class.getName() + ".state";
    public static final String OFFLINE = "Offline";
    public static final String OFFLINE_MSG = "System is offline for maintainence";
    public static final String READ_ONLY = "ReadOnly";
    public static final String READ_ONLY_MSG = "System is in read-only mode for maintainence";
    public static final String READ_WRITE = "ReadWrite";
    private boolean readable = true;
    private boolean writable  = true;
    private boolean allowPartialPaths = false;
    private boolean disregardLocks = false;

    private SSLSocketFactory socketFactory;
    private int subjectHashCode;
    
    // cache of groupURI to isMember
    private Map<String, Boolean> groupMembershipCache;
    
    private NodePersistence nodePersistence;
    private RegistryClient registryClient;
    private GmsClient gmsClient;

    public VOSpaceAuthorizer()
    {
        this(false);
    }
    
    public VOSpaceAuthorizer(boolean allowPartialPaths)
    {
        groupMembershipCache = new HashMap<String, Boolean>();
        initState();
        this.allowPartialPaths = allowPartialPaths;
        this.registryClient = new RegistryClient();
        this.gmsClient = new GmsClient(registryClient);
    }

    // this method will only downgrade the state to !readable and !writable
    // and will never restore them to true - that is intentional
    private void initState()
    {
        String key = VOSpaceAuthorizer.MODE_KEY;
        String val = System.getProperty(key);
        if (OFFLINE.equals(val))
        {
            readable = false;
            writable = false;
        }
        else if (READ_ONLY.equals(val))
        {
            writable = false;
        }
    }

    /**
     * Obtain the Read Permission for the given URI.
     *
     * @param uri       The URI to check.
     * @return          The Read Permission objectual representation, such as
     *                  a Group, or User.
     * @throws AccessControlException If the user does not have read permission
     * @throws FileNotFoundException If the node could not be found
     */
    public Object getReadPermission(URI uri)
        throws AccessControlException, FileNotFoundException, TransientException
    {
        initState();
        if (!readable)
        {
            if (!writable)
                throw new IllegalStateException(OFFLINE_MSG);
            throw new IllegalStateException(READ_ONLY_MSG);
        }
        try
        {
            VOSURI vos = new VOSURI(uri);
            Node node = nodePersistence.get(vos, allowPartialPaths);
            return getReadPermission(node);
        } 
        catch(NodeNotFoundException ex)
        {
            throw new FileNotFoundException("not found: " + uri);
        }
    }
    
    /**
     * Obtain the Read Permission for the given Node.
     *
     * @param node      The Node to check.
     * @return          The persistent version of the target node.
     * @throws AccessControlException If the user does not have read permission
     * @throws FileNotFoundException If the node could not be found
     */
    public Object getReadPermission(Node node)
            throws AccessControlException
    {
        initState();
        if (!readable)
        {
            if (!writable)
                throw new IllegalStateException(OFFLINE_MSG);
            throw new IllegalStateException(READ_ONLY_MSG);
        }

        AccessControlContext acContext = AccessController.getContext();
        Subject subject = Subject.getSubject(acContext);
        
        LinkedList<Node> nodes = Node.getNodeList(node);
        
        // check for root ownership
        Node rootNode = nodes.getLast();
        if (isOwner(rootNode, subject))
        {
            LOG.debug("Read permission granted to root user.");
            return node;
        }
        
        Iterator<Node> iter = nodes.descendingIterator(); // root at end
        while (iter.hasNext())
        {
            Node n = iter.next();
            if (!hasSingleNodeReadPermission(n, subject))
                throw new AccessControlException("Read permission denied on " + n.getUri().toString());
        }
        return node;
    }

    /**
     * Obtain the Write Permission for the given URI.
     *
     * @param uri       The URI to check.
     * @return          The resource object for the argument URI (if any)
     * @throws AccessControlException If the user does not have write permission
     * @throws FileNotFoundException If the node could not be found
     * @throws NodeLockedException    If the node is locked
     */
    public Object getWritePermission(URI uri)
        throws AccessControlException, FileNotFoundException,
            TransientException, NodeLockedException
    {
        initState();
        if (!writable)
        {
            if (readable)
                throw new IllegalStateException(READ_ONLY_MSG);
            throw new IllegalStateException(OFFLINE_MSG);
        }
            
        try
        {
            VOSURI vos = new VOSURI(uri);
            Node node = nodePersistence.get(vos, allowPartialPaths);
            return getWritePermission(node);
        }
        catch(NodeNotFoundException ex)
        {
            throw new FileNotFoundException("not found: " + uri);
        }
    }

    /**
     * Obtain the Write Permission for the given Node.
     *
     * @param node      The Node to check.
     * @return          The persistent version of the target node.
     * @throws AccessControlException If the user does not have write permission
     * @throws NodeLockedException    If the node is locked
     */
    public Object getWritePermission(Node node)
        throws AccessControlException, NodeLockedException
    {
        initState();
        if (!writable)
        {
            if (readable)
                throw new IllegalStateException(READ_ONLY_MSG);
            throw new IllegalStateException(OFFLINE_MSG);
        }
        
        AccessControlContext acContext = AccessController.getContext();
        Subject subject = Subject.getSubject(acContext);
        
        // check if the node is locked
        if (!disregardLocks && node.isLocked())
            throw new NodeLockedException(node.getUri().toString());
        
        // check for root ownership
        LinkedList<Node> nodes = Node.getNodeList(node);
        Node rootNode = nodes.getLast();
        if (isOwner(rootNode, subject))
        {
            LOG.debug("Write permission granted to root user.");
            return node;
        }
        
        Iterator<Node> iter = nodes.descendingIterator(); // root at end
        while (iter.hasNext())
        {
            Node n = iter.next();
            if (n == node) // target needs write
                if (!hasSingleNodeWritePermission(n, subject))
                    throw new AccessControlException("Write permission denied on " + n.getUri().toString());
            else // part of path needs read
                if (!hasSingleNodeReadPermission(n, subject))
                    throw new AccessControlException("Read permission denied on " + n.getUri().toString());
        }
        return node;
    }
    
    /**
     * Recursively checks if a node can be deleted by the current subject. 
     * The caller must have write permission on the parent container and all 
     * non-empty containers. The argument and all child nodes must not 
     * be locked (unless locks are being ignored).
     * 
     * @param node
     * @throws AccessControlException
     * @throws NodeLockedException
     * @throws TransientException 
     */
    public void getDeletePermission(Node node)
        throws AccessControlException, NodeLockedException, TransientException
    {
        initState();
        if (!writable)
        {
            if (readable)
                throw new IllegalStateException(READ_ONLY_MSG);
            throw new IllegalStateException(OFFLINE_MSG);
        }
        
        // check parent
        ContainerNode parent = node.getParent();
        getWritePermission(parent); // checks lock and rw permission
        
        // check if the node is locked
        if (!disregardLocks && node.isLocked())
            throw new NodeLockedException(node.getUri().toString());
        
        if (node instanceof ContainerNode)
        {
            ContainerNode container = (ContainerNode) node;
            getWritePermission(container); // checks lock and rw permission
                
            Integer batchSize = new Integer(1000); // TODO: any value in not hard-coding this?
            VOSURI startURI = null;
            nodePersistence.getChildren(container, startURI, batchSize);
            while ( !container.getNodes().isEmpty() )
            {
                for (Node child : container.getNodes())
                {
                    getDeletePermission(child); // recursive
                    startURI = child.getUri();
                }
                // clear the children for garbage collection
                container.getNodes().clear();
                
                // get next batch
                nodePersistence.getChildren(container, startURI, batchSize);
                if ( !container.getNodes().isEmpty() )
                {
                    Node n = container.getNodes().get(0);
                    if ( n.getUri().equals(startURI) )
                        container.getNodes().remove(0); // avoid recheck and infinite loop
                }
            }
        }
    }
    
    /**
     * Given the groupURI, determine if the user identified by the subject
     * has membership.
     * @param groupURI The group or list of groups
     * @param subject The user's subject
     * @return True if the user is a member
     */
    private boolean hasMembership(NodeProperty groupProp, Subject subject)
    {
        List<String> groupURIs = groupProp.extractPropertyValueList();

        try
        {
            // check the group membership cache first
            Boolean cachedMembership = checkCachedMembership(groupURIs);
            if (cachedMembership != null)
            {
                LOG.debug("Using cached groupMembership results.");
                return cachedMembership;
            }
            
            // require identification for group membership: create a private key chain
            if (subject.getPrincipals().isEmpty())
                return false;
            
            // check for a cached socket factory
            if (socketFactory == null)
            {
                // init the socket factory using a proxy certificate from credPrivateClient or from
                // the private key chain
                try
                {
                    X509CertificateChain privateKeyChain = X509CertificateChain.findPrivateKeyChain(
                            subject.getPublicCredentials());
                    
                    // If we don't have the private key chain, get it from the credential
                    // delegation service and add it to the subject's public credentials
                    // for use later.
                    if (privateKeyChain == null)
                    {
                        URL credBaseURL = registryClient.getServiceURL(new URI(CRED_SERVICE_ID), "https");
                        CredPrivateClient credentialPrivateClient = CredPrivateClient.getInstance(credBaseURL);
                        privateKeyChain = credentialPrivateClient.getCertificate();
                        if (privateKeyChain == null)
                        {
                            // no delegated credentials == cannot check membership == not a member
                            // TODO: we could throw an exception with a useful message if that
                            //       could be added to the response message, essentially a reason
                            //       for the false
                            return false;
                        }
                        
                        privateKeyChain.getChain()[0].checkValidity();
                        
                        subject.getPublicCredentials().add(privateKeyChain);
                    }
                    
                    socketFactory = SSLUtil.getSocketFactory(privateKeyChain);
                    subjectHashCode = subject.hashCode();
                    
                }
                catch (URISyntaxException e)
                {
                    LOG.error("credPrivateClient invalid URL", e);
                    return false;
                }
                catch (CertificateException e)
                {
                    LOG.error("Error getting private certificate", e);
                    return false;
                }
                catch (AuthorizationException e)
                {
                    LOG.debug("Could't make credPrivateClientCall", e);
                    return false;
                }
                catch (InstantiationException e)
                {
                    LOG.error("Could't construct CredentialPrivateClient", e);
                    return false;
                }
                catch (IllegalAccessException e)
                {
                    LOG.error("Could't construct CredentialPrivateClient", e);
                    return false;
                }
                catch (ClassNotFoundException e)
                {
                    LOG.error("No implementing CredentialPrivateClients", e);
                    return false;
                }
            }
            else
            {
                // Ensure the subject hash code hasn't changed.  If it has, throw
                // an exception indicating that the VOSpaceAuthorizer cannot be reused
                // between HTTP requests.
                if (subject.hashCode() != subjectHashCode)
                {
                    throw new IllegalStateException(
                            "Illegal use of VOSpaceAuthorizer: Different subject detected.");
                }
            }
            
            // make gms calls to see if the user has group membership
            for (String groupURI : groupURIs)
            {
                try
                {
                    // check cache again in case the result was stored in an earlier iteration
                    // of this loop (duplicate groupsURIs in same property value)
                    cachedMembership = checkCachedMembership(groupURI);
                    if (cachedMembership != null)
                    {
                        LOG.debug(String.format(
                                "Using cached groupMembership: Group: %s isMember: %s",
                                groupURI, cachedMembership));
                        if (cachedMembership)
                            return true;
                    }
                    else
                    {
                        LOG.debug("Checking GMS on groupURI: " + groupURI);
                        
                        URI guri = new URI(groupURI);
                        if (guri.getFragment() == null)
                        {
                            throw new URISyntaxException(groupURI, "Missing fragment");
                        }
                        URI serviceURI = new URI(guri.getScheme(), guri.getSchemeSpecificPart(), null); // drop fragment
                        URL gmsBaseURL = registryClient.getServiceURL(serviceURI, VOS.GMS_PROTOCOL);
            
                        LOG.debug("GmsClient baseURL: " + gmsBaseURL);
                        if (gmsBaseURL == null)
                        {
                            LOG.warn("failed to find base URL for GMS service: " + serviceURI);
                        }
                        else
                        {
                            GmsClient gms = new GmsClient();
                            gms.setSslSocketFactory(socketFactory);
                            
                            // check group membership for each x500 principal that exists
                            Set<X500Principal> x500Principals = subject.getPrincipals(X500Principal.class);
                            for (X500Principal x500Principal : x500Principals)
                            {
                                boolean isMember = gms.isMember(guri, x500Principal);
                                LOG.debug("GmsClient.isMember(" + guri.getFragment() + ","
                                        + x500Principal.getName() + ") returned " + isMember);
                                
                                // cache this result for future queries
                                LOG.debug(String.format(
                                        "Caching groupMembership: Group: %s isMember: %s",
                                                groupURI, isMember));
                                groupMembershipCache.put(groupURI, isMember);
                                
                                if (isMember)
                                    return true;
                            }
                        }
                    }
                }
                catch (URISyntaxException e)
                {
                    LOG.warn("Invalid groupURI: " + groupURI);
                }
                catch(IOException ex)
                {
                    LOG.error("failed to call GMS service: " + ex);
                    Throwable cause = ex.getCause();
                    while (cause != null)
                    {
                        LOG.error("                    reason: " + cause.getCause());
                        cause = cause.getCause();
                    }
                }
            }
            
            return false;
        }
        catch (MalformedURLException e)
        {
            LOG.error("GMSClient invalid URL", e);
        }
        catch (Throwable t)
        {
            LOG.error("Internal Error", t);
        }
        return false;
    }
    
    /**
     * Return true if a cached membership result for any one of the
     * provided group URIs is true.
     * 
     * Return false if there is a cached version of each provided group URI
     * and each version is false.
     * 
     * Return null (unknown) otherwise.
     * 
     * @param groupURIs
     * @return
     */
    private Boolean checkCachedMembership(List<String> groupURIs)
    {
        boolean allURIsChecked = true;
        for (String groupURI : groupURIs)
        {
            LOG.debug("Checking cache for groupURI: " + groupURI);
            Boolean isMember = checkCachedMembership(groupURI);
            if (isMember != null)
            {
                if (isMember)
                    return true;
            }
            else
            {
                allURIsChecked = false;
            }
        }
        
        if (allURIsChecked)
            return false;
        else
            return null;
    }
    
    /**
     * Return true if there is a cached version of the groupURI set to true.
     * Return false if there is a cached version of the groupURI set to false.
     * Return null (unknown) otherwise.
     * @param groupURI
     * @return
     */
    private Boolean checkCachedMembership(String groupURI)
    {
        if (groupMembershipCache.containsKey(groupURI))
        {
            boolean isMember = groupMembershipCache.get(groupURI);
            LOG.debug(String.format(
                    "Found cached groupMembership: Group: %s isMember: %s",
                            groupURI, isMember));
            return isMember;
        }
        return null;
    }
    
    /**
     * Check if the specified subject is the owner of the node.
     *
     * @param subject
     * @param node
     * @return true of the current subject is the owner, otherwise false
     */
    private boolean isOwner(Node node, Subject subject)
    {
        NodeID nodeID = (NodeID) node.appData;
        if (nodeID == null)
        {
            throw new IllegalStateException("BUG: no owner found for node: " + node);
        }
        if (nodeID.getID() == null) // root node, no owner
            return false;

        Subject owner = nodeID.getOwner();
        if (owner == null)
        {
            throw new IllegalStateException("BUG: no owner found for node: " + node);
        }
        
        Set<Principal> ownerPrincipals = owner.getPrincipals();
        Set<Principal> callerPrincipals = subject.getPrincipals();
        
        for (Principal oPrin : ownerPrincipals)
        {
            for (Principal cPrin : callerPrincipals)
            {
                if (LOG.isDebugEnabled())
                {
                    LOG.debug(String.format(
                            "Checking owner of node \"%s\" (owner=\"%s\") where user=\"%s\"",
                            node.getName(), oPrin, cPrin));
                }
                if (AuthenticationUtil.equals(oPrin, cPrin))
                    return true; // caller===owner
            }
        }
        return false;
    }

    /**
     * Check the read permission on a single node.
     * 
     * For full node authorization, use getReadPermission(Node).
     * 
     * @param node The node to check.
     * @throws AccessControlException If permission is denied.
     */
    private boolean hasSingleNodeReadPermission(Node node, Subject subject)
    {
        LOG.debug("checkSingleNodeReadPermission: " + node.getUri());
        if (node.isPublic())
            return true; // OK
        
        // return true if this is the owner of the node or if a member
        // of the groupRead or groupWrite property
        if (subject != null)
        {
            if (isOwner(node, subject))
            {
                LOG.debug("Node owner granted read permission.");
                return true; // OK
            }

            // the GROUPREAD property means the user has read-only permission
            NodeProperty groupRead = node.findProperty(VOS.PROPERTY_URI_GROUPREAD);
            if (LOG.isDebugEnabled())
            {
                String groupReadString = groupRead == null ? "null" : groupRead.getPropertyValue();
                LOG.debug(String.format(
                        "Checking group read permission on node \"%s\" (groupRead=\"%s\")",
                        node.getName(), groupReadString));
            }
            if (groupRead != null && groupRead.getPropertyValue() != null)
            {
                if (hasMembership(groupRead, subject))
                    return true; // OK
            }
            
            // the GROUPWRITE property means the user has read+write permission
            // so check that too
            NodeProperty groupWrite = node.findProperty(VOS.PROPERTY_URI_GROUPWRITE);
            if (LOG.isDebugEnabled())
            {
                String groupReadString = groupWrite == null ? "null" : groupWrite.getPropertyValue();
                LOG.debug(String.format(
                        "Checking group write permission on node \"%s\" (groupWrite=\"%s\")",
                        node.getName(), groupReadString));
            }
            if (groupWrite != null && groupWrite.getPropertyValue() != null)
            {
                if (hasMembership(groupWrite, subject))
                    return true; // OK
            }
        }
        
        return false;
    }
    
    /**
     * Check the write permission on a single node.
     * 
     * For full node authorization, use getWritePermission(Node).
     * 
     * @param node The node to check.
     * @throws AccessControlException If permission is denied.
     */
    private boolean hasSingleNodeWritePermission(Node node, Subject subject)
    {
        if (node.getUri().isRoot())
            return false;
        if (subject != null)
        {
            if (isOwner(node, subject))
            {
                LOG.debug("Node owner granted write permission.");
                return true; // OK
            }
            
            NodeProperty groupWrite = node.findProperty(VOS.PROPERTY_URI_GROUPWRITE);
            if (LOG.isDebugEnabled())
            {
                String groupReadString = groupWrite == null ? "null" : groupWrite.getPropertyValue();
                LOG.debug(String.format(
                        "Checking group write permission on node \"%s\" (groupWrite=\"%s\")",
                        node.getName(), groupReadString));
            }
            if (groupWrite != null && groupWrite.getPropertyValue() != null)
            {
                if (hasMembership(groupWrite, subject))
                    return true; // OK
            }
        }
        return false;
    }

    public void setDisregardLocks(boolean disregardLocks)
    {
        this.disregardLocks = disregardLocks;
    }

    /**
     * Node NodePersistence Getter.
     *
     * @return  NodePersistence instance.
     */
    public NodePersistence getNodePersistence()
    {
        return nodePersistence;
    }

    /**
     * Node NodePersistence Setter.
     *
     * @param nodePersistence       NodePersistence instance.
     */
    public void setNodePersistence(final NodePersistence nodePersistence)
    {
        this.nodePersistence = nodePersistence;
    }
    
}
