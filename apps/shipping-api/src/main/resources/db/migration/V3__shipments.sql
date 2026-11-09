-- V3: booked shipments (docs/modules/shipments.md, ADR 0012), applied by shipping_owner.
-- Never edit after merge. Cancellation columns are reserved; the application currently books only.
-- No cross-schema foreign keys: QuoteOffers validates the quote through the module API.
create schema shipments;

create sequence shipments.tracking_number_seq minvalue 1 maxvalue 9999999999 no cycle;

create table shipments.shipment (
    tenant_id uuid not null,
    shipment_id uuid not null,
    quote_id uuid not null,
    offer_id uuid not null,
    status text not null,
    service_level text not null,
    amount numeric(11, 2) not null,
    currency char(3) not null,
    tracking_number char(13) not null,
    origin_country char(2) not null,
    origin_postcode varchar(10) not null,
    recipient_name varchar(70) not null,
    recipient_address_line1 varchar(70) not null,
    recipient_address_line2 varchar(70),
    recipient_postcode varchar(10) not null,
    recipient_city varchar(50) not null,
    recipient_country char(2) not null,
    reference varchar(35),
    idempotency_key varchar(64) not null,
    request_fingerprint char(64) not null,
    booked_at timestamptz not null,
    cancelled_at timestamptz,
    version integer not null default 0,
    constraint shipment_pkey primary key (tenant_id, shipment_id),
    constraint shipment_idempotency_key unique (tenant_id, idempotency_key),
    constraint shipment_quote_key unique (tenant_id, quote_id),
    constraint shipment_tracking_key unique (tenant_id, tracking_number),
    constraint shipment_status_check check (status in ('BOOKED', 'CANCELLED')),
    constraint shipment_cancellation_check check ((status = 'CANCELLED') = (cancelled_at is not null)),
    constraint shipment_version_check check (version >= 0),
    constraint shipment_service_level_check check (service_level in ('ECONOMY', 'STANDARD', 'EXPRESS')),
    constraint shipment_amount_check check (amount >= 0),
    constraint shipment_currency_check check (currency = 'EUR'),
    constraint shipment_tracking_check check (tracking_number ~ '^SIM[0-9]{10}$'),
    constraint shipment_country_check check (origin_country ~ '^[A-Z]{2}$' and recipient_country ~ '^[A-Z]{2}$'),
    constraint shipment_postcode_check check (origin_postcode ~ '^[A-Z0-9][A-Z0-9 -]{0,8}[A-Z0-9]$'
        and recipient_postcode ~ '^[A-Z0-9][A-Z0-9 -]{0,8}[A-Z0-9]$'),
    constraint shipment_recipient_check check (length(recipient_name) >= 1 and length(recipient_address_line1) >= 1
        and (recipient_address_line2 is null or length(recipient_address_line2) >= 1) and length(recipient_city) >= 1),
    constraint shipment_reference_check check (reference is null or reference ~ '^[A-Za-z0-9 ._/-]{1,35}$'),
    constraint shipment_idempotency_check check (idempotency_key ~ '^[A-Za-z0-9_-]{16,64}$'),
    constraint shipment_fingerprint_check check (request_fingerprint ~ '^[0-9a-f]{64}$'),
    constraint shipment_booked_at_check check (booked_at = date_trunc('milliseconds', booked_at))
);
create index shipment_listing on shipments.shipment (tenant_id, booked_at desc, shipment_id desc);

alter table shipments.shipment enable row level security;
alter table shipments.shipment force row level security;
create policy tenant_isolation on shipments.shipment
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

grant usage on schema shipments to shipping_app;
grant select, insert, update on shipments.shipment to shipping_app;
grant usage on sequence shipments.tracking_number_seq to shipping_app;
