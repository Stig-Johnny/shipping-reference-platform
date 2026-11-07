// Client-side checks that mirror the contract's bounds and patterns, so that most errors show before anything is
// sent. They report like the server's validation-failed (one InvalidParam per JSON Pointer), so a form shows both the
// same way; the server stays authoritative. validation.test.ts compares PATTERNS and LIMITS with the contract file.

import type { BookingRequest, InvalidParam, Place, QuoteRequest } from './types.ts';

/** The contract's `pattern` values, character for character. */
export const PATTERNS = {
  countryCode: '^[A-Z]{2}$',
  postcode: '^[A-Z0-9][A-Z0-9 -]{0,8}[A-Z0-9]$',
  text: '^[^\\u0000-\\u001F\\u007F]+$',
  reference: '^[A-Za-z0-9 ._/-]+$',
  uuid: '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
} as const;

/** The contract's bounds: minimum and maximum of the parcel, maxLength of the recipient and reference. */
export const LIMITS = {
  weightGrams: { min: 1, max: 30000 },
  sideMm: { min: 1, max: 1200 },
  name: 70,
  addressLine: 70,
  city: 50,
  reference: 35,
} as const;

const COUNTRY_CODE = new RegExp(PATTERNS.countryCode, 'u');
const POSTCODE = new RegExp(PATTERNS.postcode, 'u');
const TEXT = new RegExp(PATTERNS.text, 'u');
const REFERENCE = new RegExp(PATTERNS.reference, 'u');
const UUID = new RegExp(PATTERNS.uuid, 'u');

const MESSAGES = {
  weight: `Enter a whole number of grams from ${LIMITS.weightGrams.min} to ${LIMITS.weightGrams.max}.`,
  side: `Enter a whole number of millimetres from ${LIMITS.sideMm.min} to ${LIMITS.sideMm.max}.`,
  countryCode: 'Enter a two-letter country code in capitals, for example SE.',
  postcode: 'Enter 2 to 10 capital letters, digits, spaces or hyphens, starting and ending with a letter or digit.',
  name: `Enter the recipient's name: 1 to ${LIMITS.name} characters, no line breaks.`,
  addressLine1: `Enter address line 1: 1 to ${LIMITS.addressLine} characters, no line breaks.`,
  addressLine2: `Leave address line 2 empty, or enter up to ${LIMITS.addressLine} characters with no line breaks.`,
  city: `Enter the city: 1 to ${LIMITS.city} characters, no line breaks.`,
  reference: `Use up to ${LIMITS.reference} letters, digits, spaces and the characters . _ / -`,
  id: 'The quote or offer is not valid. Get a fresh quote.',
} as const;

function invalid(pointer: string, detail: string): InvalidParam {
  return { in: 'body', name: pointer, detail };
}

function wholeNumberWithin(value: number, bounds: { readonly min: number; readonly max: number }): boolean {
  return Number.isInteger(value) && value >= bounds.min && value <= bounds.max;
}

/** A code point that is half of a surrogate pair standing alone; iterating a string yields it as its own unit. */
function isLoneSurrogate(character: string): boolean {
  const codePoint = character.codePointAt(0) ?? 0;
  return codePoint >= 0xd800 && codePoint <= 0xdfff;
}

/**
 * Length in characters (code points), as JSON Schema counts it, not in UTF-16 code units; well-formed Unicode only,
 * because the contract rejects a lone surrogate in these fields.
 */
function textWithin(value: string, maxLength: number): boolean {
  const characters = [...value];
  return characters.length >= 1 && characters.length <= maxLength && TEXT.test(value)
    && !characters.some(isLoneSurrogate);
}

function checkPlace(errors: InvalidParam[], pointer: string, place: Place): void {
  if (!COUNTRY_CODE.test(place.countryCode)) {
    errors.push(invalid(`${pointer}/countryCode`, MESSAGES.countryCode));
  }
  if (!POSTCODE.test(place.postcode)) {
    errors.push(invalid(`${pointer}/postcode`, MESSAGES.postcode));
  }
}

export function validateQuoteRequest(request: QuoteRequest): InvalidParam[] {
  const errors: InvalidParam[] = [];
  const { parcel } = request;
  if (!wholeNumberWithin(parcel.weightGrams, LIMITS.weightGrams)) {
    errors.push(invalid('/parcel/weightGrams', MESSAGES.weight));
  }
  for (const side of ['lengthMm', 'widthMm', 'heightMm'] as const) {
    if (!wholeNumberWithin(parcel[side], LIMITS.sideMm)) {
      errors.push(invalid(`/parcel/${side}`, MESSAGES.side));
    }
  }
  checkPlace(errors, '/origin', request.origin);
  checkPlace(errors, '/destination', request.destination);
  return errors;
}

export function validateBookingRequest(request: BookingRequest): InvalidParam[] {
  const errors: InvalidParam[] = [];
  if (!UUID.test(request.quoteId)) {
    errors.push(invalid('/quoteId', MESSAGES.id));
  }
  if (!UUID.test(request.offerId)) {
    errors.push(invalid('/offerId', MESSAGES.id));
  }
  const { recipient } = request;
  if (!textWithin(recipient.name, LIMITS.name)) {
    errors.push(invalid('/recipient/name', MESSAGES.name));
  }
  if (!textWithin(recipient.addressLine1, LIMITS.addressLine)) {
    errors.push(invalid('/recipient/addressLine1', MESSAGES.addressLine1));
  }
  if (recipient.addressLine2 !== undefined && !textWithin(recipient.addressLine2, LIMITS.addressLine)) {
    errors.push(invalid('/recipient/addressLine2', MESSAGES.addressLine2));
  }
  if (!POSTCODE.test(recipient.postcode)) {
    errors.push(invalid('/recipient/postcode', MESSAGES.postcode));
  }
  if (!textWithin(recipient.city, LIMITS.city)) {
    errors.push(invalid('/recipient/city', MESSAGES.city));
  }
  if (!COUNTRY_CODE.test(recipient.countryCode)) {
    errors.push(invalid('/recipient/countryCode', MESSAGES.countryCode));
  }
  if (request.reference !== undefined
    && !([...request.reference].length <= LIMITS.reference && REFERENCE.test(request.reference))) {
    errors.push(invalid('/reference', MESSAGES.reference));
  }
  return errors;
}
