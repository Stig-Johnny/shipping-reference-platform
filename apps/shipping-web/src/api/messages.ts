// The user's message for each failure: the table "Messages per problem type" in docs/ui/workbench.md. Screens add what
// their context knows (a quote's expiry time, an action); the wording for a problem type lives only here.

import type { ApiFailure, Problem } from './problems.ts';
import type { UnavailableReason } from './types.ts';

const ON_OUR_SIDE = 'Something went wrong on our side. Try again.';

export function failureMessage(failure: ApiFailure): string {
  switch (failure.kind) {
    case 'network':
      return 'The server could not be reached.';
    case 'unexpected':
      return ON_OUR_SIDE;
    case 'aborted':
      return 'The request was stopped.';
    case 'problem':
      return problemMessage(failure.problem);
  }
}

/** The heading of a ProblemAlert: the problem's own title, or a fixed one when no problem body arrived. */
export function failureTitle(failure: ApiFailure): string {
  switch (failure.kind) {
    case 'network':
      return 'No connection';
    case 'unexpected':
      return 'Unexpected response';
    case 'aborted':
      return 'Stopped';
    case 'problem':
      return failure.problem.title;
  }
}

function problemMessage(problem: Problem): string {
  switch (problem.type) {
    case 'validation-failed':
      return 'Check the highlighted fields.';
    case 'malformed-request':
      return 'The request could not be read. Reload and try again.';
    case 'unauthenticated':
      return 'Sign in again.';
    case 'no-tenant-membership':
      return 'This synthetic user belongs to no tenant.';
    case 'no-offer-available':
      return noOfferMessage(problem.reason);
    case 'quote-not-found':
    case 'offer-not-found':
      return 'Quote not found.';
    case 'quote-expired':
      return 'This quote expired. Get a fresh quote.';
    case 'recipient-does-not-match-quote':
      return 'The recipient\'s postcode and country must match the quote\'s destination.';
    case 'idempotency-key-reused':
      return 'This booking changed after it was sent. Review the details and book again.';
    case 'quote-already-booked':
      return 'This quote has already been booked.';
    case 'shipment-not-found':
      return 'Shipment not found.';
    case 'shipment-cancelled':
      return 'This shipment is cancelled, so it has no label.';
    case 'not-acceptable':
    case 'unsupported-media-type':
    case 'method-not-allowed':
    case 'not-found':
    case 'internal-error':
      return ON_OUR_SIDE;
  }
}

function noOfferMessage(reason: UnavailableReason | undefined): string {
  switch (reason) {
    case 'ROUTE_NOT_SERVED':
      return 'The simulated carrier does not serve this route. Served countries: DE, DK, FI, NL, NO, SE.';
    case 'BILLABLE_WEIGHT_ABOVE_LIMIT':
      return 'The billable weight is above 30 kg. Billable weight is the larger of actual weight and length x width x '
        + 'height / 5000.';
    case 'SERVICE_LEVEL_NOT_AVAILABLE':
      return 'Express is available up to 10 kg billable weight. Choose another service level or Any.';
    case undefined:
      return 'No offer is available for this parcel and route.';
  }
}
