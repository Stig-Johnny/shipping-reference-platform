// ShipmentTable without a DOM: the list as the API pages it, newest first, with the status filter and "Load more".
// Each request has a number, and only the answer to the latest one is applied.

import type { ApiResult } from '../../api/client.ts';
import type { ApiFailure } from '../../api/problems.ts';
import type { ShipmentPage, ShipmentQuery, ShipmentStatus, ShipmentSummary } from '../../api/types.ts';
import type { BadgeTone } from '../../ui/components.ts';
import { formatDateTime, formatPlace, formatPrice, formatServiceLevel, formatStatus } from '../../ui/format.ts';
import type { PriceText } from '../../ui/format.ts';

export type StatusFilter = 'ALL' | ShipmentStatus;

export const FILTERS: ReadonlyArray<{ readonly value: StatusFilter; readonly label: string }> = [
  { value: 'ALL', label: 'All' },
  { value: 'BOOKED', label: 'Booked' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

export interface ListState {
  readonly filter: StatusFilter;
  readonly phase: 'loading' | 'ready' | 'failed';
  readonly items: readonly ShipmentSummary[];
  readonly nextCursor: string | undefined;
  readonly loadingMore: boolean;
  /** Why the first page failed. */
  readonly failure: ApiFailure | undefined;
  /** Why a later page failed; the rows already shown stay. */
  readonly moreFailure: ApiFailure | undefined;
  /** The number of the request the list waits for. */
  readonly request: number;
}

export function initialList(filter: StatusFilter = 'ALL'): ListState {
  return {
    filter, phase: 'loading', items: [], nextCursor: undefined, loadingMore: false, failure: undefined,
    moreFailure: undefined, request: 1,
  };
}

/** A new filter starts again from the first page. */
export function withFilter(state: ListState, filter: StatusFilter): ListState {
  return { ...initialList(filter), request: state.request + 1 };
}

export function retry(state: ListState): ListState {
  return { ...state, phase: 'loading', failure: undefined, request: state.request + 1 };
}

export function loadMore(state: ListState): ListState {
  return state.nextCursor === undefined || state.loadingMore ? state
    : { ...state, loadingMore: true, moreFailure: undefined, request: state.request + 1 };
}

/** The listShipments parameters for the request the state waits for. */
export function queryFor(state: ListState): ShipmentQuery {
  return {
    ...(state.filter === 'ALL' ? {} : { status: state.filter }),
    ...(state.loadingMore && state.nextCursor !== undefined ? { cursor: state.nextCursor } : {}),
  };
}

export function received(state: ListState, request: number, result: ApiResult<ShipmentPage>): ListState {
  if (request !== state.request || (!result.ok && result.failure.kind === 'aborted')) {
    return state;
  }
  if (state.loadingMore) {
    return result.ok
      ? {
        ...state, items: [...state.items, ...result.value.items], nextCursor: result.value.nextCursor,
        loadingMore: false,
      }
      : { ...state, loadingMore: false, moreFailure: result.failure };
  }
  return result.ok
    ? { ...state, phase: 'ready', items: result.value.items, nextCursor: result.value.nextCursor, failure: undefined }
    : { ...state, phase: 'failed', failure: result.failure };
}

export function emptyMessage(filter: StatusFilter): string {
  switch (filter) {
    case 'ALL':
      return 'No shipments yet.';
    case 'BOOKED':
      return 'No booked shipments.';
    case 'CANCELLED':
      return 'No cancelled shipments.';
  }
}

export interface RowView {
  readonly booked: string;
  readonly trackingNumber: string;
  readonly recipient: string;
  readonly destination: string;
  readonly serviceLevel: string;
  readonly price: PriceText;
  readonly status: string;
  readonly reference: string;
}

export function rowView(summary: ShipmentSummary, timeZone?: string): RowView {
  return {
    booked: formatDateTime(summary.bookedAt, timeZone),
    trackingNumber: summary.trackingNumber,
    recipient: summary.recipientName,
    destination: formatPlace(summary.destination),
    serviceLevel: formatServiceLevel(summary.serviceLevel),
    price: formatPrice(summary.price.amount, summary.price.currency),
    status: formatStatus(summary.status),
    reference: summary.reference ?? '',
  };
}

export function statusTone(status: ShipmentStatus): BadgeTone {
  return status === 'BOOKED' ? 'success' : 'neutral';
}
