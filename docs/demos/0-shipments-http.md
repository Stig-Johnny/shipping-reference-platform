---
title: "Book, browse and download a simulated shipment label"
description: "A clerk can book an offer once, safely retry it, browse only their tenant's shipments and download an escaped SVG label."
status: accepted
updated: 2026-11-08
pr: 0
kind: feature
recorded: 2026-11-08
tests: [ShipmentsHttpContractTest, SvgLabelRendererTest, ContractTest]
---

## What changed

The shipment booking rules already ran in application tests. Four HTTP operations now expose booking, listing,
detail and a simulated label. Booking again with the same key and body returns the same shipment, even after its
quote expires. A foreign shipment ID answers exactly like an unknown one for both detail and label requests.

## See it

Read [the shipment module](../modules/shipments.md) for the four operations and remaining limits, and
[the flow](../flows/quote-book-label.md) for the booking and retry acceptance evidence.

## Try it

Use the reviewed Java 25 toolchain and the repository root. The tests supply the synthetic Alpha and Beta memberships,
quotes and shipments in memory; no database, container or running server is required. Fixtures are cleared between
tests, and the recorded exchanges are build output. Run:

```sh
./gradlew check
```

The shipment HTTP tests write token-free recordings under the API's build reports. The source placeholder `pr: 0`
must receive the orchestrator's actual pull request number before the documentation validator can pass.

## Evidence

Recorded output — not rerun by this site build.

Captured on 2026-11-08 on macOS arm64, Temurin 25.0.5+8, using the application's MockMvc filters and in-memory
persistence. The targeted API test run exited 0. Request Authorization values are replaced by the synthetic user's
name by the recording helper; no token is recorded. The booking and replay below are complete exchanges from the
same test. The replay occurs at the quote's expiry, 30 minutes after booking.

```text
POST /api/v1/shipments
Content-Type: application/json;charset=UTF-8
Correlation-Id: demo-88-shipments
Idempotency-Key: demo-booking-key-0001
Authorization: demo token of clerk-alpha

{
  "quoteId" : "00000000-0000-4000-8000-000000000249",
  "offerId" : "00000000-0000-4000-8000-000000000251",
  "recipient" : {
    "name" : "Synthetic Recipient 1",
    "addressLine1" : "1 Example Street",
    "postcode" : "11122",
    "city" : "Exampleby",
    "countryCode" : "SE"
  },
  "reference" : "ORDER-1001"
}

HTTP 201
Cache-Control: no-store
Content-Type: application/json
Correlation-Id: demo-88-shipments
Location: /api/v1/shipments/00000000-0000-4000-9000-000000000057
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 0

{
  "shipmentId" : "00000000-0000-4000-9000-000000000057",
  "quoteId" : "00000000-0000-4000-8000-000000000249",
  "offerId" : "00000000-0000-4000-8000-000000000251",
  "status" : "BOOKED",
  "serviceLevel" : "STANDARD",
  "price" : {
    "amount" : "18.81",
    "currency" : "EUR"
  },
  "trackingNumber" : "SIM0000000029",
  "origin" : {
    "countryCode" : "NO",
    "postcode" : "0150"
  },
  "recipient" : {
    "name" : "Synthetic Recipient 1",
    "addressLine1" : "1 Example Street",
    "postcode" : "11122",
    "city" : "Exampleby",
    "countryCode" : "SE"
  },
  "reference" : "ORDER-1001",
  "bookedAt" : "2026-11-10T09:00:00.000Z"
}
```

```text
POST /api/v1/shipments
Content-Type: application/json;charset=UTF-8
Correlation-Id: demo-88-shipments
Idempotency-Key: demo-booking-key-0001
Authorization: demo token of clerk-alpha

{
  "quoteId" : "00000000-0000-4000-8000-000000000249",
  "offerId" : "00000000-0000-4000-8000-000000000251",
  "recipient" : {
    "name" : "Synthetic Recipient 1",
    "addressLine1" : "1 Example Street",
    "postcode" : "11122",
    "city" : "Exampleby",
    "countryCode" : "SE"
  },
  "reference" : "ORDER-1001"
}

HTTP 200
Cache-Control: no-store
Content-Type: application/json
Correlation-Id: demo-88-shipments
Idempotency-Replayed: true
Location: /api/v1/shipments/00000000-0000-4000-9000-000000000057
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 0

{
  "shipmentId" : "00000000-0000-4000-9000-000000000057",
  "quoteId" : "00000000-0000-4000-8000-000000000249",
  "offerId" : "00000000-0000-4000-8000-000000000251",
  "status" : "BOOKED",
  "serviceLevel" : "STANDARD",
  "price" : {
    "amount" : "18.81",
    "currency" : "EUR"
  },
  "trackingNumber" : "SIM0000000029",
  "origin" : {
    "countryCode" : "NO",
    "postcode" : "0150"
  },
  "recipient" : {
    "name" : "Synthetic Recipient 1",
    "addressLine1" : "1 Example Street",
    "postcode" : "11122",
    "city" : "Exampleby",
    "countryCode" : "SE"
  },
  "reference" : "ORDER-1001",
  "bookedAt" : "2026-11-10T09:00:00.000Z"
}
```

One page, filtered to booked shipments, with the continuation cursor. The test also walks all 23 shipments across
pages and checks descending time/ID order without repeats or gaps.

```text
GET /api/v1/shipments?status=BOOKED&limit=1
Correlation-Id: demo-88-shipments
Authorization: demo token of clerk-alpha

HTTP 200
Cache-Control: no-store
Content-Type: application/json
Correlation-Id: demo-88-shipments
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 0

{
  "items" : [ {
    "shipmentId" : "00000000-0000-4000-9000-000000000049",
    "status" : "BOOKED",
    "serviceLevel" : "STANDARD",
    "price" : {
      "amount" : "18.81",
      "currency" : "EUR"
    },
    "trackingNumber" : "SIM0000000025",
    "recipientName" : "Synthetic Recipient 1",
    "destination" : {
      "countryCode" : "SE",
      "postcode" : "11122"
    },
    "reference" : "ORDER-1001",
    "bookedAt" : "2026-11-10T09:00:11.000Z"
  } ],
  "nextCursor" : "MTExMTExMTEtMTExMS00MTExLTgxMTEtMTExMTExMTExMTExfDIwMjYtMTEtMTBUMDk6MDA6MTFafDAwMDAwMDAwLTAwMDAtNDAwMC05MDAwLTAwMDAwMDAwMDA0OXxCT09LRUQ"
}
```

The foreign-ID response below comes from Beta requesting Alpha's shipment. The test compares it with an unknown-ID
response, including all headers; only the echoed path can differ when the same correlation ID is supplied.

```text
GET /api/v1/shipments/00000000-0000-4000-9000-000000000055
Correlation-Id: demo-88-shipments
Authorization: demo token of clerk-beta

HTTP 404
Cache-Control: no-store
Content-Type: application/problem+json
Correlation-Id: demo-88-shipments
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 0

{
  "detail" : "No shipment with this ID exists.",
  "instance" : "/api/v1/shipments/00000000-0000-4000-9000-000000000055",
  "status" : 404,
  "title" : "Shipment not found",
  "type" : "https://shipping-reference-platform.example/problems/shipment-not-found",
  "correlationId" : "demo-88-shipments"
}
```

The label exchange below is an excerpt: headers are retained; the SVG body is omitted from this public text excerpt. The tests compare repeated label bytes and separately parse the full document against the allowed
elements and attributes. The golden fixture checks the full expected document.

```text
GET /api/v1/shipments/00000000-0000-4000-9000-000000000003/label
Correlation-Id: demo-88-shipments
Authorization: demo token of clerk-alpha

HTTP 200
Cache-Control: no-store
Content-Disposition: attachment; filename="label-SIM0000000002.svg"
Content-Length: 2036
Content-Security-Policy: default-src 'none'; sandbox
Content-Type: image/svg+xml
Correlation-Id: demo-88-shipments
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 0

```

Review reference: issue #88; the orchestrator assigns the pull request number. `ShipmentsHttpContractTest`,
`SvgLabelRendererTest` and `ContractTest` are separate current-build evidence only when they pass in the publishing
build. The displayed HTTP exchanges are recordings, never rerun by the documentation renderer.

## Limits

MockMvc exercises registered servlet filters, without a network server or servlet-container parsing. The stores and
tracking numbers are test doubles. This does not prove PostgreSQL persistence, row-level security, transactionality,
concurrent booking, durable event publication, cancellation, cancelled-state replay/label denial or browser behavior.
`CANCELLED` is accepted as a list filter and currently returns no matches. The label is explicitly simulated and its
bars are decorative. U+FFFE and U+FFFF display as visible code-point placeholders because XML cannot contain them;
booking values and contract bounds are unchanged. Scenario reports remain `COVERAGE_INCOMPLETE` with 106 exact known
gaps. The temporary positive demo number used for local rendering is not an assigned pull request number.
