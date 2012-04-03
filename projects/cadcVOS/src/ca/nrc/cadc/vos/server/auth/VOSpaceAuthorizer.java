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
import ca.nrc.cadc.reg.client.RegistryClient;
import ca.nrc.cadc.vos.Node;
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
    
    private SSLSocketFactory socketFactory;
    private int subjectHashCode;
    
    // cache of groupURI to isMember
    private Map<String, Boolean> groupMembershipCache;
    
    private NodePersistence nodePersistence;

    public VOSpaceAuthorizer()
    {
        groupMembershipCache = new HashMap<String, Boolean>();
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
        throws AccessControlException, FileNotFoundException
    {
        try
        {
            VOSURI vos = new VOSURI(uri);
            Node node = nodePersistence.get(vos);
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
     * @return          The Write Permission objectual representation, such as
     *                  a Group, or User.
     * @throws AccessControlException If the user does not have write permission
     * @throws FileNotFoundException If the node could not be found
     */
    public Object getWritePermission(URI uri)
            throws AccessControlException, FileNotFoundException
    {
        try
        {
            VOSURI vos = new VOSURI(uri);
            Node node = nodePersistence.get(vos);
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
     */
    public Object getWritePermission(Node node)
            throws AccessControlException
    {
        AccessControlContext acContext = AccessController.getContext();
        Subject subject = Subject.getSubject(acContext);
        
        LinkedList<Node> nodes = Node.getNodeList(node);
        
        // check for root ownership
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
     * Given the groupURI, determine if the user identified by the subject
     * has membership.
     * @param groupURI The group
     * @param subject The user's subject
     * @return True if the user is a member
     */
    private boolean hasMembership(String groupURI, Subject subject)
    {
        try
        {
            // check to see if we've cached the hasMembership result
            // for this groupURI
            if (groupMembershipCache.containsKey(groupURI))
            {
                boolean isMember = groupMembershipCache.get(groupURI);
                LOG.debug(String.format(
                        "Using cached groupMembership: Group: %s isMember: %s",
                                groupURI, isMember));
                return isMember;
            }
            
            // require identification for group membership
            if (subject.getPrincipals().size() <= 0)
            {
                return false;
            }
            
            RegistryClient registryClient = new RegistryClient();
            
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
                
                try { privateKeyChain.getChain()[0].checkValidity(); }
                catch(CertificateException ex)
                {
                    // TODO: as above, try to respond with a reason for the false
                    LOG.warn("invalid certificate for use in GMS calls for: " + privateKeyChain.getPrincipal());
                    return false;
                }

                subject.getPublicCredentials().add(privateKeyChain);
            }
            
            if (socketFactory == null)
            {
                socketFactory = SSLUtil.getSocketFactory(privateKeyChain);
                subjectHashCode = subject.hashCode();
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
            
            URI guri = new URI(groupURI);
            if (guri.getFragment() == null)
            {
                LOG.warn("Invalid Group URI: " + groupURI);
                return false;
            }
            URI serviceURI = new URI(guri.getScheme(), guri.getSchemeSpecificPart(), null); // drop fragment
            URL gmsBaseURL = registryClient.getServiceURL(serviceURI, VOS.GMS_PROTOCOL);

            LOG.debug("GmsClient baseURL: " + gmsBaseURL);
            if (gmsBaseURL == null)
            {
                LOG.warn("failed to find base URL for GMS service: " + serviceURI);
                return false;
            }
            GmsClient gms = new GmsClient(gmsBaseURL);
            gms.setSslSocketFactory(socketFactory);
            
            // check group membership for each x500 principal that exists
            Set<X500Principal> x500Principals = subject.getPrincipals(X500Principal.class);
            for (X500Principal x500Principal : x500Principals)
            {
                try
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
                catch(IOException ex)
                {
                    LOG.error("failed to call GMS service: " + ex);
                    Throwable cause = ex.getCause();
                    while (cause != null)
                    {
                        LOG.error("                    reason: " + cause.getCause());
                        cause = cause.getCause();
                    }
                    LOG.error("bailing out of membership checking", ex);
                    return false;
                }
            }
            return false;
        }
        catch (MalformedURLException e)
        {
            LOG.error("GMSClient invalid URL", e);
            //throw new IllegalStateException(e);
        }
        catch (URISyntaxException e)
        {
            LOG.warn("Invalid Group URI: " + groupURI, e);
            //return false;
        }
        catch (CertificateException e)
        {
            LOG.error("Error getting private certificate", e);
            //throw new IllegalStateException(e);
        }
        catch (AuthorizationException e)
        {
            LOG.debug("Could't make credPrivateClientCall", e);
            //return false;
        }
        catch (InstantiationException e)
        {
            LOG.error("Could't construct CredentialPrivateClient", e);
            //throw new IllegalStateException(e);
        }
        catch (IllegalAccessException e)
        {
            LOG.error("Could't construct CredentialPrivateClient", e);
            //throw new IllegalStateException(e);
        }
        catch (ClassNotFoundException e)
        {
            LOG.error("No implementing CredentialPrivateClients", e);
            //throw new IllegalStateException(e);
        }
        catch(Throwable t)
        {
            LOG.error("unexpected failure", t);
            //throw new IllegalStateException("unexpected failure", t);
        }
        return false;
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
                if (hasMembership(groupRead.getPropertyValue(), subject))
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
                if (hasMembership(groupWrite.getPropertyValue(), subject))
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
                if (hasMembership(groupWrite.getPropertyValue(), subject))
                    return true; // OK
            }
        }
        return false;
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
