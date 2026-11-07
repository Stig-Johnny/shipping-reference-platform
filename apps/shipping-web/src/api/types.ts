// Hand-written types for exactly the schemas of contracts/shipping.openapi.yaml that the web app uses. The contract
// is the authority: problems.test.ts and validation.test.ts compare the enums, patterns and bounds here with it.
// Absent values are omitted, never null or undefined, as the contract's conventions say.

export const SERVICE_LEVELS = ['ECONOMY', 'STANDARD', 'EXPRESS'] as const;
export const ZONES = ['DOMESTIC', 'INTERNATIONAL'] as const;
export const QUOTE_STATUSES = ['OPEN', 'EXPIRED'] as const;
export const SHIPMENT_STATUSES = ['BOOKED', 'CANCELLED'] as const;
export const UNAVAILABLE_REASONS = [
  'ROUTE_NOT_SERVED', 'BILLABLE_WEIGHT_ABOVE_LIMIT', 'SERVICE_LEVEL_NOT_AVAILABLE',
] as const;
export const FLAG_KEYS = ['label-preview'] as const;

export type ServiceLevel = (typeof SERVICE_LEVELS)[number];
export type Zone = (typeof ZONES)[number];
export type QuoteStatus = (typeof QUOTE_STATUSES)[number];
export type ShipmentStatus = (typeof SHIPMENT_STATUSES)[number];
export type UnavailableReason = (typeof UNAVAILABLE_REASONS)[number];
export type FlagKey = (typeof FLAG_KEYS)[number];

/** A decimal string with exactly two fraction digits, shown as received: the web app never computes a price. */
export interface Money {
  readonly amount: string;
  readonly currency: 'EUR';
}

export interface Place {
  readonly countryCode: string;
  readonly postcode: string;
}

export interface Parcel {
  readonly weightGrams: number;
  readonly lengthMm: number;
  readonly widthMm: number;
  readonly heightMm: number;
}

export interface TransitDays {
  readonly minDays: number;
  readonly maxDays: number;
}

export interface QuoteRequest {
  readonly parcel: Parcel;
  readonly origin: Place;
  readonly destination: Place;
  readonly serviceLevel?: ServiceLevel;
}

export interface Offer {
  readonly offerId: string;
  readonly serviceLevel: ServiceLevel;
  readonly price: Money;
  readonly transitDays: TransitDays;
}

export interface Quote {
  readonly quoteId: string;
  readonly status: QuoteStatus;
  readonly issuedAt: string;
  readonly expiresAt: string;
  readonly parcel: Parcel;
  readonly origin: Place;
  readonly destination: Place;
  readonly zone: Zone;
  readonly billableWeightGrams: number;
  readonly offers: readonly Offer[];
}

/** Personal data: synthetic values only, never logged. */
export interface Recipient {
  readonly name: string;
  readonly addressLine1: string;
  readonly addressLine2?: string;
  readonly postcode: string;
  readonly city: string;
  readonly countryCode: string;
}

export interface BookingRequest {
  readonly quoteId: string;
  readonly offerId: string;
  readonly recipient: Recipient;
  readonly reference?: string;
}

export interface Shipment {
  readonly shipmentId: string;
  readonly quoteId: string;
  readonly offerId: string;
  readonly status: ShipmentStatus;
  readonly serviceLevel: ServiceLevel;
  readonly price: Money;
  readonly trackingNumber: string;
  readonly origin: Place;
  readonly recipient: Recipient;
  readonly reference?: string;
  readonly bookedAt: string;
  readonly cancelledAt?: string;
}

export interface ShipmentSummary {
  readonly shipmentId: string;
  readonly status: ShipmentStatus;
  readonly serviceLevel: ServiceLevel;
  readonly price: Money;
  readonly trackingNumber: string;
  readonly recipientName: string;
  readonly destination: Place;
  readonly reference?: string;
  readonly bookedAt: string;
}

export interface ShipmentPage {
  readonly items: readonly ShipmentSummary[];
  readonly nextCursor?: string;
}

/** The query parameters of listShipments; each is sent only when present. */
export interface ShipmentQuery {
  readonly limit?: number;
  readonly cursor?: string;
  readonly status?: ShipmentStatus;
}

export interface Membership {
  readonly userId: string;
  readonly userDisplayName: string;
  readonly tenantId: string;
  readonly tenantDisplayName: string;
  readonly role: 'CLERK';
  readonly enabledFlags: readonly FlagKey[];
}

export interface InvalidParam {
  readonly in: 'body' | 'query' | 'path' | 'header';
  /** A JSON Pointer into the body when `in` is `body`; otherwise the parameter's name. */
  readonly name: string;
  readonly detail: string;
}

/** The simulated label of getShipmentLabel: the SVG document and the file name from Content-Disposition. */
export interface Label {
  readonly blob: Blob;
  readonly fileName: string;
}
