// Shape checks for response bodies. A body that fails them is reported as an unexpected response instead of reaching
// the screens: the contract tests on the server are the authority, and this keeps the browser from rendering guesses.

import { FLAG_KEYS, QUOTE_STATUSES, SERVICE_LEVELS, SHIPMENT_STATUSES, ZONES } from './types.ts';
import type {
  Membership, Money, Offer, Parcel, Place, Quote, Recipient, Shipment, ShipmentPage, ShipmentSummary,
} from './types.ts';

type Fields = Readonly<Record<string, unknown>>;

export function isRecord(value: unknown): value is Fields {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isString(value: unknown): value is string {
  return typeof value === 'string';
}

function isOptionalString(value: unknown): boolean {
  return value === undefined || typeof value === 'string';
}

function isInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value);
}

function isOneOf<T extends string>(values: readonly T[], value: unknown): value is T {
  return values.some((candidate) => candidate === value);
}

function isMoney(value: unknown): value is Money {
  return isRecord(value) && isString(value.amount) && value.currency === 'EUR';
}

function isPlace(value: unknown): value is Place {
  return isRecord(value) && isString(value.countryCode) && isString(value.postcode);
}

function isParcel(value: unknown): value is Parcel {
  return isRecord(value) && isInteger(value.weightGrams) && isInteger(value.lengthMm) && isInteger(value.widthMm)
    && isInteger(value.heightMm);
}

function isOffer(value: unknown): value is Offer {
  return isRecord(value) && isString(value.offerId) && isOneOf(SERVICE_LEVELS, value.serviceLevel)
    && isMoney(value.price) && isRecord(value.transitDays) && isInteger(value.transitDays.minDays)
    && isInteger(value.transitDays.maxDays);
}

function isRecipient(value: unknown): value is Recipient {
  return isRecord(value) && isString(value.name) && isString(value.addressLine1) && isOptionalString(value.addressLine2)
    && isString(value.postcode) && isString(value.city) && isString(value.countryCode);
}

export function isMembership(value: unknown): value is Membership {
  return isRecord(value) && isString(value.userId) && isString(value.userDisplayName) && isString(value.tenantId)
    && isString(value.tenantDisplayName) && value.role === 'CLERK' && Array.isArray(value.enabledFlags)
    && value.enabledFlags.every((flag) => isOneOf(FLAG_KEYS, flag));
}

export function isQuote(value: unknown): value is Quote {
  return isRecord(value) && isString(value.quoteId) && isOneOf(QUOTE_STATUSES, value.status) && isString(value.issuedAt)
    && isString(value.expiresAt) && isParcel(value.parcel) && isPlace(value.origin) && isPlace(value.destination)
    && isOneOf(ZONES, value.zone) && isInteger(value.billableWeightGrams) && Array.isArray(value.offers)
    && value.offers.length > 0 && value.offers.every(isOffer);
}

export function isShipment(value: unknown): value is Shipment {
  return isRecord(value) && isString(value.shipmentId) && isString(value.quoteId) && isString(value.offerId)
    && isOneOf(SHIPMENT_STATUSES, value.status) && isOneOf(SERVICE_LEVELS, value.serviceLevel) && isMoney(value.price)
    && isString(value.trackingNumber) && isPlace(value.origin) && isRecipient(value.recipient)
    && isOptionalString(value.reference) && isString(value.bookedAt) && isOptionalString(value.cancelledAt);
}

function isShipmentSummary(value: unknown): value is ShipmentSummary {
  return isRecord(value) && isString(value.shipmentId) && isOneOf(SHIPMENT_STATUSES, value.status)
    && isOneOf(SERVICE_LEVELS, value.serviceLevel) && isMoney(value.price) && isString(value.trackingNumber)
    && isString(value.recipientName) && isPlace(value.destination) && isOptionalString(value.reference)
    && isString(value.bookedAt);
}

export function isShipmentPage(value: unknown): value is ShipmentPage {
  return isRecord(value) && Array.isArray(value.items) && value.items.every(isShipmentSummary)
    && isOptionalString(value.nextCursor);
}
