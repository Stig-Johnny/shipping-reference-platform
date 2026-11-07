import assert from 'node:assert/strict';
import { describe, test } from 'node:test';
import {
  formatDateTime, formatDimensions, formatGrams, formatPrice, formatTime, formatTransitDays, formatValidity, minutesLeft,
} from './format.ts';

describe('formatPrice', () => {
  test('shows the amount and currency as received, with the accessible name in words', () => {
    assert.deepEqual(formatPrice('18.81', 'EUR'), { visual: '18.81 EUR', spoken: '18.81 euros' });
    assert.deepEqual(formatPrice('0.00', 'EUR'), { visual: '0.00 EUR', spoken: '0.00 euros' });
  });

  test('groups thousands without turning the decimal string into a float', () => {
    assert.deepEqual(formatPrice('1234.50', 'EUR'), { visual: '1,234.50 EUR', spoken: '1,234.50 euros' });
    assert.deepEqual(formatPrice('999999999.99', 'EUR'), { visual: '999,999,999.99 EUR', spoken: '999,999,999.99 euros' });
  });

  test('never re-rounds or repairs an amount outside the contract: it shows it unchanged', () => {
    assert.deepEqual(formatPrice('12.345', 'EUR'), { visual: '12.345 EUR', spoken: '12.345 EUR' });
    assert.deepEqual(formatPrice('free', 'EUR'), { visual: 'free EUR', spoken: 'free EUR' });
    assert.deepEqual(formatPrice('1.00', 'XX'), { visual: '1.00 XX', spoken: '1.00 XX' });
  });
});

describe('dates and times', () => {
  test('times are 24-hour hours and minutes in the given time zone', () => {
    assert.equal(formatTime('2026-11-10T09:30:00.000Z', 'UTC'), '09:30');
    assert.equal(formatTime('2026-11-10T09:30:00.000Z', 'Europe/Oslo'), '10:30');
  });

  test('date and time for tables and the drawer', () => {
    assert.equal(formatDateTime('2026-11-10T09:05:00.000Z', 'UTC'), '10 Nov 2026, 09:05');
  });

  test('an unreadable timestamp is shown as received', () => {
    assert.equal(formatTime('not a time', 'UTC'), 'not a time');
    assert.equal(formatDateTime('', 'UTC'), '');
  });
});

describe('quote validity', () => {
  const expiresAt = '2026-11-10T09:30:00.000Z';
  const at = (time: string) => Date.parse(`2026-11-10T${time}Z`);

  test('minutes left round up, so the last minute still reads 1 min', () => {
    assert.equal(minutesLeft(expiresAt, at('09:18:00.000')), 12);
    assert.equal(minutesLeft(expiresAt, at('09:18:30.000')), 12);
    assert.equal(minutesLeft(expiresAt, at('09:29:59.000')), 1);
    assert.equal(minutesLeft(expiresAt, at('09:30:00.000')), 0);
    assert.equal(minutesLeft(expiresAt, at('09:45:00.000')), 0);
  });

  test('reads "Valid until 09:30 (in 12 min)" until expiry, then "Expired at 09:30"', () => {
    assert.deepEqual(formatValidity(expiresAt, at('09:18:00.000'), 'UTC'), { until: 'Valid until 09:30', relative: '(in 12 min)', expired: false });
    assert.deepEqual(formatValidity(expiresAt, at('09:30:00.000'), 'UTC'), { until: 'Expired at 09:30', relative: '', expired: true });
  });
});

describe('quantities', () => {
  test('transit days', () => {
    assert.equal(formatTransitDays({ minDays: 3, maxDays: 5 }), '3 to 5 days');
    assert.equal(formatTransitDays({ minDays: 1, maxDays: 1 }), '1 day');
    assert.equal(formatTransitDays({ minDays: 2, maxDays: 2 }), '2 days');
  });

  test('weights and dimensions', () => {
    assert.equal(formatGrams(1200), '1,200 g');
    assert.equal(formatDimensions({ lengthMm: 300, widthMm: 200, heightMm: 100 }), '300 × 200 × 100 mm');
  });
});
