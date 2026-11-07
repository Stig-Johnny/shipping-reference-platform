// AppShell: the skip link, the left navigation (a top bar below 720 px) with New quote and Shipments, a footer with the
// signed-in user, the tenant, the theme override and Switch user, the main region and the one status region.

import { button, createAnnouncer } from '../ui/components.ts';
import { el, replace } from '../ui/dom.ts';
import { themeToggle } from '../ui/ThemeToggle.ts';

export interface ShellPaths {
  newQuote(): string;
  shipments(): string;
}

export interface Shell {
  readonly main: HTMLElement;
  setUser(user: { readonly name: string; readonly tenant: string } | undefined): void;
  setSection(section: 'new-quote' | 'shipments' | undefined): void;
  announce(message: string): void;
  /** Asks before switching user while a booking outcome is still unknown, because its recovery would be lost. */
  confirmSwitch(onConfirm: () => void): void;
}

export function renderShell(root: HTMLElement, paths: ShellPaths, onSwitchUser: () => void): Shell {
  const announcer = createAnnouncer();
  const newQuote = el('a', { href: paths.newQuote(), class: 'nav-link' }, 'New quote');
  const shipments = el('a', { href: paths.shipments(), class: 'nav-link' }, 'Shipments');
  const links = el('ul', { class: 'nav-list' }, el('li', {}, newQuote), el('li', {}, shipments));
  const who = el('div', { class: 'who' });
  const switchArea = el('div', { class: 'switch-area' });
  const main = el('main', { id: 'main', class: 'main', tabindex: '-1' });
  const switchButton = button('Switch user', 'secondary', onSwitchUser);

  replace(root,
    el('a', { href: '#main', class: 'skip-link' }, 'Skip to main content'),
    el('div', { class: 'app' },
      el('nav', { class: 'sidebar', 'aria-label': 'Main' },
        el('p', { class: 'brand' }, 'Shipping workbench'),
        links,
        el('div', { class: 'sidebar__footer' }, who, themeToggle(document.documentElement), switchArea)),
      main),
    announcer.element);

  return {
    main,
    setUser(user) {
      links.hidden = user === undefined;
      replace(who, user === undefined ? el('p', { class: 'who__name' }, 'Not signed in')
        : el('p', {},
          el('span', { class: 'who__name' }, user.name),
          el('span', { class: 'who__tenant' }, user.tenant)));
      replace(switchArea, user === undefined ? null : switchButton);
    },
    setSection(section) {
      for (const [link, name] of [[newQuote, 'new-quote'], [shipments, 'shipments']] as const) {
        if (section === name) {
          link.setAttribute('aria-current', 'page');
        } else {
          link.removeAttribute('aria-current');
        }
      }
    },
    announce: (message) => announcer.announce(message),
    confirmSwitch(onConfirm) {
      const stay = button('Stay', 'secondary', () => {
        replace(switchArea, switchButton);
        switchButton.focus();
      });
      replace(switchArea, el('div', { class: 'notice notice--warning', role: 'alert' },
        el('p', {}, 'A booking outcome is still unknown. Switching user loses the safe retry: check Shipments first.'),
        el('div', { class: 'button-row' }, button('Switch anyway', 'primary', onConfirm), stay)));
      stay.focus();
    },
  };
}
