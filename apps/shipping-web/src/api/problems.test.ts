import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, test } from 'node:test';
import { PROBLEM_BASE, PROBLEM_TYPES, isRetryable, readProblem, referenceOf } from './problems.ts';
import type { ApiFailure, Problem, ProblemType } from './problems.ts';

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

describe('the hand-written problem types follow the contract', () => {
  test('the problem types are exactly the contract\'s ProblemType enum', () => {
    assert.deepEqual(PROBLEM_TYPES.map((type) => PROBLEM_BASE + type).toSorted(), enumValues('ProblemType').toSorted());
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
});
