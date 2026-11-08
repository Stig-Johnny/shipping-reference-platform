import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, test } from 'node:test';
import type { BookingRequest, QuoteRequest } from './types.ts';
import { LIMITS, PATTERNS, validateBookingRequest, validateQuoteRequest } from './validation.ts';

const CONTRACT = readFileSync(new URL('../../../../contracts/shipping.openapi.yaml', import.meta.url), 'utf8').split('\n');

/** The lines of one entry under components (a schema or a parameter), read as text. */
function block(name: string): string[] {
  const start = CONTRACT.indexOf(`    ${name}:`);
  assert.notEqual(start, -1, `the contract has no component ${name}`);
  const end = CONTRACT.findIndex((line, index) => index > start && /^ {0,4}\S/.test(line));
  return CONTRACT.slice(start + 1, end);
}

/** The value of `key:` in a block, after the line that names `property:` when one is given. */
function value(name: string, key: string, property?: string): string {
  const lines = block(name);
  const from = property === undefined ? 0 : lines.findIndex((line) => line.trim() === `${property}:`);
  assert.notEqual(from, -1, `${name} has no property ${property}`);
  const line = lines.slice(from).find((candidate) => candidate.trim().startsWith(`${key}:`));
  assert.ok(line, `${name}${property === undefined ? '' : `.${property}`} has no ${key}`);
  return line.trim().slice(key.length + 1).trim().replace(/^'|'$/g, '');
}

const valid: QuoteRequest = {
  parcel: { weightGrams: 1200, lengthMm: 300, widthMm: 200, heightMm: 100 },
  origin: { countryCode: 'NO', postcode: '0150' },
  destination: { countryCode: 'SE', postcode: '11122' },
};

const booking: BookingRequest = {
  quoteId: '5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21',
  offerId: '0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a02',
  recipient: { name: 'Synthetic Recipient 1', addressLine1: '1 Example Street', postcode: '11122', city: 'Exampleby', countryCode: 'SE' },
  reference: 'ORDER-1001',
};

function pointers(errors: ReadonlyArray<{ readonly name: string }>): string[] {
  return errors.map((error) => error.name);
}

describe('the checks mirror the contract', () => {
  test('patterns are the contract\'s, character for character', () => {
    assert.equal(PATTERNS.countryCode, value('CountryCode', 'pattern'));
    assert.equal(PATTERNS.postcode, value('Postcode', 'pattern'));
    assert.equal(PATTERNS.text, value('Recipient', 'pattern', 'name'));
    assert.equal(PATTERNS.text, value('Recipient', 'pattern', 'city'));
    assert.equal(PATTERNS.reference, value('ShipmentReference', 'pattern'));
    assert.equal(PATTERNS.uuid, value('Uuid', 'pattern'));
  });

  test('bounds and lengths are the contract\'s', () => {
    assert.equal(LIMITS.weightGrams.min, Number(value('Parcel', 'minimum', 'weightGrams')));
    assert.equal(LIMITS.weightGrams.max, Number(value('Parcel', 'maximum', 'weightGrams')));
    const sideValues1 = ['lengthMm', 'widthMm', 'heightMm'];
    for (const side of sideValues1) {
      assert.equal(LIMITS.sideMm.min, Number(value('Parcel', 'minimum', side)));
      assert.equal(LIMITS.sideMm.max, Number(value('Parcel', 'maximum', side)));
    }
    assert.equal(LIMITS.name, Number(value('Recipient', 'maxLength', 'name')));
    assert.equal(LIMITS.addressLine, Number(value('Recipient', 'maxLength', 'addressLine1')));
    assert.equal(LIMITS.addressLine, Number(value('Recipient', 'maxLength', 'addressLine2')));
    assert.equal(LIMITS.city, Number(value('Recipient', 'maxLength', 'city')));
    assert.equal(LIMITS.reference, Number(value('ShipmentReference', 'maxLength')));
  });
});

describe('validateQuoteRequest', () => {
  test('accepts the contract\'s example, with and without a service level', () => {
    assert.deepEqual(validateQuoteRequest(valid), []);
    assert.deepEqual(validateQuoteRequest({ ...valid, serviceLevel: 'EXPRESS' }), []);
  });

  test('weight is a whole number of grams from 1 to 30000', () => {
    const weightGramsValues2 = [1, 30000];
    for (const weightGrams of weightGramsValues2) {
      assert.deepEqual(validateQuoteRequest({ ...valid, parcel: { ...valid.parcel, weightGrams } }), []);
    }
    const weightGramsValues3 = [0, 30001, 12.5, Number.NaN, -1];
    for (const weightGrams of weightGramsValues3) {
      const errors = validateQuoteRequest({ ...valid, parcel: { ...valid.parcel, weightGrams } });
      assert.deepEqual(errors, [{ in: 'body', name: '/parcel/weightGrams', detail: 'Enter a whole number of grams from 1 to 30000.' }], String(weightGrams));
    }
  });

  test('each side is a whole number of millimetres from 1 to 1200', () => {
    const errors = validateQuoteRequest({ ...valid, parcel: { weightGrams: 1200, lengthMm: 0, widthMm: 1201, heightMm: 1200 } });
    assert.deepEqual(pointers(errors), ['/parcel/lengthMm', '/parcel/widthMm']);
    assert.equal(errors[0]?.detail, 'Enter a whole number of millimetres from 1 to 1200.');
  });

  test('country codes are two capital letters; any well-formed code passes, the server decides what is served', () => {
    assert.deepEqual(validateQuoteRequest({ ...valid, destination: { countryCode: 'US', postcode: '10001' } }), []);
    const countryCodeValues4 = ['se', 'S', 'SWE', ''];
    for (const countryCode of countryCodeValues4) {
      const errors = validateQuoteRequest({ ...valid, origin: { countryCode, postcode: '0150' } });
      assert.deepEqual(pointers(errors), ['/origin/countryCode'], countryCode);
    }
  });

  test('postcodes are 2 to 10 capitals, digits, spaces or hyphens, with a letter or digit at each end', () => {
    const postcodeValues5 = ['01', '0150', 'AB1 2CD', '12-345', '1234567890'];
    for (const postcode of postcodeValues5) {
      assert.deepEqual(validateQuoteRequest({ ...valid, destination: { countryCode: 'SE', postcode } }), [], postcode);
    }
    const postcodeValues6 = ['1', '-123', '123-', 'ab12', '12345678901', ''];
    for (const postcode of postcodeValues6) {
      assert.deepEqual(pointers(validateQuoteRequest({ ...valid, destination: { countryCode: 'SE', postcode } })), ['/destination/postcode'], postcode);
    }
  });
});

describe('validateBookingRequest', () => {
  test('accepts the contract\'s example and the optional fields absent', () => {
    assert.deepEqual(validateBookingRequest(booking), []);
    const { reference: _reference, ...withoutReference } = booking;
    assert.deepEqual(validateBookingRequest(withoutReference), []);
    assert.deepEqual(validateBookingRequest({ ...booking, recipient: { ...booking.recipient, addressLine2: 'Floor 2' } }), []);
  });

  test('text fields need 1 to their maximum characters and no control characters', () => {
    const recipient = { ...booking.recipient, name: '', addressLine1: 'x'.repeat(71), addressLine2: 'Line\nbreak', city: 'y'.repeat(51) };
    const errors = validateBookingRequest({ ...booking, recipient });
    assert.deepEqual(pointers(errors), ['/recipient/name', '/recipient/addressLine1', '/recipient/addressLine2', '/recipient/city']);
    assert.deepEqual(validateBookingRequest({ ...booking, recipient: { ...booking.recipient, name: 'n'.repeat(70), city: 'c'.repeat(50) } }), []);
  });

  test('text fields must be well-formed Unicode: a lone surrogate is rejected, as the contract says', () => {
    const loneValues7 = ['\uD800', 'Exampleby \uDFFF', '\uDC00\uD800'];
    for (const lone of loneValues7) {
      const recipient = { ...booking.recipient, name: lone, addressLine1: lone, addressLine2: lone, city: lone };
      assert.deepEqual(pointers(validateBookingRequest({ ...booking, recipient })),
        ['/recipient/name', '/recipient/addressLine1', '/recipient/addressLine2', '/recipient/city'], JSON.stringify(lone));
    }
    assert.deepEqual(validateBookingRequest({ ...booking, recipient: { ...booking.recipient, name: 'Synthetic 📦' } }), []);
  });

  test('lengths count characters, not UTF-16 code units', () => {
    assert.deepEqual(validateBookingRequest({ ...booking, recipient: { ...booking.recipient, city: '\u{1F4E6}'.repeat(50) } }), []);
  });

  test('the reference allows letters, digits, space and . _ / - up to 35 characters', () => {
    assert.deepEqual(validateBookingRequest({ ...booking, reference: 'ORDER/1001 a.b_c-d' }), []);
    assert.deepEqual(pointers(validateBookingRequest({ ...booking, reference: 'ORDER#1' })), ['/reference']);
    assert.deepEqual(pointers(validateBookingRequest({ ...booking, reference: 'R'.repeat(36) })), ['/reference']);
  });

  test('the IDs are lower-case UUIDs', () => {
    assert.deepEqual(pointers(validateBookingRequest({ ...booking, quoteId: booking.quoteId.toUpperCase(), offerId: 'x' })), ['/quoteId', '/offerId']);
  });

  test('the field messages say what to do', () => {
    const errors = validateBookingRequest({ ...booking, recipient: { ...booking.recipient, name: '' }, reference: '#' });
    assert.deepEqual(errors.map((error) => error.detail), [
      'Enter the recipient\'s name: 1 to 70 characters, no line breaks.',
      'Use up to 35 letters, digits, spaces and the characters . _ / -',
    ]);
  });
});
