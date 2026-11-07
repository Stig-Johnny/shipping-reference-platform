// The three public demo users of docs/modules/tenants.md. Their tokens are published, guessable test fixtures
// (demo-<handle>), not secrets: anyone can act as any synthetic user. They prove tenant derivation and isolation,
// not authentication strength.

export interface SyntheticUser {
  readonly handle: string;
  readonly displayName: string;
  /** Shown under the name in the picker. */
  readonly description: string;
  readonly token: string;
}

function user(handle: string, displayName: string, description: string): SyntheticUser {
  return { handle, displayName, description, token: `demo-${handle}` };
}

export const SYNTHETIC_USERS: readonly SyntheticUser[] = [
  user('clerk-alpha', 'Clerk Alpha (synthetic)', 'Clerk of Tenant Alpha (synthetic)'),
  user('clerk-beta', 'Clerk Beta (synthetic)', 'Clerk of Tenant Beta (synthetic)'),
  user('no-tenant-user', 'User without tenant (synthetic)', 'Belongs to no tenant'),
];
