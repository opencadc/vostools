 ----
 -----------------------------------------------------------------------------
 ------  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  --------
 --
 -- (c) 2009.                            (c) 2009.
 -- National Research Council            Conseil national de recherches
 -- Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 -- All rights reserved                  Tous droits reserves
 --
 -- NRC disclaims any warranties         Le CNRC denie toute garantie
 -- expressed, implied, or statu-        enoncee, implicite ou legale,
 -- tory, of any kind with respect       de quelque nature que se soit,
 -- to the software, including           concernant le logiciel, y com-
 -- without limitation any war-          pris sans restriction toute
 -- ranty of merchantability or          garantie de valeur marchande
 -- fitness for a particular pur-        ou de pertinence pour un usage
 -- pose.  NRC shall not be liable       particulier.  Le CNRC ne
 -- in any event for any damages,        pourra en aucun cas etre tenu
 -- whether direct or indirect,          responsable de tout dommage,
 -- special or general, consequen-       direct ou indirect, particul-
 -- tial or incidental, arising          ier ou general, accessoire ou
 -- from the use of the software.        fortuit, resultant de l'utili-
 --                                      sation du logiciel.
 --
 --
 -- @author jenkinsd
 -- Jul 14, 2009 - 10:07:37 AM
 --
 --
 --
 ------  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  --------
 -----------------------------------------------------------------------------
 --


create table jobs
(
    job_id                    bigint      identity,
    execution_phase           varchar(10) not null,
    execution_duration_sec    bigint      not null,
    destruction_time          datetime    not null,
    quote                     bigint      not null,
    start_time                datetime    null,
    end_time                  datetime    null,
    run_id                    varchar(32) null,
    owner                     varchar(32) null
)
lock datarows
go
 
alter table jobs add constraint jobs_pk primary key (job_id)
go

grant all on jobs to public
go
