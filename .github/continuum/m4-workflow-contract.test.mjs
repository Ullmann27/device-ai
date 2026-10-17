import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const workflow=path.resolve(path.dirname(fileURLToPath(import.meta.url)),
 '../../.github/workflows/continuum-branch-tests.yml');

test('M4 workflow runs the isolated real repair acceptance before broad regressions',()=>{
 const source=fs.readFileSync(workflow,'utf8');
 assert.match(source,/name: M4 end-to-end repair acceptance/);
 assert.match(source,/repair-loop\.test\.mjs/);
 assert.match(source,/repair-cycle-safety\.integration\.test\.mjs/);
 assert.match(source,/continuum-m4-repair-junit\.xml/);
 assert.match(source,/name: Full Continuum Node regressions/);
 assert.match(source,/node --test --test-reporter=junit/);
 assert.match(source,/name: Backend Node regressions/);
 assert.match(source,/backend\/test\/\*\.test\.js/);
});

test('M4 workflow keeps backend changes covered and uploads executable evidence',()=>{
 const source=fs.readFileSync(workflow,'utf8');
 assert.match(source,/- "backend\/\*\*"/);
 assert.match(source,/name: Publish M4 test evidence/);
 assert.match(source,/if: always\(\)/);
 assert.match(source,/actions\/upload-artifact@v4/);
 assert.match(source,/continuum-node-junit\.xml/);
 assert.match(source,/backend-node-junit\.xml/);
 assert.match(source,/continuum-m4-repair-junit\.xml/);
 assert.match(source,/name: Validate executed M4 regression evidence/);
 assert.match(source,/m4-junit-evidence\.mjs/);
 assert.match(source,/if-no-files-found: error/);
});
