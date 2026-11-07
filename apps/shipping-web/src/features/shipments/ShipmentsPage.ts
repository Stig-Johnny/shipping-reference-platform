// /shipments and /shipments/:shipmentId: the table with its status filter and "Load more", and the drawer over it.
// Opening and closing the drawer keeps the list as it is. Closing returns focus to the tracking-number link that
// opened the drawer, or to the heading after direct navigation.

import type { ApiClient } from '../../api/client.ts';
import { failureMessage, failureTitle } from '../../api/messages.ts';
import { isRetryable, problemType, referenceOf } from '../../api/problems.ts';
import { button, emptyState, problemAlert } from '../../ui/components.ts';
import { el, focusProgrammatically, replace } from '../../ui/dom.ts';
import { openShipmentDrawer } from './ShipmentDrawer.ts';
import type { DrawerView } from './ShipmentDrawer.ts';
import { shipmentTable, skeletonTable } from './ShipmentTable.ts';
import {
  FILTERS, emptyMessage, initialList, loadMore, queryFor, received, retry, withFilter,
} from './ShipmentTableModel.ts';
import type { ListState } from './ShipmentTableModel.ts';

export interface ShipmentsPaths {
  newQuote(): string;
  shipments(): string;
  shipment(shipmentId: string): string;
}

export interface ShipmentsContext {
  readonly api: ApiClient;
  readonly paths: ShipmentsPaths;
  readonly navigate: (path: string) => void;
  readonly announce: (message: string) => void;
}

export interface ShipmentsPageView {
  readonly heading: HTMLElement;
  /** Opens the drawer of a shipment, or closes it for undefined, without reloading the list. */
  show(shipmentId: string | undefined): void;
  dispose(): void;
}

export function mountShipmentsPage(
  container: HTMLElement, context: ShipmentsContext, shipmentId: string | undefined,
): ShipmentsPageView {
  const controller = new AbortController();
  let disposed = false;
  let state: ListState = initialList();
  let shownItems: ListState['items'] | undefined;
  let shownPhase: ListState['phase'] | undefined;
  let drawer: { readonly id: string; readonly view: DrawerView; readonly fromList: boolean } | undefined;
  let clickedId: string | undefined;

  const heading = el('h1', { tabindex: '-1' }, 'Shipments');
  const list = el('div', { class: 'list-area' });
  const moreAlert = el('div', {});
  const moreButton = button('Load more', 'secondary', () => {
    state = loadMore(state);
    fetchPage();
  });
  const filter = el('fieldset', { class: 'segmented' }, el('legend', { class: 'segmented__legend' }, 'Status'));
  for (const option of FILTERS) {
    const input = el('input', {
      type: 'radio', name: 'status-filter', value: option.value, checked: option.value === 'ALL',
    });
    input.addEventListener('change', () => {
      if (input.checked) {
        state = withFilter(state, option.value);
        fetchPage();
      }
    });
    filter.append(el('label', { class: 'segment' }, input, el('span', {}, option.label)));
  }
  const page = el('section', { class: 'page' },
    heading, filter, list, el('div', { class: 'more-area' }, moreAlert, moreButton));
  replace(container, page);

  list.addEventListener('click', (event) => {
    const link = event.target instanceof Element ? event.target.closest('a[data-shipment-id]') : null;
    clickedId = link?.getAttribute('data-shipment-id') ?? undefined;
  });

  const renderList = (): void => {
    if (state.items === shownItems && state.phase === shownPhase) {
      return;
    }
    shownItems = state.items;
    shownPhase = state.phase;
    const { failure } = state;
    if (state.phase === 'loading') {
      replace(list, skeletonTable());
    } else if (state.phase === 'failed' && failure !== undefined) {
      replace(list, problemAlert({
        title: failureTitle(failure), message: failureMessage(failure), reference: referenceOf(failure),
        onRetry: isRetryable(failure) ? () => {
          state = retry(state);
          fetchPage();
        } : undefined,
      }));
    } else if (state.items.length === 0) {
      replace(list, emptyState(emptyMessage(state.filter), { label: 'New quote', href: context.paths.newQuote() }));
    } else {
      replace(list, shipmentTable(state.items, context.paths.shipment));
    }
  };

  const render = (): void => {
    const buttonHadFocus = document.activeElement === moreButton;
    renderList();
    const { moreFailure } = state;
    replace(moreAlert, moreFailure === undefined ? null
      : problemAlert({
        title: failureTitle(moreFailure), message: failureMessage(moreFailure), reference: referenceOf(moreFailure),
      }));
    moreButton.hidden = state.phase !== 'ready' || state.nextCursor === undefined;
    moreButton.disabled = state.loadingMore;
    moreButton.textContent = state.loadingMore ? 'Loading more...' : 'Load more';
    const region = list.querySelector<HTMLElement>('.table-region');
    if (buttonHadFocus && moreButton.hidden && region !== null) {
      region.focus();
    }
  };

  function fetchPage(): void {
    const request = state.request;
    render();
    void context.api.listShipments(queryFor(state), controller.signal).then((result) => {
      if (disposed || (!result.ok && problemType(result.failure) === 'unauthenticated')) {
        return;
      }
      const next = received(state, request, result);
      if (next !== state) {
        state = next;
        render();
      }
    });
  }

  const closeDrawer = (restoreFocus: boolean): void => {
    if (drawer === undefined) {
      return;
    }
    const { id, view, fromList } = drawer;
    drawer = undefined;
    view.close();
    if (!restoreFocus) {
      return;
    }
    const link = fromList ? list.querySelector<HTMLElement>(`a[data-shipment-id="${id}"]`) : null;
    if (link === null) {
      focusProgrammatically(heading);
    } else {
      link.focus();
    }
  };

  const show = (id: string | undefined): void => {
    if (drawer !== undefined && drawer.id === id) {
      return;
    }
    closeDrawer(id === undefined);
    if (id !== undefined) {
      drawer = {
        id,
        fromList: clickedId === id,
        view: openShipmentDrawer(page, id, {
          api: context.api, announce: context.announce, shipmentsPath: context.paths.shipments(),
          onClose: () => context.navigate(context.paths.shipments()),
        }),
      };
      clickedId = undefined;
    }
  };

  fetchPage();
  show(shipmentId);
  return {
    heading,
    show,
    dispose: () => {
      disposed = true;
      controller.abort();
      closeDrawer(false);
    },
  };
}
