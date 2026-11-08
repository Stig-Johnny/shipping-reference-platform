-- Reverts db/migration/V2__quotes.sql (ADR 0016): drops what it created, in reverse order, without cascading, so that
-- it fails if anything else still depends on the schema. Development and CI only: it deletes every quote and offer.
-- Run it as shipping_owner in one transaction that also deletes version 2 from flyway.flyway_schema_history, and only
-- when version 2 is the latest applied migration.

drop table quotes.quote_offer;
drop table quotes.quote;
drop schema quotes;
