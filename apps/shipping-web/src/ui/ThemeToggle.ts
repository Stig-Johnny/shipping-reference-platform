// The theme override: System, Light or Dark, applied as data-theme on <html>. System follows prefers-color-scheme.
// The choice lasts until reload: it is kept nowhere, because v0.1 uses no browser storage.

import { el } from './dom.ts';

export type ThemeChoice = 'system' | 'light' | 'dark';

export const THEME_CHOICES: ReadonlyArray<{ readonly value: ThemeChoice; readonly label: string }> = [
  { value: 'system', label: 'System' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
];

/** The two calls the toggle makes on the root element. */
export interface ThemeRoot {
  setAttribute(name: string, value: string): void;
  removeAttribute(name: string): void;
}

export function themeAttribute(choice: ThemeChoice): 'light' | 'dark' | null {
  return choice === 'system' ? null : choice;
}

export function applyTheme(root: ThemeRoot, choice: ThemeChoice): void {
  const value = themeAttribute(choice);
  if (value === null) {
    root.removeAttribute('data-theme');
  } else {
    root.setAttribute('data-theme', value);
  }
}

/** A radio group, so arrow keys move between the three choices. */
export function themeToggle(root: ThemeRoot): HTMLFieldSetElement {
  const fieldset = el('fieldset', { class: 'theme-toggle' }, el('legend', { class: 'theme-toggle__legend' }, 'Theme'));
  for (const choice of THEME_CHOICES) {
    const input = el('input', {
      type: 'radio', name: 'theme', value: choice.value, checked: choice.value === 'system',
    });
    input.addEventListener('change', () => {
      if (input.checked) {
        applyTheme(root, choice.value);
      }
    });
    fieldset.append(el('label', { class: 'segment' }, input, el('span', {}, choice.label)));
  }
  return fieldset;
}
