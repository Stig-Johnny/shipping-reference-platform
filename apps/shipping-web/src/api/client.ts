// The web app's only HTTP code: one typed adapter over fetch for the operations of contracts/shipping.openapi.yaml
// that the workbench uses. Same-origin /api/v1, the bearer token from memory, a Correlation-Id on every request,
// problem+json read into typed problems, and one Idempotency-Key per booking intent. It never computes a price.

import { isMembership, isQuote, isShipment, isShipmentPage } from './guards.ts';
import { readProblem } from './problems.ts';
import type { ApiFailure, Problem } from './problems.ts';
import type {
  BookingRequest, Label, Membership, Quote, QuoteRequest, Shipment, ShipmentPage, ShipmentQuery,
} from './types.ts';

export type ApiResult<T> =
  | { readonly ok: true; readonly value: T; readonly status: number; readonly replayed: boolean }
  | { readonly ok: false; readonly failure: ApiFailure };

/**
 * One booking as the user intends it: the request and the Idempotency-Key that every retry of it sends again. A
 * changed recipient or offer is a new intent with a new key.
 */
export interface BookingIntent {
  readonly idempotencyKey: string;
  readonly request: BookingRequest;
}

export interface ApiClientOptions {
  /** The demo bearer token, held in memory only. */
  readonly token?: string;
  /** Aborts every request of this client, for example when the user switches. */
  readonly signal?: AbortSignal;
  readonly fetch?: typeof fetch;
  readonly newCorrelationId?: () => string;
  readonly baseUrl?: string;
  /** Called on a 401 unauthenticated problem, so that the session's owner can end the session. */
  readonly onUnauthenticated?: () => void;
}

export interface ApiClient {
  getCurrentMembership(signal?: AbortSignal): Promise<ApiResult<Membership>>;
  createQuote(request: QuoteRequest, signal?: AbortSignal): Promise<ApiResult<Quote>>;
  getQuote(quoteId: string, signal?: AbortSignal): Promise<ApiResult<Quote>>;
  bookShipment(intent: BookingIntent, signal?: AbortSignal): Promise<ApiResult<Shipment>>;
  listShipments(query: ShipmentQuery, signal?: AbortSignal): Promise<ApiResult<ShipmentPage>>;
  getShipment(shipmentId: string, signal?: AbortSignal): Promise<ApiResult<Shipment>>;
  getShipmentLabel(shipmentId: string, signal?: AbortSignal): Promise<ApiResult<Label>>;
}

const JSON_ACCEPT = 'application/json, application/problem+json';
const LABEL_ACCEPT = 'image/svg+xml, application/problem+json';
const LABEL_FILE_NAME = /^attachment; filename="(label-SIM[0-9]{10}\.svg)"$/;

function randomId(): string {
  return crypto.randomUUID();
}

/** A new booking intent with a key from crypto.randomUUID(): a UUID fits the contract's Idempotency-Key pattern. */
export function newBookingIntent(request: BookingRequest, newKey: () => string = randomId): BookingIntent {
  return { idempotencyKey: newKey(), request };
}

/** The file name in the label's Content-Disposition when it is the contract's exact form, otherwise a fixed name. */
export function labelFileName(header: string | null): string {
  return LABEL_FILE_NAME.exec(header ?? '')?.[1] ?? 'label.svg';
}

interface Call<T> {
  readonly method: 'GET' | 'POST';
  readonly path: string;
  readonly accept: string;
  readonly body?: unknown;
  readonly headers?: Readonly<Record<string, string>>;
  readonly read: (response: Response) => Promise<T | undefined>;
}

/** An AbortSignal that aborts when any of the given ones does, and a way to stop listening to them. */
function linkSignals(signals: ReadonlyArray<AbortSignal | undefined>): { signal: AbortSignal; release: () => void } {
  const controller = new AbortController();
  const releases: Array<() => void> = [];
  for (const signal of signals) {
    if (signal === undefined) {
      continue;
    }
    if (signal.aborted) {
      controller.abort(signal.reason);
      break;
    }
    const abort = (): void => controller.abort(signal.reason);
    signal.addEventListener('abort', abort, { once: true });
    releases.push(() => signal.removeEventListener('abort', abort));
  }
  return { signal: controller.signal, release: () => releases.forEach((release) => release()) };
}

function json<T>(guard: (value: unknown) => value is T): (response: Response) => Promise<T | undefined> {
  return async (response) => {
    const value: unknown = await response.json();
    return guard(value) ? value : undefined;
  };
}

async function readLabel(response: Response): Promise<Label | undefined> {
  if (!(response.headers.get('Content-Type') ?? '').startsWith('image/svg+xml')) {
    return undefined;
  }
  const fileName = labelFileName(response.headers.get('Content-Disposition'));
  return { blob: await response.blob(), fileName };
}

async function readProblemBody(response: Response): Promise<Problem | undefined> {
  if (!(response.headers.get('Content-Type') ?? '').startsWith('application/problem+json')) {
    return undefined;
  }
  try {
    return readProblem(await response.json());
  } catch {
    return undefined;
  }
}

function unexpected(response: Response): ApiFailure {
  const correlationId = response.headers.get('Correlation-Id');
  return correlationId === null
    ? { kind: 'unexpected', status: response.status }
    : { kind: 'unexpected', status: response.status, correlationId };
}

function queryString(query: ShipmentQuery): string {
  const parameters = new URLSearchParams();
  if (query.limit !== undefined) {
    parameters.set('limit', String(query.limit));
  }
  if (query.cursor !== undefined) {
    parameters.set('cursor', query.cursor);
  }
  if (query.status !== undefined) {
    parameters.set('status', query.status);
  }
  const text = parameters.toString();
  return text === '' ? '' : `?${text}`;
}

export function createApiClient(options: ApiClientOptions = {}): ApiClient {
  const fetchImpl = options.fetch ?? globalThis.fetch.bind(globalThis);
  const newCorrelationId = options.newCorrelationId ?? randomId;
  const baseUrl = options.baseUrl ?? '/api/v1';

  async function send<T>(call: Call<T>, callSignal: AbortSignal | undefined): Promise<ApiResult<T>> {
    const headers = new Headers({ Accept: call.accept, 'Correlation-Id': newCorrelationId() });
    if (options.token !== undefined) {
      headers.set('Authorization', `Bearer ${options.token}`);
    }
    for (const entry of Object.entries(call.headers ?? {})) {
      const name = entry[0];
      const value = entry[1];
      headers.set(name, value);
    }
    const linked = linkSignals([options.signal, callSignal]);
    const init: RequestInit = {
      method: call.method, headers, credentials: 'omit', cache: 'no-store', redirect: 'error', signal: linked.signal,
    };
    if (call.body !== undefined) {
      headers.set('Content-Type', 'application/json');
      init.body = JSON.stringify(call.body);
    }
    const failed = (failure: ApiFailure): ApiResult<T> =>
      ({ ok: false, failure: linked.signal.aborted ? { kind: 'aborted' } : failure });
    try {
      let response: Response;
      try {
        response = await fetchImpl(baseUrl + call.path, init);
      } catch {
        return failed({ kind: 'network' });
      }
      if (response.ok) {
        try {
          const value = await call.read(response);
          if (value === undefined) {
            return failed(unexpected(response));
          }
          const replayed = response.headers.get('Idempotency-Replayed') === 'true';
          return { ok: true, value, status: response.status, replayed };
        } catch {
          return failed(unexpected(response));
        }
      }
      const problem = await readProblemBody(response);
      if (problem === undefined) {
        return failed(unexpected(response));
      }
      if (problem.type === 'unauthenticated') {
        options.onUnauthenticated?.();
      }
      return failed({ kind: 'problem', problem });
    } finally {
      linked.release();
    }
  }

  const accept = JSON_ACCEPT;
  return {
    getCurrentMembership: (signal) => send({ method: 'GET', path: '/me', accept, read: json(isMembership) }, signal),
    createQuote: (request, signal) =>
      send({ method: 'POST', path: '/quotes', accept, body: request, read: json(isQuote) }, signal),
    getQuote: (quoteId, signal) =>
      send({ method: 'GET', path: `/quotes/${encodeURIComponent(quoteId)}`, accept, read: json(isQuote) }, signal),
    bookShipment: (intent, signal) => send({
      method: 'POST', path: '/shipments', accept, body: intent.request,
      headers: { 'Idempotency-Key': intent.idempotencyKey }, read: json(isShipment),
    }, signal),
    listShipments: (query, signal) =>
      send({ method: 'GET', path: `/shipments${queryString(query)}`, accept, read: json(isShipmentPage) }, signal),
    getShipment: (shipmentId, signal) => send({
      method: 'GET', path: `/shipments/${encodeURIComponent(shipmentId)}`, accept, read: json(isShipment),
    }, signal),
    getShipmentLabel: (shipmentId, signal) => send({
      method: 'GET', path: `/shipments/${encodeURIComponent(shipmentId)}/label`, accept: LABEL_ACCEPT, read: readLabel,
    }, signal),
  };
}
