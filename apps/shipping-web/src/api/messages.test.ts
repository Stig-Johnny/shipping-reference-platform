import assert from 'node:assert/strict';
import { describe, test } from 'node:test';
import { failureMessage, failureTitle } from './messages.ts';
import { PROBLEM_TYPES } from './problems.ts';
import type { ApiFailure, Problem, ProblemType } from './problems.ts';

function problem(type: ProblemType, extra: Partial<Problem> = {}): ApiFailure {
  return {
    kind: 'problem',
    problem: { type, title: 'Title', status: 400, detail: 'Detail.', instance: '/api/v1/x', correlationId: 'corr-0000000001', ...extra },
  };
}

describe('the user\'s message for each failure', () => {
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
