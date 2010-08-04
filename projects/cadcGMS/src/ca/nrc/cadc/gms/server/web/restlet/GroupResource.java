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
import java.net.URLDecoder;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.restlet.data.Status;
import org.restlet.resource.Delete;
import org.restlet.resource.Post;
import org.restlet.resource.Put;

import ca.nrc.cadc.gms.AuthorizationException;
import ca.nrc.cadc.gms.Group;
import ca.nrc.cadc.gms.GroupWriter;
import ca.nrc.cadc.gms.InvalidGroupException;
import ca.nrc.cadc.gms.server.GroupService;


public class GroupResource extends AbstractResource
{
    private final static Logger LOGGER =
        Logger.getLogger(GroupResource.class);
    
    private GroupService groupService;

    private Group group;

    /**
     * Hidden constructor for JavaBean tools.
     */
    GroupResource()
    {
        super();
    }

    /**
     * Full constructor.
     *
     * @param groupService      The GroupService instance.
     */
    public GroupResource(final GroupService groupService)
    {
        this.groupService = groupService;
    }

    /**
     * Get a reference to the resource identified by the user.
     * 
     * @throws FileNotFoundException If the resource doesn't exist.
     */
    @Override
    protected boolean obtainResource() throws FileNotFoundException
    {
        LOGGER.debug("Enter GroupResource.obtainResource()");
        String groupMemberID = null;
        String groupID = null;
        
        try
        {
            groupID = URLDecoder.decode(getGroupID(), "UTF-8");
            LOGGER.debug(String.format(
                    "groupID: %s",
                    groupID));
            
            group = getGroupService().getGroup(groupID);
            return true;
        }
        catch (final InvalidGroupException e)
        {
            final String message = String.format("No such Group with ID %s",
                                                 groupID);
            processError(e, Status.CLIENT_ERROR_NOT_FOUND, message);
        }
        catch (IllegalArgumentException e)
        {
            final String message =
                    String.format("The given User with ID %s is not a member "
                                  + "of Group with ID %s.", groupMemberID,
                                  groupID);
            processError(e, Status.CLIENT_ERROR_NOT_FOUND, message);
        }
        catch (AuthorizationException e)
        {
            final String message =
                    String.format("You are not authorized to view Member ID "
                                  + "'%s' of Group ID '%s'.", 
                                  groupMemberID, groupID);
            processError(e, Status.CLIENT_ERROR_UNAUTHORIZED, message);
        }
        catch (UnsupportedEncodingException e)
        {
            final String message =
                String.format("Could not URL decode groupMemberID (%s) or "
                              + "groupID (%s).", 
                              groupMemberID, groupID);
            processError(e, Status.CLIENT_ERROR_BAD_REQUEST, message);
        }
        return false;
    }
    
    /**
     * Assemble the XML for this Resource's Representation into the given
     * Document.
     *
     * @param document The Document to build up.
     * @throws java.io.IOException If something went wrong or the XML cannot be
     *                             built.
     */
    protected void buildXML(final Document document) throws IOException
    {
        LOGGER.debug("Enter GroupMemberResource.buildXML()");
        document.addContent(GroupWriter.getGroupElement(group));
    }

    /**
     * Accept a POST to this Resource to Update a Group.
     */
    @Post
    public void acceptPost()
    {
        processNotImplemented(String.format("The Service to update Group with "
                                            + "ID '%s' is not yet implemented.",
                                            getGroupID()));
    }

    /**
     * Accept a PUT to this Resource to Create a Group.
     */
    @Put
    public void acceptPut()
    {
        processNotImplemented(String.format("The Service to create Group with "
                                            + "ID '%s' is not yet implemented.",
                                            getGroupID()));
    }

    /**
     * Accept a DELETE to this Resource to Delete a Group.
     */
    @Delete
    public void acceptDelete()
    {
        processNotImplemented(String.format("The Service to delete Group with "
                                            + "ID '%s' is not yet implemented.",
                                            getGroupID()));
    }

    protected Group getGroup() throws AuthorizationException, InvalidGroupException
    {
        return getGroupService().getGroup(getGroupID());
    }

    protected String getGroupID()
    {
        return (String) getRequestAttribute("groupID");
    }

    public GroupService getGroupService()
    {
        return groupService;
    }

    public void setGroupService(final GroupService groupService)
    {
        this.groupService = groupService;
    }
}
