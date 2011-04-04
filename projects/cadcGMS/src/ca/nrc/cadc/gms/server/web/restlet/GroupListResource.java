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
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Put;

import ca.nrc.cadc.gms.AuthorizationException;
import ca.nrc.cadc.gms.GmsConsts;
import ca.nrc.cadc.gms.Group;
import ca.nrc.cadc.gms.GroupReader;
import ca.nrc.cadc.gms.GroupWriter;
import ca.nrc.cadc.gms.InvalidGroupException;
import ca.nrc.cadc.gms.ReaderException;
import ca.nrc.cadc.gms.server.GroupService;


public class GroupListResource extends AbstractResource
{
    @SuppressWarnings("unused")
    private final static Logger logger = Logger.getLogger(GroupListResource.class);

    private GroupService groupService;

    private Collection<Group> groups;

    /**
     * Hidden constructor for JavaBean tools.
     */
    GroupListResource()
    {
        super();
    }

    /**
     * Full constructor.
     * 
     * @param groupService
     *            The GroupService instance.
     */
    public GroupListResource(final GroupService groupService)
    {
        this.groupService = groupService;
    }

    /**
     * Get a reference to the resource identified by the user.
     * 
     * @throws FileNotFoundException
     *             If the resource doesn't exist.
     * @throws InvalidGroupException 
     * @throws AuthorizationException 
     */
    @Override
    protected boolean obtainResource() throws FileNotFoundException,
                                              InvalidGroupException,
                                              AuthorizationException
    {
        // Get the criteria from http query
        Form queryForm = getForm();
        String dn = queryForm.getFirstValue(GmsConsts.PROPERTY_OWNER_DN);
        logger.info("DN=" + dn);
        
        Map<String, String> criteria = new HashMap<String, String>();
        criteria.put(GmsConsts.PROPERTY_OWNER_DN, dn);

        groups = getGroupService().getGroups(criteria);
        return true;
    }

    /**
     * Accept a PUT request to create a new Group.
     *
     * @param  payload      The Request body to PUT.
     */
    @Put
    public void store(final Representation payload)
    {
        try
        {
            final Group group = GroupReader.read(payload.getStream());
            final Group createdGroup = getGroupService().putGroup(group);

            getResponse().setStatus(Status.SUCCESS_CREATED);
            getResponse().setLocationRef(createdGroup.getID().toString());
        }
        catch (ReaderException e)
        {
            processError(e, Status.CLIENT_ERROR_UNPROCESSABLE_ENTITY,
                         "Unable to build Group from input.");
        }
        catch (AuthorizationException e)
        {
            processError(e, Status.CLIENT_ERROR_FORBIDDEN,
                         "Not Authorized to Create Groups.");
        }
        catch (IOException e)
        {
            processError(e, Status.CLIENT_ERROR_UNPROCESSABLE_ENTITY,
                         "Unable to build Group from input: " + e.getMessage());
        }
        catch (InvalidGroupException e)
        {
            processError(e, Status.CLIENT_ERROR_BAD_REQUEST,
                         "Unable to build Group from input: " + e.getMessage());            
        }
        catch (URISyntaxException e)
        {
            processError(e, Status.CLIENT_ERROR_UNPROCESSABLE_ENTITY,
                         "Unable to build Group from input: " + e.getMessage());
        }
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
        Element eleGroups = GroupWriter.getGroupsElement(groups);
        document.addContent(eleGroups);
    }

    private GroupService getGroupService()
    {
        return groupService;
    }

    public void setGroupService(final GroupService groupService)
    {
        this.groupService = groupService;
    }

}
