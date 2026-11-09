// BookingForm without a DOM. A booking intent is one request with one Idempotency-Key: Retry and "Book again (same
// request)" resend that intent unchanged; an edit before submission makes a new one; after idempotency-key-reused the
// next submission gets a fresh key. While a booking is pending, unknown or done, its payload is frozen.

import { newBookingIntent } from '../../api/client.ts';
import type { ApiResult, BookingIntent } from '../../api/client.ts';
import { failureMessage, failureTitle } from '../../api/messages.ts';
import { isRetryable, problemType, referenceOf } from '../../api/problems.ts';
import type { ApiFailure } from '../../api/problems.ts';
import type { BookingRequest, InvalidParam, Place, Shipment } from '../../api/types.ts';
import { validateBookingRequest } from '../../api/validation.ts';
import { formatTime } from '../../ui/format.ts';

export interface RecipientFields {
  readonly name: string;
  readonly addressLine1: string;
  readonly addressLine2: string;
  readonly city: string;
  readonly reference: string;
}

export type RecipientField = keyof RecipientFields;
export type RecipientErrors = Partial<Record<RecipientField, string>>;

export const RECIPIENT_FIELD_ORDER: readonly RecipientField[] = [
  'name', 'addressLine1', 'addressLine2', 'city', 'reference',
];
export const EMPTY_RECIPIENT: RecipientFields = {
  name: '', addressLine1: '', addressLine2: '', city: '', reference: '',
};

/** What the quote page tells the form: the chosen offer and the quote's destination and expiry. */
export interface BookingTarget {
  readonly quoteId: string;
  readonly offerId: string;
  readonly destination: Place;
  readonly expiresAt: string;
}

export type BookingPhase =
  | { readonly kind: 'editing' }
  | { readonly kind: 'pending'; readonly intent: BookingIntent }
  /** No answer, or a server error: the booking may exist. Only the same intent may be sent again. */
  | { readonly kind: 'unknown'; readonly intent: BookingIntent }
  | { readonly kind: 'booked'; readonly intent: BookingIntent; readonly shipment: Shipment; readonly replayed: boolean }
  | { readonly kind: 'rejected'; readonly failure: ApiFailure };

export interface BookingRecord {
  readonly target: BookingTarget;
  readonly fields: RecipientFields;
  readonly phase: BookingPhase;
  /** The intent that a submission of the same payload sends again; undefined after idempotency-key-reused. */
  readonly lastIntent: BookingIntent | undefined;
  readonly errors: RecipientErrors;
  readonly unplaced: readonly string[];
}

export type OutcomeAction = 'open-shipment' | 'book-again' | 'retry' | 'open-shipments' | 'new-quote';

export interface OutcomeView {
  readonly tone: 'success' | 'warning' | 'danger';
  readonly heading?: string;
  readonly message: string;
  readonly reference?: string;
  readonly actions: readonly OutcomeAction[];
}

const UNKNOWN_OUTCOME = 'No answer arrived, so the shipment may or may not have been booked. Retry sends the identical '
  + 'request with the same key, which cannot book twice. Or open Shipments to check.';

export function newRecord(target: BookingTarget): BookingRecord {
  return {
    target, fields: EMPTY_RECIPIENT, phase: { kind: 'editing' }, lastIntent: undefined, errors: {}, unplaced: [],
  };
}

export function isFrozen(phase: BookingPhase): boolean {
  return phase.kind === 'pending' || phase.kind === 'unknown' || phase.kind === 'booked';
}

/** Pending or unknown: leaving now loses the only way to retry with the same key. */
export function isUnresolved(phase: BookingPhase): boolean {
  return phase.kind === 'pending' || phase.kind === 'unknown';
}

/** Postcode and country come from the quote's destination; empty optional fields are omitted; nothing is trimmed. */
export function buildRequest(target: BookingTarget, fields: RecipientFields): BookingRequest {
  return {
    quoteId: target.quoteId,
    offerId: target.offerId,
    recipient: {
      name: fields.name,
      addressLine1: fields.addressLine1,
      ...(fields.addressLine2 === '' ? {} : { addressLine2: fields.addressLine2 }),
      postcode: target.destination.postcode,
      city: fields.city,
      countryCode: target.destination.countryCode,
    },
    ...(fields.reference === '' ? {} : { reference: fields.reference }),
  };
}

/** Equal in every field the server's request fingerprint covers, absent and present told apart. */
export function samePayload(a: BookingRequest, b: BookingRequest): boolean {
  return a.quoteId === b.quoteId && a.offerId === b.offerId && a.recipient.name === b.recipient.name
    && a.recipient.addressLine1 === b.recipient.addressLine1 && a.recipient.addressLine2 === b.recipient.addressLine2
    && a.recipient.postcode === b.recipient.postcode && a.recipient.city === b.recipient.city
    && a.recipient.countryCode === b.recipient.countryCode && a.reference === b.reference;
}

export function withFields(record: BookingRecord, fields: RecipientFields): BookingRecord {
  return isFrozen(record.phase) ? record : { ...record, fields };
}

export function withTarget(record: BookingRecord, target: BookingTarget): BookingRecord {
  return isFrozen(record.phase) ? record : { ...record, target };
}

const FIELD_POINTERS: Readonly<Record<string, RecipientField>> = {
  '/recipient/name': 'name',
  '/recipient/addressLine1': 'addressLine1',
  '/recipient/addressLine2': 'addressLine2',
  '/recipient/city': 'city',
  '/reference': 'reference',
};

function mapErrors(invalid: readonly InvalidParam[]): { errors: RecipientErrors; unplaced: string[] } {
  const errors = new Map<RecipientField, string>();
  const unplaced: string[] = [];
  for (const item of invalid) {
    const field = item.in === 'body' ? new Map(Object.entries(FIELD_POINTERS)).get(item.name) : undefined;
    if (field === undefined) {
      unplaced.push(`${item.name}: ${item.detail}`);
    } else {
      if (!errors.has(field)) {
        errors.set(field, item.detail);
      }
    }
  }
  return { errors: Object.fromEntries(errors), unplaced };
}

export interface Submission {
  readonly record: BookingRecord;
  /** The intent to send, or undefined when nothing may be sent. */
  readonly intent: BookingIntent | undefined;
}

export function submit(record: BookingRecord, newKey?: () => string): Submission {
  if (isFrozen(record.phase)) {
    return { record, intent: undefined };
  }
  const request = buildRequest(record.target, record.fields);
  const invalid = validateBookingRequest(request);
  if (invalid.length > 0) {
    return { record: { ...record, phase: { kind: 'editing' }, ...mapErrors(invalid) }, intent: undefined };
  }
  const intent = record.lastIntent !== undefined && samePayload(record.lastIntent.request, request)
    ? record.lastIntent
    : newBookingIntent(request, newKey);
  return {
    record: { ...record, phase: { kind: 'pending', intent }, lastIntent: intent, errors: {}, unplaced: [] },
    intent,
  };
}

/** Retry after an unknown outcome, "Book again (same request)" after a booking, Try again after a server failure. */
export function resend(record: BookingRecord): Submission {
  const { phase } = record;
  const intent = phase.kind === 'unknown' || phase.kind === 'booked' ? phase.intent
    : phase.kind === 'rejected' && isRetryable(phase.failure) ? record.lastIntent : undefined;
  return intent === undefined
    ? { record, intent: undefined }
    : { record: { ...record, phase: { kind: 'pending', intent } }, intent };
}

export function settle(record: BookingRecord, intent: BookingIntent, result: ApiResult<Shipment>): BookingRecord {
  if (record.phase.kind !== 'pending' || record.phase.intent !== intent) {
    return record;
  }
  if (result.ok) {
    return { ...record, phase: { kind: 'booked', intent, shipment: result.value, replayed: result.replayed } };
  }
  const { failure } = result;
  if (failure.kind !== 'problem' || failure.problem.status >= 500) {
    return { ...record, phase: { kind: 'unknown', intent } };
  }
  switch (failure.problem.type) {
    case 'validation-failed':
      return { ...record, phase: { kind: 'editing' }, ...mapErrors(failure.problem.errors ?? []) };
    case 'idempotency-key-reused':
      return { ...record, phase: { kind: 'rejected', failure }, lastIntent: undefined };
    default:
      return { ...record, phase: { kind: 'rejected', failure } };
  }
}

function rejectedView(failure: ApiFailure, target: BookingTarget, timeZone: string | undefined): OutcomeView {
  const reference = referenceOf(failure);
  const failed = { heading: failureTitle(failure), ...(reference === undefined ? {} : { reference }) };
  switch (problemType(failure)) {
    case 'quote-expired':
      return {
        tone: 'warning', ...failed, actions: [],
        message: `This quote expired at ${formatTime(target.expiresAt, timeZone)}. Get a fresh quote.`,
      };
    case 'quote-already-booked':
      return { tone: 'danger', ...failed, message: failureMessage(failure), actions: ['open-shipments'] };
    case 'quote-not-found':
    case 'offer-not-found':
      return { tone: 'danger', ...failed, message: failureMessage(failure), actions: ['new-quote'] };
    default:
      return {
        tone: 'danger', ...failed, message: failureMessage(failure), actions: isRetryable(failure) ? ['retry'] : [],
      };
  }
}

/** What the form shows about the booking, or undefined while editing or pending. */
export function outcomeView(record: BookingRecord, timeZone?: string): OutcomeView | undefined {
  const { phase } = record;
  switch (phase.kind) {
    case 'editing':
    case 'pending':
      return undefined;
    case 'unknown':
      return {
        tone: 'warning', heading: 'Booking outcome unknown', message: UNKNOWN_OUTCOME,
        actions: ['retry', 'open-shipments'],
      };
    case 'booked': {
      const tracking = phase.shipment.trackingNumber;
      return {
        tone: 'success',
        message: phase.replayed
          ? `Already booked. This is the same shipment: ${tracking}.`
          : `Shipment booked: ${tracking}`,
        actions: ['open-shipment', 'book-again'],
      };
    }
    case 'rejected':
      return rejectedView(phase.failure, record.target, timeZone);
  }
}

/** Server-detected expiry: the quote page switches to its expired state. */
export function expiredByServer(record: BookingRecord | undefined): boolean {
  return record !== undefined && record.phase.kind === 'rejected'
    && problemType(record.phase.failure) === 'quote-expired';
}

export interface RecipientError {
  readonly field: RecipientField;
  readonly message: string;
}

export function orderedRecipientErrors(errors: RecipientErrors): RecipientError[] {
  return RECIPIENT_FIELD_ORDER.flatMap((field) => {
    const message = new Map(Object.entries(errors)).get(field);
    return message === undefined ? [] : [{ field, message }];
  });
}
