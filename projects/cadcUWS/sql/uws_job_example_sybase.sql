
-- table names: these can be changed since the names of table used at runtime
-- are returned from the abstract methods of ca.nrc.cadc.uws.JobDAO

-- best indices: clustered indices on jobID in each table

-- implementors must decide on the size of varchar columns and decide on using one
-- or two tables to store parameters; for services with params that have arbitrary
-- length we would use the uws_parameter_long table (column type text)

create table uws_job
(
    jobID                   varchar(16)     not null,
    executionPhase          varchar(16)     not null,
    executionDuration       bigint          not null,
    destructionTime         datetime        null,
    quote                   datetime        null,
    startTime               datetime        null,
    endTime                 datetime        null,
    error_summaryMessage    varchar(128)    null,
    error_documentURL       varchar(512)    null,
    owner                   varchar(512)    null,
    runID                   varchar(16)     null,
    requestPath             varchar(32)     null,
    deletedByUser           tinyint         default 0

--    primary key (jobID)
);

alter table uws_job add
constraint uws_job_pk primary key clustered (jobID);


create table uws_parameter_short
(
    jobID                   varchar(16)     not null,
    name                    varchar(64)     not null,
    value                   varchar(256)    null,

    foreign key (jobID) references uws_job (jobID)
);

create clustered index uws_param_short_i1 on uws_parameter_short(jobID);


create table uws_parameter_long
(
    jobID                   varchar(16)     not null,
    name                    varchar(64)     not null,
    value                   text            null,

    foreign key (jobID) references uws_job (jobID)
);

create clustered index uws_param_long_i1 on uws_parameter_long(jobID);

create table uws_result
(
    jobID                   varchar(16)     not null,
    name                    varchar(32)     not null,
    url                     varchar(256)    null,
    primary                 tinyint         default 0

    foreign key (jobID) references uws_job (jobID)
);

create clustered index uws_result_i1 on uws_result(jobID);

-- GRANT SELECT: public

-- GRANT INSERT, UPDATE, DELETE: account that JobDAO uses, probably via JNDI

