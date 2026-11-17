import test from 'node:test';
import assert from 'node:assert/strict';
import { rotateRight } from './subject.mjs';

test('independent signed edge cases', () => {
  const input = ['oak', 'elm', 'ash', 'fir', 'yew', 'birch', 'pine'];
  assert.deepEqual(rotateRight(input, -9), ['ash', 'fir', 'yew', 'birch', 'pine', 'oak', 'elm']);
  assert.deepEqual(rotateRight(input, 10), ['yew', 'birch', 'pine', 'oak', 'elm', 'ash', 'fir']);
});
test('each original element reaches its specified circular destination', () => {
  for (let length = 2; length <= 13; length++) {
    const input = Object.freeze(Array.from({ length }, (_, id) => Object.freeze({ id })));
    for (const amount of [-9001, -29, -13, -2, -1, 0, 1, 2, 13, 29, 9001]) {
      const output = rotateRight(input, amount);
      assert.equal(output.length, length);
      assert.notEqual(output, input);
      for (let index = 0; index < length; index++) {
        const destination = ((index + amount) % length + length) % length;
        assert.equal(output[destination], input[index], `n=${length} shift=${amount} index=${index}`);
      }
    }
  }
});
test('safe-integer shifts retain periodicity without truncating to 32 bits', () => {
  const input = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
  for (const amount of [Number.MAX_SAFE_INTEGER, Number.MIN_SAFE_INTEGER, 2 ** 40 + 3, -(2 ** 40 + 3)]) {
    assert.deepEqual(rotateRight(input, amount), rotateRight(input, amount % input.length));
  }
});
test('inverse and repeated rotations compose without mutating inputs', () => {
  const input = Object.freeze(['red', 'green', 'blue', 'white', 'black']);
  for (const a of [-11, -2, 0, 1, 18]) {
    assert.deepEqual(rotateRight(rotateRight(input, a), -a), input);
    for (const b of [-9, 0, 4]) {
      assert.deepEqual(rotateRight(rotateRight(input, a), b), rotateRight(input, a + b));
    }
  }
});
