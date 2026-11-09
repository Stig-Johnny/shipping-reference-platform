import assert from 'node:assert/strict';
import { describe, test } from 'node:test';
import type { ApiResult } from '../../api/client.ts';
import type { ShipmentPage, ShipmentSummary } from '../../api/types.ts';
import { FILTERS, emptyMessage, initialList, loadMore, queryFor, received, retry, rowView, withFilter } from './ShipmentTableModel.ts';

function summary(n: number, extra: Partial<ShipmentSummary> = {}): ShipmentSummary {
  return {
    shipmentId: `9d1c2b3a-4e5f-4a6b-8c7d-0e1f2a3b4c${String(n).padStart(2, '0')}`, status: 'BOOKED', serviceLevel: 'STANDARD',
    price: { amount: '18.81', currency: 'EUR' }, trackingNumber: `SIM${String(n).padStart(10, '0')}`, recipientName: `Synthetic Recipient ${n}`,
    destination: { countryCode: 'SE', postcode: '11122' }, reference: `ORDER-${1000 + n}`, bookedAt: '2026-11-10T09:05:00.000Z', ...extra,
  };
}

const page = (items: ShipmentSummary[], nextCursor?: string): ApiResult<ShipmentPage> =>
  ({ ok: true, status: 200, replayed: false, value: nextCursor === undefined ? { items } : { items, nextCursor } });

describe('the list', () => {
  test('starts loading all shipments, newest first as the API returns them', () => {
    const state = initialList();
    assert.equal(state.phase, 'loading');
    assert.equal(state.filter, 'ALL');
    assert.deepEqual(queryFor(state), {});
    const ready = received(state, state.request, page([summary(2), summary(1)], 'cursor-2'));
    assert.equal(ready.phase, 'ready');
    assert.deepEqual(ready.items.map((item) => item.trackingNumber), ['SIM0000000002', 'SIM0000000001']);
    assert.equal(ready.nextCursor, 'cursor-2');
  });

  test('"Load more" appends the next page with its cursor and disappears on the last page', () => {
    const first = received(initialList(), 1, page([summary(3), summary(2)], 'cursor-2'));
    const more = loadMore(first);
    assert.equal(more.loadingMore, true);
    assert.deepEqual(queryFor(more), { cursor: 'cursor-2' });
    const last = received(more, more.request, page([summary(1)]));
    assert.deepEqual(last.items.map((item) => item.trackingNumber), ['SIM0000000003', 'SIM0000000002', 'SIM0000000001']);
    assert.equal(last.nextCursor, undefined);
    assert.equal(last.loadingMore, false);
  });

  test('the status filter sets the status parameter and resets pagination', () => {
    assert.deepEqual(FILTERS.map((filter) => filter.label), ['All', 'Booked', 'Cancelled']);
    const loaded = received(initialList(), 1, page([summary(1)], 'cursor-1'));
    const cancelled = withFilter(loaded, 'CANCELLED');
    assert.equal(cancelled.phase, 'loading');
    assert.deepEqual(cancelled.items, []);
    assert.equal(cancelled.nextCursor, undefined);
    assert.deepEqual(queryFor(cancelled), { status: 'CANCELLED' });
  });

  test('an answer to an earlier request is dropped, so a slow page never mixes into another filter', () => {
    const state = withFilter(initialList(), 'BOOKED');
    assert.equal(received(state, state.request - 1, page([summary(9)])), state);
    assert.equal(received(state, state.request, { ok: false, failure: { kind: 'aborted' } }), state);
  });

  test('a failed first page is the failed state, and Try again loads it again', () => {
    const failed = received(initialList(), 1, { ok: false, failure: { kind: 'network' } });
    assert.equal(failed.phase, 'failed');
    assert.deepEqual(failed.failure, { kind: 'network' });
    const again = retry(failed);
    assert.equal(again.phase, 'loading');
    assert.equal(again.failure, undefined);
  });

  test('a failed later page keeps the rows already shown', () => {
    const more = loadMore(received(initialList(), 1, page([summary(2)], 'cursor-2')));
    const failed = received(more, more.request, { ok: false, failure: { kind: 'network' } });
    assert.equal(failed.phase, 'ready');
    assert.equal(failed.items.length, 1);
    assert.deepEqual(failed.moreFailure, { kind: 'network' });
    assert.equal(failed.nextCursor, 'cursor-2');
  });

  test('the empty state says what is missing', () => {
    assert.equal(emptyMessage('ALL'), 'No shipments yet.');
    assert.equal(emptyMessage('BOOKED'), 'No booked shipments.');
    assert.equal(emptyMessage('CANCELLED'), 'No cancelled shipments.');
  });
});

describe('a row', () => {
  test('shows the summary as received, formatted for reading', () => {
    assert.deepEqual(rowView(summary(1), 'UTC'), {
      booked: '10 Nov 2026, 09:05', trackingNumber: 'SIM0000000001', recipient: 'Synthetic Recipient 1', destination: 'SE 11122',
      serviceLevel: 'Standard', price: { visual: '18.81 EUR', spoken: '18.81 euros' }, status: 'Booked', reference: 'ORDER-1001',
    });
    const { reference: _reference, ...withoutReference } = summary(2, { status: 'CANCELLED' });
    const row = rowView(withoutReference, 'UTC');
    assert.equal(row.status, 'Cancelled');
    assert.equal(row.reference, '');
  });
});
