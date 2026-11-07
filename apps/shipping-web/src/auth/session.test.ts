import assert from 'node:assert/strict';
import { describe, test } from 'node:test';
import { createAuth, sessionEndMessage, signInMessage } from './session.ts';

const PROBLEM_BASE = 'https://shipping-reference-platform.example/problems/';

const memberships: Record<string, unknown> = {
  'demo-clerk-alpha': {
    userId: '00000000-0000-4000-8000-0000000000a1', userDisplayName: 'Clerk Alpha (synthetic)',
    tenantId: '11111111-1111-4111-8111-111111111111', tenantDisplayName: 'Tenant Alpha (synthetic)', role: 'CLERK', enabledFlags: ['label-preview'],
  },
  'demo-clerk-beta': {
    userId: '00000000-0000-4000-8000-0000000000b1', userDisplayName: 'Clerk Beta (synthetic)',
    tenantId: '22222222-2222-4222-8222-222222222222', tenantDisplayName: 'Tenant Beta (synthetic)', role: 'CLERK', enabledFlags: [],
  },
};

function problemResponse(status: number, type: string): Response {
  return new Response(JSON.stringify({
    type: PROBLEM_BASE + type, title: 'Title', status, detail: 'Detail.', instance: '/api/v1/me', correlationId: 'corr-0000000001',
  }), { status, headers: { 'Content-Type': 'application/problem+json' } });
}

/**
 * A server double for the demo tokens: /me answers per token as the tenants module documents, every other path with an
 * empty page (or 401 once the tokens have `expired`); paths in `hold` wait until their request is aborted.
 */
function server(options: { hold?: string[]; offline?: boolean; expired?: boolean } = {}) {
  const seen: Array<{ url: string; authorization: string | null }> = [];
  const fetchStub = (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
    const url = String(input);
    const authorization = new Headers(init?.headers).get('Authorization');
    seen.push({ url, authorization });
    if (options.offline === true) {
      return Promise.reject(new TypeError('Failed to fetch'));
    }
    if (options.hold?.includes(url) === true) {
      return new Promise((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
      });
    }
    const token = authorization?.replace(/^Bearer /, '') ?? '';
    if (url === '/api/v1/me') {
      if (token === 'demo-no-tenant-user') {
        return Promise.resolve(problemResponse(403, 'no-tenant-membership'));
      }
      const membership = memberships[token];
      return Promise.resolve(membership === undefined ? problemResponse(401, 'unauthenticated')
        : new Response(JSON.stringify(membership), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    }
    return Promise.resolve(options.expired === true || memberships[token] === undefined ? problemResponse(401, 'unauthenticated')
      : new Response(JSON.stringify({ items: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
  };
  return { fetch: fetchStub as typeof fetch, seen };
}

describe('signing in', () => {
  test('holds the token in memory, confirms it with /me and keeps the membership', async () => {
    const { fetch, seen } = server();
    const auth = createAuth({ fetch });
    const changes: string[] = [];
    auth.onChange((_session, reason) => changes.push(reason));
    const outcome = await auth.signIn('clerk-alpha');
    assert.equal(outcome.kind, 'signed-in');
    assert.deepEqual(seen, [{ url: '/api/v1/me', authorization: 'Bearer demo-clerk-alpha' }]);
    assert.equal(auth.current()?.membership.tenantDisplayName, 'Tenant Alpha (synthetic)');
    assert.equal(auth.current()?.user.handle, 'clerk-alpha');
    assert.deepEqual(changes, ['signed-in']);
    assert.equal(signInMessage(outcome), undefined);
  });

  test('a user without a tenant stays signed out, and the attempted token is dropped', async () => {
    const auth = createAuth(server());
    const outcome = await auth.signIn('no-tenant-user');
    assert.equal(outcome.kind, 'no-tenant');
    assert.equal(auth.current(), undefined);
    assert.equal(signInMessage(outcome), 'This synthetic user belongs to no tenant.');
  });

  test('401 is "Sign-in failed." and an unreachable server says so', async () => {
    const auth = createAuth(server());
    const unknown = await auth.signIn('nobody');
    assert.equal(unknown.kind, 'failed');
    assert.equal(signInMessage(unknown), 'Sign-in failed.');
    const offline = await createAuth(server({ offline: true })).signIn('clerk-alpha');
    assert.equal(offline.kind, 'unreachable');
    assert.equal(signInMessage(offline), 'The server could not be reached.');
  });

  test('the session\'s API client sends that user\'s token on every request', async () => {
    const { fetch, seen } = server();
    const auth = createAuth({ fetch });
    await auth.signIn('clerk-beta');
    await auth.current()?.api.listShipments({});
    assert.deepEqual(seen.map((request) => request.authorization), ['Bearer demo-clerk-beta', 'Bearer demo-clerk-beta']);
  });
});

describe('switching user', () => {
  test('aborts the outstanding requests of the session and forgets the membership and the token', async () => {
    const { fetch } = server({ hold: ['/api/v1/shipments'] });
    const auth = createAuth({ fetch });
    const changes: string[] = [];
    auth.onChange((_session, reason) => changes.push(reason));
    await auth.signIn('clerk-alpha');
    const session = auth.current();
    assert.ok(session);
    const outstanding = session.api.listShipments({});
    auth.switchUser();
    assert.deepEqual(await outstanding, { ok: false, failure: { kind: 'aborted' } });
    assert.equal(session.signal.aborted, true);
    assert.equal(auth.current(), undefined);
    assert.deepEqual(changes, ['signed-in', 'switched']);
  });

  test('a sign-in still under way when the user switches is superseded and signs nobody in', async () => {
    const auth = createAuth(server({ hold: ['/api/v1/me'] }));
    const attempt = auth.signIn('clerk-alpha');
    auth.switchUser();
    assert.equal((await attempt).kind, 'superseded');
    assert.equal(auth.current(), undefined);
  });

  test('the next user gets a new session: a new generation, their own token and a fresh signal', async () => {
    const { fetch, seen } = server();
    const auth = createAuth({ fetch });
    await auth.signIn('clerk-alpha');
    const alpha = auth.current();
    auth.switchUser();
    await auth.signIn('clerk-beta');
    const beta = auth.current();
    assert.ok(alpha && beta);
    assert.notEqual(beta.generation, alpha.generation);
    assert.equal(beta.signal.aborted, false);
    assert.equal(alpha.isCurrent(), false);
    assert.equal(beta.isCurrent(), true);
    assert.equal(seen.at(-1)?.authorization, 'Bearer demo-clerk-beta');
  });

  test('a 401 during the session ends it with "Sign in again."', async () => {
    const tokens = { expired: false };
    const double = server(tokens);
    const auth = createAuth({ fetch: double.fetch });
    const reasons: string[] = [];
    auth.onChange((_session, reason) => reasons.push(reason));
    await auth.signIn('clerk-alpha');
    const session = auth.current();
    tokens.expired = true;
    await session?.api.listShipments({});
    assert.equal(auth.current(), undefined);
    assert.deepEqual(reasons, ['signed-in', 'expired']);
    assert.equal(sessionEndMessage('expired'), 'Sign in again.');
    assert.equal(sessionEndMessage('switched'), undefined);
  });
});
