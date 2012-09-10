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
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import ca.nrc.cadc.vos.LinkNode;
import ca.nrc.cadc.vos.LinkingException;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.server.auth.VOSpaceAuthorizer;

/**
 * Utility class to follow and resolve the target of link nodes in a local vospace.
 * 
 * If the last node on the path is a link node, it will not be resolved.
 * 
 * @author adriand
 * @author majorb
 *
 */
public class PathResolver
{
    
    protected static final Logger LOG = Logger.getLogger(PathResolver.class);
    
    private NodePersistence nodePersistence;
    private List<String> visitedPaths;
    
    // enforce a maximum visit limit to prevent stack overflows on
    // the recursive method
    private static final int VISIT_LIMIT_MAX = 40;
    private int visitLimit = 20;
    private int visitCount = 0;
    
    /**
     * Constructor.
     * 
     * @param nodePersistence
     */
    public PathResolver(NodePersistence nodePersistence)
    {
        if (nodePersistence == null)
        {
            throw new IllegalArgumentException("null node persistence.");
        }
        this.nodePersistence = nodePersistence;
    }
    
    /**
     * Resolve the path of the node identified by parameter uri.
     * 
     * @param uri
     * @return
     * @throws NodeNotFoundException
     * @throws LinkingException
     */
    public Node resolve(VOSURI uri) throws NodeNotFoundException, LinkingException
    {
        return resolveWithReadPermissionCheck(uri, null, false);
    }


    /**
     * Resolve the path/node identified by parameter uri.  Check read permission on
     * each link node resolution.
     * 
     * @param uri
     * @param readAuthorizer
     * @param resolveLeafNodes true if leaf nodes resolved, false otherwise
     * @return
     * @throws NodeNotFoundException
     * @throws LinkingException
     */
    public Node resolveWithReadPermissionCheck(VOSURI uri, 
            VOSpaceAuthorizer readAuthorizer,
            boolean resolveLeafNodes)
            throws NodeNotFoundException, LinkingException
    {
        visitCount = 0;
        visitedPaths = new ArrayList<String>();
        Node result = doResolve(uri, readAuthorizer);
        if (resolveLeafNodes)
        {
            // resolve the leaf linknodes
            while (result instanceof LinkNode)
            {
                LinkNode linkNode = (LinkNode)result;
                PathResolver.validateTargetURI(linkNode);
                // follow the link           
                result = doResolve(
                        new VOSURI(linkNode.getTarget()), readAuthorizer);
            }
        }
        return result;
    }

    public Node resolveWithReadPermissionCheck(VOSURI uri, Node node,
            VOSpaceAuthorizer readAuthorizer,
            boolean resolveLeafNodes)
            throws NodeNotFoundException, LinkingException
    {
        visitCount = 0;
        visitedPaths = new ArrayList<String>();
        Node result = doResolve(uri, node, readAuthorizer);
        if (resolveLeafNodes)
        {
            // resolve the leaf linknodes
            while (result instanceof LinkNode)
            {
                LinkNode linkNode = (LinkNode)result;
                PathResolver.validateTargetURI(linkNode);
                // follow the link
                result = doResolve(
                        new VOSURI(linkNode.getTarget()), readAuthorizer);
            }
        }
        return result;
    }

    /**
     * Used recursively to following the path of a node, resolving link targets
     * on the way.
     * 
     * @param uri
     * @param readAuthorizer
     * @return
     * @throws NodeNotFoundException
     * @throws LinkingException
     */
    private Node doResolve(VOSURI vosuri, 
            VOSpaceAuthorizer readAuthorizer)
            throws NodeNotFoundException, LinkingException
    {
        Node node = nodePersistence.get(vosuri, true);
        return doResolve(vosuri, node, readAuthorizer);
    }

    /**
     *
     * @param vosuri requested path
     * @param node actual node found (possibly partial path)
     * @param readAuthorizer
     * @return
     * @throws NodeNotFoundException
     * @throws LinkingException
     */
    private Node doResolve(VOSURI vosuri, Node node,
            VOSpaceAuthorizer readAuthorizer)
            throws NodeNotFoundException, LinkingException
    {
        if (visitCount > visitLimit)
        {
            throw new LinkingException("Exceeded link limit.");
        }
        visitCount++;
        LOG.debug("visit number " + visitCount);

        if (readAuthorizer != null)
        {
            LOG.debug("doing read permission check.");
            readAuthorizer.getReadPermission(node);
        }
        
        // extract the paths
        String requestedPath = vosuri.getPath();
        String nodePath = node.getUri().getPath();
        LOG.debug("[requested][resulting] path: [" + requestedPath + "][" + nodePath + "]");
        
        // check circular reference
        if (visitedPaths.contains(nodePath))
        {
            LOG.debug("Found circular link: " + nodePath);
            throw new LinkingException("Circular link reference: " + nodePath);
        }
        visitedPaths.add(nodePath);
        LOG.debug("Added " + nodePath + " to visited list.");
        
        if (!requestedPath.equals(nodePath))
        {
            if (!(node instanceof LinkNode))
            {
                throw new NodeNotFoundException(vosuri.toString());
            }
            LinkNode linkNode = (LinkNode) node;
            String remainingPath = requestedPath.substring(nodePath.length());
            
            VOSURI linkURI = validateTargetURI(linkNode);
            try
            {
                VOSURI resolvedURI = new VOSURI(linkURI.toString() + remainingPath);
                return doResolve(resolvedURI, readAuthorizer);
            }
            catch (URISyntaxException e)
            {
                throw new LinkingException("Invalid link URI");
            }
        }
        
        return node;
    }

    /**
     * Return a new VOSURI representing the target URI of the link node.
     * 
     * @param linkNode
     * @return A VOSURI of the target of the link node.
     * @throws LinkingException If the target is non vospace, not local, or
     * an invalid URI.
     */
    public static VOSURI validateTargetURI(LinkNode linkNode) throws LinkingException
    {
        VOSURI nodeURI = linkNode.getUri();
        URI targetURI = linkNode.getTarget();
        
        // check the scheme
        if (nodeURI.getScheme() == null ||
                targetURI.getScheme() == null ||
                !nodeURI.getScheme().equals(targetURI.getScheme()))
        {
            throw new LinkingException("Unsupported link target");
        }
        
        try
        {
            VOSURI returnURI = new VOSURI(targetURI);
            
            String nodeAuth = nodeURI.getAuthority();
            String targetAuth = returnURI.getAuthority();
            
            if (nodeAuth == null || targetAuth == null)
            {
                throw new LinkingException("Non-local VOSpace target.");
            }
            
            nodeAuth = nodeAuth.replace('~', '!');
            targetAuth = targetAuth.replace('~', '!');
            
            // authorities must match
            if (!nodeAuth.equals(targetAuth))
            {                
                throw new LinkingException("Non-local VOSpace target.");
            }
            
            return returnURI;
        }
        catch (Exception e)
        {
            throw new LinkingException("Invalid target URI", e);
        }
    }
    
    /**
     * Set the limit for number of link reference resolutions.
     * Default The value is 20.
     * @param visitLimit
     */
    public void setVisitLimit(int visitLimit)
    {
        if (visitLimit > VISIT_LIMIT_MAX)
        {
            throw new IllegalArgumentException(
                    "Too high a visit limit.  Must be below " + VISIT_LIMIT_MAX);
        }
        this.visitLimit = visitLimit;
    }

}
