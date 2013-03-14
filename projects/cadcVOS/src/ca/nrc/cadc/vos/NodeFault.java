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

package ca.nrc.cadc.vos;

import org.restlet.data.Status;

/**
 * Enumeration of type types of faults that can occur
 * with node processing.
 * 
 * @author majorb
 *
 */
public enum NodeFault
{
    // IVOA Standard Faults
    InternalFault
    ( 
        new Status(500, 
                   "InternalFault", 
                   "A HTTP 500 status code with an InternalFault fault in the body is thrown if the operation fails", 
                   "http://www.ivoa.net/Documents/latest/VOSpace.html")
    ),
    PermissionDenied
    (
        new Status(401,
                   "PermissionDenied",
                   "A HTTP 401 status code with a PermissionDenied fault in the body is thrown if the user does not have permission to perform the operation",
                   "http://www.ivoa.net/Documents/latest/VOSpace.html")
    ),
    InvalidURI
    (
        new Status(400, 
                   "InvalidURI", 
                   "A HTTP 400 status code with an InvalidURI fault in the body is thrown if the specified URI is invalid", 
                   "http://www.ivoa.net/Documents/latest/VOSpace.html")
    ),
    NodeNotFound
    (
        new Status(404,
                   "NodeNotFound",
                   "A HTTP 404 status code with a NodeNotFound fault in the body is thrown if the specified node does not exist",
                   "http://www.ivoa.net/Documents/latest/VOSpace.html")
    ),
    DuplicateNode
    ( 
        new Status(409,
                   "DuplicateNode",
                   "A HTTP 409 status code with a DuplicateFault fault in the body is thrown if the specified node already exists",
                   "http://www.ivoa.net/Documents/latest/VOSpace.html")
    ),
    InvalidToken
    (
        new Status(400,
                   "InvalidToken",
                   "A HTTP 400 status code with a InvalidToken fault in the body is thrown if ?????",
                   "http://www.ivoa.net/Documents/latest/VOSpace.html")
    ),
    InvalidArgument
    (
        new Status(400,
                   "InvalidArgument",
                   "A HTTP 400 status code with a InvalidArgument fault in the body is thrown if a specified value is invalid",
                   "http://www.ivoa.net/Documents/latest/VOSpace.html")
    ),
    TypeNotSupported
    (
        new Status(400,
                   "TypeNotSupported",
                   "A HTTP 400 status code with a TypeNotSupported fault in the body is thrown if the type specified in xsi:type is not supported",
                   "http://www.ivoa.net/Documents/latest/VOSpace.html")
    ),
    ContainerNotFound
    ( 
        new Status(404,
                   "ContainerNotFound",
                   "A HTTP 500 status code with a ContainerNotFound fault in the body is thrown if a container is not found",
                   "http://www.ivoa.net/Documents/latest/VOSpace.html")
    ),
    
    // Other Faults
    RequestEntityTooLarge
    (
        new Status(413,
                   "InvalidArgument",
                   "A HTTP 413 status code with a InvalidArgument fault in the body is thrown if the XML document on the input stream is too large.",
                   "http://www.ivoa.net/Documents/latest/VOSpace.html")
    ),
    UnreadableLinkTarget
    (
        new Status(404,
                   "NodeNotFound",
                   "A HTTP 404 status code with a NodeNotFound fault in the body is thrown if the target of a link node could not be resolved by this service.",
                   "http://www.ivoa.net/Documents/latest/VOSpace.html")
    ),
    ServiceBusy
    (
        new Status(503,
                   "ServiceBusy",
                   "A HTTP 503 status code with a NodeNotFound fault in the body is thrown if the service is too busy to handle the request.",
                   "http://www.ivoa.net/Documents/latest/VOSpace.html")
    ),
    NodeLocked
    (
        new Status(423,
                   "NodeLocked",
                   "A HTTP 423 status code with a NodeLocked fault in the body is thrown if the requested node is locked for writing or deleting.",
                   "http://www.ivoa.net/Documents/latest/VOSpace.html")
    );
//    NotSupported ( Status.SERVER_ERROR_NOT_IMPLEMENTED ),
//    BadRequest ( Status.CLIENT_ERROR_BAD_REQUEST ),
//    NodeBusy ( Status.CLIENT_ERROR_CONFLICT );

    private Status status;
    private String message;
    private boolean serviceFailure;
    
    private NodeFault(Status status)
    {
        this.status = status;
        this.serviceFailure = false;
    }

    public Status getStatus()
    {
        return status;
    }
    
    public String toString()
    {
        return name();
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }
    
    public boolean isServiceFailure()
    {
        return serviceFailure;
    }
    
    public void setServiceFailure(boolean serviceFailure)
    {
        this.serviceFailure = serviceFailure;
    }

}
