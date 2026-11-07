// ShipmentDrawer without a DOM: loading the shipment, what the drawer shows, and the label download's states. A
// shipment of another tenant is "Shipment not found.", exactly like one that does not exist.

import type { ApiResult } from '../../api/client.ts';
import { failureMessage } from '../../api/messages.ts';
import { problemType } from '../../api/problems.ts';
import type { ApiFailure } from '../../api/problems.ts';
import type { Label, Shipment } from '../../api/types.ts';
import type { BadgeTone } from '../../ui/components.ts';
import { formatDateTime, formatPlace, formatPrice, formatServiceLevel, formatStatus } from '../../ui/format.ts';
import { statusTone } from './ShipmentTableModel.ts';

export type DrawerLoad =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly shipment: Shipment }
  | { readonly kind: 'not-found' }
  | { readonly kind: 'failed'; readonly failure: ApiFailure };

export function drawerLoaded(result: ApiResult<Shipment>): DrawerLoad {
  if (result.ok) {
    return { kind: 'loaded', shipment: result.value };
  }
  return problemType(result.failure) === 'shipment-not-found'
    ? { kind: 'not-found' }
    : { kind: 'failed', failure: result.failure };
}

/** The dialog's heading, which also labels it. */
export function drawerHeading(load: DrawerLoad): string {
  return load.kind === 'loaded' ? `Shipment ${load.shipment.trackingNumber}` : 'Shipment';
}

export interface Detail {
  readonly term: string;
  readonly lines: readonly string[];
  /** For the price: what a screen reader says instead of the visible text. */
  readonly spoken?: string;
  /** Shown as a status badge of this tone. */
  readonly tone?: BadgeTone;
  /** Shown in monospace: identifiers. */
  readonly code?: boolean;
}

export function drawerDetails(shipment: Shipment, timeZone?: string): Detail[] {
  const price = formatPrice(shipment.price.amount, shipment.price.currency);
  const { recipient } = shipment;
  const details: Detail[] = [
    { term: 'Status', lines: [formatStatus(shipment.status)], tone: statusTone(shipment.status) },
    { term: 'Service level', lines: [formatServiceLevel(shipment.serviceLevel)] },
    { term: 'Price', lines: [price.visual], spoken: price.spoken },
    { term: 'Booked', lines: [formatDateTime(shipment.bookedAt, timeZone)] },
  ];
  if (shipment.cancelledAt !== undefined) {
    details.push({ term: 'Cancelled', lines: [formatDateTime(shipment.cancelledAt, timeZone)] });
  }
  details.push(
    { term: 'Origin', lines: [formatPlace(shipment.origin)] },
    {
      term: 'Recipient',
      lines: [
        recipient.name, recipient.addressLine1,
        ...(recipient.addressLine2 === undefined ? [] : [recipient.addressLine2]),
        `${recipient.postcode} ${recipient.city}`, recipient.countryCode,
      ],
    },
  );
  if (shipment.reference !== undefined) {
    details.push({ term: 'Reference', lines: [shipment.reference] });
  }
  details.push(
    { term: 'Quote ID', lines: [shipment.quoteId], code: true },
    { term: 'Offer ID', lines: [shipment.offerId], code: true },
  );
  return details;
}

export function canDownloadLabel(load: DrawerLoad): boolean {
  return load.kind === 'loaded' && load.shipment.status === 'BOOKED';
}

export type LabelState =
  | { readonly kind: 'idle' }
  | { readonly kind: 'downloading' }
  | { readonly kind: 'saved'; readonly fileName: string }
  | { readonly kind: 'failed'; readonly failure: ApiFailure };

export function labelSettled(result: ApiResult<Label>): LabelState {
  return result.ok ? { kind: 'saved', fileName: result.value.fileName } : { kind: 'failed', failure: result.failure };
}

export function labelMessage(state: LabelState): string | undefined {
  switch (state.kind) {
    case 'saved':
      return `Label saved as ${state.fileName}.`;
    case 'failed':
      return failureMessage(state.failure);
    case 'idle':
    case 'downloading':
      return undefined;
  }
}
