import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { scan } from '../../tools/status-literal-scan/scan.mjs';

const fx = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../tools/status-literal-scan/fixtures');

test('status literal scan finds hard-coded status values, placeholders and garbled video text', () => {
  const hits = scan(path.join(fx, 'bad'), { allowlist: [] });
  const kinds = (k) => hits.filter((h) => h.kind === k).map((h) => h.text);
  for (const t of ['CPU 18%', 'Temperatur 34 °C', 'RAM 42 / 256 GB', '12,5 tok/s', 'Antwort 120 ms', '4 Running', '4 Agenten aktiv'])
    assert.ok(kinds('status-number').some((s) => s.includes(t)), `missing status hit: ${t}`);
  assert.deepEqual(kinds('placeholder').sort(), ['Kachel 3', 'Kachel 8']);
  assert.equal(kinds('garbled-video-text').length, 2);
  for (const h of hits) assert.match(`${h.file}:${h.line}`, /:\d+$/);
});

test('allowed: unknown markers, interpolated live values, format strings, dimensions, comments', () => {
  assert.deepEqual(scan(path.join(fx, 'good'), { allowlist: [] }), []);
});

test('allowlist entries need a reason and suppress exactly their file+text', () => {
  const all = scan(path.join(fx, 'bad'), { allowlist: [] });
  const one = { file: 'app/src/main/java/x/ui/BadCard.kt', text: 'Kachel 3', reason: 'fixture' };
  assert.equal(scan(path.join(fx, 'bad'), { allowlist: [one] }).length, all.length - 1);
  assert.throws(() => scan(path.join(fx, 'bad'), { allowlist: [{ file: one.file, text: one.text }] }), /reason/);
});
