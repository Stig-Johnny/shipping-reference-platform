import assert from 'node:assert/strict';
import { describe, test } from 'node:test';
import type { ApiResult, BookingIntent } from '../../api/client.ts';
import type { ApiFailure, ProblemType } from '../../api/problems.ts';
import type { Shipment } from '../../api/types.ts';
import {
  EMPTY_RECIPIENT, buildRequest, isFrozen, isUnresolved, newRecord, outcomeView, resend, samePayload, settle, submit, withFields,
  withTarget,
} from './BookingFormModel.ts';
import type { BookingRecord, BookingTarget, RecipientFields } from './BookingFormModel.ts';
import { createBookingMemory } from './booking-memory.ts';

const target: BookingTarget = {
  quoteId: '5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21', offerId: '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a02',
  destination: { countryCode: 'SE', postcode: '11122' }, expiresAt: '2026-11-10T09:30:00.000Z',
};

const fields: RecipientFields = { ...EMPTY_RECIPIENT, name: 'Synthetic Recipient 1', addressLine1: '1 Example Street', city: 'Exampleby', reference: 'ORDER-1001' };

const shipment: Shipment = {
  shipmentId: '9d1c2b3a-4e5f-4a6b-8c7d-0e1f2a3b4c5d', quoteId: target.quoteId, offerId: target.offerId, status: 'BOOKED',
  serviceLevel: 'STANDARD', price: { amount: '18.81', currency: 'EUR' }, trackingNumber: 'SIM0000000001',
  origin: { countryCode: 'NO', postcode: '0150' },
  recipient: { name: 'Synthetic Recipient 1', addressLine1: '1 Example Street', postcode: '11122', city: 'Exampleby', countryCode: 'SE' },
  reference: 'ORDER-1001', bookedAt: '2026-11-10T09:05:00.000Z',
};

const keys = (() => { let n = 0; return () => `key-${String(++n).padStart(12, '0')}`; })();
const created: ApiResult<Shipment> = { ok: true, value: shipment, status: 201, replayed: false };
const replayed: ApiResult<Shipment> = { ok: true, value: shipment, status: 200, replayed: true };
const network: ApiResult<Shipment> = { ok: false, failure: { kind: 'network' } };
const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

function problem(type: ProblemType, status: number, extra: Record<string, unknown> = {}): ApiResult<Shipment> {
  const failure: ApiFailure = {
    kind: 'problem',
    problem: { type, title: 'Title', status, detail: 'Detail.', instance: '/api/v1/shipments', correlationId: 'corr-0000000009', ...extra },
  };
  return { ok: false, failure };
}

/** Fills the form, submits, and returns the record and the intent that was sent. */
function sent(record: BookingRecord = withFields(newRecord(target), fields)): { record: BookingRecord; intent: BookingIntent } {
  const submitted = submit(record, keys);
  assert.ok(submitted.intent, 'a valid form sends a request');
  return { record: submitted.record, intent: submitted.intent };
}

describe('the request', () => {
  test('takes postcode and country from the quote\'s destination, so a mismatch cannot be entered', () => {
    assert.deepEqual(buildRequest(target, fields), {
      quoteId: target.quoteId, offerId: target.offerId,
      recipient: { name: 'Synthetic Recipient 1', addressLine1: '1 Example Street', postcode: '11122', city: 'Exampleby', countryCode: 'SE' },
      reference: 'ORDER-1001',
    });
  });

  test('omits empty optional fields and sends the others exactly as typed', () => {
    const request = buildRequest(target, { ...fields, addressLine2: '', reference: '', name: ' Synthetic  Recipient ' });
    assert.equal('reference' in request, false);
    assert.equal('addressLine2' in request.recipient, false);
    assert.equal(request.recipient.name, ' Synthetic  Recipient ');
  });

  test('two requests are the same intent exactly when every field the server fingerprints is equal', () => {
    const base = buildRequest(target, fields);
    assert.equal(samePayload(base, buildRequest(target, { ...fields })), true);
    for (const changed of [{ ...fields, name: 'Synthetic Recipient 2' }, { ...fields, addressLine2: 'Floor 2' }, { ...fields, reference: '' }]) {
      assert.equal(samePayload(base, buildRequest(target, changed)), false);
    }
    assert.equal(samePayload(base, buildRequest({ ...target, offerId: '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a03' }, fields)), false);
  });

  test('an invalid form sends nothing and says what to do next to each field', () => {
    const { record, intent } = submit(withFields(newRecord(target), { ...fields, name: '', reference: 'ORDER#1' }), keys);
    assert.equal(intent, undefined);
    assert.equal(record.phase.kind, 'editing');
    assert.deepEqual(record.errors, {
      name: 'Enter the recipient\'s name: 1 to 70 characters, no line breaks.',
      reference: 'Use up to 35 letters, digits, spaces and the characters . _ / -',
    });
  });
});

describe('one Idempotency-Key per booking intent', () => {
  test('a network error leaves the outcome unknown; Retry resends the identical request with the same key', () => {
    const first = sent();
    assert.equal(first.record.phase.kind, 'pending');
    assert.equal(isUnresolved(first.record.phase), true);
    const unknown = settle(first.record, first.intent, network);
    assert.equal(unknown.phase.kind, 'unknown');
    assert.deepEqual(outcomeView(unknown, 'UTC')?.actions, ['retry', 'open-shipments']);
    assert.equal(outcomeView(unknown, 'UTC')?.heading, 'Booking outcome unknown');
    const again = resend(unknown);
    assert.equal(again.intent, first.intent);
    assert.equal(again.intent?.idempotencyKey, first.intent.idempotencyKey);
  });

  test('a server error is an unknown outcome too: the booking may have committed', () => {
    const first = sent();
    assert.equal(settle(first.record, first.intent, problem('internal-error', 500)).phase.kind, 'unknown');
    assert.equal(settle(first.record, first.intent, { ok: false, failure: { kind: 'unexpected', status: 502 } }).phase.kind, 'unknown');
  });

  test('201 then "Book again (same request)" with the same key, answered 200 replayed: the same shipment', () => {
    const first = sent();
    const booked = settle(first.record, first.intent, created);
    assert.deepEqual(outcomeView(booked, 'UTC'), { tone: 'success', message: 'Shipment booked: SIM0000000001', actions: ['open-shipment', 'book-again'] });
    const again = resend(booked);
    assert.equal(again.intent?.idempotencyKey, first.intent.idempotencyKey);
    assert.ok(again.intent);
    const replay = settle(again.record, again.intent, replayed);
    assert.deepEqual(outcomeView(replay, 'UTC'), {
      tone: 'success', message: 'Already booked. This is the same shipment: SIM0000000001.', actions: ['open-shipment', 'book-again'],
    });
  });

  test('resubmitting an unchanged form reuses the key; an edit before submission is a new intent', () => {
    const first = sent();
    const rejected = settle(first.record, first.intent, problem('quote-already-booked', 409));
    assert.equal(submit(rejected, keys).intent?.idempotencyKey, first.intent.idempotencyKey);
    const edited = withFields(rejected, { ...fields, name: 'Synthetic Recipient 2' });
    assert.notEqual(submit(edited, keys).intent?.idempotencyKey, first.intent.idempotencyKey);
  });

  test('after idempotency-key-reused the next submit uses a fresh key, even unchanged', () => {
    const first = sent();
    const reused = settle(first.record, first.intent, problem('idempotency-key-reused', 422));
    assert.equal(outcomeView(reused, 'UTC')?.message, 'This booking changed after it was sent. Review the details and book again.');
    const next = submit(reused, keys);
    assert.ok(next.intent);
    assert.notEqual(next.intent.idempotencyKey, first.intent.idempotencyKey);
  });

  test('while a booking is pending, unknown or booked, the submitted payload is frozen', () => {
    const first = sent();
    for (const record of [first.record, settle(first.record, first.intent, network), settle(first.record, first.intent, created)]) {
      assert.equal(isFrozen(record.phase), true);
      assert.deepEqual(withFields(record, { ...fields, name: 'Changed' }).fields, fields);
      assert.equal(withTarget(record, { ...target, offerId: '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a03' }).target.offerId, target.offerId);
      assert.equal(submit(record, keys).intent, undefined, 'no second request while frozen');
    }
  });

  test('a result for an intent that is no longer pending is ignored', () => {
    const first = sent();
    const unknown = settle(first.record, first.intent, network);
    assert.equal(settle(unknown, first.intent, created), unknown);
  });
});

describe('problems the booking can meet', () => {
  test('each has the workbench message, the problem\'s title and reference, and the action that helps', () => {
    const first = sent();
    const view = (result: ApiResult<Shipment>) => outcomeView(settle(first.record, first.intent, result), 'UTC');
    const failed = { heading: 'Title', reference: 'corr-0000000009' };
    assert.deepEqual(view(problem('quote-expired', 422)), { tone: 'warning', ...failed, message: 'This quote expired at 09:30. Get a fresh quote.', actions: [] });
    assert.deepEqual(view(problem('quote-already-booked', 409)), { tone: 'danger', ...failed, message: 'This quote has already been booked.', actions: ['open-shipments'] });
    assert.deepEqual(view(problem('quote-not-found', 404)), { tone: 'danger', ...failed, message: 'Quote not found.', actions: ['new-quote'] });
    assert.deepEqual(view(problem('offer-not-found', 404)), { tone: 'danger', ...failed, message: 'Quote not found.', actions: ['new-quote'] });
    assert.deepEqual(view(problem('recipient-does-not-match-quote', 422)), {
      tone: 'danger', ...failed, message: 'The recipient\'s postcode and country must match the quote\'s destination.', actions: [],
    });
    assert.deepEqual(view(problem('not-acceptable', 406)), { tone: 'danger', ...failed, message: 'Something went wrong on our side. Try again.', actions: ['retry'] });
  });

  test('the server\'s validation-failed maps to the fields and the summary', () => {
    const first = sent();
    const record = settle(first.record, first.intent, problem('validation-failed', 400, {
      errors: [{ in: 'body', name: '/recipient/city', detail: 'must not be blank' }, { in: 'header', name: 'Idempotency-Key', detail: 'is malformed' }],
    }));
    assert.equal(record.phase.kind, 'editing');
    assert.deepEqual(record.errors, { city: 'must not be blank' });
    assert.deepEqual(record.unplaced, ['Idempotency-Key: is malformed']);
    assert.equal(outcomeView(record, 'UTC'), undefined);
  });
});

describe('the booking memory of a session', () => {
  test('keeps a booking running across route changes and reports unresolved outcomes', async () => {
    let answer: (result: ApiResult<Shipment>) => void = () => undefined;
    const requests: BookingIntent[] = [];
    const memory = createBookingMemory({
      bookShipment: (intent) => {
        requests.push(intent);
        return new Promise((resolve) => { answer = resolve; });
      },
    });
    memory.choose(target);
    memory.edit(target.quoteId, fields);
    const seen: string[] = [];
    const unsubscribe = memory.subscribe(target.quoteId, (record) => seen.push(record.phase.kind));
    memory.submit(target.quoteId);
    assert.equal(memory.hasUnresolved(), true);
    unsubscribe();
    answer(network);
    await flush();
    assert.equal(memory.peek(target.quoteId)?.phase.kind, 'unknown');
    assert.equal(memory.hasUnresolved(), true);
    memory.subscribe(target.quoteId, (record) => seen.push(record.phase.kind));
    memory.resend(target.quoteId);
    answer(created);
    await flush();
    assert.equal(memory.peek(target.quoteId)?.phase.kind, 'booked');
    assert.equal(memory.hasUnresolved(), false);
    assert.deepEqual(seen, ['pending', 'pending', 'booked']);
    assert.equal(requests.length, 2);
    assert.equal(requests[1], requests[0], 'the retry is the same intent object, key and payload');
  });

  test('choosing another offer before booking keeps the typed recipient', () => {
    const memory = createBookingMemory({ bookShipment: () => Promise.resolve(created) });
    memory.choose(target);
    memory.edit(target.quoteId, fields);
    memory.choose({ ...target, offerId: '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a03' });
    assert.deepEqual(memory.peek(target.quoteId)?.fields, fields);
    assert.equal(memory.peek(target.quoteId)?.target.offerId, '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a03');
  });
});
