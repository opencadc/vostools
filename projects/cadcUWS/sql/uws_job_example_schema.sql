
create table uws_job
(
    jobId                   varchar(16)     not null,
    executionPhase          varchar(16)     not null,
    executionDuration       bigint          not null,
    destructionTime         datetime        null,
    quote                   datetime        null,
    startTime               datetime        null,
    endTime                 datetime        null,
    error_summaryMessage    varchar(128)    null,
    error_documentURL       varchar(512)    null,
    owner                   varchar(512)    null,
    runId                   varchar(16)     null,
    requestPath             varchar(32)     null,
    deletedByUser           tinyint         default 0

	primary key (jobId)
);


create table uws_parameter_short
(
	jobId                   varchar(16)     not null,
	name                    varchar(64)     not null,
	value                   varchar(256)    null,

	foreign key (jobId) references uws_job (jobId)
);


create table uws_parameter_long
(
	jobId                   varchar(16)     not null,
	name                    varchar(64)     not null,
	value                   text            null,

    foreign key (jobId) references uws_job (jobId)
);

create table uws_result
(
	jobId                   varchar(16)     not null,
	name                    varchar(32)     not null,
	url                     varchar(256)    null,

	foreign key (jobId) references uws_job (jobId)
);
