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

package ca.nrc.cadc.vos.server.web.restlet.action;

import java.io.FileNotFoundException;
import java.net.URL;
import java.security.AccessControlException;
import java.util.ListIterator;

import org.restlet.data.Reference;

import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.Search;
import ca.nrc.cadc.vos.server.AbstractView;
import ca.nrc.cadc.vos.server.web.representation.NodeOutputRepresentation;
import ca.nrc.cadc.vos.server.web.representation.ViewRepresentation;

/**
 * Class to perform the retrieval of a Node.
 * 
 * @author majorb
 */
public class GetNodeAction extends NodeAction
{
    private Search search;

    /**
     * Basic empty constructor.
     */
    public GetNodeAction()
    {
    }

    /**
     * Constructor with search parameters.  It is perfectly valid for the
     * Search to be null.
     *
     * @param search    Search criteria.
     */
    public GetNodeAction(final Search search)
    {
        this.search = search;
    }

    @Override
    public Node doAuthorizationCheck()
        throws AccessControlException, FileNotFoundException
    {
        return (Node) voSpaceAuthorizer.getReadPermission(vosURI.getURIObject());
    }
    
    @Override
    public NodeActionResult performNodeAction(Node clientNode, Node serverNode)
    {
        long start;
        long end;
        if (serverNode instanceof ContainerNode)
        {
            ContainerNode cn = (ContainerNode) serverNode;
            start = System.currentTimeMillis();
            nodePersistence.getChildren(cn);
            end = System.currentTimeMillis();
            log.debug("nodePersistence.getChildren() elapsed time: " + (end - start) + "ms");
            doFilterChildren(cn);
        }
        start = System.currentTimeMillis();
        nodePersistence.getProperties(serverNode);
        end = System.currentTimeMillis();
        log.debug("nodePersistence.getProperties() elapsed time: " + (end - start) + "ms");

        AbstractView view;
        try
        {
            view = getView();
        }
        catch (Exception ex)
        {
            log.error("failed to load view: " + this.viewReference, ex);
            // this should generate an InternalFault in NodeAction
            throw new RuntimeException("view was configured but failed to load: " + this.viewReference);
        }

        if (view == null)
        {
            // no view specified or found--return the xml representation
            final NodeWriter nodeWriter = new NodeWriter();
            nodeWriter.setStylesheetURL(getStylesheetURL());
            return new NodeActionResult(new NodeOutputRepresentation(serverNode, nodeWriter));
        }
        else
        {
            Reference ref = request.getOriginalRef();
            URL url = ref.toUrl();
            view.setNode(serverNode, viewReference, url);
            URL redirectURL = view.getRedirectURL();
            if (redirectURL != null)
            {
                return new NodeActionResult(redirectURL);
            }
            else
            {
                // return a representation for the view
                return new NodeActionResult(new ViewRepresentation(view));
            }
        }
    }
    
    /**
     * Look for the stylesheet URL in the request context.
     * @return      The String URL of the stylesheet for this action.
     *              Null if no reference is provided.
     */
    public String getStylesheetURL()
    {
        log.debug("Stylesheet Reference is: " + stylesheetReference);
        if (stylesheetReference != null)
        {
            String scheme = request.getHostRef().getScheme();
            String server = request.getHostRef().getHostDomain();
            StringBuilder url = new StringBuilder();
            url.append(scheme);
            url.append("://");
            url.append(server);
            if (!stylesheetReference.startsWith("/"))
                url.append("/");
            url.append(stylesheetReference);
            return url.toString();
        }
        return null;
    }


    public Search getSearch()
    {
        return search;
    }

    public void setSearch(final Search search)
    {
        this.search = search;
    }

    // if this is the root node, we apply a privacy policy and filter out
    // child nodes the caller is not allowed to read
    private void doFilterChildren(ContainerNode node)
    {
        if ( !node.getUri().isRoot() )
            return;

        ListIterator<Node> iter = node.getNodes().listIterator();
        while ( iter.hasNext() )
        {
            Node n = iter.next();
            try
            {
                voSpaceAuthorizer.getReadPermission(n);
            }
            catch(AccessControlException ex)
            {
                log.debug("doFilterChildren: remove " + n);
                iter.remove();
            }
        }
    }
}
