-- V1: schema tenants (docs/modules/tenants.md, ADR 0005). Flyway applies it as shipping_owner. Never edit it once it
-- is merged; change the schema with a new migration. db/down/V1__tenants.down.sql reverts it in development and CI
-- only (ADR 0016).
--
-- The four identity tables are read before a tenant is known, so they have no row-level security. shipping_app has no
-- privileges on them and reads them only through tenants.resolve_demo_token. prototype_flag is tenant-owned: its key
-- starts with tenant_id, and forced row-level security limits every role but a superuser to the bound tenant's rows.

create schema tenants;

create table tenants.tenant (
    tenant_id uuid not null,
    display_name varchar(70) not null,
    constraint tenant_pkey primary key (tenant_id),
    constraint tenant_display_name_check check (display_name <> '')
);

create table tenants.app_user (
    user_id uuid not null,
    handle varchar(40) not null,
    display_name varchar(70) not null,
    constraint app_user_pkey primary key (user_id),
    constraint app_user_handle_key unique (handle),
    constraint app_user_handle_check check (handle ~ '^[a-z0-9-]{3,40}$'),
    constraint app_user_display_name_check check (display_name <> '')
);

-- One membership per user in v0.1.
create table tenants.membership (
    user_id uuid not null,
    tenant_id uuid not null,
    role text not null,
    constraint membership_pkey primary key (user_id),
    constraint membership_user_fkey foreign key (user_id) references tenants.app_user (user_id),
    constraint membership_tenant_fkey foreign key (tenant_id) references tenants.tenant (tenant_id),
    constraint membership_role_check check (role = 'CLERK')
);

-- Only the SHA-256 of a demo token is stored.
create table tenants.demo_token (
    token_sha256 bytea not null,
    user_id uuid not null,
    constraint demo_token_pkey primary key (token_sha256),
    constraint demo_token_user_fkey foreign key (user_id) references tenants.app_user (user_id),
    constraint demo_token_sha256_check check (octet_length(token_sha256) = 32)
);

create table tenants.prototype_flag (
    tenant_id uuid not null,
    flag_key varchar(40) not null,
    enabled boolean not null default false,
    constraint prototype_flag_pkey primary key (tenant_id, flag_key),
    constraint prototype_flag_tenant_fkey foreign key (tenant_id) references tenants.tenant (tenant_id)
);

alter table tenants.prototype_flag enable row level security;
alter table tenants.prototype_flag force row level security;
create policy tenant_isolation on tenants.prototype_flag
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

-- Zero rows for an unknown token; one row with null tenant columns for a user without a membership.
create function tenants.resolve_demo_token(p_token_sha256 bytea)
    returns table (user_id uuid, user_display_name varchar, tenant_id uuid, tenant_display_name varchar, role text)
    language sql
    stable
    security definer
    set search_path = pg_catalog, tenants
as $$
    select u.user_id, u.display_name, m.tenant_id, t.display_name, m.role
    from tenants.demo_token d
    join tenants.app_user u on u.user_id = d.user_id
    left join tenants.membership m on m.user_id = u.user_id
    left join tenants.tenant t on t.tenant_id = m.tenant_id
    where d.token_sha256 = p_token_sha256
$$;

revoke all on function tenants.resolve_demo_token(bytea) from public;

grant usage on schema tenants to shipping_app;
grant execute on function tenants.resolve_demo_token(bytea) to shipping_app;
grant select on tenants.prototype_flag to shipping_app;
