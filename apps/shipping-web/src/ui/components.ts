// Presentational building blocks of docs/ui/workbench.md: Button, ProblemAlert, EmptyState, StatusBadge, Skeleton,
// the error summary and the one status region. They know nothing of the API: callers pass text.

import { el, focusProgrammatically, visuallyHidden } from './dom.ts';
import type { Child } from './dom.ts';

export type ButtonKind = 'primary' | 'secondary';

export function button(
  label: string, kind: ButtonKind, onClick?: () => void, type: 'button' | 'submit' = 'button',
): HTMLButtonElement {
  const element = el('button', { type, class: `button button--${kind}` }, label);
  if (onClick !== undefined) {
    element.addEventListener('click', onClick);
  }
  return element;
}

/** A link styled as a button, for actions that go somewhere. */
export function linkButton(label: string, href: string, kind: ButtonKind = 'secondary'): HTMLAnchorElement {
  return el('a', { href, class: `button button--${kind}` }, label);
}

export interface AlertContent {
  readonly title: string;
  readonly message: string;
  /** The correlation ID, shown as "Reference: <id>". */
  readonly reference?: string | undefined;
  /** Offered as "Try again" when retrying can help. */
  readonly onRetry?: (() => void) | undefined;
  readonly actions?: readonly Child[] | undefined;
  readonly tone?: 'danger' | 'warning' | undefined;
}

/** ProblemAlert: role="alert", so a failure is announced when it appears. */
export function problemAlert(content: AlertContent): HTMLElement {
  const actions = [...(content.actions ?? [])];
  if (content.onRetry !== undefined) {
    actions.unshift(button('Try again', 'secondary', content.onRetry));
  }
  return el('div', { class: `alert alert--${content.tone ?? 'danger'}`, role: 'alert' },
    el('p', { class: 'alert__title' }, content.title),
    el('p', { class: 'alert__message' }, content.message),
    content.reference === undefined ? null
      : el('p', { class: 'alert__reference' }, 'Reference: ', el('code', { class: 'mono' }, content.reference)),
    actions.length === 0 ? null : el('div', { class: 'button-row' }, ...actions));
}

/** A notice that is not an alert: for states the user caused, such as an expired quote. */
export function notice(tone: 'warning' | 'success' | 'info', ...children: Child[]): HTMLElement {
  return el('div', { class: `notice notice--${tone}` }, ...children);
}

/** EmptyState: one sentence and one action. */
export function emptyState(message: string, action?: { readonly label: string; readonly href: string }): HTMLElement {
  return el('div', { class: 'empty-state' },
    el('p', { class: 'empty-state__message' }, message),
    action === undefined ? null : linkButton(action.label, action.href, 'primary'));
}

export type BadgeTone = 'neutral' | 'success' | 'warning' | 'danger';

/** StatusBadge: status is never colour alone, the label is always text. */
export function statusBadge(label: string, tone: BadgeTone): HTMLSpanElement {
  return el('span', { class: `badge badge--${tone}` }, label);
}

/** Skeleton blocks are decorative; the container they fill carries aria-busy and a hidden "Loading". */
export function skeleton(shape: 'line' | 'heading' | 'card' | 'cell'): HTMLSpanElement {
  return el('span', { class: `skeleton skeleton--${shape}`, 'aria-hidden': 'true' });
}

export function loadingText(what: string): HTMLSpanElement {
  return visuallyHidden(`Loading ${what}`);
}

export interface SummaryItem {
  readonly text: string;
  /** The field to move to; without it the item is plain text. */
  readonly targetId?: string | undefined;
}

/** The error summary at the top of a form. It is focused after a failed submit; its links move focus to the field. */
export function errorSummary(items: readonly SummaryItem[]): HTMLElement {
  const list = el('ul', { class: 'error-summary__list' });
  for (const item of items) {
    const targetId = item.targetId;
    if (targetId === undefined) {
      list.append(el('li', {}, item.text));
      continue;
    }
    const link = el('a', { href: `#${targetId}` }, item.text);
    link.addEventListener('click', (event) => {
      event.preventDefault();
      const target = document.getElementById(targetId);
      if (target !== null) {
        target.focus();
      }
    });
    list.append(el('li', {}, link));
  }
  return el('div', { class: 'error-summary', role: 'alert', tabindex: '-1' },
    el('p', { class: 'error-summary__title' }, 'Check the highlighted fields.'), list);
}

export function showErrorSummary(container: HTMLElement, items: readonly SummaryItem[]): void {
  if (items.length === 0) {
    container.replaceChildren();
    return;
  }
  const summary = errorSummary(items);
  container.replaceChildren(summary);
  focusProgrammatically(summary);
}

export interface Announcer {
  readonly element: HTMLElement;
  /** Says a completed action once, politely: "Offers ready", "Shipment booked". */
  announce(message: string): void;
}

/** The page's one role="status" region. */
export function createAnnouncer(): Announcer {
  const element = el('div', { class: 'visually-hidden', role: 'status' });
  let timer: number | undefined;
  return {
    element,
    announce(message: string): void {
      element.textContent = '';
      if (timer !== undefined) {
        clearTimeout(timer);
      }
      timer = setTimeout(() => {
        element.textContent = message;
      }, 100);
    },
  };
}
