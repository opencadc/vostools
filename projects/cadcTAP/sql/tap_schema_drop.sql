
-- drop all TAP_SCHEMA tables in the correct order to not violate
-- foreign key constraints

drop table TAP_SCHEMA.key_columns;
drop table TAP_SCHEMA.keys;
drop table TAP_SCHEMA.columns;
drop table TAP_SCHEMA.tables;
drop table TAP_SCHEMA.schemas;
