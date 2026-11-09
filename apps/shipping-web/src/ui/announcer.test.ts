import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createAnnouncer } from './components.ts';

// Only the live region's text sink is doubled; Node's mocked clock runs the real announcer callbacks.
function region(t) {
  const element = { textContent: '', setAttribute() {}, removeAttribute() {} };
  const original = Object.getOwnPropertyDescriptor(globalThis, 'document');
  Object.defineProperty(globalThis, 'document', { configurable: true, value: { createElement: () => element } });
  t.after(() => {
    if (original) Object.defineProperty(globalThis, 'document', original);
    else Reflect.deleteProperty(globalThis, 'document');
  });
  t.mock.timers.enable();
  return createAnnouncer();
}

test('ending a session clears the previously announced tenant data', (t) => {
  const announcer = region(t);
  announcer.announce('Shipment booked: SIM0000000001');
  t.mock.timers.tick(100);
  assert.equal(announcer.element.textContent, 'Shipment booked: SIM0000000001');
  announcer.reset();
  assert.equal(announcer.element.textContent, '');
});

test('ending a session cancels its delayed announcement before the next user arrives', (t) => {
  const announcer = region(t);
  announcer.announce('Shipment booked: SIM0000000001');
  announcer.reset();
  t.mock.timers.tick(100);
  assert.equal(announcer.element.textContent, '');
  announcer.announce('Offers ready');
  t.mock.timers.tick(100);
  assert.equal(announcer.element.textContent, 'Offers ready');
});
