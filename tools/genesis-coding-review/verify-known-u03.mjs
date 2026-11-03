import fs from 'node:fs';import path from 'node:path';import assert from 'node:assert/strict';import {createHash} from 'node:crypto';
const digest=b=>createHash('sha256').update(b).digest('hex');
const root=path.join(path.dirname(new URL(import.meta.url).pathname),'known-u03'),bytes=fs.readFileSync(path.join(root,'FROZEN-MANIFEST.json'));
assert.equal(digest(bytes),'f1a2bda8f582d09b74ade64a73b72e59147a915183f4397ae5ec3d5a815b4c1e');const m=JSON.parse(bytes);assert.equal(m.status,'KNOWN_REGRESSION_ONLY');assert.equal(m.maximumModelCalls,1);
for(const [name,hash] of Object.entries(m.files))assert.equal(digest(fs.readFileSync(path.join(root,name))),hash);
console.log('Verified known u03 regression bytes; not an unknown task or model execution.');
