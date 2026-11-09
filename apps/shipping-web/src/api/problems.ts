type ArrayValue<T> = T extends ReadonlyArray<infer Value> ? Value : never;

// RFC 9457 problems as the contract defines them, and the other ways a call can fail. Every failure the screens see
// is one of the four kinds of ApiFailure; a problem carries the type suffix, never the full URI.

import { isRecord } from './guards.ts';
import { UNAVAILABLE_REASONS } from './types.ts';
import type { InvalidParam, UnavailableReason } from './types.ts';

export const PROBLEM_BASE = 'https://shipping-reference-platform.example/problems/';

/** The suffixes of the contract's ProblemType enum, in the contract's order. */
export const PROBLEM_TYPES = [
  'validation-failed', 'malformed-request', 'unauthenticated', 'no-tenant-membership', 'not-found', 'quote-not-found',
  'offer-not-found', 'shipment-not-found', 'method-not-allowed', 'not-acceptable', 'quote-already-booked',
  'shipment-cancelled', 'unsupported-media-type', 'quote-expired', 'recipient-does-not-match-quote',
  'idempotency-key-reused', 'no-offer-available', 'internal-error',
] as const;

export type ProblemType = ArrayValue<typeof PROBLEM_TYPES>;

export interface Problem {
  readonly type: ProblemType;
  readonly title: string;
  readonly status: number;
  /** Safe to show to the user, by contract. */
  readonly detail: string;
  readonly instance: string;
  readonly correlationId: string;
  /** Only with validation-failed. */
  readonly errors?: readonly InvalidParam[];
  /** Only with no-offer-available. */
  readonly reason?: UnavailableReason;
}

export type ApiFailure =
  /** The server answered with a contract problem. */
  | { readonly kind: 'problem'; readonly problem: Problem }
  /** No answer arrived: the request may or may not have been processed. */
  | { readonly kind: 'network' }
  /** An answer that is not the contract's: an error without a problem body, or a body of the wrong shape. */
  | { readonly kind: 'unexpected'; readonly status: number; readonly correlationId?: string }
  /** The session or the screen stopped the request; nobody is waiting for it. */
  | { readonly kind: 'aborted' };

/** Problems that a repeat of the same request can get past: failures on the server's side, not in the request. */
const RETRYABLE: ReadonlySet<ProblemType> = new Set<ProblemType>([
  'not-acceptable', 'unsupported-media-type', 'method-not-allowed', 'not-found', 'internal-error',
]);

const PARAMETER_LOCATIONS: ReadonlySet<string> = new Set(['body', 'query', 'path', 'header']);

/** The contract problem in a parsed problem+json body, or undefined when the body is not one. */
export function readProblem(body: unknown): Problem | undefined {
  if (!isRecord(body)) {
    return undefined;
  }
  const { type, title, status, detail, instance, correlationId } = body;
  if (typeof type !== 'string' || !type.startsWith(PROBLEM_BASE) || typeof title !== 'string'
    || typeof detail !== 'string' || typeof instance !== 'string' || typeof correlationId !== 'string'
    || typeof status !== 'number' || !Number.isInteger(status) || status < 400 || status > 599) {
    return undefined;
  }
  const suffix = PROBLEM_TYPES.find((candidate) => PROBLEM_BASE + candidate === type);
  if (suffix === undefined) {
    return undefined;
  }
  const errors = Array.isArray(body.errors) ? body.errors.filter(isInvalidParam) : [];
  const reason = UNAVAILABLE_REASONS.find((candidate) => candidate === body.reason);
  return {
    type: suffix, title, status, detail, instance, correlationId,
    ...(errors.length > 0 ? { errors } : {}),
    ...(reason === undefined ? {} : { reason }),
  };
}

function isInvalidParam(value: unknown): value is InvalidParam {
  return isRecord(value) && typeof value.in === 'string' && PARAMETER_LOCATIONS.has(value.in)
    && typeof value.name === 'string' && typeof value.detail === 'string';
}

/** The problem type of a failure, or undefined when the failure is not a contract problem. */
export function problemType(failure: ApiFailure): ProblemType | undefined {
  return failure.kind === 'problem' ? failure.problem.type : undefined;
}

/** Whether "Try again" can help: failures in transport or on the server's side, never a rejection of the request. */
export function isRetryable(failure: ApiFailure): boolean {
  switch (failure.kind) {
    case 'network':
    case 'unexpected':
      return true;
    case 'aborted':
      return false;
    case 'problem':
      return RETRYABLE.has(failure.problem.type);
  }
}

/** The correlation ID to quote as "Reference", when the server sent one. */
export function referenceOf(failure: ApiFailure): string | undefined {
  switch (failure.kind) {
    case 'problem':
      return failure.problem.correlationId;
    case 'unexpected':
      return failure.correlationId;
    case 'network':
    case 'aborted':
      return undefined;
  }
}
