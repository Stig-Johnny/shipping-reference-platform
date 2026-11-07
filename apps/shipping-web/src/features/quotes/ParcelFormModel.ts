// ParcelForm without a DOM: the field values as typed, the request they make, the client checks (the contract's
// bounds and patterns, from api/validation.ts) and the mapping of the server's validation-failed back to the fields.

import { validateQuoteRequest } from '../../api/validation.ts';
import { SERVICE_LEVELS } from '../../api/types.ts';
import type { InvalidParam, Place, QuoteRequest } from '../../api/types.ts';

export const SERVED_COUNTRIES = ['DE', 'DK', 'FI', 'NL', 'NO', 'SE'] as const;
export const OTHER_COUNTRY = 'OTHER';

export interface ParcelFields {
  readonly weight: string;
  readonly length: string;
  readonly width: string;
  readonly height: string;
  /** '' until chosen, a served country, or OTHER with the code in originOther. */
  readonly originCountry: string;
  readonly originOther: string;
  readonly originPostcode: string;
  readonly destinationCountry: string;
  readonly destinationOther: string;
  readonly destinationPostcode: string;
  /** '' is "Any". */
  readonly serviceLevel: string;
}

export type ParcelField = keyof ParcelFields;
export type ParcelErrors = Partial<Record<ParcelField, string>>;

/** The order of the fields in the form, which is also the order of the error summary. */
export const PARCEL_FIELD_ORDER: readonly ParcelField[] = [
  'weight', 'length', 'width', 'height', 'originCountry', 'originOther', 'originPostcode',
  'destinationCountry', 'destinationOther', 'destinationPostcode', 'serviceLevel',
];

export const EMPTY_PARCEL_FIELDS: ParcelFields = {
  weight: '', length: '', width: '', height: '',
  originCountry: '', originOther: '', originPostcode: '',
  destinationCountry: '', destinationOther: '', destinationPostcode: '',
  serviceLevel: '',
};

const WHOLE_NUMBER = /^[0-9]+$/;

/** Digits only, surrounding spaces allowed; anything else is NaN, which the contract checks reject. */
export function parseWholeNumber(raw: string): number {
  const trimmed = raw.trim();
  return WHOLE_NUMBER.test(trimmed) ? Number(trimmed) : Number.NaN;
}

/** Postcodes and country codes are upper case in the contract; the form converts them on blur and before sending. */
export function normalizeCode(value: string): string {
  return value.toUpperCase();
}

function place(country: string, other: string, postcode: string): Place {
  return { countryCode: normalizeCode(country === OTHER_COUNTRY ? other : country), postcode: normalizeCode(postcode) };
}

export function toQuoteRequest(fields: ParcelFields): QuoteRequest {
  const serviceLevel = SERVICE_LEVELS.find((level) => level === fields.serviceLevel);
  const request: QuoteRequest = {
    parcel: {
      weightGrams: parseWholeNumber(fields.weight),
      lengthMm: parseWholeNumber(fields.length),
      widthMm: parseWholeNumber(fields.width),
      heightMm: parseWholeNumber(fields.height),
    },
    origin: place(fields.originCountry, fields.originOther, fields.originPostcode),
    destination: place(fields.destinationCountry, fields.destinationOther, fields.destinationPostcode),
  };
  return serviceLevel === undefined ? request : { ...request, serviceLevel };
}

function countryFields(code: string): { readonly country: string; readonly other: string } {
  return SERVED_COUNTRIES.some((served) => served === code)
    ? { country: code, other: '' }
    : { country: OTHER_COUNTRY, other: code };
}

/** The form for an earlier request: "Edit parcel" and "Get a fresh quote" start from it. */
export function fieldsFromRequest(request: QuoteRequest): ParcelFields {
  const origin = countryFields(request.origin.countryCode);
  const destination = countryFields(request.destination.countryCode);
  return {
    weight: String(request.parcel.weightGrams),
    length: String(request.parcel.lengthMm),
    width: String(request.parcel.widthMm),
    height: String(request.parcel.heightMm),
    originCountry: origin.country,
    originOther: origin.other,
    originPostcode: request.origin.postcode,
    destinationCountry: destination.country,
    destinationOther: destination.other,
    destinationPostcode: request.destination.postcode,
    serviceLevel: request.serviceLevel ?? '',
  };
}

/** The field a JSON Pointer of the request names, given which country control is in use. */
function fieldFor(pointer: string, fields: ParcelFields): ParcelField | undefined {
  switch (pointer) {
    case '/parcel/weightGrams':
      return 'weight';
    case '/parcel/lengthMm':
      return 'length';
    case '/parcel/widthMm':
      return 'width';
    case '/parcel/heightMm':
      return 'height';
    case '/origin/countryCode':
      return fields.originCountry === OTHER_COUNTRY ? 'originOther' : 'originCountry';
    case '/origin/postcode':
      return 'originPostcode';
    case '/destination/countryCode':
      return fields.destinationCountry === OTHER_COUNTRY ? 'destinationOther' : 'destinationCountry';
    case '/destination/postcode':
      return 'destinationPostcode';
    case '/serviceLevel':
      return 'serviceLevel';
    default:
      return undefined;
  }
}

export interface MappedErrors {
  readonly errors: ParcelErrors;
  /** Errors no field can show, for the summary: "<name>: <detail>". */
  readonly unplaced: readonly string[];
}

export function mapServerErrors(fields: ParcelFields, invalid: readonly InvalidParam[]): MappedErrors {
  const errors: ParcelErrors = {};
  const unplaced: string[] = [];
  for (const item of invalid) {
    const field = item.in === 'body' ? fieldFor(item.name, fields) : undefined;
    if (field === undefined) {
      unplaced.push(`${item.name}: ${item.detail}`);
    } else if (errors[field] === undefined) {
      errors[field] = item.detail;
    }
  }
  return { errors, unplaced };
}

/** The contract checks before sending; an unchosen country asks for a choice rather than a code. */
export interface CheckedParcel {
  readonly request: QuoteRequest;
  readonly errors: ParcelErrors;
}

export function checkParcelFields(fields: ParcelFields): CheckedParcel {
  const request = toQuoteRequest(fields);
  const { errors } = mapServerErrors(fields, validateQuoteRequest(request));
  if (fields.originCountry === '') {
    errors.originCountry = 'Choose the origin country.';
  }
  if (fields.destinationCountry === '') {
    errors.destinationCountry = 'Choose the destination country.';
  }
  return { request, errors };
}

export function orderedErrors(errors: ParcelErrors): Array<{ readonly field: ParcelField; readonly message: string }> {
  return PARCEL_FIELD_ORDER.flatMap((field) => {
    const message = errors[field];
    return message === undefined ? [] : [{ field, message }];
  });
}
