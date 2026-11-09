import assert from 'node:assert/strict';
import { test } from 'node:test';
import { THEME_CHOICES, applyTheme, themeAttribute } from './ThemeToggle.ts';

/** Stands in for document.documentElement: only the two calls the toggle may make. */
function fakeRoot() {
  const attributes = new Map<string, string>();
  return {
    attributes,
    setAttribute: (name: string, value: string) => { attributes.set(name, value); },
    removeAttribute: (name: string) => { attributes.delete(name); },
  };
}

test('offers System, Light and Dark, in that order, with System first', () => {
  assert.deepEqual(THEME_CHOICES.map((choice) => choice.label), ['System', 'Light', 'Dark']);
  assert.deepEqual(THEME_CHOICES.map((choice) => choice.value), ['system', 'light', 'dark']);
});

test('System leaves the colour scheme to prefers-color-scheme; Light and Dark override it', () => {
  assert.equal(themeAttribute('system'), null);
  assert.equal(themeAttribute('light'), 'light');
  assert.equal(themeAttribute('dark'), 'dark');
});

test('the override is the data-theme attribute on the root element and nothing else', () => {
  const root = fakeRoot();
  applyTheme(root, 'dark');
  assert.deepEqual([...root.attributes], [['data-theme', 'dark']]);
  applyTheme(root, 'light');
  assert.deepEqual([...root.attributes], [['data-theme', 'light']]);
  applyTheme(root, 'system');
  assert.deepEqual([...root.attributes], []);
});
