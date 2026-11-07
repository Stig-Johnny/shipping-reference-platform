import assert from 'node:assert/strict';
import { describe, test } from 'node:test';
import type { Quote } from '../../api/types.ts';
import { isQuoteExpired, msUntilExpiry, offerCards, quoteSummary } from './QuoteCardListModel.ts';

const quote: Quote = {
  quoteId: '5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21', status: 'OPEN',
  issuedAt: '2026-11-10T09:00:00.000Z', expiresAt: '2026-11-10T09:30:00.000Z',
  parcel: { weightGrams: 1200, lengthMm: 300, widthMm: 200, heightMm: 100 },
  origin: { countryCode: 'NO', postcode: '0150' }, destination: { countryCode: 'SE', postcode: '11122' },
  zone: 'INTERNATIONAL', billableWeightGrams: 1200,
  offers: [
    { offerId: '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a01', serviceLevel: 'ECONOMY', price: { amount: '15.05', currency: 'EUR' }, transitDays: { minDays: 5, maxDays: 8 } },
    { offerId: '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a02', serviceLevel: 'STANDARD', price: { amount: '18.81', currency: 'EUR' }, transitDays: { minDays: 3, maxDays: 5 } },
    { offerId: '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a03', serviceLevel: 'EXPRESS', price: { amount: '28.22', currency: 'EUR' }, transitDays: { minDays: 1, maxDays: 2 } },
  ],
};

const open = { expired: false, frozen: false, selectedOfferId: undefined };
const at = (time: string) => Date.parse(`2026-11-10T${time}Z`);

describe('offer cards', () => {
  test('one card per offer, in the order received, with the prices exactly as the server sent them', () => {
    const cards = offerCards(quote, open);
    assert.deepEqual(cards.map((card) => card.heading), ['Economy', 'Standard', 'Express']);
    assert.deepEqual(cards.map((card) => card.price.visual), ['15.05 EUR', '18.81 EUR', '28.22 EUR']);
    assert.deepEqual(cards.map((card) => card.price.spoken), ['15.05 euros', '18.81 euros', '28.22 euros']);
    assert.deepEqual(cards.map((card) => card.transit), ['5 to 8 days', '3 to 5 days', '1 to 2 days']);
    assert.deepEqual(cards.map((card) => [card.checked, card.disabled, card.badge]), [[false, false, undefined], [false, false, undefined], [false, false, undefined]]);
  });

  test('the chosen offer is checked', () => {
    const cards = offerCards(quote, { ...open, selectedOfferId: '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a02' });
    assert.deepEqual(cards.map((card) => card.checked), [false, true, false]);
  });

  test('expired: every card shows "Expired" and none can be chosen', () => {
    const cards = offerCards(quote, { ...open, expired: true, selectedOfferId: '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a02' });
    assert.deepEqual(cards.map((card) => [card.badge, card.disabled]), [['Expired', true], ['Expired', true], ['Expired', true]]);
  });

  test('while a booking is under way or done, the choice is frozen without claiming expiry', () => {
    const cards = offerCards(quote, { ...open, frozen: true, selectedOfferId: '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a01' });
    assert.deepEqual(cards.map((card) => [card.disabled, card.badge, card.checked]), [[true, undefined, true], [true, undefined, false], [true, undefined, false]]);
  });

  test('a quote for one requested level has one card', () => {
    const express = quote.offers[2];
    assert.ok(express);
    assert.deepEqual(offerCards({ ...quote, offers: [express] }, open).map((card) => card.heading), ['Express']);
  });
});

describe('expiry', () => {
  test('the clock decides until expiresAt; the server\'s EXPIRED decides at once', () => {
    assert.equal(isQuoteExpired(quote, at('09:29:59.999')), false);
    assert.equal(isQuoteExpired(quote, at('09:30:00.000')), true);
    assert.equal(isQuoteExpired({ ...quote, status: 'EXPIRED' }, at('09:00:00.000')), true);
  });

  test('an unreadable expiry counts as expired, so nothing is offered that the server would refuse', () => {
    assert.equal(isQuoteExpired({ ...quote, expiresAt: 'soon' }, at('09:00:00.000')), true);
  });

  test('the page schedules the switch to expired for the exact moment', () => {
    assert.equal(msUntilExpiry(quote, at('09:29:00.000')), 60_000);
    assert.equal(msUntilExpiry(quote, at('09:31:00.000')), 0);
  });
});

describe('the quote header', () => {
  test('route, parcel, billable weight and zone', () => {
    assert.deepEqual(quoteSummary(quote), {
      from: 'NO 0150', to: 'SE 11122', parcel: '1,200 g, 300 × 200 × 100 mm', billable: 'Billable weight 1,200 g', zone: 'International',
    });
    assert.equal(quoteSummary({ ...quote, zone: 'DOMESTIC' }).zone, 'Domestic');
  });
});
