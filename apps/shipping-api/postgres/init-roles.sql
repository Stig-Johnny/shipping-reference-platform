-- The two database roles of shipping-reference-platform and their rights on one database (ADR 0005,
-- docs/modules/tenants.md).
--
-- A superuser runs this script outside Flyway, connected to the database, before its first migration: the database
-- tests run it through JDBC. It is never mounted as a container initialisation script. Running it again is safe. It
-- sets no passwords; whoever runs it sets them afterwards (the tests use new random ones on every run).
--
--   shipping_owner  owns the schemas, tables and functions, and only Flyway connects as it. Forced row-level security
--                   applies to its queries on tenant tables as to every other role's.
--   shipping_app    the runtime role: owns nothing, is a member of no role, and holds only the grants the migrations
--                   give it.
--
-- Neither role is a superuser, creates databases or roles, replicates, bypasses row-level security or inherits the
-- privileges of another role.

do $$
begin
    if not exists (select from pg_catalog.pg_roles where rolname = 'shipping_owner') then
        create role shipping_owner;
    end if;
    if not exists (select from pg_catalog.pg_roles where rolname = 'shipping_app') then
        create role shipping_app;
    end if;
end
$$;

alter role shipping_owner with login nosuperuser nocreatedb nocreaterole noinherit noreplication nobypassrls;
alter role shipping_app with login nosuperuser nocreatedb nocreaterole noinherit noreplication nobypassrls;

-- Only the two roles may connect, only the owner may create schemas, and no role may create temporary tables: PUBLIC
-- holds CONNECT and TEMPORARY on a new database until they are revoked here.
do $$
begin
    execute format('revoke all on database %I from public', current_database());
    execute format('grant connect, create on database %I to shipping_owner', current_database());
    execute format('grant connect on database %I to shipping_app', current_database());
end
$$;
