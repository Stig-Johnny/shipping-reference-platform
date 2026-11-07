import assert from 'node:assert/strict';
import { describe, test } from 'node:test';
import type { ApiResult, BookingIntent } from '../../api/client.ts';
import type { Shipment } from '../../api/types.ts';
import { EMPTY_RECIPIENT } from './BookingFormModel.ts';
import type { BookingTarget, RecipientFields } from './BookingFormModel.ts';
import { createBookingMemory } from './booking-memory.ts';

const target: BookingTarget = {
  quoteId: '5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21', offerId: '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a02',
  destination: { countryCode: 'SE', postcode: '11122' }, expiresAt: '2026-11-10T09:30:00.000Z',
};

const fields: RecipientFields = {
  ...EMPTY_RECIPIENT, name: 'Synthetic Recipient 1', addressLine1: '1 Example Street', city: 'Exampleby', reference: 'ORDER-1001',
};

const shipment: Shipment = {
  shipmentId: '9d1c2b3a-4e5f-4a6b-8c7d-0e1f2a3b4c5d', quoteId: target.quoteId, offerId: target.offerId, status: 'BOOKED',
  serviceLevel: 'STANDARD', price: { amount: '18.81', currency: 'EUR' }, trackingNumber: 'SIM0000000001',
  origin: { countryCode: 'NO', postcode: '0150' },
  recipient: { name: 'Synthetic Recipient 1', addressLine1: '1 Example Street', postcode: '11122', city: 'Exampleby', countryCode: 'SE' },
  reference: 'ORDER-1001', bookedAt: '2026-11-10T09:05:00.000Z',
};

const created: ApiResult<Shipment> = { ok: true, value: shipment, status: 201, replayed: false };
const network: ApiResult<Shipment> = { ok: false, failure: { kind: 'network' } };
const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

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
