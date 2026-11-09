import assert from 'node:assert/strict';
import { describe, test } from 'node:test';
import type { ApiResult } from '../../api/client.ts';
import type { ProblemType } from '../../api/problems.ts';
import type { Label, Shipment } from '../../api/types.ts';
import { canDownloadLabel, drawerDetails, drawerHeading, drawerLoaded, labelMessage, labelSettled } from './ShipmentDrawerModel.ts';

const shipment: Shipment = {
  shipmentId: '9d1c2b3a-4e5f-4a6b-8c7d-0e1f2a3b4c5d', quoteId: '5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21',
  offerId: '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a02', status: 'BOOKED', serviceLevel: 'STANDARD', price: { amount: '18.81', currency: 'EUR' },
  trackingNumber: 'SIM0000000001', origin: { countryCode: 'NO', postcode: '0150' },
  recipient: { name: 'Synthetic Recipient 1', addressLine1: '1 Example Street', postcode: '11122', city: 'Exampleby', countryCode: 'SE' },
  reference: 'ORDER-1001', bookedAt: '2026-11-10T09:05:00.000Z',
};

function failure<T>(type: ProblemType, status: number): ApiResult<T> {
  return {
    ok: false,
    failure: { kind: 'problem', problem: { type, title: 'Title', status, detail: 'Detail.', instance: '/api/v1/x', correlationId: 'corr-0000000007' } },
  };
}

describe('the drawer', () => {
  test('is labelled by its heading, which names the tracking number once the shipment is loaded', () => {
    assert.equal(drawerHeading({ kind: 'loading' }), 'Shipment');
    assert.equal(drawerHeading(drawerLoaded({ ok: true, value: shipment, status: 200, replayed: false })), 'Shipment SIM0000000001');
  });

  test('404 shipment-not-found is "Shipment not found.", which is also what another tenant sees', () => {
    assert.deepEqual(drawerLoaded(failure<Shipment>('shipment-not-found', 404)), { kind: 'not-found' });
    assert.equal(drawerHeading({ kind: 'not-found' }), 'Shipment');
    assert.equal(drawerLoaded({ ok: false, failure: { kind: 'network' } }).kind, 'failed');
  });

  test('shows status, service level, price, times, origin, recipient, reference and the IDs', () => {
    assert.deepEqual(drawerDetails(shipment, 'UTC'), [
      { term: 'Status', lines: ['Booked'], tone: 'success' },
      { term: 'Service level', lines: ['Standard'] },
      { term: 'Price', lines: ['18.81 EUR'], spoken: '18.81 euros' },
      { term: 'Booked', lines: ['10 Nov 2026, 09:05'] },
      { term: 'Origin', lines: ['NO 0150'] },
      { term: 'Recipient', lines: ['Synthetic Recipient 1', '1 Example Street', '11122 Exampleby', 'SE'] },
      { term: 'Reference', lines: ['ORDER-1001'] },
      { term: 'Quote ID', lines: ['5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21'], code: true },
      { term: 'Offer ID', lines: ['0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a02'], code: true },
    ]);
  });

  test('a cancelled shipment shows its cancelled time; optional lines appear only when present', () => {
    const { reference: _reference, ...rest } = shipment;
    const cancelled: Shipment = {
      ...rest, status: 'CANCELLED', cancelledAt: '2026-11-10T10:00:00.000Z',
      recipient: { ...shipment.recipient, addressLine2: 'Floor 2' },
    };
    const details = drawerDetails(cancelled, 'UTC');
    assert.deepEqual(details.find((detail) => detail.term === 'Cancelled')?.lines, ['10 Nov 2026, 10:00']);
    assert.deepEqual(details.find((detail) => detail.term === 'Recipient')?.lines, ['Synthetic Recipient 1', '1 Example Street', 'Floor 2', '11122 Exampleby', 'SE']);
    assert.equal(details.some((detail) => detail.term === 'Reference'), false);
  });

  test('offers the label download only for a booked shipment', () => {
    assert.equal(canDownloadLabel({ kind: 'loaded', shipment }), true);
    assert.equal(canDownloadLabel({ kind: 'loaded', shipment: { ...shipment, status: 'CANCELLED', cancelledAt: '2026-11-10T10:00:00.000Z' } }), false);
    assert.equal(canDownloadLabel({ kind: 'not-found' }), false);
  });
});

describe('the label download', () => {
  test('reports the response filename without claiming the browser saved the file', () => {
    const label: Label = { blob: new Blob(['<svg/>'], { type: 'image/svg+xml' }), fileName: 'label-SIM0000000001.svg' };
    const state = labelSettled({ ok: true, value: label, status: 200, replayed: false });
    assert.equal(state.kind, 'ready');
    assert.equal(labelMessage(state), 'Label ready: label-SIM0000000001.svg. Check your browser downloads.');
  });

  test('a cancelled shipment has no label; a network failure can be tried again', () => {
    const cancelled = labelSettled(failure<Label>('shipment-cancelled', 409));
    assert.equal(labelMessage(cancelled), 'This shipment is cancelled, so it has no label.');
    const offline = labelSettled({ ok: false, failure: { kind: 'network' } });
    assert.equal(labelMessage(offline), 'The server could not be reached.');
    assert.equal(labelMessage({ kind: 'idle' }), undefined);
    assert.equal(labelMessage({ kind: 'downloading' }), undefined);
  });
});
