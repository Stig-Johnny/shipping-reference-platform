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

test('every emitted module reachable from the entry stays outside the authenticated API namespace', () => {
  const html = readFileSync(new URL('../../index.html', import.meta.url), 'utf8');
  const entry = /<script type="module" src="([^"]+)"><\/script>/.exec(html)?.[1];
  assert.ok(entry);
  const pending = [new URL(entry, 'http://workbench.invalid')];
  const seen = new Set<string>();
  while (pending.length > 0) {
    const url = pending.pop();
    assert.ok(url);
    if (seen.has(url.pathname)) continue;
    seen.add(url.pathname);
    assert.equal(url.origin, 'http://workbench.invalid');
    assert.ok(!url.pathname.startsWith('/api/'), `module needs public static serving: ${url.pathname}`);
    const js = readFileSync(new URL(`../../dist${url.pathname}`, import.meta.url), 'utf8');
    for (const match of js.matchAll(/\bfrom\s+['"]([^'"]+)['"]/g)) {
      pending.push(new URL(match[1], url));
    }
  }
  assert.ok(seen.size > 20, 'walk the real workbench module graph, not just the entry');
});
