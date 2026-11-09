// QuoteCardList and the quote header without a DOM. The cards show the offers as received; expiry only disables and
// explains, because the server decides whether an offer can still be booked.

import type { Quote } from '../../api/types.ts';
import {
  formatDimensions, formatGrams, formatPlace, formatPrice, formatServiceLevel, formatTransitDays,
} from '../../ui/format.ts';
import type { PriceText } from '../../ui/format.ts';

export interface OfferCard {
  readonly offerId: string;
  readonly heading: string;
  readonly price: PriceText;
  readonly transit: string;
  readonly checked: boolean;
  readonly disabled: boolean;
  /** "Expired" once the quote has expired. */
  readonly badge: string | undefined;
}

export interface CardOptions {
  readonly expired: boolean;
  /** A booking is pending, unknown or done: the chosen offer must not change under it. */
  readonly frozen: boolean;
  readonly selectedOfferId: string | undefined;
}

export function offerCards(quote: Quote, options: CardOptions): OfferCard[] {
  return quote.offers.map((offer) => ({
    offerId: offer.offerId,
    heading: formatServiceLevel(offer.serviceLevel),
    price: formatPrice(offer.price.amount, offer.price.currency),
    transit: formatTransitDays(offer.transitDays),
    checked: offer.offerId === options.selectedOfferId,
    disabled: options.expired || options.frozen,
    badge: options.expired ? 'Expired' : undefined,
  }));
}

/** Expired once the clock reaches expiresAt, or at once when the server said EXPIRED; unreadable counts as expired. */
export function isQuoteExpired(quote: Quote, now: number): boolean {
  const expiresAt = Date.parse(quote.expiresAt);
  return quote.status === 'EXPIRED' || Number.isNaN(expiresAt) || now >= expiresAt;
}

/** Milliseconds until the page must switch to the expired state; 0 when it already has. */
export function msUntilExpiry(quote: Quote, now: number): number {
  const remaining = Date.parse(quote.expiresAt) - now;
  return Number.isNaN(remaining) || remaining < 0 ? 0 : remaining;
}

export interface QuoteSummary {
  readonly from: string;
  readonly to: string;
  readonly parcel: string;
  readonly billable: string;
  readonly zone: string;
}

export function quoteSummary(quote: Quote): QuoteSummary {
  return {
    from: formatPlace(quote.origin),
    to: formatPlace(quote.destination),
    parcel: `${formatGrams(quote.parcel.weightGrams)}, ${formatDimensions(quote.parcel)}`,
    billable: `Billable weight ${formatGrams(quote.billableWeightGrams)}`,
    zone: quote.zone === 'DOMESTIC' ? 'Domestic' : 'International',
  };
}
