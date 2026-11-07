// The signed-in session: the demo token and the membership, in memory only. Each session has its own API client and
// AbortSignal. Switching user aborts every outstanding request of the session and forgets the token, so that no late
// answer from the previous user can reach the screen of the next one.

import { createApiClient } from '../api/client.ts';
import type { ApiClient } from '../api/client.ts';
import type { Membership } from '../api/types.ts';
import { SYNTHETIC_USERS } from './users.ts';
import type { SyntheticUser } from './users.ts';

export interface Session {
  readonly generation: number;
  readonly user: SyntheticUser;
  readonly membership: Membership;
  /** Every request of the session goes through this client, which carries the session's token and signal. */
  readonly api: ApiClient;
  /** Aborted when the session ends. */
  readonly signal: AbortSignal;
  /** Whether this is still the signed-in session: results that arrive later are dropped when it is not. */
  isCurrent(): boolean;
}

export type SignInOutcome =
  | { readonly kind: 'signed-in'; readonly session: Session }
  | { readonly kind: 'no-tenant' }
  | { readonly kind: 'failed' }
  | { readonly kind: 'unreachable' }
  | { readonly kind: 'superseded' };

export type SessionChange = 'signed-in' | 'switched' | 'expired';

export interface Auth {
  current(): Session | undefined;
  signIn(handle: string): Promise<SignInOutcome>;
  /** Ends the session, or the sign-in under way, and forgets token and membership. */
  switchUser(): void;
  onChange(listener: (session: Session | undefined, change: SessionChange) => void): () => void;
}

export interface AuthOptions {
  readonly fetch?: typeof fetch;
  readonly newCorrelationId?: () => string;
}

export function signInMessage(outcome: SignInOutcome): string | undefined {
  switch (outcome.kind) {
    case 'no-tenant':
      return 'This synthetic user belongs to no tenant.';
    case 'failed':
      return 'Sign-in failed.';
    case 'unreachable':
      return 'The server could not be reached.';
    case 'signed-in':
    case 'superseded':
      return undefined;
  }
}

/** What the sign-in page says after a session ended on its own. */
export function sessionEndMessage(change: SessionChange): string | undefined {
  return change === 'expired' ? 'Sign in again.' : undefined;
}

export function createAuth(options: AuthOptions = {}): Auth {
  let generation = 0;
  let session: Session | undefined;
  let controller: AbortController | undefined;
  const listeners = new Set<(session: Session | undefined, change: SessionChange) => void>();

  const notify = (change: SessionChange): void => {
    for (const listener of [...listeners]) {
      listener(session, change);
    }
  };

  /** Ends whatever session or attempt is under way. */
  const end = (): boolean => {
    generation += 1;
    controller?.abort();
    controller = undefined;
    const ended = session !== undefined;
    session = undefined;
    return ended;
  };

  return {
    current: () => session,

    async signIn(handle: string): Promise<SignInOutcome> {
      if (end()) {
        notify('switched');
      }
      const user = SYNTHETIC_USERS.find((candidate) => candidate.handle === handle);
      if (user === undefined) {
        return { kind: 'failed' };
      }
      const attempt = ++generation;
      const attemptController = new AbortController();
      controller = attemptController;
      const api = createApiClient({
        token: user.token,
        signal: attemptController.signal,
        ...(options.fetch === undefined ? {} : { fetch: options.fetch }),
        ...(options.newCorrelationId === undefined ? {} : { newCorrelationId: options.newCorrelationId }),
        onUnauthenticated: () => {
          if (session !== undefined && session.generation === attempt) {
            end();
            notify('expired');
          }
        },
      });
      const result = await api.getCurrentMembership();
      if (attempt !== generation) {
        return { kind: 'superseded' };
      }
      if (!result.ok) {
        controller = undefined;
        attemptController.abort();
        const failure = result.failure;
        if (failure.kind === 'network') {
          return { kind: 'unreachable' };
        }
        if (failure.kind === 'problem' && failure.problem.type === 'no-tenant-membership') {
          return { kind: 'no-tenant' };
        }
        return { kind: 'failed' };
      }
      const signedIn: Session = {
        generation: attempt, user, membership: result.value, api, signal: attemptController.signal,
        isCurrent: () => session !== undefined && session.generation === attempt,
      };
      session = signedIn;
      notify('signed-in');
      return { kind: 'signed-in', session: signedIn };
    },

    switchUser(): void {
      if (end()) {
        notify('switched');
      }
    },

    onChange(listener) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}
