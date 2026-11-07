import assert from 'node:assert/strict';
import { describe, test } from 'node:test';
import type { QuoteRequest } from '../../api/types.ts';
import {
  EMPTY_PARCEL_FIELDS, SERVED_COUNTRIES, checkParcelFields, fieldsFromRequest, mapServerErrors, normalizeCode, orderedErrors,
  parseWholeNumber, toQuoteRequest,
} from './ParcelFormModel.ts';
import type { ParcelFields } from './ParcelFormModel.ts';

const example: ParcelFields = {
  ...EMPTY_PARCEL_FIELDS,
  weight: '1200', length: '300', width: '200', height: '100',
  originCountry: 'NO', originPostcode: '0150', destinationCountry: 'SE', destinationPostcode: '11122',
};

const exampleRequest: QuoteRequest = {
  parcel: { weightGrams: 1200, lengthMm: 300, widthMm: 200, heightMm: 100 },
  origin: { countryCode: 'NO', postcode: '0150' },
  destination: { countryCode: 'SE', postcode: '11122' },
};

describe('the fields', () => {
  test('start empty, with service level Any', () => {
    assert.deepEqual(Object.values(EMPTY_PARCEL_FIELDS), Array(11).fill(''));
    assert.deepEqual([...SERVED_COUNTRIES], ['DE', 'DK', 'FI', 'NL', 'NO', 'SE']);
  });

  test('become the contract\'s request; Any omits the service level', () => {
    assert.deepEqual(toQuoteRequest(example), exampleRequest);
    assert.deepEqual(toQuoteRequest({ ...example, serviceLevel: 'EXPRESS' }), { ...exampleRequest, serviceLevel: 'EXPRESS' });
    assert.equal('serviceLevel' in toQuoteRequest(example), false);
  });

  test('postcodes and other country codes are sent in upper case, as on blur', () => {
    assert.equal(normalizeCode(' ab1 2cd'), ' AB1 2CD');
    const request = toQuoteRequest({ ...example, destinationCountry: 'OTHER', destinationOther: 'us', destinationPostcode: 'ab-12' });
    assert.deepEqual(request.destination, { countryCode: 'US', postcode: 'AB-12' });
  });

  test('only digits make a whole number; everything else is left for validation to reject', () => {
    assert.equal(parseWholeNumber('1200'), 1200);
    assert.equal(parseWholeNumber(' 1200 '), 1200);
    for (const raw of ['', '1,200', '12.5', '1e3', '-5', '0x10', 'twelve']) {
      assert.ok(Number.isNaN(parseWholeNumber(raw)), raw);
    }
  });

  test('a prefilled form shows a quote\'s parcel and route again', () => {
    assert.deepEqual(fieldsFromRequest(exampleRequest), example);
    const other = fieldsFromRequest({ ...exampleRequest, destination: { countryCode: 'US', postcode: '10001' }, serviceLevel: 'STANDARD' });
    assert.equal(other.destinationCountry, 'OTHER');
    assert.equal(other.destinationOther, 'US');
    assert.equal(other.serviceLevel, 'STANDARD');
  });
});

describe('checks before sending', () => {
  test('a valid form has no errors and yields the request', () => {
    assert.deepEqual(checkParcelFields(example), { request: exampleRequest, errors: {} });
  });

  test('each contract bound shows next to its field, saying what to do', () => {
    const { errors } = checkParcelFields({ ...example, weight: '0', height: '1201', destinationPostcode: '' });
    assert.deepEqual(errors, {
      weight: 'Enter a whole number of grams from 1 to 30000.',
      height: 'Enter a whole number of millimetres from 1 to 1200.',
      destinationPostcode: 'Enter 2 to 10 capital letters, digits, spaces or hyphens, starting and ending with a letter or digit.',
    });
  });

  test('an unchosen country asks for a choice; a malformed other code asks for two letters', () => {
    const { errors } = checkParcelFields({ ...example, originCountry: '', destinationCountry: 'OTHER', destinationOther: 'usa' });
    assert.deepEqual(errors, {
      originCountry: 'Choose the origin country.',
      destinationOther: 'Enter a two-letter country code in capitals, for example SE.',
    });
  });

  test('errors are listed in the order of the form, for the summary', () => {
    const { errors } = checkParcelFields({ ...EMPTY_PARCEL_FIELDS });
    assert.deepEqual(orderedErrors(errors).map((error) => error.field), [
      'weight', 'length', 'width', 'height', 'originCountry', 'originPostcode', 'destinationCountry', 'destinationPostcode',
    ]);
  });
});

describe('the server\'s validation-failed', () => {
  test('maps each body pointer to its field and keeps what it cannot place for the summary', () => {
    const fields = { ...example, destinationCountry: 'OTHER', destinationOther: 'XX' };
    const mapped = mapServerErrors(fields, [
      { in: 'body', name: '/parcel/weightGrams', detail: 'must be between 1 and 30000' },
      { in: 'body', name: '/destination/countryCode', detail: 'must match ^[A-Z]{2}$' },
      { in: 'body', name: '/origin/postcode', detail: 'must not be blank' },
      { in: 'body', name: '/unknownMember', detail: 'is not allowed' },
      { in: 'header', name: 'Correlation-Id', detail: 'is malformed' },
    ]);
    assert.deepEqual(mapped.errors, {
      weight: 'must be between 1 and 30000',
      destinationOther: 'must match ^[A-Z]{2}$',
      originPostcode: 'must not be blank',
    });
    assert.deepEqual(mapped.unplaced, ['/unknownMember: is not allowed', 'Correlation-Id: is malformed']);
  });
});
