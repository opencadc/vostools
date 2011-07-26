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


package ca.nrc.cadc.uws.web.restlet;

import org.restlet.service.StatusService;
import org.restlet.data.Status;
import org.restlet.data.MediaType;
import org.restlet.Request;
import org.restlet.Response;
import org.apache.log4j.Logger;
import ca.nrc.cadc.uws.server.JobNotFoundException;
import ca.nrc.cadc.uws.server.JobPersistenceException;
import ca.nrc.cadc.uws.server.JobPhaseException;
import java.security.AccessControlException;
import org.restlet.resource.ResourceException;


/**
 * Status service to handle errors more appropriately.
 */
public class UWSStatusService extends StatusService
{
    private static final Logger LOGGER =
            Logger.getLogger(UWSStatusService.class);


    /**
     * Constructor.
     *
     * @param enabled True if the service has been enabled.
     */
    public UWSStatusService(final boolean enabled)
    {
        super(enabled);
    }


    /**
     * Returns a status for a given exception or error. By default it unwraps
     * the status of {@link org.restlet.resource.ResourceException}. For other
     * exceptions or errors, it returns an
     * {@link org.restlet.data.Status#SERVER_ERROR_INTERNAL} status and logs a
     * severe message.<br>
     * <br>
     * In order to customize the default behavior, this method can be
     * overridden.
     *
     * @param throwable The exception or error caught.
     * @param request   The request handled.
     * @param response  The response updated.
     * @return The representation of the given status.
     */
    @Override
    public Status getStatus(Throwable throwable, Request request, Response response)
    {
        // unwrap checked exceptions from UWS library
        if (RuntimeException.class.equals(throwable.getClass())
                && throwable.getCause() != null)
            throwable = throwable.getCause();

        response.setEntity("Unable to complete your request: "
                           + (throwable.getCause() == null
                              ? throwable : throwable.getCause()).getMessage()
                           + "\n",
                           MediaType.TEXT_PLAIN);

        if (throwable instanceof JobNotFoundException)
        {
            return Status.CLIENT_ERROR_NOT_FOUND;
        }
        else if (throwable instanceof JobPersistenceException)
        {
            return Status.SERVER_ERROR_INTERNAL;
        }
        else if (throwable instanceof JobPhaseException)
        {
            return Status.CLIENT_ERROR_BAD_REQUEST;
        }
        else if (throwable instanceof AccessControlException)
        {
            return new Status(Status.CLIENT_ERROR_FORBIDDEN, throwable.getMessage());
        }
        else if (throwable instanceof InvalidResourceException ||
                throwable.getCause() instanceof InvalidResourceException)
        {
            return Status.CLIENT_ERROR_NOT_FOUND;
        }
        else if (throwable instanceof InvalidActionException ||
                throwable.getCause() instanceof InvalidActionException)
        {
            return Status.CLIENT_ERROR_METHOD_NOT_ALLOWED;
        }
        else
        {
            LOGGER.error("unexpected Throwable", throwable);
            return super.getStatus(throwable, request, response);
        }
    }
}
