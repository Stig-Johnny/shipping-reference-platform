// ShipmentTable: a real <table> with a caption and scoped headers, scrolling inside its own labelled region. Each
// tracking number is a link to the shipment's drawer.

import type { ShipmentSummary } from '../../api/types.ts';
import { skeleton, statusBadge } from '../../ui/components.ts';
import { el, visuallyHidden } from '../../ui/dom.ts';
import { rowView, statusTone } from './ShipmentTableModel.ts';

const COLUMNS = [
  'Booked', 'Tracking number', 'Recipient', 'Destination', 'Service level', 'Price', 'Status', 'Reference',
];
const CAPTION_ID = 'shipments-caption';

function table(rows: HTMLTableRowElement[], busy: boolean): HTMLElement {
  const region = {
    class: 'table-region', role: 'region', 'aria-labelledby': CAPTION_ID, tabindex: '0', 'aria-busy': String(busy),
  };
  return el('div', region,
    el('table', { class: 'table' },
      el('caption', { id: CAPTION_ID, class: 'table__caption' }, 'Shipments, newest first'),
      el('thead', {}, el('tr', {}, ...COLUMNS.map((column) => el('th', { scope: 'col' }, column)))),
      el('tbody', {}, ...rows)));
}

export function shipmentTable(items: readonly ShipmentSummary[], linkFor: (shipmentId: string) => string): HTMLElement {
  return table(items.map((item) => {
    const row = rowView(item);
    return el('tr', {},
      el('td', { class: 'num' }, row.booked),
      el('th', { scope: 'row' },
        el('a', { href: linkFor(item.shipmentId), class: 'mono', 'data-shipment-id': item.shipmentId },
          row.trackingNumber)),
      el('td', {}, row.recipient),
      el('td', {}, row.destination),
      el('td', {}, row.serviceLevel),
      el('td', { class: 'num' },
        el('span', { 'aria-hidden': 'true' }, row.price.visual), visuallyHidden(row.price.spoken)),
      el('td', {}, statusBadge(row.status, statusTone(item.status))),
      el('td', {}, row.reference));
  }), false);
}

/** Five skeleton rows in the shape of the table, while the first page loads. */
export function skeletonTable(): HTMLElement {
  const rows = Array.from({ length: 5 },
    () => el('tr', { 'aria-hidden': 'true' }, ...COLUMNS.map(() => el('td', {}, skeleton('cell')))));
  return table(rows, true);
}
