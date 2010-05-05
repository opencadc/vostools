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
import org.w3c.dom.Node;
import org.restlet.data.Status;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.apache.log4j.Logger;
import org.apache.xerces.parsers.DOMParser;

import java.io.*;
import java.net.URLDecoder;

import ca.nrc.cadc.gms.*;
import ca.nrc.cadc.gms.service.UserService;
import ca.nrc.cadc.gms.service.GroupService;


public class GroupMemberResource extends GroupResource
{
    private final static Logger LOGGER =
            Logger.getLogger(GroupMemberResource.class);

    private UserService userService;


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
     * @param groupService      The Group Service to use.
     * @param userService       The User Service to use.
     */
    public GroupMemberResource(final GroupService groupService,
                               final UserService userService)
    {
        super(groupService);
        this.userService = userService;
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
        catch (AuthorizationException e)
        {
            final String message =
                    String.format("You are not authorized to view Member ID "
                                  + "'%s' of Group ID '%s'.", 
                                  groupMemberID, groupID);
            processError(e, Status.CLIENT_ERROR_UNAUTHORIZED, message);
        }
    }

    protected String getMemberID()
    {
        return (String) getRequestAttribute("memberID");
    }

    protected User getMember()
    {
        return getUserService().getUser(getMemberID());
    }

    /**
     * Append the XML data to the given Document for this Group Member.
     *
     * @param document  The Document, already initialized.
     */
    private void appendContent(final Document document)
    {
        final User member = getMember();
        final OutputStream outputStream = getOutputStream();
        final UserXMLWriter memberXMLWriter =
                createMemberXMLWriter(outputStream, member);

        try
        {
            memberXMLWriter.write();

            final Node node = adoptNode(outputStream, document);
            document.appendChild(node);
        }
        catch (WriterException we)
        {
            // Do nothing for now...
        }
    }

    /**
     * Obtain an OutputStream to write to.  This can be overridden.
     * @return      An OutputStream instance.
     */
    protected OutputStream getOutputStream()
    {
        return new ByteArrayOutputStream(256);
    }

    /**
     * Adopt a new Node based on the given Stream of data and Document.
     *
     * @param outputStream      The OutputStream to be written to
     * @param document          The Document to import the node to.
     * @return                  The newly created Node.
     */
    protected Node adoptNode(final OutputStream outputStream,
                             final Document document)
    {
        final String writtenData = outputStream.toString();
        return document.importNode(
                parseDocument(writtenData).getDocumentElement(), true);
    }

    /**
     * Parse a Document from the given String.
     *
     * @param writtenData   The String data.
     * @return          The Document object.
     */
    protected Document parseDocument(final String writtenData)
    {
        final DOMParser parser = new DOMParser();

        try
        {
            parser.parse(new InputSource(new StringReader(writtenData)));
            return parser.getDocument();
        }
        catch (IOException e)
        {
            final String message = "Unable to parse document.";
            LOGGER.error(message, e);
            throw new WebRepresentationException(message, e);
        }
        catch (SAXException e)
        {
            final String message = "Unable to parse document.";
            LOGGER.error(message, e);
            throw new WebRepresentationException(message, e);
        }
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
