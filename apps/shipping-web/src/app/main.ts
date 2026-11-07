// The entry module: one session at a time, a small router on the History API, and the screens of the features.
// Everything the session knows (membership, token, quotes, booking intents, open requests, object URLs) belongs to
// it and is dropped when the user switches, so the next user can never see the previous one's data.

import { createAuth, sessionEndMessage } from '../auth/session.ts';
import type { Session } from '../auth/session.ts';
import { mountSignIn } from '../auth/SignIn.ts';
import { createQuoteMemory, mountNewQuotePage, mountQuotePage } from '../features/quotes/index.ts';
import type { QuoteMemory, QuotesContext } from '../features/quotes/index.ts';
import { createBookingMemory, mountShipmentsPage } from '../features/shipments/index.ts';
import type { BookingMemory, ShipmentsPageView } from '../features/shipments/index.ts';
import { emptyState } from '../ui/components.ts';
import { el, focusProgrammatically, replace } from '../ui/dom.ts';
import { renderShell } from './AppShell.ts';
import type { Shell } from './AppShell.ts';
import { PATHS, documentTitle, isProtected, navSection, parseRoute, returnPath, screenName } from './routes.ts';
import type { Route } from './routes.ts';

interface SessionMemory {
  readonly session: Session;
  readonly quotes: QuoteMemory;
  readonly bookings: BookingMemory;
}

interface Screen {
  readonly heading: HTMLElement;
  readonly dispose: () => void;
  /** Present on the shipments page, which opens and closes its drawer itself. */
  readonly shipments?: ShipmentsPageView;
}

function wasReloaded(): boolean {
  const navigation = performance.getEntriesByType('navigation')[0];
  return navigation instanceof PerformanceNavigationTiming && navigation.type === 'reload';
}

function start(root: HTMLElement): void {
  const auth = createAuth();
  const reloaded = wasReloaded();
  let memory: SessionMemory | undefined;
  let screen: Screen | undefined;
  let returnTo: string | undefined;
  let signInNote: string | undefined;
  let renderedPath: string | undefined;

  const shell: Shell = renderShell(root, PATHS, () => {
    if (memory?.bookings.hasUnresolved() === true) {
      shell.confirmSwitch(() => auth.switchUser());
    } else {
      auth.switchUser();
    }
  });

  const memoryFor = (session: Session): SessionMemory => {
    if (memory === undefined || memory.session !== session) {
      memory = { session, quotes: createQuoteMemory(), bookings: createBookingMemory(session.api) };
    }
    return memory;
  };

  const navigate = (path: string): void => {
    history.pushState(null, '', path);
    render(false);
  };

  const quotesContext = (session: Session): QuotesContext => {
    const { quotes, bookings } = memoryFor(session);
    return { api: session.api, quotes, bookings, paths: PATHS, navigate, announce: shell.announce, reloaded };
  };

  const mount = (route: Route, session: Session | undefined): Screen => {
    if (route.name === 'sign-in' || session === undefined) {
      const message = signInNote;
      signInNote = undefined;
      return mountSignIn(shell.main, {
        auth, message,
        onSignedIn: () => {
          const target = returnPath(returnTo);
          returnTo = undefined;
          navigate(target);
        },
      });
    }
    switch (route.name) {
      case 'new-quote':
      case 'root':
        return mountNewQuotePage(shell.main, quotesContext(session));
      case 'quote':
        return mountQuotePage(shell.main, route.quoteId, quotesContext(session));
      case 'shipments':
      case 'shipment': {
        const context = { api: session.api, paths: PATHS, navigate, announce: shell.announce };
        const page = mountShipmentsPage(shell.main, context, route.name === 'shipment' ? route.shipmentId : undefined);
        return { heading: page.heading, dispose: page.dispose, shipments: page };
      }
      case 'not-found': {
        const heading = el('h1', { tabindex: '-1' }, 'Page not found');
        replace(shell.main, el('section', { class: 'page' }, heading,
          emptyState('There is no page at this address.', { label: 'New quote', href: PATHS.newQuote() })));
        return { heading, dispose: () => undefined };
      }
    }
  };

  function render(initial: boolean): void {
    let route = parseRoute(location.pathname);
    const session = auth.current();
    if (route.name === 'root' || (route.name === 'sign-in' && session !== undefined)) {
      route = { name: 'new-quote' };
      history.replaceState(null, '', PATHS.newQuote());
    }
    if (isProtected(route) && session === undefined) {
      returnTo = location.pathname;
      route = { name: 'sign-in' };
      history.replaceState(null, '', PATHS.signIn());
    }
    renderedPath = location.pathname;
    document.title = documentTitle(screenName(route));
    shell.setSection(navSection(route));
    shell.setUser(session === undefined ? undefined
      : { name: session.membership.userDisplayName, tenant: session.membership.tenantDisplayName });
    const page = screen?.shipments;
    if (page !== undefined && (route.name === 'shipments' || route.name === 'shipment')) {
      page.show(route.name === 'shipment' ? route.shipmentId : undefined);
      return;
    }
    screen?.dispose();
    screen = mount(route, session);
    if (!initial && route.name !== 'shipment') {
      focusProgrammatically(screen.heading);
    }
  }

  auth.onChange((_session, change) => {
    if (change === 'signed-in') {
      return;
    }
    screen?.dispose();
    screen = undefined;
    memory = undefined;
    if (change === 'expired') {
      signInNote = sessionEndMessage(change);
      returnTo = location.pathname;
    } else {
      returnTo = undefined;
    }
    navigate(PATHS.signIn());
  });

  document.addEventListener('click', (event) => {
    const modified = event.metaKey || event.ctrlKey || event.shiftKey || event.altKey;
    if (event.defaultPrevented || event.button !== 0 || modified) {
      return;
    }
    const link = event.target instanceof Element ? event.target.closest('a') : null;
    if (link === null || link.target !== '' || link.hasAttribute('download') || link.origin !== location.origin
      || (link.getAttribute('href') ?? '').startsWith('#')) {
      return;
    }
    event.preventDefault();
    navigate(link.pathname);
  });
  // A fragment link such as the skip link changes no route: only a new path renders.
  window.addEventListener('popstate', () => {
    if (location.pathname !== renderedPath) {
      render(false);
    }
  });
  window.addEventListener('beforeunload', (event) => {
    if (memory?.bookings.hasUnresolved() === true) {
      event.preventDefault();
    }
  });
  render(true);
}

const root = document.getElementById('app');
if (root !== null) {
  start(root);
}
