// QuoteCardList: one card per offer, a radio group labelled "Offers". Choosing does not rebuild the cards, so focus
// and arrow-key movement stay on the radios; update() changes checked, disabled and the badges in place.

import { statusBadge } from '../../ui/components.ts';
import { el, replace, visuallyHidden } from '../../ui/dom.ts';
import type { OfferCard } from './QuoteCardListModel.ts';

export interface CardListView {
  readonly element: HTMLFieldSetElement;
  update(cards: readonly OfferCard[]): void;
}

export function quoteCardList(cards: readonly OfferCard[], onChoose: (offerId: string) => void): CardListView {
  const inputs = new Map<string, { readonly input: HTMLInputElement; readonly badge: HTMLElement }>();
  const list = el('ul', { class: 'offer-list' });
  for (const card of cards) {
    const headingId = `offer-${card.offerId}-heading`;
    const input = el('input', { type: 'radio', name: 'offer', value: card.offerId, class: 'offer-card__radio' });
    input.addEventListener('change', () => {
      if (input.checked) {
        onChoose(card.offerId);
      }
    });
    const badge = el('span', { class: 'offer-card__badge' });
    inputs.set(card.offerId, { input, badge });
    list.append(el('li', { class: 'offer-card' },
      el('div', { class: 'offer-card__top' },
        el('h2', { class: 'offer-card__heading', id: headingId }, card.heading), badge),
      el('p', { class: 'offer-card__price num' },
        el('span', { 'aria-hidden': 'true' }, card.price.visual), visuallyHidden(card.price.spoken)),
      el('p', { class: 'offer-card__transit' }, card.transit),
      el('label', { class: 'offer-card__choice' }, input, ' Choose this offer',
        visuallyHidden(`: ${card.heading}, ${card.price.spoken}, ${card.transit}`))));
  }
  const view: CardListView = {
    element: el('fieldset', { class: 'offers' }, el('legend', { class: 'offers__legend' }, 'Offers'), list),
    update(next) {
      for (const card of next) {
        const entry = inputs.get(card.offerId);
        if (entry !== undefined) {
          entry.input.checked = card.checked;
          entry.input.disabled = card.disabled;
          replace(entry.badge, card.badge === undefined ? null : statusBadge(card.badge, 'warning'));
        }
      }
    },
  };
  view.update(cards);
  return view;
}
