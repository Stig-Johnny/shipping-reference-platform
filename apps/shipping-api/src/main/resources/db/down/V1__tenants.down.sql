-- Reverts db/migration/V1__tenants.sql (ADR 0016): drops what it created, in reverse order, without cascading, so that
-- it fails if anything else still depends on the schema. Development and CI only: it deletes every tenant, user,
-- membership, demo token and flag. Run it as shipping_owner in one transaction that also deletes version 1 from
-- flyway.flyway_schema_history, and only when version 1 is the latest applied migration.

drop function tenants.resolve_demo_token(bytea);
drop table tenants.prototype_flag;
drop table tenants.demo_token;
drop table tenants.membership;
drop table tenants.app_user;
drop table tenants.tenant;
drop schema tenants;
