import test from 'node:test';
import assert from 'node:assert/strict';
import { rotateRight } from './subject.mjs';

test('positive shift moves the last element to the front', () => {
  assert.deepEqual(rotateRight(['a', 'b', 'c', 'd'], 1), ['d', 'a', 'b', 'c']);
});
test('large shifts wrap around the array length', () => {
  assert.deepEqual(rotateRight([10, 20, 30, 40, 50], 7), [40, 50, 10, 20, 30]);
});
test('a negative right shift rotates left', () => {
  assert.deepEqual(rotateRight([1, 2, 3, 4], -1), [2, 3, 4, 1]);
});
test('zero and full rotations return a new array', () => {
  const input = [0, false, '', null];
  for (const shift of [0, 4, -4]) {
    const result = rotateRight(input, shift);
    assert.deepEqual(result, input);
    assert.notEqual(result, input);
  }
});
test('empty and one-element arrays are supported', () => {
  assert.deepEqual(rotateRight([], 17), []);
  assert.deepEqual(rotateRight(['only'], -19), ['only']);
});
test('input and element references are preserved', () => {
  const a = { id: 'a' }; const b = { id: 'b' }; const c = { id: 'c' };
  const input = Object.freeze([a, b, c]);
  const result = rotateRight(input, 1);
  assert.deepEqual(input, [a, b, c]);
  assert.equal(result[0], c);
  assert.equal(result[1], a);
  assert.equal(result[2], b);
});
