import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const qa = fs.readFileSync(path.join(root, '.github/workflows/continuum-code52-integration-qa.yml'), 'utf8');

test('all Android build configuration changes re-run Code52 exact source QA', () => {
  const pathsBlock = qa.split('\n    paths:\n')[1]?.split('\npermissions:\n')[0];
  assert.ok(pathsBlock, 'missing push path scope');
  for (const entry of [
    'app/**', 'backend/**', 'build.gradle.kts', 'settings.gradle.kts',
    'gradle/**', 'gradle.properties', 'gradlew',
    '.github/workflows/continuum-code52-integration-qa.yml',
  ]) {
    assert.ok(pathsBlock.includes("      - '" + entry + "'"), 'uncovered build input: ' + entry);
  }
  assert.match(qa, /branches:\n\s+- chatgpt\/code52-unified-integration-20261109/);
  assert.doesNotMatch(qa, /secrets\./);
});

test('unsigned Android QA cannot sign, publish or grant release authorization', () => {
  assert.match(qa, /Code52-UNSIGNED-NOT-INSTALLABLE/);
  assert.match(qa, /continuumStandaloneVersionCode=52/);
  assert.match(qa, /versionCode='52' versionName='0\.3\.51'/);
  assert.match(qa, /original_signature=NOT_USED/);
  assert.doesNotMatch(qa, /\bapksigner sign\b|\bgh release create\b/);
  assert.match(qa, /:app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug/);
  assert.match(qa, /continuum-code52-junit-check\.py/);
});
