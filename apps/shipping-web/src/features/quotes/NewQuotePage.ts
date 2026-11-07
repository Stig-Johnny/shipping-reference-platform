// /quotes/new: ParcelForm, then the offers. While pending the results area shows three skeleton cards; a 201 opens
// the quote page with the quote just returned, without a second request.

import { failureMessage, failureTitle } from '../../api/messages.ts';
import { isRetryable, problemType, referenceOf } from '../../api/problems.ts';
import { loadingText, problemAlert, skeleton } from '../../ui/components.ts';
import { el, replace } from '../../ui/dom.ts';
import type { QuotesContext, QuotesPage } from './context.ts';
import { parcelForm } from './ParcelForm.ts';
import {
  EMPTY_PARCEL_FIELDS, checkParcelFields, fieldsFromRequest, mapServerErrors, orderedErrors,
} from './ParcelFormModel.ts';
import type { ParcelFields } from './ParcelFormModel.ts';

function skeletonCards(): HTMLElement {
  const card = (): HTMLElement => el('li', { class: 'offer-card offer-card--skeleton' },
    skeleton('heading'), skeleton('line'), skeleton('line'), skeleton('line'));
  return el('ul', { class: 'offer-list' }, card(), card(), card());
}

export function mountNewQuotePage(container: HTMLElement, context: QuotesContext): QuotesPage {
  const controller = new AbortController();
  let disposed = false;
  const heading = el('h1', { tabindex: '-1' }, 'New quote');
  const alert = el('div', {});
  const results = el('div', { class: 'offers-area', 'aria-busy': 'false' });
  const prefill = context.quotes.takePrefill();

  const submit = async (fields: ParcelFields): Promise<void> => {
    const { request, errors } = checkParcelFields(fields);
    if (orderedErrors(errors).length > 0) {
      form.showErrors(errors, []);
      return;
    }
    form.clearErrors();
    replace(alert);
    form.setPending(true);
    results.setAttribute('aria-busy', 'true');
    replace(results, loadingText('offers'), skeletonCards());
    const result = await context.api.createQuote(request, controller.signal);
    if (disposed) {
      return;
    }
    form.setPending(false);
    results.setAttribute('aria-busy', 'false');
    replace(results);
    if (result.ok) {
      context.quotes.remember(result.value, true);
      context.announce('Offers ready');
      context.navigate(context.paths.quote(result.value.quoteId));
      return;
    }
    const { failure } = result;
    const type = problemType(failure);
    if (failure.kind === 'aborted' || type === 'unauthenticated') {
      return;
    }
    if (failure.kind === 'problem' && type === 'validation-failed') {
      const mapped = mapServerErrors(fields, failure.problem.errors ?? []);
      form.showErrors(mapped.errors, mapped.unplaced);
      return;
    }
    replace(alert, problemAlert({
      title: failureTitle(failure),
      message: failureMessage(failure),
      reference: referenceOf(failure),
      onRetry: isRetryable(failure) ? () => void submit(form.values()) : undefined,
    }));
  };

  const initial = prefill === undefined ? EMPTY_PARCEL_FIELDS : fieldsFromRequest(prefill);
  const form = parcelForm(initial, (fields) => void submit(fields));
  replace(container, el('section', { class: 'page' }, heading, form.element, alert, results));
  return {
    heading,
    dispose: () => {
      disposed = true;
      controller.abort();
    },
  };
}
