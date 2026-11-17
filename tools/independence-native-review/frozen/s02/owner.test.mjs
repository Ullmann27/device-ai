import test from 'node:test';
import assert from 'node:assert/strict';
import { escapeHtmlText } from './subject.mjs';

test('ordinary text is unchanged', () => {
  assert.equal(escapeHtmlText('Grüße aus Wien 42'), 'Grüße aus Wien 42');
  assert.equal(escapeHtmlText(''), '');
});
test('every less-than and greater-than character is escaped', () => {
  assert.equal(escapeHtmlText('<a><b>'), '&lt;a&gt;&lt;b&gt;');
});
test('all original ampersands are escaped', () => {
  assert.equal(escapeHtmlText('bread & butter & jam'), 'bread &amp; butter &amp; jam');
});
test('both kinds of quotes use the specified entities', () => {
  assert.equal(escapeHtmlText('"a" and \'b\''), '&quot;a&quot; and &#39;b&#39;');
});
test('inserted entity ampersands are not escaped again', () => {
  assert.equal(escapeHtmlText('<&>'), '&lt;&amp;&gt;');
});
test('preexisting entity-like input is treated as raw text', () => {
  assert.equal(escapeHtmlText('&lt;'), '&amp;lt;');
});
