// The only way the web app builds DOM: document.createElement, attributes and text nodes. There is no HTML parsing
// anywhere, so text from the server is always text. Inline handlers and style attributes are refused because the
// page runs under a Content-Security-Policy without 'unsafe-inline'.

export type Child = Node | string | null | undefined | false;
export type Attributes = Readonly<Record<string, string | number | boolean | null | undefined>>;

const REFUSED_ATTRIBUTE = /^(on|style$|srcdoc$)/i;

export function setAttributes(element: Element, attributes: Attributes): void {
  for (const entry of Object.entries(attributes)) {
    const name = entry[0];
    const value = entry[1];
    if (REFUSED_ATTRIBUTE.test(name)) {
      throw new Error(`The attribute ${name} is not allowed: use addEventListener and CSS classes.`);
    }
    if (value === undefined || value === null || value === false) {
      element.removeAttribute(name);
    } else {
      element.setAttribute(name, value === true ? '' : String(value));
    }
  }
}

export function append(parent: Node, ...children: Child[]): void {
  for (const child of children) {
    if (child !== undefined && child !== null && child !== false) {
      parent.appendChild(typeof child === 'string' ? document.createTextNode(child) : child);
    }
  }
}

/** Replaces all children of a container. */
export function replace(parent: Element, ...children: Child[]): void {
  parent.replaceChildren();
  append(parent, ...children);
}

export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K, attributes: Attributes = {}, ...children: Child[]
) {
  const element = document.createElement(tag);
  setAttributes(element, attributes);
  append(element, ...children);
  return element;
}

/** Text for screen readers only. */
export function visuallyHidden(text: string): HTMLSpanElement {
  return el('span', { class: 'visually-hidden' }, text);
}

let sequence = 0;

/** An ID that is unique in the page, for label, hint and error associations. */
export function uniqueId(prefix: string): string {
  sequence += 1;
  return `${prefix}-${sequence}`;
}

/** Moves focus to an element that is not focusable by Tab, such as a heading or a summary. */
export function focusProgrammatically(element: HTMLElement): void {
  if (!element.hasAttribute('tabindex')) {
    element.setAttribute('tabindex', '-1');
  }
  element.focus();
}
