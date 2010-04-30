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
package ca.nrc.cadc.gms.web.resources.restlet;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.restlet.data.Status;

import java.io.IOException;
import java.net.URLDecoder;

import ca.nrc.cadc.gms.service.GroupService;
import ca.nrc.cadc.gms.service.UserService;
import ca.nrc.cadc.gms.Group;
import ca.nrc.cadc.gms.User;
import ca.nrc.cadc.gms.InvalidGroupException;
import ca.nrc.cadc.gms.InvalidMemberException;


public class MemberGroupResource extends MemberResource
{
    private GroupService groupService;


    /**
     * No-argument constructor.
     */
    public MemberGroupResource()
    {
        super();
    }

    /**
     * Full constructor with appropriate arguments.
     *
     * @param userService The UserService instance.
     * @param groupService  The GroupService instance.
     */
    public MemberGroupResource(final UserService userService,
                               final GroupService groupService)
    {
        super(userService);
        setGroupService(groupService);
    }

    /**
     * Assemble the XML for this Resource's Representation into the given
     * Document.
     *
     * @param document The Document to build up.
     * @throws java.io.IOException If something went wrong or the XML cannot be
     *                             built.
     */
    @Override
    protected void buildXML(final Document document) throws IOException
    {
        final String groupMemberID = URLDecoder.decode(getMemberID(), "UTF-8");
        final String groupID = URLDecoder.decode(getGroupID(), "UTF-8");

        try
        {
            getUserService().getMember(groupMemberID, groupID);
            appendContent(document);
        }
        catch (final InvalidGroupException e)
        {
            final String message = String.format("No such Group with ID %s",
                                                 groupID);
            processError(e, Status.CLIENT_ERROR_NOT_FOUND, message);
        }
        catch (InvalidMemberException e)
        {
            final String message = String.format("No such User with ID %s",
                                                 groupMemberID);
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
        catch (ca.nrc.cadc.gms.AuthorizationException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }


    /**
     * Append the XML data to the given Document for this Group Member.
     *
     * @param document  The Document, already initialized.
     */
    private void appendContent(final Document document)
    {
        final User user = getUserService().getUser(getMemberID());

        final Element userElement = document.createElement("member");
        userElement.setAttribute("id", user.getUserID());

        final Element usernameElement = document.createElement("username");
        usernameElement.setTextContent(user.getUsername());
        userElement.appendChild(usernameElement);

        document.appendChild(userElement);
    }

    
    protected String getGroupID()
    {
        return (String) getRequestAttribute("groupID");
    }

    protected Group getGroup()
    {
        return getGroupService().getGroup(getGroupID());
    }


    public GroupService getGroupService()
    {
        return groupService;
    }

    public void setGroupService(GroupService groupService)
    {
        this.groupService = groupService;
    }
}
