import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { FLAG_KEYS, QUOTE_STATUSES, SERVICE_LEVELS, SHIPMENT_STATUSES, UNAVAILABLE_REASONS, ZONES } from './types.ts';

const CONTRACT = readFileSync(new URL('../../../../contracts/shipping.openapi.yaml', import.meta.url), 'utf8');

/** The lines of one schema under components/schemas, read as text: enough to find its enum values. */
function schemaLines(name: string): string[] {
  const lines = CONTRACT.split('\n');
  const start = lines.indexOf(`    ${name}:`);
  assert.notEqual(start, -1, `the contract has no schema ${name}`);
  const end = lines.findIndex((line, index) => index > start && /^ {0,4}\S/.test(line));
  return lines.slice(start + 1, end);
}

function enumValues(name: string): string[] {
  const block = schemaLines(name);
  const inline = block.map((line) => /^\s+enum: \[(.*)\]$/.exec(line)?.[1]).find((value) => value !== undefined);
  if (inline !== undefined) {
    return inline.split(',').map((value) => value.trim().replace(/^'|'$/g, ''));
  }
  return block.map((line) => /^\s+- (\S+)$/.exec(line)?.[1]).filter((value) => value !== undefined);
}

test('the enums are exactly the contract\'s', () => {
  assert.deepEqual([...SERVICE_LEVELS], enumValues('ServiceLevel'));
  assert.deepEqual([...ZONES], enumValues('Zone'));
  assert.deepEqual([...QUOTE_STATUSES], enumValues('QuoteStatus'));
  assert.deepEqual([...SHIPMENT_STATUSES], enumValues('ShipmentStatus'));
  assert.deepEqual([...UNAVAILABLE_REASONS], enumValues('UnavailableReason'));
  assert.deepEqual([...FLAG_KEYS], enumValues('FlagKey'));
});
