--
--***********************************************************************
--******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
--*************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
--
--  (c) 2010.                            (c) 2010.
--  Government of Canada                 Gouvernement du Canada
--  National Research Council            Conseil national de recherches
--  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
--  All rights reserved                  Tous droits réservés
--
--  NRC disclaims any warranties,        Le CNRC dénie toute garantie
--  expressed, implied, or               énoncée, implicite ou légale,
--  statutory, of any kind with          de quelque nature que ce
--  respect to the software,             soit, concernant le logiciel,
--  including without limitation         y compris sans restriction
--  any warranty of merchantability      toute garantie de valeur
--  or fitness for a particular          marchande ou de pertinence
--  purpose. NRC shall not be            pour un usage particulier.
--  liable in any event for any          Le CNRC ne pourra en aucun cas
--  damages, whether direct or           être tenu responsable de tout
--  indirect, special or general,        dommage, direct ou indirect,
--  consequential or incidental,         particulier ou général,
--  arising from the use of the          accessoire ou fortuit, résultant
--  software.  Neither the name          de l'utilisation du logiciel. Ni
--  of the National Research             le nom du Conseil National de
--  Council of Canada nor the            Recherches du Canada ni les noms
--  names of its contributors may        de ses  participants ne peuvent
--  be used to endorse or promote        être utilisés pour approuver ou
--  products derived from this           promouvoir les produits dérivés
--  software without specific prior      de ce logiciel sans autorisation
--  written permission.                  préalable et particulière
--                                       par écrit.
--
--  This file is part of the             Ce fichier fait partie du projet
--  OpenCADC project.                    OpenCADC.
--
--  OpenCADC is free software:           OpenCADC est un logiciel libre ;
--  you can redistribute it and/or       vous pouvez le redistribuer ou le
--  modify it under the terms of         modifier suivant les termes de
--  the GNU Affero General Public        la “GNU Affero General Public
--  License as published by the          License” telle que publiée
--  Free Software Foundation,            par la Free Software Foundation
--  either version 3 of the              : soit la version 3 de cette
--  License, or (at your option)         licence, soit (à votre gré)
--  any later version.                   toute version ultérieure.
--
--  OpenCADC is distributed in the       OpenCADC est distribué
--  hope that it will be useful,         dans l’espoir qu’il vous
--  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
--  without even the implied             GARANTIE : sans même la garantie
--  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
--  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
--  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
--  General Public License for           Générale Publique GNU Affero
--  more details.                        pour plus de détails.
--
--  You should have received             Vous devriez avoir reçu une
--  a copy of the GNU Affero             copie de la Licence Générale
--  General Public License along         Publique GNU Affero avec
--  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
--  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
--                                       <http://www.gnu.org/licenses/>.
--
--  $Revision: 4 $
--
--***********************************************************************
--

-- sample tables for use with NodeDAO in Sybase ASE

print "create table Node ..."
go

CREATE TABLE Node (
   nodeID            BIGINT            IDENTITY,
   parentID          BIGINT            NULL,
-- max node name length==256, + 20 for timestamp in nodes marked for delete
   name              VARCHAR(276)      NOT NULL,
   type              CHAR(1)           NOT NULL,
   busyState         CHAR(1)           NOT NULL,

-- this is only used if using the NodeDAO.markForDeletion but needs to be here
   markedForDeletion BIT               NOT NULL,

   ownerID           VARCHAR(256)      NOT NULL,
   creatorID         VARCHAR(256)      NOT NULL,
   
   groupRead         VARCHAR(256)      NULL,
   groupWrite        VARCHAR(256)      NULL,
   isPublic          BIT               NOT NULL,
-- NodeDAO stores length for both data and container nodes (aggregate for the latter)
   contentLength     BIGINT            NOT NULL,
   contentType       VARCHAR(100)      NULL,
   contentEncoding   VARCHAR(50)       NULL,
   contentMD5        BINARY(16)        NULL,
-- createdOn: internal column not referenced in NodeDAO
   createdOn         DATETIME          DEFAULT getDate(),
   lastModified      DATETIME          NOT NULL
)
lock datarows
with identity_gap = 512
go

create unique clustered index Node_parentID_name_index on Node(parentID, name)
go

alter table Node add
constraint Node_pk primary key nonclustered (nodeID)
go

print "create table NodeProperty ..."
go

CREATE TABLE NodeProperty
(
-- nodePropertyID is used by sybase replication and update trigger below
-- it is not used by any cadcVOS software at this time
   nodePropertyID    BIGINT            IDENTITY,
   nodeID            BIGINT            NOT NULL,
   propertyURI       VARCHAR(256)      NOT NULL,
   propertyValue     VARCHAR(512)      NULL,
-- this is something we add to tables to make it easier to see when things change
-- see also the update trigger below
   lastModified      DATETIME          DEFAULT getDate()
)
lock datarows
with identity_gap = 512
go

create clustered index NodeProperty_nodeID_index on NodeProperty(nodeID,propertyURI)
go

alter table NodeProperty add constraint NodeProperty_pk
primary key nonclustered (nodePropertyID)
go

alter table NodeProperty add
constraint NodeProperty_fk foreign key (nodeID)
references Node(nodeID)
go

CREATE TRIGGER NodeProperty_update_trigger
    ON NodeProperty
    FOR UPDATE
    AS
    BEGIN
        UPDATE NodeProperty SET lastModified=getDate()
        FROM inserted i
        WHERE i.nodePropertyID = NodeProperty.nodePropertyID
    END
go

-- TODO: grant permissions to read and write to these tables
