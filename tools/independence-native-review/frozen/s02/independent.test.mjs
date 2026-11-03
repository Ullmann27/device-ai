import test from 'node:test';
import assert from 'node:assert/strict';
import { escapeHtmlText } from './subject.mjs';

test('independent adjacent repeated special characters', () => {
  assert.equal(escapeHtmlText('&&<<>>""\'\''), '&amp;&amp;&lt;&lt;&gt;&gt;&quot;&quot;&#39;&#39;');
});
test('unicode, line breaks and slashes are preserved', () => {
  assert.equal(escapeHtmlText('🧠\nA/B\\C\t<Ω> &'), '🧠\nA/B\\C\t&lt;Ω&gt; &amp;');
});
test('raw numeric and named entity-like strings are escaped independently', () => {
  assert.equal(escapeHtmlText('&#39; &amp; &unknown;'), '&amp;#39; &amp;amp; &amp;unknown;');
});
test('escaping respects concatenation across many boundaries', () => {
  const pieces = ['', 'x', '&', '<', '>', '"', "'", 'Ω', '\n', '&lt;', '<&>'];
  for (const left of pieces) for (const right of pieces) {
    assert.equal(escapeHtmlText(left + right), escapeHtmlText(left) + escapeHtmlText(right));
  }
  assert.equal(escapeHtmlText('<&>'.repeat(257)), '&lt;&amp;&gt;'.repeat(257));
});
