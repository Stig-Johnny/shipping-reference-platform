import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

// Same-origin API and static modules: no inline execution, evaluation or external origins.
const POLICY = "default-src 'self'; script-src 'self'; connect-src 'self'; style-src 'self'; "
  + "img-src 'self'; font-src 'self'; base-uri 'none'; form-action 'none'; object-src 'none'; "
  + "frame-src 'none'; worker-src 'none'";

test('the entry document declares the strict CSP before any resource is loaded', () => {
  const html = readFileSync(new URL('../../index.html', import.meta.url), 'utf8');
  const policies = [...html.matchAll(/<meta http-equiv="Content-Security-Policy" content="([^"]*)">/g)];
  assert.equal(policies.length, 1);
  const policy = policies.at(0);
  assert.ok(policy);
  assert.equal(policy.at(1), POLICY);
  assert.ok(policy.index < html.indexOf('<link'));
  assert.ok(policy.index < html.indexOf('<script'));
});
