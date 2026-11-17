import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const production = path.join(root, 'app/src/main');
const executable = /\.(?:kt|java|xml|gradle|kts|js|mjs)$/i;
const foreignApp = /lumo/i;

function walk(dir) {
  const found = [];
  for (const item of fs.readdirSync(dir, {withFileTypes: true})) {
    const abs = path.join(dir, item.name);
    if (item.isSymbolicLink()) continue;
    if (item.isDirectory()) found.push(...walk(abs));
    else if (item.isFile() && executable.test(item.name)) found.push(abs);
  }
  return found;
}

test('Continuum production files contain no unrelated application integration', () => {
  const files = walk(production);
  assert.ok(files.length > 100, 'Android production source tree must be present');
  for (const abs of files) {
    const rel = path.relative(root, abs);
    assert.doesNotMatch(rel, foreignApp, 'unrelated application path: ' + rel);
    assert.doesNotMatch(fs.readFileSync(abs, 'utf8'), foreignApp,
      'unrelated application reference in Continuum production: ' + rel);
  }
});
