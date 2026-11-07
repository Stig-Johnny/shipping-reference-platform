// What the quote screens need from the app: the session's API client and memories, the paths of the other screens,
// navigation and the status region. The app passes it in; this feature never imports the app.

import type { ApiClient } from '../../api/client.ts';
import type { Quote, QuoteRequest } from '../../api/types.ts';
import type { BookingMemory } from '../shipments/index.ts';

export interface QuotePaths {
  newQuote(): string;
  quote(quoteId: string): string;
  shipments(): string;
  shipment(shipmentId: string): string;
}

/** Quotes of the session: the quote just created opens without a second request; a form can be prefilled. */
export interface QuoteMemory {
  /** `created` is true for a quote created in this page load, false for one read from the server. */
  remember(quote: Quote, created: boolean): void;
  recall(quoteId: string): RememberedQuote | undefined;
  prefill(request: QuoteRequest): void;
  takePrefill(): QuoteRequest | undefined;
}

export interface RememberedQuote {
  readonly quote: Quote;
  readonly created: boolean;
}

export interface QuotesContext {
  readonly api: ApiClient;
  readonly quotes: QuoteMemory;
  readonly bookings: BookingMemory;
  readonly paths: QuotePaths;
  readonly navigate: (path: string) => void;
  readonly announce: (message: string) => void;
  /** The document was reloaded, so bookings started before it are no longer tracked in memory. */
  readonly reloaded: boolean;
}

export interface QuotesPage {
  /** The page's h1, which receives focus after a route change. */
  readonly heading: HTMLElement;
  dispose(): void;
}

export function createQuoteMemory(): QuoteMemory {
  const quotes = new Map<string, RememberedQuote>();
  let pending: QuoteRequest | undefined;
  return {
    remember: (quote, created) => {
      quotes.set(quote.quoteId, { quote, created: created || quotes.get(quote.quoteId)?.created === true });
    },
    recall: (quoteId) => quotes.get(quoteId),
    prefill: (request) => {
      pending = request;
    },
    takePrefill: () => {
      const request = pending;
      pending = undefined;
      return request;
    },
  };
}
