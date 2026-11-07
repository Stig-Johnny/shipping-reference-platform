import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, test } from 'node:test';
import { failureMessage, failureTitle } from './messages.ts';
import { PROBLEM_BASE, PROBLEM_TYPES, isRetryable, readProblem, referenceOf } from './problems.ts';
import type { ApiFailure, Problem, ProblemType } from './problems.ts';
import { FLAG_KEYS, QUOTE_STATUSES, SERVICE_LEVELS, SHIPMENT_STATUSES, UNAVAILABLE_REASONS, ZONES } from './types.ts';

const CONTRACT = readFileSync(new URL('../../../../contracts/shipping.openapi.yaml', import.meta.url), 'utf8');

/** The lines of one schema under components/schemas, read as text: enough to find its enum values. */
function schemaLines(name: string): string[] {
  const lines = CONTRACT.split('\n');
  const start = lines.indexOf(`    ${name}:`);
  assert.notEqual(start, -1, `the contract has no schema ${name}`);
  const end = lines.findIndex((line, index) => index > start && /^ {0,4}\S/.test(line));
  return lines.slice(start + 1, end);
}

function enumValues(name: string): string[] {
  const block = schemaLines(name);
  const inline = block.map((line) => /^\s+enum: \[(.*)\]$/.exec(line)?.[1]).find((value) => value !== undefined);
  if (inline !== undefined) {
    return inline.split(',').map((value) => value.trim().replace(/^'|'$/g, ''));
  }
  return block.map((line) => /^\s+- (\S+)$/.exec(line)?.[1]).filter((value) => value !== undefined);
}

function problem(type: ProblemType, extra: Partial<Problem> = {}): ApiFailure {
  return {
    kind: 'problem',
    problem: { type, title: 'Title', status: 400, detail: 'Detail.', instance: '/api/v1/x', correlationId: 'corr-0000000001', ...extra },
  };
}

describe('the hand-written types follow the contract', () => {
  test('the problem types are exactly the contract\'s ProblemType enum', () => {
    assert.deepEqual(PROBLEM_TYPES.map((type) => PROBLEM_BASE + type).toSorted(), enumValues('ProblemType').toSorted());
  });

  test('the enums are exactly the contract\'s', () => {
    assert.deepEqual([...SERVICE_LEVELS], enumValues('ServiceLevel'));
    assert.deepEqual([...ZONES], enumValues('Zone'));
    assert.deepEqual([...QUOTE_STATUSES], enumValues('QuoteStatus'));
    assert.deepEqual([...SHIPMENT_STATUSES], enumValues('ShipmentStatus'));
    assert.deepEqual([...UNAVAILABLE_REASONS], enumValues('UnavailableReason'));
    assert.deepEqual([...FLAG_KEYS], enumValues('FlagKey'));
  });
});

describe('readProblem', () => {
  const valid = {
    type: `${PROBLEM_BASE}no-offer-available`, title: 'No offer available', status: 422,
    detail: 'The route from NO to US is not served by the simulated carrier.', instance: '/api/v1/quotes',
    correlationId: '3f2b9c1e-6a7d-4e8f-9b0a-1c2d3e4f5a6b', reason: 'ROUTE_NOT_SERVED',
  };

  test('reads a contract problem and keeps the type suffix', () => {
    assert.deepEqual(readProblem(valid), { ...valid, type: 'no-offer-available' });
  });

  test('refuses a problem that is not the contract\'s', () => {
    assert.equal(readProblem(null), undefined);
    assert.equal(readProblem([]), undefined);
    assert.equal(readProblem({ ...valid, type: 'about:blank' }), undefined);
    assert.equal(readProblem({ ...valid, type: `${PROBLEM_BASE}made-up` }), undefined);
    assert.equal(readProblem({ ...valid, status: 200 }), undefined);
    assert.equal(readProblem({ ...valid, correlationId: undefined }), undefined);
    assert.equal(readProblem({ ...valid, detail: 7 }), undefined);
  });

  test('keeps well-formed errors[] items and an allowed reason, and drops the rest', () => {
    const read = readProblem({
      ...valid, reason: 'NOT_A_REASON',
      errors: [{ in: 'body', name: '/parcel/weightGrams', detail: 'must be between 1 and 30000' }, { in: 'cookie', name: 'x', detail: 'x' }, 'junk'],
    });
    assert.deepEqual(read?.errors, [{ in: 'body', name: '/parcel/weightGrams', detail: 'must be between 1 and 30000' }]);
    assert.equal(read?.reason, undefined);
    assert.equal(read !== undefined && 'reason' in read, false, 'absent values are omitted, never undefined');
  });
});

describe('what a failure means for the user', () => {
  test('retry helps for network failures, unexpected responses and errors on our side only', () => {
    assert.equal(isRetryable({ kind: 'network' }), true);
    assert.equal(isRetryable({ kind: 'unexpected', status: 502 }), true);
    assert.equal(isRetryable(problem('internal-error', { status: 500 })), true);
    assert.equal(isRetryable(problem('not-acceptable', { status: 406 })), true);
    assert.equal(isRetryable(problem('validation-failed')), false);
    assert.equal(isRetryable(problem('malformed-request')), false);
    assert.equal(isRetryable(problem('quote-expired', { status: 422 })), false);
    assert.equal(isRetryable({ kind: 'aborted' }), false);
  });

  test('the reference is the problem\'s correlation ID, or the response\'s when the body was not a problem', () => {
    assert.equal(referenceOf(problem('internal-error')), 'corr-0000000001');
    assert.equal(referenceOf({ kind: 'unexpected', status: 502, correlationId: 'corr-gateway-01' }), 'corr-gateway-01');
    assert.equal(referenceOf({ kind: 'network' }), undefined);
  });

  test('messages follow the workbench table', () => {
    const cases: Array<[ApiFailure, string]> = [
      [{ kind: 'network' }, 'The server could not be reached.'],
      [{ kind: 'unexpected', status: 502 }, 'Something went wrong on our side. Try again.'],
      [problem('validation-failed'), 'Check the highlighted fields.'],
      [problem('malformed-request'), 'The request could not be read. Reload and try again.'],
      [problem('unauthenticated'), 'Sign in again.'],
      [problem('no-tenant-membership'), 'This synthetic user belongs to no tenant.'],
      [problem('no-offer-available', { reason: 'ROUTE_NOT_SERVED' }), 'The simulated carrier does not serve this route. Served countries: DE, DK, FI, NL, NO, SE.'],
      [problem('no-offer-available', { reason: 'BILLABLE_WEIGHT_ABOVE_LIMIT' }), 'The billable weight is above 30 kg. Billable weight is the larger of actual weight and length x width x height / 5000.'],
      [problem('no-offer-available', { reason: 'SERVICE_LEVEL_NOT_AVAILABLE' }), 'Express is available up to 10 kg billable weight. Choose another service level or Any.'],
      [problem('quote-not-found'), 'Quote not found.'],
      [problem('offer-not-found'), 'Quote not found.'],
      [problem('recipient-does-not-match-quote'), 'The recipient\'s postcode and country must match the quote\'s destination.'],
      [problem('idempotency-key-reused'), 'This booking changed after it was sent. Review the details and book again.'],
      [problem('quote-already-booked'), 'This quote has already been booked.'],
      [problem('shipment-not-found'), 'Shipment not found.'],
      [problem('shipment-cancelled'), 'This shipment is cancelled, so it has no label.'],
      [problem('not-acceptable'), 'Something went wrong on our side. Try again.'],
      [problem('unsupported-media-type'), 'Something went wrong on our side. Try again.'],
      [problem('method-not-allowed'), 'Something went wrong on our side. Try again.'],
      [problem('not-found'), 'Something went wrong on our side. Try again.'],
      [problem('internal-error'), 'Something went wrong on our side. Try again.'],
    ];
    for (const [failure, message] of cases) {
      assert.equal(failureMessage(failure), message, JSON.stringify(failure));
    }
  });

  test('every problem type has a message', () => {
    for (const type of PROBLEM_TYPES) {
      assert.ok(failureMessage(problem(type)).length > 0, type);
    }
  });

  test('the title is the problem\'s own; transport failures get a fixed one', () => {
    assert.equal(failureTitle(problem('internal-error', { title: 'Internal error' })), 'Internal error');
    assert.equal(failureTitle({ kind: 'network' }), 'No connection');
    assert.equal(failureTitle({ kind: 'unexpected', status: 502 }), 'Unexpected response');
  });
});
