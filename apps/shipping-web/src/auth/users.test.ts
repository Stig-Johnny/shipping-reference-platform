import assert from 'node:assert/strict';
import { describe, test } from 'node:test';
import { SYNTHETIC_USERS } from './users.ts';

describe('the synthetic users', () => {
  test('are the three public demo users of docs/modules/tenants.md, with their published tokens', () => {
    assert.deepEqual(SYNTHETIC_USERS.map((user) => [user.handle, user.displayName, user.token]), [
      ['clerk-alpha', 'Clerk Alpha (synthetic)', 'demo-clerk-alpha'],
      ['clerk-beta', 'Clerk Beta (synthetic)', 'demo-clerk-beta'],
      ['no-tenant-user', 'User without tenant (synthetic)', 'demo-no-tenant-user'],
    ]);
    for (const user of SYNTHETIC_USERS) {
      assert.match(user.token, /^demo-[a-z0-9-]{3,40}$/);
    }
  });
});
