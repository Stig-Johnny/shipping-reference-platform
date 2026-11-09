// BookingForm: the recipient of the chosen offer, with postcode and country read-only from the quote's destination.
// The booking itself lives in the session's BookingMemory and this view only shows it, so leaving the page and coming
// back shows the same pending, unknown or booked state, with the same key.

import { button, linkButton, notice, problemAlert, showErrorSummary } from '../../ui/components.ts';
import type { SummaryItem } from '../../ui/components.ts';
import { el, replace } from '../../ui/dom.ts';
import { readOnlyField, textField } from '../../ui/fields.ts';
import type { Field } from '../../ui/fields.ts';
import type { BookingMemory } from './booking-memory.ts';
import { isFrozen, orderedRecipientErrors, outcomeView } from './BookingFormModel.ts';
import type { BookingRecord, OutcomeAction, RecipientField, RecipientFields } from './BookingFormModel.ts';

export interface BookingPaths {
  newQuote(): string;
  shipments(): string;
  shipment(shipmentId: string): string;
}

export interface BookingFormOptions {
  readonly bookings: BookingMemory;
  readonly quoteId: string;
  readonly paths: BookingPaths;
  readonly announce: (message: string) => void;
  /** The page was reloaded and this quote was not created since: a booking from before may exist. */
  readonly reloadHint: boolean;
}

export interface BookingFormView {
  /** An expired quote hides the form, but never a booking that is pending, unknown or done. */
  setExpired(expired: boolean): void;
  dispose(): void;
}

const IDS: Readonly<Record<RecipientField, string>> = {
  name: 'booking-name', addressLine1: 'booking-address-line-1', addressLine2: 'booking-address-line-2',
  city: 'booking-city', reference: 'booking-reference',
};

export function mountBookingForm(container: HTMLElement, options: BookingFormOptions): BookingFormView {
  const { bookings, quoteId, paths } = options;
  const initial = bookings.peek(quoteId);
  if (initial === undefined) {
    return { setExpired: () => undefined, dispose: () => undefined };
  }
  const fields: Readonly<Record<RecipientField, Field<HTMLInputElement>>> = {
    name: textField({ id: IDS.name, label: 'Recipient name', required: true, value: initial.fields.name }),
    addressLine1: textField({
      id: IDS.addressLine1, label: 'Address line 1', required: true, value: initial.fields.addressLine1,
    }),
    addressLine2: textField({
      id: IDS.addressLine2, label: 'Address line 2', required: false, value: initial.fields.addressLine2,
    }),
    city: textField({ id: IDS.city, label: 'City', required: true, value: initial.fields.city }),
    reference: textField({
      id: IDS.reference, label: 'Reference', hint: 'Your own reference, for example an order number.', required: false,
      value: initial.fields.reference,
    }),
  };
  const postcode = readOnlyField('booking-postcode', 'Postcode', initial.target.destination.postcode);
  const country = readOnlyField('booking-country', 'Country', initial.target.destination.countryCode);
  const values = (): RecipientFields => ({
    name: fields.name.control.value, addressLine1: fields.addressLine1.control.value,
    addressLine2: fields.addressLine2.control.value, city: fields.city.control.value,
    reference: fields.reference.control.value,
  });

  const summary = el('div', {});
  const submit = button('Book shipment', 'primary', undefined, 'submit');
  const submitRow = el('div', { class: 'button-row' }, submit);
  const outcome = el('div', { class: 'booking__outcome' });
  const form = el('form', { class: 'form booking-form', novalidate: true, 'aria-labelledby': 'booking-heading' },
    summary, fields.name.root, fields.addressLine1.root, fields.addressLine2.root, fields.city.root, postcode.root,
    country.root, fields.reference.root, submitRow);
  const heading = el('h2', { id: 'booking-heading', tabindex: '-1' }, 'Book shipment');
  const section = el('section', { class: 'booking', 'aria-labelledby': 'booking-heading' },
    heading,
    options.reloadHint
      ? notice('info', el('p', {}, 'The page was reloaded, so a booking started before then is no longer tracked here. '
        + 'Check Shipments before you book this quote.'),
      el('div', { class: 'button-row' }, linkButton('Open shipments', paths.shipments())))
      : null,
    form, outcome);
  replace(container, section);

  let expired = false;
  let previous = initial.phase.kind;

  const showSummary = (record: BookingRecord, focus: boolean): void => {
    const items: SummaryItem[] = [
      ...orderedRecipientErrors(record.errors).map((error) => ({ text: error.message, targetId: new Map(Object.entries(IDS)).get(error.field) })),
      ...record.unplaced.map((text) => ({ text })),
    ];
    if (focus) {
      showErrorSummary(summary, items);
    } else if (items.length === 0) {
      summary.replaceChildren();
    }
  };

  const action = (kind: OutcomeAction, record: BookingRecord): HTMLElement => {
    switch (kind) {
      case 'open-shipment':
        return record.phase.kind === 'booked'
          ? linkButton('Open shipment', paths.shipment(record.phase.shipment.shipmentId), 'primary')
          : linkButton('Open shipments', paths.shipments());
      case 'book-again':
        return button('Book again (same request)', 'secondary', () => bookings.resend(quoteId));
      case 'retry':
        return button(record.phase.kind === 'unknown' ? 'Retry' : 'Try again', 'primary',
          () => bookings.resend(quoteId));
      case 'open-shipments':
        return linkButton('Open shipments', paths.shipments());
      case 'new-quote':
        return linkButton('New quote', paths.newQuote());
    }
  };

  const render = (record: BookingRecord): void => {
    const active = document.activeElement;
    const restoreFocus = outcome.contains(active) || active === heading || active === submit;
    const frozen = isFrozen(record.phase);
    section.hidden = expired && !frozen;
    const errors = new Map(Object.entries(record.errors));
    for (const entry of Object.entries(fields)) {
      entry[1].control.readOnly = frozen;
      entry[1].setError(errors.get(entry[0]));
    }
    postcode.control.value = record.target.destination.postcode;
    country.control.value = record.target.destination.countryCode;
    const pending = record.phase.kind === 'pending';
    submit.disabled = pending;
    submit.textContent = pending ? 'Booking...' : 'Book shipment';
    submitRow.hidden = record.phase.kind === 'unknown' || record.phase.kind === 'booked';
    const view = outcomeView(record);
    if (view === undefined) {
      replace(outcome);
    } else if (view.tone === 'success') {
      replace(outcome, notice('success', el('p', { class: 'notice__message' }, view.message),
        el('div', { class: 'button-row' }, ...view.actions.map((kind) => action(kind, record)))));
    } else {
      replace(outcome, problemAlert({
        title: view.heading ?? 'Booking failed', message: view.message, reference: view.reference, tone: view.tone,
        actions: view.actions.map((kind) => action(kind, record)),
      }));
    }
    if (restoreFocus && (pending || submitRow.hidden)) {
      (outcome.querySelector<HTMLElement>('a, button') ?? heading).focus();
    }
  };

  form.addEventListener('input', () => bookings.edit(quoteId, values()));
  form.addEventListener('submit', (event) => {
    event.preventDefault();
    bookings.edit(quoteId, values());
    bookings.submit(quoteId);
    const record = bookings.peek(quoteId);
    if (record !== undefined && record.phase.kind === 'editing') {
      showSummary(record, true);
    }
  });

  const unsubscribe = bookings.subscribe(quoteId, (record) => {
    const before = previous;
    previous = record.phase.kind;
    render(record);
    showSummary(record, before === 'pending' && record.phase.kind === 'editing');
    if (before === 'pending' && record.phase.kind === 'booked') {
      options.announce(outcomeView(record)?.message ?? 'Shipment booked');
    }
  });

  render(initial);
  showSummary(initial, false);
  return {
    setExpired(value) {
      expired = value;
      const record = bookings.peek(quoteId);
      if (record !== undefined) {
        section.hidden = expired && !isFrozen(record.phase);
      }
    },
    dispose: unsubscribe,
  };
}
