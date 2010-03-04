
-- table names: currently tables are names for classes and are in the uws schema, but
-- these can be changed since the names of table used at runtime
-- are returned from the abstract methods of ca.nrc.cadc.uws.JobDAO

-- best indices: indices on jobID in each table
-- probably good to CLUSTER the uws.Parameter and uws.Result tables

-- implementors must decide on the size of varchar columns and decide on using one
-- or two tables to store parameters; here we only specify the zie of jobID column
-- sicne the ID generator code is fixed at 16 characters and our understaing is that
-- PostgtreSQL does not enforce any limit that one might actually reach

create table uws.Job
(
    jobID                   varchar(16)     not null,
    executionPhase          varchar         not null,
    executionDuration       bigint          not null,
    destructionTime         timestamp,
    quote                   timestamp,
    startTime               timestamp,
    endTime                 timestamp,
    error_summaryMessage    varchar,
    error_documentURL       varchar,
    owner                   varchar,
    runID                   varchar,
    requestPath             varchar,
    deletedByUser           integer         default 0,

     primary key (jobID) using index tablespace caom_index
-- can append this to previous line: using index tablespace <name of tablespace>
);

create table uws.Parameter
(
    jobID                   varchar(16)     not null,
    name                    varchar     not null,
    value                   varchar,

    foreign key (jobID) references uws.Job (jobID)
);

create index uws_param_i1 on uws.Parameter(jobID);

create table uws.Result
(
    jobID                   varchar(16)     not null,
    name                    varchar         not null,
    url                     varchar,

    foreign key (jobID) references uws.Job (jobID)
);

create index uws_result_i1 on uws.Result(jobID);

-- GRANT SELECT: <public>

-- GRANT INSERT, UPDATE, DELETE: <read-write account>

