---
title: "Module: quotes"
description: "Prices parcels with the synthetic tariff and issues tenant-scoped quotes whose offers expire after 30 minutes."
status: accepted
updated: 2026-11-07
module: quotes
moduleStatus: built
riskLevel: 3
---

# Module: quotes

Package `io.github.stigjohnny.shipping.quotes`. C4 component `quotes` (`contract.status built`). Risk level 3:
all of its code is pricing.

## Implementation state

Built: the public API below, the domain model and two application classes with their port: `CreateQuote`,
`QuoteOffersService` and `QuoteStore`. Their tests use a store in memory, a fixed clock and deterministic IDs.
`quotes.synthetic-tariff` and `quotes.quote-expiry` are shipped; `quotes.create-quote` and `quotes.offer-lookup`
are partial, since `QuotesHttpContractTest` and `QuotesModuleIntegrationTest` are planned; `quotes.get-quote` is
planned.

Planned: `GetQuote`, both adapters, the migration and the demo fixture, and the wiring into a running application.
Until then no class of the module is a Spring bean: `CreateQuote`'s `@Transactional` has no effect yet, and nothing
yet shows that `QuoteIssued` is published inside the quote's transaction.

## Responsibilities

- Turn a parcel, a route and an optional service level into a quote with one offer per available service level,
  priced by the synthetic tariff below.
- Store quotes per tenant. Quotes never change after they are issued.
- Answer, for the shipments module, whether an offer exists for a tenant and is still bookable at a given instant.
- Publish `QuoteIssued` when a quote is stored.

Not its job: booking, labels, carrier contracts, currency conversion, postcode validation per country, discounts.

## Relationships (from `architecture/workspace.dsl`)

| Direction | Other component | Mode | Meaning |
|---|---|---|---|
| quotes -> tenants | tenants | REQUIRED | Resolves the caller's tenant (`CurrentTenant`, `TenantId`). |
| shipments -> quotes | shipments | REQUIRED | Books only against an existing, unexpired offer (`QuoteOffers`). |
| audit -> quotes | audit | PERMITTED | May record `QuoteIssued`; no listener in v0.1, so the report shows "no static evidence". |
| orders -> quotes | orders (planned) | PERMITTED | Not evaluated while orders is planned. |

## Public API: `io.github.stigjohnny.shipping.quotes.api`

Annotated `@NamedInterface("api")` and `@NullMarked`. Everything else in the module is internal.

| Type | Shape | Notes |
|---|---|---|
| `QuoteId` | `record QuoteId(UUID value)` | |
| `OfferId` | `record OfferId(UUID value)` | |
| `ServiceLevel` | `enum { ECONOMY, STANDARD, EXPRESS }` | Declaration order is the offer order. |
| `Money` | `record Money(BigDecimal amount, Currency currency)` | Canonical constructor: both non-null; scale set to the currency's default fraction digits with `RoundingMode.UNNECESSARY` (an amount with more digits is rejected); negative amounts rejected. |
| `Place` | `record Place(String countryCode, String postcode)` | Validates the contract patterns `^[A-Z]{2}$` and `^[A-Z0-9][A-Z0-9 -]{0,8}[A-Z0-9]$`. |
| `QuoteOffers` | `interface { OfferLookup findOffer(TenantId tenant, QuoteId quote, OfferId offer, Instant at); }` | Runs in the caller's transaction. Implemented in `internal.application`. |
| `OfferLookup` | `sealed interface permits Bookable, QuoteNotFound, OfferNotFound, Expired` | `Bookable(BookableOffer offer)`, `QuoteNotFound()`, `OfferNotFound()`, `Expired(Instant expiredAt)`. A quote of another tenant is `QuoteNotFound`. `Expired` when `at` is at or after `expiresAt`. |
| `BookableOffer` | `record BookableOffer(QuoteId quoteId, OfferId offerId, ServiceLevel serviceLevel, Money price, Place origin, Place destination, Instant expiresAt)` | Everything shipments needs to book and to check the recipient. |
| `QuoteIssued` | `record QuoteIssued(UUID eventId, TenantId tenantId, QuoteId quoteId, Instant occurredAt)` | Event (ADR 0006). No consumer in v0.1. |

## HTTP operations (`contracts/shipping.openapi.yaml`, tag `quotes`)

| operationId | Method and path | Success | Module-specific problems |
|---|---|---|---|
| `createQuote` | `POST /api/v1/quotes` | `201` `Quote`, `Location` | `422 no-offer-available` with `reason` |
| `getQuote` | `GET /api/v1/quotes/{quoteId}` | `200` `Quote` | `404 quote-not-found` |

## Events

| Event | Published when | Consumers in v0.1 |
|---|---|---|
| `QuoteIssued` | In `CreateQuote`'s transaction, after the quote is inserted. Not for `Unavailable` results. | None. Because no transactional listener exists, the publication registry stores no row for it. `audit -> quotes` is PERMITTED for a future listener. |

Consumes: none.

## The synthetic tariff

All values are fictional and fixed in code (`internal.domain.SyntheticTariff`). Changing any of them is a level 3
change and must update this table, the golden values and `SyntheticTariffTest` in the same pull request.

**Served countries:** `DE`, `DK`, `FI`, `NL`, `NO`, `SE`. Postcodes do not affect the price in v0.1.

**Zone:** `DOMESTIC` when origin and destination country are equal, otherwise `INTERNATIONAL`.

**Billable weight:** `volumetricGrams = ceil(lengthMm x widthMm x heightMm / 5000)` (computed in `long`);
`billableWeightGrams = max(weightGrams, volumetricGrams)`. Request bounds: weight 1 to 30000 g, each side 1 to
1200 mm, so the billable weight can reach 345600 g before the availability rules apply.

**Weight band:** the first band whose upper bound is at least the billable weight.

| Band | Billable grams | Base DOMESTIC (EUR) | Base INTERNATIONAL (EUR) |
|---|---|---:|---:|
| 1 | 1 to 1000 | 6.90 | 14.00 |
| 2 | 1001 to 2000 | 8.50 | 17.50 |
| 3 | 2001 to 5000 | 11.90 | 24.00 |
| 4 | 5001 to 10000 | 15.50 | 32.00 |
| 5 | 10001 to 20000 | 21.00 | 45.00 |
| 6 | 20001 to 30000 | 27.50 | 58.00 |

**Service level factor:** ECONOMY 0.80, STANDARD 1.00, EXPRESS 1.50.

**Fuel surcharge:** 7.5 % on every offer (factor 1.075).

**Price:** `price = (base x levelFactor x 1.075)` computed exactly with `BigDecimal`, then rounded half-up to two
decimal places, then wrapped in `Money` (currency `EUR`).

**Availability, checked in this order** (the first failing rule decides the `UnavailableReason`):

1. Both countries served, else `ROUTE_NOT_SERVED`.
2. Billable weight at most 30000 g, else `BILLABLE_WEIGHT_ABOVE_LIMIT`.
3. If a `serviceLevel` was requested, it must be available for the parcel, else `SERVICE_LEVEL_NOT_AVAILABLE`.
   EXPRESS is available only up to 10000 g billable weight; ECONOMY and STANDARD up to 30000 g.

Without a requested level, the quote contains every available level. A quote has at least one offer, because
STANDARD is always available once rules 1 and 2 pass.

**Transit days** (simulated, inclusive range):

| Level | DOMESTIC | INTERNATIONAL |
|---|---|---|
| ECONOMY | 3 to 5 | 5 to 8 |
| STANDARD | 1 to 3 | 3 to 5 |
| EXPRESS | 1 to 1 | 1 to 2 |

**Expiry:** `expiresAt = issuedAt + 30 minutes`; `issuedAt` is the injected clock's instant truncated to
milliseconds. A quote is `OPEN` while `now < expiresAt` and `EXPIRED` from `expiresAt` on.

### Golden prices (EUR, after surcharge and rounding)

`SyntheticTariffTest` asserts all 32 values.

| Band | DOMESTIC ECONOMY | DOMESTIC STANDARD | DOMESTIC EXPRESS | INTL ECONOMY | INTL STANDARD | INTL EXPRESS |
|---|---:|---:|---:|---:|---:|---:|
| 1 | 5.93 | 7.42 | 11.13 | 12.04 | 15.05 | 22.58 |
| 2 | 7.31 | 9.14 | 13.71 | 15.05 | 18.81 | 28.22 |
| 3 | 10.23 | 12.79 | 19.19 | 20.64 | 25.80 | 38.70 |
| 4 | 13.33 | 16.66 | 24.99 | 27.52 | 34.40 | 51.60 |
| 5 | 18.06 | 22.58 | not offered | 38.70 | 48.38 | not offered |
| 6 | 23.65 | 29.56 | not offered | 49.88 | 62.35 | not offered |

Worked example (the contract's example): `NO 0150` to `SE 11122`, 1200 g, 300 x 200 x 100 mm. Volumetric weight
`300 x 200 x 100 / 5000 = 1200` g, billable 1200 g, band 2, INTERNATIONAL. STANDARD:
`17.50 x 1.00 x 1.075 = 18.8125`, rounded half-up `18.81`.

## Domain model: `internal.domain`

- `Parcel(int weightGrams, int lengthMm, int widthMm, int heightMm)`: validates bounds; `volumetricGrams()`,
  `billableWeightGrams()`.
- `Zone` (`DOMESTIC`, `INTERNATIONAL`), `WeightBand` (six bands with upper bounds), `TransitDays(int minDays, int maxDays)`.
- `SyntheticTariff`: pure functions for zone, band, price, availability and transit days; no I/O, no clock.
- `QuoteRequest(Parcel parcel, Place origin, Place destination, @Nullable ServiceLevel serviceLevel)`.
- `Offer(OfferId id, ServiceLevel level, Money price, TransitDays transitDays)`.
- `Quote` (aggregate): `QuoteId`, `TenantId`, request values, `Zone`, billable weight, `issuedAt`, `expiresAt` and
  `List<Offer>` in `ServiceLevel` order. Immutable. `status(Instant now)` returns `QuoteStatus.OPEN` or
  `QuoteStatus.EXPIRED`.
- `QuoteResult`: `sealed interface QuoteResult permits Offered, Unavailable`; `Offered(Quote quote)`,
  `Unavailable(UnavailableReason reason)`.
- `UnavailableReason`: `ROUTE_NOT_SERVED`, `BILLABLE_WEIGHT_ABOVE_LIMIT`, `SERVICE_LEVEL_NOT_AVAILABLE`.
- `Rating`: `QuoteResult quote(TenantId tenant, QuoteRequest request, Instant issuedAt)`; uses `SyntheticTariff`
  and an `IdGenerator` (`UUID next()`) passed to its constructor.

## Application: `internal.application`

- `CreateQuote.handle(QuoteRequest) -> QuoteResult`, `@Transactional`: tenant from `CurrentTenant.require()`;
  `rating.quote(tenant, request, clock.instant())`; on `Offered` insert through the `QuoteStore` port and publish
  `QuoteIssued`; on `Unavailable` store and publish nothing.
- `GetQuote.handle(QuoteId) -> Optional<Quote>`, read-only, tenant from `CurrentTenant`.
- `QuoteOffersService implements QuoteOffers`: loads the quote by tenant and ID and returns the `OfferLookup`.
- `QuoteStore` port: `void insert(Quote)`, `Optional<Quote> find(TenantId, QuoteId)`.
- Collaborators are constructor-injected: `Clock`, `IdGenerator`, `CurrentTenant`, `ApplicationEventPublisher`,
  `QuoteStore`.

## Adapters

- `internal.adapter.in.web`: `QuotesController` (both operations), request and response records that mirror the
  contract, mapping of `QuoteResult` and validation failures to problems (`validation-failed`,
  `no-offer-available`, `quote-not-found`).
- `internal.adapter.out.jdbc`: `JdbcQuoteStore` with Spring JDBC's `JdbcClient` (ADR 0012); it writes and reads a
  `Quote` as one aggregate (quote plus offers).

## Data: schema `quotes` (migration `V2__quotes.sql`)

| Table | Columns | Keys and checks |
|---|---|---|
| `quotes.quote` | `tenant_id uuid`, `quote_id uuid`, `issued_at timestamptz`, `expires_at timestamptz`, `weight_grams int`, `length_mm int`, `width_mm int`, `height_mm int`, `billable_weight_grams int`, `zone text`, `origin_country char(2)`, `origin_postcode varchar(10)`, `destination_country char(2)`, `destination_postcode varchar(10)`; all `not null` | PK `(tenant_id, quote_id)`; checks on every bound in the contract; `expires_at = issued_at + interval '30 minutes'` |
| `quotes.quote_offer` | `tenant_id uuid`, `quote_id uuid`, `offer_id uuid`, `service_level text`, `amount numeric(11,2)`, `currency char(3)`, `transit_min_days smallint`, `transit_max_days smallint`; all `not null` | PK `(tenant_id, offer_id)`; unique `(tenant_id, quote_id, service_level)`; FK `(tenant_id, quote_id)` to `quotes.quote`; `amount >= 0`; `currency = 'EUR'` |

Both tables: row-level security enabled and forced, policy `tenant_isolation` (ADR 0012). Grants to
`shipping_app`: `USAGE` on the schema, `SELECT, INSERT` on both tables (quotes are immutable). No foreign keys to
other modules' schemas.

**Demo fixture** (`db/demo/V1001__expired_quote_fixture.sql`, `demo` profile and tests only): an already expired
quote for Tenant Alpha, used by `expired-quote.spec.ts`. The script binds Tenant Alpha with
`set_config('app.tenant_id', '11111111-1111-4111-8111-111111111111', true)` before inserting.

| Field | Value |
|---|---|
| `quote_id` | `33333333-3333-4333-8333-333333333333` |
| issued, expires | `2026-01-05T09:00:00.000Z`, `2026-01-05T09:30:00.000Z` |
| route, parcel | `NO 0150` to `SE 11122`; 1200 g; 300 x 200 x 100 mm; billable 1200 g; INTERNATIONAL |
| offers | `33333333-3333-4333-8333-3333333333e1` ECONOMY 15.05 (5 to 8 days); `...e2` STANDARD 18.81 (3 to 5); `...e3` EXPRESS 28.22 (1 to 2) |

## Invariants and the tests that assert them

| # | Invariant | Tests |
|---|---|---|
| Q1 | Prices equal the golden table; band boundaries are 1000/1001, 2000/2001, 5000/5001, 10000/10001, 20000/20001 and 30000/30001 g. | `SyntheticTariffTest` |
| Q2 | For a fixed zone and level, price never decreases as billable weight grows; for the same band, ECONOMY < STANDARD < EXPRESS and DOMESTIC < INTERNATIONAL; every price is the half-up rounding of the exact product. | `SyntheticTariffPropertiesTest` (JUnit Jupiter 6.0.3 `@ParameterizedTest` with a `@MethodSource` that enumerates every tariff boundary ±1 minor unit plus a fixed-seed `java.util.random.RandomGenerator` sample) |
| Q3 | Unavailability reasons follow the stated order; EXPRESS never above 10000 g; nothing above 30000 g. | `SyntheticTariffTest`, `SyntheticTariffPropertiesTest`, `RatingTest` |
| Q4 | `Money` rejects negative amounts and amounts with more fraction digits than the currency allows. | `MoneyTest` |
| Q5 | Billable weight is `max(weight, ceil(volume / 5000))`. | `ParcelTest` |
| Q6 | Offers appear in ECONOMY, STANDARD, EXPRESS order; a requested level yields exactly that offer. | `RatingTest` |
| Q7 | A quote is OPEN until `expiresAt` (exclusive) and EXPIRED from `expiresAt`; `findOffer` returns `Expired` at `expiresAt`. | `QuoteExpiryTest` |
| Q8 | The tenant comes from `CurrentTenant`; only `Offered` results are stored and publish `QuoteIssued`. | `CreateQuoteTest` |
| Q9 | Quotes of another tenant are not found, through the repository and through `QuoteOffers`. | `QuotesModuleIntegrationTest`, `CrossTenantAccessTest` |
| Q10 | HTTP responses match the contract, including problem bodies. | `QuotesHttpContractTest` |

Mutation testing: at least 90 % killed mutants on changed lines in this module (pricing logic). Not run yet: PIT runs
for `libs/c4-contract-kit` only (`docs/gaps.md`, section 6).

## Risk levels of its paths (`policy/risk.json`)

| Path | Level | Rule |
|---|---|---|
| `apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/quotes/**` | 3 | `pricing-quotes` |
| `apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/quotes/**` | 3 | `pricing-quotes` |
| `apps/shipping-api/src/main/resources/db/**` | 3 | `migrations` |

## Capabilities

`quotes.synthetic-tariff`, `quotes.create-quote`, `quotes.get-quote`, `quotes.quote-expiry`, `quotes.offer-lookup`
(`docs/capabilities/capabilities.yaml`).
