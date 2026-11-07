// Form fields with a visible label, a hint and an error message linked by aria-describedby. Required and optional
// fields say so in text. An invalid field has aria-invalid="true" and its message says what to do.

import { el, visuallyHidden } from './dom.ts';

export interface FieldOptions {
  readonly id: string;
  readonly label: string;
  readonly hint?: string | undefined;
  readonly required: boolean;
  readonly value?: string | undefined;
  readonly readOnly?: boolean | undefined;
  readonly numeric?: boolean | undefined;
  readonly autocomplete?: string | undefined;
  readonly upperCase?: boolean | undefined;
}

export interface Field<C extends HTMLInputElement | HTMLSelectElement> {
  readonly root: HTMLElement;
  readonly control: C;
  setError(message: string | undefined): void;
}

function frame<C extends HTMLInputElement | HTMLSelectElement>(options: FieldOptions, control: C): Field<C> {
  const hintId = `${options.id}-hint`;
  const errorId = `${options.id}-error`;
  const marker = options.readOnly === true ? '(from the quote)' : options.required ? '(required)' : '(optional)';
  const label = el('label', { class: 'field__label', for: options.id },
    `${options.label} `, el('span', { class: 'field__marker' }, marker));
  const hint = options.hint === undefined ? null : el('p', { class: 'field__hint', id: hintId }, options.hint);
  const errorText = el('span', {});
  const error = el('p', { class: 'field__error', id: errorId, hidden: true }, visuallyHidden('Error: '), errorText);
  const describedBy = (withError: boolean): string | undefined => {
    const ids = [hint === null ? undefined : hintId, withError ? errorId : undefined].filter((id) => id !== undefined);
    return ids.length === 0 ? undefined : ids.join(' ');
  };
  const update = (message: string | undefined): void => {
    const invalid = message !== undefined;
    errorText.textContent = message ?? '';
    error.hidden = !invalid;
    const ids = describedBy(invalid);
    if (ids === undefined) {
      control.removeAttribute('aria-describedby');
    } else {
      control.setAttribute('aria-describedby', ids);
    }
    if (invalid) {
      control.setAttribute('aria-invalid', 'true');
    } else {
      control.removeAttribute('aria-invalid');
    }
  };
  update(undefined);
  const root = el('div', { class: 'field' }, label, hint, error, control);
  return { root, control, setError: update };
}

export function textField(options: FieldOptions): Field<HTMLInputElement> {
  const input = el('input', {
    class: 'input', id: options.id, name: options.id, type: 'text', required: options.required,
    readonly: options.readOnly === true, inputmode: options.numeric === true ? 'numeric' : undefined,
    autocomplete: options.autocomplete ?? 'off', autocapitalize: options.upperCase === true ? 'characters' : undefined,
    spellcheck: 'false',
  });
  input.value = options.value ?? '';
  if (options.upperCase === true) {
    input.addEventListener('blur', () => {
      input.value = input.value.toUpperCase();
    });
  }
  return frame(options, input);
}

export interface SelectOption {
  readonly value: string;
  readonly label: string;
}

export interface SelectOptions extends FieldOptions {
  readonly options: readonly SelectOption[];
}

export function selectField(options: SelectOptions): Field<HTMLSelectElement> {
  const select = el('select', { class: 'input select', id: options.id, name: options.id, required: options.required });
  for (const option of options.options) {
    select.append(el('option', { value: option.value }, option.label));
  }
  select.value = options.value ?? '';
  return frame(options, select);
}

/** A read-only value shown as a field, for values the user cannot change here. */
export function readOnlyField(id: string, label: string, value: string): Field<HTMLInputElement> {
  return textField({ id, label, required: true, value, readOnly: true });
}
