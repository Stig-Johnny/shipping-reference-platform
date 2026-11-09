// /quotes/:quoteId: the quote header, the offer cards and, once an offer is chosen, the BookingForm of the shipment
// feature. At expiresAt (or when the server says EXPIRED or quote-expired) the cards show "Expired", the radios are
// disabled and the form is hidden; a booking that is pending, unknown or done stays visible, because its replay is
// still valid after expiry.

import { failureMessage, failureTitle } from '../../api/messages.ts';
import { isRetryable, problemType, referenceOf } from '../../api/problems.ts';
import type { ApiFailure } from '../../api/problems.ts';
import type { Quote, QuoteRequest } from '../../api/types.ts';
import { button, emptyState, loadingText, notice, problemAlert, skeleton } from '../../ui/components.ts';
import { el, replace, visuallyHidden } from '../../ui/dom.ts';
import { formatTime, formatValidity } from '../../ui/format.ts';
import { expiredByServer, isFrozen, mountBookingForm } from '../shipments/index.ts';
import type { BookingFormView } from '../shipments/index.ts';
import type { QuotesContext, QuotesPage } from './context.ts';
import { quoteCardList } from './QuoteCardList.ts';
import { isQuoteExpired, msUntilExpiry, offerCards, quoteSummary } from './QuoteCardListModel.ts';

const LONGEST_TIMEOUT = 2_147_483_647;

function requestOf(quote: Quote): QuoteRequest {
  return { parcel: quote.parcel, origin: quote.origin, destination: quote.destination };
}

export function mountQuotePage(container: HTMLElement, quoteId: string, context: QuotesContext): QuotesPage {
  const controller = new AbortController();
  const cleanups: Array<() => void> = [];
  let disposed = false;
  const heading = el('h1', { tabindex: '-1' }, 'Quote');
  const body = el('div', { class: 'quote' });
  replace(container, el('section', { class: 'page' }, heading, body));

  const freshQuote = (quote: Quote): void => {
    context.quotes.prefill(requestOf(quote));
    context.navigate(context.paths.newQuote());
  };

  const showLoading = (): void => {
    body.setAttribute('aria-busy', 'true');
    const card = (): HTMLElement =>
      el('li', { class: 'offer-card offer-card--skeleton' }, skeleton('heading'), skeleton('line'), skeleton('line'));
    replace(body, loadingText('the quote'),
      el('div', { class: 'quote-header' }, skeleton('heading'), skeleton('line'), skeleton('line')),
      el('ul', { class: 'offer-list' }, card(), card(), card()));
  };

  const showFailure = (failure: ApiFailure): void => {
    body.setAttribute('aria-busy', 'false');
    if (problemType(failure) === 'quote-not-found') {
      replace(body, emptyState('Quote not found.', { label: 'New quote', href: context.paths.newQuote() }));
      return;
    }
    replace(body, problemAlert({
      title: failureTitle(failure), message: failureMessage(failure), reference: referenceOf(failure),
      onRetry: isRetryable(failure) ? load : undefined,
    }));
  };

  const show = (quote: Quote, createdHere: boolean): void => {
    body.setAttribute('aria-busy', 'false');
    const summary = quoteSummary(quote);
    const record = () => context.bookings.peek(quote.quoteId);
    const expired = (): boolean => isQuoteExpired(quote, Date.now()) || expiredByServer(record());
    const frozen = (): boolean => {
      const current = record();
      return current !== undefined && isFrozen(current.phase);
    };
    const cardOptions = () => ({ expired: expired(), frozen: frozen(), selectedOfferId: record()?.target.offerId });
    const until = el('span', {});
    const relative = el('span', { class: 'validity__relative' });
    const expiredNotice = notice('warning',
      el('p', {}, `This quote expired at ${formatTime(quote.expiresAt)}. Get a fresh quote to book.`),
      el('div', { class: 'button-row' }, button('Get a fresh quote', 'primary', () => freshQuote(quote))));
    const bookingSlot = el('div', { class: 'booking-slot' });
    let booking: BookingFormView | undefined;
    let wasExpired = expired();

    const ensureBookingForm = (): void => {
      if (booking === undefined && record() !== undefined) {
        booking = mountBookingForm(bookingSlot, {
          bookings: context.bookings, quoteId: quote.quoteId, paths: context.paths, announce: context.announce,
          reloadHint: context.reloaded && !createdHere,
        });
        booking.setExpired(expired());
      }
    };

    const cards = quoteCardList(offerCards(quote, cardOptions()), (offerId) => {
      const { quoteId: id, destination, expiresAt } = quote;
      context.bookings.choose({ quoteId: id, offerId, destination, expiresAt });
      ensureBookingForm();
    });

    const refresh = (): void => {
      const isExpired = expired();
      const validity = formatValidity(quote.expiresAt, Date.now());
      until.textContent = isExpired ? `Expired at ${formatTime(quote.expiresAt)}` : validity.until;
      relative.textContent = isExpired ? '' : validity.relative;
      cards.update(offerCards(quote, cardOptions()));
      expiredNotice.hidden = !isExpired;
      booking?.setExpired(isExpired);
      if (isExpired && !wasExpired) {
        context.announce('This quote has expired.');
      }
      wasExpired = isExpired;
    };

    replace(body,
      el('div', { class: 'quote-header' },
        el('p', { class: 'quote-route' },
          summary.from, el('span', { 'aria-hidden': 'true' }, ' → '), visuallyHidden(' to '), summary.to),
        el('p', { class: 'quote-facts' }, `${summary.parcel} · ${summary.billable} · ${summary.zone}`),
        el('p', { class: 'validity num' }, until, ' ', relative),
        el('div', { class: 'button-row' }, button('Edit parcel', 'secondary', () => freshQuote(quote)))),
      expiredNotice,
      cards.element,
      bookingSlot);
    ensureBookingForm();
    refresh();

    const interval = setInterval(() => refresh(), 15_000);
    const timeout = setTimeout(() => refresh(), Math.min(msUntilExpiry(quote, Date.now()) + 10, LONGEST_TIMEOUT));
    cleanups.push(
      () => clearInterval(interval),
      () => clearTimeout(timeout),
      context.bookings.subscribe(quote.quoteId, refresh),
      () => booking?.dispose());
  };

  function load(): void {
    const remembered = context.quotes.recall(quoteId);
    if (remembered !== undefined) {
      show(remembered.quote, remembered.created);
      return;
    }
    showLoading();
    void context.api.getQuote(quoteId, controller.signal).then((result) => {
      if (disposed) {
        return;
      }
      if (result.ok) {
        context.quotes.remember(result.value, false);
        show(result.value, false);
        return;
      }
      if (result.failure.kind !== 'aborted' && problemType(result.failure) !== 'unauthenticated') {
        showFailure(result.failure);
      }
    });
  }

  load();
  return {
    heading,
    dispose: () => {
      disposed = true;
      controller.abort();
      cleanups.forEach((cleanup) => cleanup());
    },
  };
}
