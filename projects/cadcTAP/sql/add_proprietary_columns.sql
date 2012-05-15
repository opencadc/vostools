
-- special set of alter table statements to run if your existing tap_schema
-- does not have the proprietary extension hook and you need it for the latest
-- code in ca.nrc.acdc.tap.schema (if TapSchemaDAO.ASSET_ID_COLUMNS is there,
-- you need these columns but do not nede to populate them)

alter table TAP_SCHEMA.schemas add column schemaID bigint;
alter table TAP_SCHEMA.tables add column tableID bigint;
alter table TAP_SCHEMA.columns add column columnID bigint;
alter table TAP_SCHEMA.keys add column keyID bigint;
alter table TAP_SCHEMA.key_columns add column key_columnID bigint;

