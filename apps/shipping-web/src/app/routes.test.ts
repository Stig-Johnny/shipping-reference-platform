import assert from 'node:assert/strict';
import { describe, test } from 'node:test';
import { PATHS, documentTitle, isProtected, navSection, parseRoute, pathOf, returnPath, screenName } from './routes.ts';

const QUOTE = '5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21';
const SHIPMENT = '9d1c2b3a-4e5f-4a6b-8c7d-0e1f2a3b4c5d';

describe('parseRoute', () => {
  test('knows the five routes of the workbench and the root', () => {
    assert.deepEqual(parseRoute('/'), { name: 'root' });
    assert.deepEqual(parseRoute('/sign-in'), { name: 'sign-in' });
    assert.deepEqual(parseRoute('/quotes/new'), { name: 'new-quote' });
    assert.deepEqual(parseRoute(`/quotes/${QUOTE}`), { name: 'quote', quoteId: QUOTE });
    assert.deepEqual(parseRoute('/shipments'), { name: 'shipments' });
    assert.deepEqual(parseRoute(`/shipments/${SHIPMENT}`), { name: 'shipment', shipmentId: SHIPMENT });
  });

  test('accepts one trailing slash', () => {
    assert.deepEqual(parseRoute('/shipments/'), { name: 'shipments' });
    assert.deepEqual(parseRoute('/quotes/new/'), { name: 'new-quote' });
  });

  test('IDs must be lower-case UUIDs, as in the contract; anything else is not found', () => {
    const pathValues1 = [`/quotes/${QUOTE.toUpperCase()}`, '/quotes/123', '/shipments/x', `/quotes/${QUOTE}/extra`, '/quote', '/elsewhere', '//shipments'];
    for (const path of pathValues1) {
      assert.deepEqual(parseRoute(path), { name: 'not-found' }, path);
    }
  });

  test('pathOf is the inverse for every route', () => {
    const pathValues2 = ['/sign-in', '/quotes/new', `/quotes/${QUOTE}`, '/shipments', `/shipments/${SHIPMENT}`];
    for (const path of pathValues2) {
      assert.equal(pathOf(parseRoute(path)), path);
    }
    assert.equal(PATHS.quote(QUOTE), `/quotes/${QUOTE}`);
    assert.equal(PATHS.shipment(SHIPMENT), `/shipments/${SHIPMENT}`);
  });
});

describe('what a route needs and shows', () => {
  test('everything but sign-in and unknown pages needs a membership', () => {
    assert.equal(isProtected({ name: 'sign-in' }), false);
    assert.equal(isProtected({ name: 'not-found' }), false);
    const pathValues3 = ['/', '/quotes/new', `/quotes/${QUOTE}`, '/shipments', `/shipments/${SHIPMENT}`];
    for (const path of pathValues3) {
      assert.equal(isProtected(parseRoute(path)), true, path);
    }
  });

  test('every route sets the document title "<screen> - Shipping workbench"', () => {
    assert.equal(documentTitle(screenName({ name: 'sign-in' })), 'Sign in - Shipping workbench');
    assert.equal(documentTitle(screenName({ name: 'new-quote' })), 'New quote - Shipping workbench');
    assert.equal(documentTitle(screenName({ name: 'quote', quoteId: QUOTE })), 'Quote - Shipping workbench');
    assert.equal(documentTitle(screenName({ name: 'shipments' })), 'Shipments - Shipping workbench');
    assert.equal(documentTitle(screenName({ name: 'shipment', shipmentId: SHIPMENT })), 'Shipment - Shipping workbench');
    assert.equal(documentTitle(screenName({ name: 'not-found' })), 'Page not found - Shipping workbench');
  });

  test('the navigation marks New quote or Shipments as the current section', () => {
    assert.equal(navSection({ name: 'new-quote' }), 'new-quote');
    assert.equal(navSection({ name: 'quote', quoteId: QUOTE }), undefined);
    assert.equal(navSection({ name: 'shipments' }), 'shipments');
    assert.equal(navSection({ name: 'shipment', shipmentId: SHIPMENT }), 'shipments');
    assert.equal(navSection({ name: 'sign-in' }), undefined);
  });
});

describe('returnPath', () => {
  test('keeps the internal route that sign-in interrupted', () => {
    assert.equal(returnPath(`/shipments/${SHIPMENT}`), `/shipments/${SHIPMENT}`);
    assert.equal(returnPath(`/quotes/${QUOTE}`), `/quotes/${QUOTE}`);
  });

  test('sends everything else to New quote: sign-in itself, unknown and foreign paths', () => {
    const candidateValues4 = [undefined, '/', '/sign-in', '/nope', '//evil.example/x', 'https://evil.example/', 'javascript:alert(1)'];
    for (const candidate of candidateValues4) {
      assert.equal(returnPath(candidate), '/quotes/new', String(candidate));
    }
  });
});
