
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

   owner             VARCHAR(256)      NOT NULL,
   groupRead         VARCHAR(256)      NULL,
   groupWrite        VARCHAR(256)      NULL,
   isPublic          BIT               NOT NULL,
-- NodeDAO stores length for both data and container nodes (aggregate for the latter)
   contentLength     BIGINT            NOT NULL,
   contentType       VARCHAR(100)      NULL,
   contentEncoding   VARCHAR(50)       NULL,
   contentMD5        BINARY(16)        NULL,
-- internal column not referenced in NodeDAO
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

print "create table DeletedNode ..."
go
create table DeletedNode
(
    nodeID        BIGINT     NOT NULL,
    lastModified  DATETIME   NOT NULL
)
lock datarows
go

alter table DeletedNode add
constraint Node_pk primary key nonclustered (nodeID)

CREATE TRIGGER Node_delete_trig
    ON Node
    FOR DELETE
    AS
    BEGIN
        INSERT INTO DeletedNode (nodeID,lastModified)
        (SELECT nodeID,getdate() FROM deleted)

    END
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

create clustered index NodeProperty_nodeID_index on NodeProperty(nodeID)
go

alter table NodeProperty add constraint NodeProperty_pk
primary key nonclustered (nodePropertyID)
go

alter table NodeProperty add
constraint NodeProperty_nodePropertyID_fk foreign key (nodeID)
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