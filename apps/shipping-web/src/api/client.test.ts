import assert from 'node:assert/strict';
import { describe, test } from 'node:test';
import { createApiClient, labelFileName, newBookingIntent } from './client.ts';
import type { ApiClientOptions } from './client.ts';
import { PROBLEM_BASE } from './problems.ts';
import type { BookingRequest, QuoteRequest } from './types.ts';

interface Call {
  readonly url: string;
  readonly method: string;
  readonly headers: Headers;
  readonly body: string | undefined;
  readonly credentials: string | undefined;
}

/** A fetch that records each request and answers with the next scripted response (or throws the next error). */
function scriptedFetch(script: Array<Response | Error | 'wait-for-abort'>) {
  const calls: Call[] = [];
  const fetchStub = (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
    calls.push({
      url: String(input),
      method: init?.method ?? 'GET',
      headers: new Headers(init?.headers),
      body: typeof init?.body === 'string' ? init.body : undefined,
      credentials: init?.credentials,
    });
    const next = script.shift();
    if (next === undefined) {
      return Promise.reject(new Error('the test scripted no further response'));
    }
    if (next === 'wait-for-abort') {
      return new Promise((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
      });
    }
    return next instanceof Error ? Promise.reject(next) : Promise.resolve(next);
  };
  return { fetch: fetchStub as typeof fetch, calls };
}

function json(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', 'Correlation-Id': 'corr-response-0001', ...headers },
  });
}

function problem(status: number, type: string, extra: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({
    type: PROBLEM_BASE + type, title: 'Title from the server', status, detail: 'Detail from the server.',
    instance: '/api/v1/somewhere', correlationId: 'corr-problem-0001', ...extra,
  }), { status, headers: { 'Content-Type': 'application/problem+json', 'Correlation-Id': 'corr-problem-0001' } });
}

let counter = 0;
function client(fetchStub: typeof fetch, extra: Partial<ApiClientOptions> = {}) {
  return createApiClient({
    fetch: fetchStub,
    token: 'demo-clerk-alpha',
    newCorrelationId: () => `corr-request-${String(++counter).padStart(4, '0')}`,
    ...extra,
  });
}

const membership = {
  userId: '00000000-0000-4000-8000-0000000000a1', userDisplayName: 'Clerk Alpha (synthetic)',
  tenantId: '11111111-1111-4111-8111-111111111111', tenantDisplayName: 'Tenant Alpha (synthetic)',
  role: 'CLERK', enabledFlags: ['label-preview'],
};

const quoteRequest: QuoteRequest = {
  parcel: { weightGrams: 1200, lengthMm: 300, widthMm: 200, heightMm: 100 },
  origin: { countryCode: 'NO', postcode: '0150' },
  destination: { countryCode: 'SE', postcode: '11122' },
};

const quote = {
  quoteId: '5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21', status: 'OPEN',
  issuedAt: '2026-11-10T09:00:00.000Z', expiresAt: '2026-11-10T09:30:00.000Z',
  parcel: quoteRequest.parcel, origin: quoteRequest.origin, destination: quoteRequest.destination,
  zone: 'INTERNATIONAL', billableWeightGrams: 1200,
  offers: [
    { offerId: '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a01', serviceLevel: 'ECONOMY', price: { amount: '15.05', currency: 'EUR' }, transitDays: { minDays: 5, maxDays: 8 } },
    { offerId: '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a02', serviceLevel: 'STANDARD', price: { amount: '18.81', currency: 'EUR' }, transitDays: { minDays: 3, maxDays: 5 } },
    { offerId: '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a03', serviceLevel: 'EXPRESS', price: { amount: '28.22', currency: 'EUR' }, transitDays: { minDays: 1, maxDays: 2 } },
  ],
};

const bookingRequest: BookingRequest = {
  quoteId: quote.quoteId,
  offerId: '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a02',
  recipient: { name: 'Synthetic Recipient 1', addressLine1: '1 Example Street', postcode: '11122', city: 'Exampleby', countryCode: 'SE' },
  reference: 'ORDER-1001',
};

const shipment = {
  shipmentId: '9d1c2b3a-4e5f-4a6b-8c7d-0e1f2a3b4c5d', quoteId: quote.quoteId, offerId: bookingRequest.offerId,
  status: 'BOOKED', serviceLevel: 'STANDARD', price: { amount: '18.81', currency: 'EUR' }, trackingNumber: 'SIM0000000001',
  origin: { countryCode: 'NO', postcode: '0150' }, recipient: bookingRequest.recipient, reference: 'ORDER-1001',
  bookedAt: '2026-11-10T09:05:00.000Z',
};

const CORRELATION_ID = /^[A-Za-z0-9-]{8,64}$/;
const IDEMPOTENCY_KEY = /^[A-Za-z0-9_-]{16,64}$/;

describe('every request', () => {
  test('goes to the same-origin /api/v1 path with the bearer token, a Correlation-Id and an Accept header', async () => {
    const { fetch, calls } = scriptedFetch([json(200, membership)]);
    const result = await client(fetch).getCurrentMembership();
    assert.equal(result.ok, true);
    const call = calls[0];
    assert.ok(call);
    assert.equal(call.url, '/api/v1/me');
    assert.equal(call.method, 'GET');
    assert.equal(call.headers.get('Authorization'), 'Bearer demo-clerk-alpha');
    assert.match(call.headers.get('Correlation-Id') ?? '', CORRELATION_ID);
    assert.equal(call.headers.get('Accept'), 'application/json, application/problem+json');
    assert.equal(call.credentials, 'omit', 'no cookies: the token is the only credential');
  });

  test('gets a new Correlation-Id each time, by default a UUID that fits the contract pattern', async () => {
    const { fetch, calls } = scriptedFetch([json(200, membership), json(200, membership)]);
    const api = createApiClient({ fetch, token: 'demo-clerk-alpha' });
    await api.getCurrentMembership();
    await api.getCurrentMembership();
    const [first, second] = calls.map((call) => call.headers.get('Correlation-Id') ?? '');
    assert.match(first ?? '', CORRELATION_ID);
    assert.match(second ?? '', CORRELATION_ID);
    assert.notEqual(first, second);
  });

  test('carries no Authorization header when the client has no token', async () => {
    const { fetch, calls } = scriptedFetch([problem(401, 'unauthenticated')]);
    await createApiClient({ fetch }).getCurrentMembership();
    assert.equal(calls[0]?.headers.has('Authorization'), false);
  });

  test('URL-encodes path parameters', async () => {
    const { fetch, calls } = scriptedFetch([problem(400, 'validation-failed')]);
    await client(fetch).getQuote('a/../b');
    assert.equal(calls[0]?.url, '/api/v1/quotes/a%2F..%2Fb');
  });
});

describe('responses', () => {
  test('createQuote posts the request as JSON and returns the quote exactly as received, prices as strings', async () => {
    const { fetch, calls } = scriptedFetch([json(201, quote, { Location: `/api/v1/quotes/${quote.quoteId}` })]);
    const result = await client(fetch).createQuote(quoteRequest);
    assert.equal(calls[0]?.method, 'POST');
    assert.equal(calls[0]?.url, '/api/v1/quotes');
    assert.equal(calls[0]?.headers.get('Content-Type'), 'application/json');
    assert.deepEqual(JSON.parse(calls[0]?.body ?? 'null'), quoteRequest);
    assert.ok(result.ok);
    assert.equal(result.status, 201);
    assert.deepEqual(result.value, quote);
    assert.deepEqual(result.value.offers.map((offer) => offer.price.amount), ['15.05', '18.81', '28.22']);
  });

  test('a success body that does not match the contract is an unexpected response, not data', async () => {
    const { fetch } = scriptedFetch([json(201, { ...quote, offers: 'none' })]);
    const result = await client(fetch).createQuote(quoteRequest);
    assert.equal(result.ok, false);
    assert.deepEqual(!result.ok && result.failure, { kind: 'unexpected', status: 201, correlationId: 'corr-response-0001' });
  });

  test('problem+json becomes a typed problem with its type suffix, reason and correlation ID', async () => {
    const { fetch } = scriptedFetch([problem(422, 'no-offer-available', { reason: 'ROUTE_NOT_SERVED' })]);
    const result = await client(fetch).createQuote(quoteRequest);
    assert.ok(!result.ok);
    assert.equal(result.failure.kind, 'problem');
    assert.ok(result.failure.kind === 'problem');
    assert.equal(result.failure.problem.type, 'no-offer-available');
    assert.equal(result.failure.problem.reason, 'ROUTE_NOT_SERVED');
    assert.equal(result.failure.problem.status, 422);
    assert.equal(result.failure.problem.correlationId, 'corr-problem-0001');
  });

  test('validation-failed keeps its errors[] so forms can map them to fields', async () => {
    const errors = [{ in: 'body', name: '/parcel/weightGrams', detail: 'must be between 1 and 30000' }];
    const { fetch } = scriptedFetch([problem(400, 'validation-failed', { errors })]);
    const result = await client(fetch).createQuote(quoteRequest);
    assert.ok(!result.ok && result.failure.kind === 'problem');
    assert.deepEqual(result.failure.problem.errors, errors);
  });

  test('an error without a contract problem body is unexpected, with the Correlation-Id header when there is one', async () => {
    const { fetch } = scriptedFetch([
      new Response('<html>Bad gateway</html>', { status: 502, headers: { 'Content-Type': 'text/html', 'Correlation-Id': 'corr-gateway-01' } }),
      new Response(JSON.stringify({ type: 'https://elsewhere.example/oops', title: 'x', status: 400, detail: 'x', instance: '/', correlationId: 'corr-x-000001' }),
        { status: 400, headers: { 'Content-Type': 'application/problem+json' } }),
    ]);
    const api = client(fetch);
    assert.deepEqual(await api.getShipment(shipment.shipmentId), { ok: false, failure: { kind: 'unexpected', status: 502, correlationId: 'corr-gateway-01' } });
    assert.deepEqual(await api.getShipment(shipment.shipmentId), { ok: false, failure: { kind: 'unexpected', status: 400 } });
  });

  test('a rejected fetch is a network failure; one aborted by the session is aborted', async () => {
    const session = new AbortController();
    const { fetch } = scriptedFetch([new TypeError('Failed to fetch'), 'wait-for-abort']);
    const api = client(fetch, { signal: session.signal });
    assert.deepEqual(await api.getCurrentMembership(), { ok: false, failure: { kind: 'network' } });
    const outstanding = api.listShipments({});
    session.abort();
    assert.deepEqual(await outstanding, { ok: false, failure: { kind: 'aborted' } });
  });

  test('a call signal aborts only that call', async () => {
    const call = new AbortController();
    const { fetch } = scriptedFetch(['wait-for-abort', json(200, membership)]);
    const api = client(fetch);
    const outstanding = api.getShipment(shipment.shipmentId, call.signal);
    call.abort();
    assert.deepEqual(await outstanding, { ok: false, failure: { kind: 'aborted' } });
    assert.equal((await api.getCurrentMembership()).ok, true);
  });

  test('401 unauthenticated tells the session owner', async () => {
    let told = 0;
    const { fetch } = scriptedFetch([problem(401, 'unauthenticated')]);
    const result = await client(fetch, { onUnauthenticated: () => { told++; } }).listShipments({});
    assert.equal(told, 1);
    assert.ok(!result.ok && result.failure.kind === 'problem' && result.failure.problem.type === 'unauthenticated');
  });
});

describe('booking', () => {
  test('sends the intent\'s Idempotency-Key, and every retry of that intent sends the same key', async () => {
    const { fetch, calls } = scriptedFetch([new TypeError('Failed to fetch'), json(201, shipment)]);
    const api = client(fetch);
    const intent = newBookingIntent(bookingRequest);
    assert.deepEqual(await api.bookShipment(intent), { ok: false, failure: { kind: 'network' } });
    const retried = await api.bookShipment(intent);
    assert.ok(retried.ok);
    const keys = calls.map((call) => call.headers.get('Idempotency-Key'));
    assert.equal(keys.length, 2);
    assert.match(keys[0] ?? '', IDEMPOTENCY_KEY);
    assert.equal(keys[1], keys[0]);
    assert.deepEqual(calls.map((call) => JSON.parse(call.body ?? 'null')), [bookingRequest, bookingRequest]);
  });

  test('a new intent gets a new key from crypto.randomUUID()', () => {
    const first = newBookingIntent(bookingRequest);
    const second = newBookingIntent(bookingRequest);
    assert.match(first.idempotencyKey, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    assert.match(first.idempotencyKey, IDEMPOTENCY_KEY);
    assert.notEqual(first.idempotencyKey, second.idempotencyKey);
    assert.equal(newBookingIntent(bookingRequest, () => 'fixed-key-0123456789').idempotencyKey, 'fixed-key-0123456789');
  });

  test('201 is a new shipment and 200 with Idempotency-Replayed: true is a replay of the same one', async () => {
    const { fetch } = scriptedFetch([json(201, shipment), json(200, shipment, { 'Idempotency-Replayed': 'true' })]);
    const api = client(fetch);
    const intent = newBookingIntent(bookingRequest);
    const created = await api.bookShipment(intent);
    const replayed = await api.bookShipment(intent);
    assert.ok(created.ok && replayed.ok);
    assert.equal(created.replayed, false);
    assert.equal(created.status, 201);
    assert.equal(replayed.replayed, true);
    assert.equal(replayed.value.trackingNumber, created.value.trackingNumber);
  });

  test('booking problems keep their type: quote-expired, idempotency-key-reused, quote-already-booked', async () => {
    const { fetch } = scriptedFetch([problem(422, 'quote-expired'), problem(422, 'idempotency-key-reused'), problem(409, 'quote-already-booked')]);
    const api = client(fetch);
    const types = [];
    for (let i = 0; i < 3; i++) {
      const result = await api.bookShipment(newBookingIntent(bookingRequest));
      types.push(!result.ok && result.failure.kind === 'problem' ? result.failure.problem.type : 'none');
    }
    assert.deepEqual(types, ['quote-expired', 'idempotency-key-reused', 'quote-already-booked']);
  });
});

describe('shipments and labels', () => {
  test('listShipments sends only the parameters it is given', async () => {
    const page = { items: [], nextCursor: 'abc_DEF-123' };
    const { fetch, calls } = scriptedFetch([json(200, page), json(200, { items: [] })]);
    const api = client(fetch);
    const first = await api.listShipments({});
    await api.listShipments({ status: 'CANCELLED', cursor: 'abc_DEF-123', limit: 20 });
    assert.deepEqual(first, { ok: true, value: page, status: 200, replayed: false });
    assert.deepEqual(calls.map((call) => call.url), ['/api/v1/shipments', '/api/v1/shipments?limit=20&cursor=abc_DEF-123&status=CANCELLED']);
  });

  test('getShipmentLabel asks for SVG and returns the blob with the file name from Content-Disposition', async () => {
    const svg = '<?xml version="1.0" encoding="UTF-8"?><svg xmlns="http://www.w3.org/2000/svg"><title>Simulated label</title></svg>';
    const { fetch, calls } = scriptedFetch([new Response(svg, {
      status: 200,
      headers: { 'Content-Type': 'image/svg+xml', 'Content-Disposition': 'attachment; filename="label-SIM0000000001.svg"' },
    })]);
    const result = await client(fetch).getShipmentLabel(shipment.shipmentId);
    assert.equal(calls[0]?.url, `/api/v1/shipments/${shipment.shipmentId}/label`);
    assert.equal(calls[0]?.headers.get('Accept'), 'image/svg+xml, application/problem+json');
    assert.ok(result.ok);
    assert.equal(result.value.fileName, 'label-SIM0000000001.svg');
    assert.equal(result.value.blob.type, 'image/svg+xml');
    assert.equal(await result.value.blob.text(), svg);
  });

  test('a label that is not SVG is unexpected; a cancelled shipment is the shipment-cancelled problem', async () => {
    const { fetch } = scriptedFetch([
      new Response('<html></html>', { status: 200, headers: { 'Content-Type': 'text/html' } }),
      problem(409, 'shipment-cancelled'),
    ]);
    const api = client(fetch);
    assert.deepEqual(await api.getShipmentLabel(shipment.shipmentId), { ok: false, failure: { kind: 'unexpected', status: 200 } });
    const cancelled = await api.getShipmentLabel(shipment.shipmentId);
    assert.ok(!cancelled.ok && cancelled.failure.kind === 'problem' && cancelled.failure.problem.type === 'shipment-cancelled');
  });

  test('labelFileName accepts only the contract\'s header and falls back to a fixed name', () => {
    assert.equal(labelFileName('attachment; filename="label-SIM0000000042.svg"'), 'label-SIM0000000042.svg');
    assert.equal(labelFileName('attachment; filename="../../evil.svg"'), 'label.svg');
    assert.equal(labelFileName('inline'), 'label.svg');
    assert.equal(labelFileName(null), 'label.svg');
  });
});
