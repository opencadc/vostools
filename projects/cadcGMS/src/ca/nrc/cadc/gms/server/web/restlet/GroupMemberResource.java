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
package ca.nrc.cadc.gms.server.web.restlet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;

import javax.security.auth.x500.X500Principal;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Post;

import ca.nrc.cadc.gms.AuthorizationException;
import ca.nrc.cadc.gms.Group;
import ca.nrc.cadc.gms.InvalidGroupException;
import ca.nrc.cadc.gms.InvalidMemberException;
import ca.nrc.cadc.gms.User;
import ca.nrc.cadc.gms.UserWriter;
import ca.nrc.cadc.gms.server.GroupService;
import ca.nrc.cadc.gms.server.UserService;

public class GroupMemberResource extends GroupResource
{
    private final static Logger LOGGER = Logger
            .getLogger(GroupMemberResource.class);

    private UserService userService;

    private User groupMember;

    /**
     * Hidden constructor for JavaBean tools.
     */
    GroupMemberResource()
    {
        super();
    }

    /**
     * Full constructor.
     * 
     * @param groupService
     *            The Group Service to use.
     * @param userService
     *            The User Service to use.
     */
    public GroupMemberResource(final GroupService groupService,
            final UserService userService)
    {
        super(groupService);
        this.userService = userService;
    }

    /**
     * Get a reference to the resource identified by the user.
     * 
     * @throws FileNotFoundException
     *             If the resource doesn't exist.
     */
    @Override
    protected boolean obtainResource()
    {
        LOGGER.debug("Enter GroupMemberResource.obtainResource()");
        String groupMemberID = null;
        String groupID = null;

        try
        {
            groupMemberID = URLDecoder.decode(getMemberID(), "UTF-8");
            groupID = URLDecoder.decode(getGroupID(), "UTF-8");
            LOGGER.debug(String.format("groupID: %s memberID: %s",
                    groupID, groupMemberID));

            groupMember = getUserService().getMember(
                    new X500Principal(groupMemberID),
                    new URI(groupService.getGroupUriPrefix() + groupID));
            if (groupMember == null)
            {
                final String message = String.format(
                        "No such User with ID %s", groupMemberID);
                processError(null, Status.CLIENT_ERROR_NOT_FOUND, message);
                return false;
            }
            return true;
        }
        catch (final InvalidGroupException e)
        {
            final String message = String.format(
                    "No such Group with ID %s", groupID);
            processError(e, Status.CLIENT_ERROR_NOT_FOUND, message);
        }
        catch (InvalidMemberException e)
        {
            final String message = String.format(
                    "No such User with ID %s", groupMemberID);
            processError(e, Status.CLIENT_ERROR_NOT_FOUND, message);
        }
        catch (AuthorizationException e)
        {
            final String message = String.format(
                    "You are not authorized to get Member ID "
                            + "'%s' from Group ID '%s'.", groupMemberID,
                    groupID);
            processError(e, Status.CLIENT_ERROR_UNAUTHORIZED, message);
        }
        catch (UnsupportedEncodingException e)
        {
            final String message = String.format(
                    "Could not URL decode groupMemberID (%s) or "
                            + "groupID (%s).", groupMemberID, groupID);
            processError(e, Status.CLIENT_ERROR_BAD_REQUEST, message);
        }
        catch (URISyntaxException e)
        {
            final String message = String.format(
                    "Could not URI decode groupID (%s).", groupID);
            processError(e, Status.CLIENT_ERROR_BAD_REQUEST, message);
        }
        return false;
    }

    /**
     * Handle POST requests: add a member to a group
     */
    @Post
    public void acceptPost(Representation entity)
    {
        LOGGER.debug("Delete member " + getMemberID() + " from group "
                + getGroupID());
        try
        {
            URI grID = new URI(groupService.getGroupUriPrefix()
                    + URLDecoder.decode(getGroupID(), "UTF-8"));
            X500Principal usrID = new X500Principal(URLDecoder.decode(
                    getMemberID(), "UTF-8"));
            Group group = getGroupService().addUserToGroup(grID,
                    usrID);
            setStatus(Status.SUCCESS_CREATED);
        }
        catch (InvalidMemberException e)
        {
            final String message = String.format(
                    "Cannot add User with ID %s", getMemberID());
            processError(e, Status.CLIENT_ERROR_BAD_REQUEST, message);
        }
        catch (AuthorizationException e)
        {
            final String message = String.format(
                    "You are not authorized to add Member ID "
                            + "'%s' in Group ID '%s'.", getMemberID(),
                    getGroupID());
            processError(e, Status.CLIENT_ERROR_UNAUTHORIZED, message);
        }
        catch (InvalidGroupException e)
        {
            // this should not happen for a null group id
            final String message = "Creation of new groups not supported";
            processError(e, Status.CLIENT_ERROR_BAD_REQUEST, message);
        }
        catch (URISyntaxException e)
        {
            final String message = String.format(
                    "Could not URI decode groupID (%s).", getGroupID());
            processError(e, Status.CLIENT_ERROR_BAD_REQUEST, message);
        }
        catch (UnsupportedEncodingException e)
        {
            final String message = String.format(
                    "Could not URL decode groupMemberID (%s) or "
                            + "groupID (%s).", getMemberID(),
                    getGroupID());
            processError(e, Status.CLIENT_ERROR_BAD_REQUEST, message);
        }
    }

    /**
     * Handle DELETE requests: remove a member from a group.
     */
    @Delete
    public void acceptDelete(Representation entity)
    {
        LOGGER.debug("Delete member " + getMemberID() + " to group "
                + getGroupID());
        try
        {
            URI grID = new URI(groupService.getGroupUriPrefix()
                    + URLDecoder.decode(getGroupID(), "UTF-8"));
            X500Principal usrID = new X500Principal(URLDecoder.decode(
                    getMemberID(), "UTF-8"));
            Group group = getGroupService().deleteUserFromGroup(grID, usrID);
            setStatus(Status.SUCCESS_OK);
        }
        catch (InvalidMemberException e)
        {
            final String message = String.format(
                    "Cannot add User with ID %s", getMemberID());
            processError(e, Status.CLIENT_ERROR_BAD_REQUEST, message);
        }
        catch (AuthorizationException e)
        {
            final String message = String.format(
                    "You are not authorized to delete Member ID "
                            + "'%s' from Group ID '%s'.", getMemberID(),
                    getGroupID());
            processError(e, Status.CLIENT_ERROR_UNAUTHORIZED, message);
        }
        catch (InvalidGroupException e)
        {
            // this should not happen for a null group id
            final String message = "Creation of new groups not supported";
            processError(e, Status.CLIENT_ERROR_BAD_REQUEST, message);
        }
        catch (URISyntaxException e)
        {
            final String message = String.format(
                    "Could not URI decode groupID (%s).", getGroupID());
            processError(e, Status.CLIENT_ERROR_BAD_REQUEST, message);
        }
        catch (UnsupportedEncodingException e)
        {
            final String message = String.format(
                    "Could not URL decode groupMemberID (%s) or "
                            + "groupID (%s).", getMemberID(),
                    getGroupID());
            processError(e, Status.CLIENT_ERROR_BAD_REQUEST, message);
        }
    }

    /**
     * Assemble the XML for this Resource's Representation into the given
     * Document.
     * 
     * @param document
     *            The Document to build up.
     * @throws java.io.IOException
     *             If something went wrong or the XML cannot be built.
     */
    @Override
    protected void buildXML(final Document document) throws IOException
    {
        LOGGER.debug("Enter GroupMemberResource.buildXML()");
        document.addContent(UserWriter.getUserElement(groupMember));
    }

    protected String getMemberID()
    {
        // TODO add decode
        return (String) getRequestAttribute("memberID");
    }

    protected User getMember() throws InvalidMemberException,
            AuthorizationException
    {
        return getUserService().getUser(new X500Principal(getMemberID()),
                false);
    }

    public UserService getUserService()
    {
        return userService;
    }

    public void setUserService(final UserService userService)
    {
        this.userService = userService;
    }

}
