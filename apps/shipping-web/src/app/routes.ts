// The routes of docs/ui/workbench.md, parsed from the location's path. IDs are lower-case UUIDs as in the contract,
// so a path that is not one of the routes cannot reach the API.

const UUID = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';
const QUOTE_PATH = new RegExp(`^/quotes/(${UUID})$`);
const SHIPMENT_PATH = new RegExp(`^/shipments/(${UUID})$`);

export type Route =
  | { readonly name: 'root' }
  | { readonly name: 'sign-in' }
  | { readonly name: 'new-quote' }
  | { readonly name: 'quote'; readonly quoteId: string }
  | { readonly name: 'shipments' }
  | { readonly name: 'shipment'; readonly shipmentId: string }
  | { readonly name: 'not-found' };

export const PATHS = {
  signIn: (): string => '/sign-in',
  newQuote: (): string => '/quotes/new',
  quote: (quoteId: string): string => `/quotes/${quoteId}`,
  shipments: (): string => '/shipments',
  shipment: (shipmentId: string): string => `/shipments/${shipmentId}`,
} as const;

export function parseRoute(pathname: string): Route {
  const path = pathname.length > 1 && pathname.endsWith('/') ? pathname.slice(0, -1) : pathname;
  switch (path) {
    case '/':
      return { name: 'root' };
    case '/sign-in':
      return { name: 'sign-in' };
    case '/quotes/new':
      return { name: 'new-quote' };
    case '/shipments':
      return { name: 'shipments' };
    default: {
      const quoteId = QUOTE_PATH.exec(path)?.[1];
      if (quoteId !== undefined) {
        return { name: 'quote', quoteId };
      }
      const shipmentId = SHIPMENT_PATH.exec(path)?.[1];
      return shipmentId === undefined ? { name: 'not-found' } : { name: 'shipment', shipmentId };
    }
  }
}

export function pathOf(route: Route): string {
  switch (route.name) {
    case 'root':
      return '/';
    case 'sign-in':
      return PATHS.signIn();
    case 'new-quote':
      return PATHS.newQuote();
    case 'quote':
      return PATHS.quote(route.quoteId);
    case 'shipments':
      return PATHS.shipments();
    case 'shipment':
      return PATHS.shipment(route.shipmentId);
    case 'not-found':
      return '/';
  }
}

/** Protected routes need a membership; without one they go to /sign-in, which keeps the route for afterwards. */
export function isProtected(route: Route): boolean {
  return route.name !== 'sign-in' && route.name !== 'not-found';
}

export function screenName(route: Route): string {
  switch (route.name) {
    case 'sign-in':
      return 'Sign in';
    case 'root':
    case 'new-quote':
      return 'New quote';
    case 'quote':
      return 'Quote';
    case 'shipments':
      return 'Shipments';
    case 'shipment':
      return 'Shipment';
    case 'not-found':
      return 'Page not found';
  }
}

export function documentTitle(screen: string): string {
  return `${screen} - Shipping workbench`;
}

/** The navigation item to mark with aria-current="page". */
export function navSection(route: Route): 'new-quote' | 'shipments' | undefined {
  switch (route.name) {
    case 'new-quote':
      return 'new-quote';
    case 'shipments':
    case 'shipment':
      return 'shipments';
    default:
      return undefined;
  }
}

/** Where to go after signing in: the interrupted route when it is an internal, protected one, else New quote. */
export function returnPath(candidate: string | undefined): string {
  if (candidate === undefined || !candidate.startsWith('/') || candidate.startsWith('//')) {
    return PATHS.newQuote();
  }
  const route = parseRoute(candidate);
  return isProtected(route) && route.name !== 'root' ? pathOf(route) : PATHS.newQuote();
}
