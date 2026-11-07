import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const path = 'app/src/main/java/at/heinz/deviceai/ui/ContinuumBrainOrbRenderer.kt';
const source = fs.readFileSync(path, 'utf8');
const between = (start, end) => {
  const first = source.indexOf(start);
  assert.notEqual(first, -1, 'Missing renderer path: ' + start);
  const last = source.indexOf(end, first + start.length);
  assert.ok(last > first, 'Missing renderer boundary: ' + end);
  return source.slice(first, last);
};

test('Code49: orb planes and wire net reuse point buffers instead of allocating each vertex', () => {
  const orbits = between('private fun drawOrbits(', 'private fun drawNet(');
  const net = between('private fun drawNet(', 'private val brainPath');
  assert.match(orbits, /fun point\(angle: Float, out: FloatArray\)/);
  assert.match(orbits, /val first = FloatArray\(3\)/);
  assert.match(orbits, /val second = FloatArray\(3\)/);
  assert.match(orbits, /val swap = a\s+a = b\s+b = swap/);
  assert.match(orbits, /point\(s\.phase \* \(\.91f \+ ring \* \.11f\) \+ ring \* 2\.1f, bead\)/);
  assert.match(net, /fun proj\(lat: Float, lon: Float, out: FloatArray\)/);
  assert.match(net, /val first = FloatArray\(3\)/);
  assert.match(net, /val second = FloatArray\(3\)/);
  assert.match(net, /val swap = prev\s+prev = q\s+q = swap/);
  assert.doesNotMatch(orbits, /return floatArrayOf\s*\(/);
  assert.doesNotMatch(net, /return floatArrayOf\s*\(/);
});
