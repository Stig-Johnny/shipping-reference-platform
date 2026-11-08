---
title: "The shipping API answers over HTTP: a demo user gets a quote, a request without a token is refused"
description: "The API's first endpoints run in its tests and answer as the contract says: who the caller is, quotes with their offers, and an RFC 9457 problem for every refusal."
status: accepted
updated: 2026-11-08
pr: 75
kind: feature
recorded: 2026-11-07
tests: [TenantsHttpContractTest, QuotesHttpContractTest, ProblemDetailsHttpContractTest, DemoTokenAuthenticationTest, SitePagesTest]
---

## What changed

Before, the shipping API's code ran only in unit tests, and no endpoint answered a request. Now the application starts
in its tests and answers three operations of the contract: who the caller is (`GET /api/v1/me`), pricing a parcel
(`POST /api/v1/quotes`) and reading a quote back (`GET /api/v1/quotes/{quoteId}`). A request needs the public demo
token of a synthetic user, and the tenant always comes from that user's membership, never from the request. Every
refusal is a problem in the format of RFC 9457, with the type, title and status that the contract gives it, and every
response that the tests exercise is checked against the contract.

Unknown members are named through 200 Unicode code points in their escaped JSON Pointer; longer pointers produce
`malformed-request`. The build-output scan refuses missing or unreadable output as well as a generated password.
ADR 0012's status panel retains the structure evidence beside the partial HTTP evidence. Pull request #72 adds
the repository's partial web import gate to that panel, with synthetic-fixture evidence and no real web imports
checked yet. The database, web compiler and logic tests, and end-to-end checks remain planned.

## Try it

From the repository root, with the reviewed JDK as `JAVA_HOME`, run the build. Its HTTP tests write two of the
exchanges they send as text to `apps/shipping-api/build/reports/http-exchanges/`: `me-without-a-token.txt` and
`create-quote.txt`.

```sh
./gradlew check
```

## Evidence

Recorded output — not rerun by this site build.

Recorded on 2026-11-07 from 23:15:18Z, on macOS 26.5 (arm64) with Temurin 25.0.5+8, on revision 16db9fa of pull
request #75, in a fresh checkout: `./gradlew check` exited 0. The HTTP tests sent the requests to the whole
application in a mock servlet environment and wrote the exchanges. The recorder indents JSON bodies and writes the
words "demo token of" and the user in place of a token, so no token is published. A request without a token is refused
with the contract's `401 unauthenticated` problem, the challenge header and the correlation ID that the client sent:

```text
GET /api/v1/me
Correlation-Id: demo-75-unauthenticated

HTTP 401
Cache-Control: no-store
Content-Type: application/problem+json
Correlation-Id: demo-75-unauthenticated
WWW-Authenticate: Bearer realm="shipping-reference-platform"
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 0

{
  "detail" : "A valid demo bearer token is required.",
  "instance" : "/api/v1/me",
  "status" : 401,
  "title" : "Authentication required",
  "type" : "https://shipping-reference-platform.example/problems/unauthenticated",
  "correlationId" : "demo-75-unauthenticated"
}
```

The contract's example parcel, sent by the synthetic user clerk-alpha with the user's demo token, gets a quote: one
offer per service level in level order, priced by the synthetic tariff, expiring 30 minutes after issue on the tests'
fixed clock. The request body, the contract's example, is left out; the response is whole:

```text
POST /api/v1/quotes
Content-Type: application/json;charset=UTF-8
Correlation-Id: demo-75-create-quote
Authorization: demo token of clerk-alpha

HTTP 201
Cache-Control: no-store
Content-Type: application/json
Correlation-Id: demo-75-create-quote
Location: /api/v1/quotes/00000000-0000-4000-8000-000000000014
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 0

{
  "quoteId" : "00000000-0000-4000-8000-000000000014",
  "status" : "OPEN",
  "issuedAt" : "2026-11-10T09:00:00.000Z",
  "expiresAt" : "2026-11-10T09:30:00.000Z",
  "parcel" : {
    "weightGrams" : 1200,
    "lengthMm" : 300,
    "widthMm" : 200,
    "heightMm" : 100
  },
  "origin" : {
    "countryCode" : "NO",
    "postcode" : "0150"
  },
  "destination" : {
    "countryCode" : "SE",
    "postcode" : "11122"
  },
  "zone" : "INTERNATIONAL",
  "billableWeightGrams" : 1200,
  "offers" : [ {
    "offerId" : "00000000-0000-4000-8000-000000000015",
    "serviceLevel" : "ECONOMY",
    "price" : {
      "amount" : "15.05",
      "currency" : "EUR"
    },
    "transitDays" : {
      "minDays" : 5,
      "maxDays" : 8
    }
  }, {
    "offerId" : "00000000-0000-4000-8000-000000000016",
    "serviceLevel" : "STANDARD",
    "price" : {
      "amount" : "18.81",
      "currency" : "EUR"
    },
    "transitDays" : {
      "minDays" : 3,
      "maxDays" : 5
    }
  }, {
    "offerId" : "00000000-0000-4000-8000-000000000017",
    "serviceLevel" : "EXPRESS",
    "price" : {
      "amount" : "28.22",
      "currency" : "EUR"
    },
    "transitDays" : {
      "minDays" : 1,
      "maxDays" : 2
    }
  } ]
}
```

The HTTP tests named above check these responses, and every other response their requests get, against the
contract in every build. `ProblemDetailsHttpContractTest` also checks supplementary characters at the pointer
boundary, and `SitePagesTest` checks ADR 0012's status panel. The scan's synthetic fixtures run separately in the
`java` workflow. These checks are not part of the recorded exchanges above. Reviewed in pull request #75.

## Limits

- The application starts in its tests only. Test doubles stand in for the database: the users of
  `docs/modules/tenants.md` and a store in memory. No token is resolved and no quote is stored in PostgreSQL yet.
- The requests went through MockMvc, with the application's own filters, not through a servlet container, so the
  container's own request parsing and error pages are not shown.
- The demo tokens are public, guessable fixtures. This shows that the tenant comes from the membership, not that the
  login is strong.
- The quote's identifiers come from the tests' deterministic generator, so they depend on the order the tests run in.
- The shipments operations, booking and the label, are not in this change.
