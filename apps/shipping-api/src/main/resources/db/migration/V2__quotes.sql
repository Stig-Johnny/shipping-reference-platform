-- V2: schema quotes (docs/modules/quotes.md, ADR 0005). Flyway applies it as shipping_owner. Never edit it once it is
-- merged; change the schema with a new migration. db/down/V2__quotes.down.sql reverts it in development and CI only
-- (ADR 0012).
--
-- Quotes never change after they are issued, so shipping_app may read and insert them, nothing else. Both tables are
-- tenant-owned: their keys start with tenant_id, the offer's foreign key includes it, so that an offer can only belong
-- to a quote of its own tenant, and forced row-level security limits every role but a superuser to the bound tenant's
-- rows. No foreign key leaves the schema. The checks hold the bounds of contracts/shipping.openapi.yaml.

create schema quotes;

create table quotes.quote (
    tenant_id uuid not null,
    quote_id uuid not null,
    issued_at timestamptz not null,
    expires_at timestamptz not null,
    weight_grams integer not null,
    length_mm integer not null,
    width_mm integer not null,
    height_mm integer not null,
    billable_weight_grams integer not null,
    zone text not null,
    origin_country char(2) not null,
    origin_postcode varchar(10) not null,
    destination_country char(2) not null,
    destination_postcode varchar(10) not null,
    constraint quote_pkey primary key (tenant_id, quote_id),
    constraint quote_expiry_check check (expires_at = issued_at + interval '30 minutes'),
    constraint quote_parcel_check check (weight_grams between 1 and 30000 and length_mm between 1 and 1200
        and width_mm between 1 and 1200 and height_mm between 1 and 1200),
    -- billable weight = max(weight, ceil(length x width x height / 5000)), never above 30000 g in a stored quote
    constraint quote_billable_weight_check check (billable_weight_grams between 1 and 30000
        and billable_weight_grams = greatest(weight_grams, ceil((length_mm::bigint * width_mm * height_mm) / 5000.0))),
    constraint quote_zone_check check (zone = case when origin_country = destination_country then 'DOMESTIC'
        else 'INTERNATIONAL' end),
    constraint quote_country_check check (origin_country ~ '^[A-Z]{2}$' and destination_country ~ '^[A-Z]{2}$'),
    constraint quote_postcode_check check (origin_postcode ~ '^[A-Z0-9][A-Z0-9 -]{0,8}[A-Z0-9]$'
        and destination_postcode ~ '^[A-Z0-9][A-Z0-9 -]{0,8}[A-Z0-9]$')
);

create table quotes.quote_offer (
    tenant_id uuid not null,
    quote_id uuid not null,
    offer_id uuid not null,
    service_level text not null,
    amount numeric(11, 2) not null,
    currency char(3) not null,
    transit_min_days smallint not null,
    transit_max_days smallint not null,
    constraint quote_offer_pkey primary key (tenant_id, offer_id),
    constraint quote_offer_service_level_key unique (tenant_id, quote_id, service_level),
    constraint quote_offer_quote_fkey foreign key (tenant_id, quote_id) references quotes.quote (tenant_id, quote_id),
    constraint quote_offer_service_level_check check (service_level in ('ECONOMY', 'STANDARD', 'EXPRESS')),
    constraint quote_offer_amount_check check (amount >= 0),
    constraint quote_offer_currency_check check (currency = 'EUR'),
    constraint quote_offer_transit_days_check check (transit_min_days between 1 and 30
        and transit_max_days between transit_min_days and 30)
);

alter table quotes.quote enable row level security;
alter table quotes.quote force row level security;
create policy tenant_isolation on quotes.quote
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

alter table quotes.quote_offer enable row level security;
alter table quotes.quote_offer force row level security;
create policy tenant_isolation on quotes.quote_offer
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

grant usage on schema quotes to shipping_app;
grant select, insert on quotes.quote, quotes.quote_offer to shipping_app;
