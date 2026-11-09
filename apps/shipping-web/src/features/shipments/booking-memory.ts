// The bookings of one session, by quote. A booking request outlives the screen that sent it: its answer is stored
// here even after the user has navigated away, so that the frozen payload and its key survive route changes and the
// "Booking outcome unknown" recovery is still there on return. Switching user drops the whole memory.

import type { ApiClient } from '../../api/client.ts';
import { isUnresolved, newRecord, resend, settle, submit, withFields, withTarget } from './BookingFormModel.ts';
import type { BookingRecord, BookingTarget, RecipientFields, Submission } from './BookingFormModel.ts';

export interface BookingMemory {
  peek(quoteId: string): BookingRecord | undefined;
  /** Starts or retargets the booking of a quote when an offer is chosen; ignored while the booking is frozen. */
  choose(target: BookingTarget): BookingRecord;
  /** Keeps what the user typed; does not notify, so typing never re-renders the form. */
  edit(quoteId: string, fields: RecipientFields): void;
  submit(quoteId: string): void;
  /** Retry, "Book again (same request)" or Try again: sends the stored intent unchanged. */
  resend(quoteId: string): void;
  subscribe(quoteId: string, listener: (record: BookingRecord) => void): () => void;
  /** A booking is pending or its outcome unknown: reloading or switching user would lose its recovery. */
  hasUnresolved(): boolean;
}

export interface BookingMemoryOptions {
  readonly newKey?: () => string;
}

export function createBookingMemory(
  api: Pick<ApiClient, 'bookShipment'>, options: BookingMemoryOptions = {},
): BookingMemory {
  const records = new Map<string, BookingRecord>();
  const listeners = new Map<string, Set<(record: BookingRecord) => void>>();

  const store = (record: BookingRecord): void => {
    records.set(record.target.quoteId, record);
    const listenerValues1 = [...(listeners.get(record.target.quoteId) ?? [])];
    for (const listener of listenerValues1) {
      listener(record);
    }
  };

  const run = ({ record, intent }: Submission): void => {
    store(record);
    if (intent === undefined) {
      return;
    }
    void api.bookShipment(intent).then((result) => {
      const current = records.get(record.target.quoteId);
      if (current !== undefined) {
        store(settle(current, intent, result));
      }
    });
  };

  return {
    peek: (quoteId) => records.get(quoteId),
    choose(target) {
      const existing = records.get(target.quoteId);
      const record = existing === undefined ? newRecord(target) : withTarget(existing, target);
      store(record);
      return record;
    },
    edit(quoteId, fields) {
      const existing = records.get(quoteId);
      if (existing !== undefined) {
        records.set(quoteId, withFields(existing, fields));
      }
    },
    submit(quoteId) {
      const existing = records.get(quoteId);
      if (existing !== undefined) {
        run(submit(existing, options.newKey));
      }
    },
    resend(quoteId) {
      const existing = records.get(quoteId);
      if (existing !== undefined) {
        run(resend(existing));
      }
    },
    subscribe(quoteId, listener) {
      const set = listeners.get(quoteId) ?? new Set();
      set.add(listener);
      listeners.set(quoteId, set);
      return () => {
        set.delete(listener);
      };
    },
    hasUnresolved: () => [...records.values()].some((record) => isUnresolved(record.phase)),
  };
}
