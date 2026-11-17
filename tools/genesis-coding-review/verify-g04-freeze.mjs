import fs from 'node:fs';import path from 'node:path';import assert from 'node:assert/strict';import {createHash} from 'node:crypto';
const digest=b=>createHash('sha256').update(b).digest('hex');
const root=path.join(path.dirname(new URL(import.meta.url).pathname),'future'),bytes=fs.readFileSync(path.join(root,'FROZEN-MANIFEST.json'));
assert.equal(digest(bytes),'4df194f5f8d5ad7957bc050291aadd37327dc6fbccd6547fcb67a4985a5a9ec0');
const m=JSON.parse(bytes);assert.equal(m.maximumModelCalls,2);assert.equal(m.case,'g04');
assert.deepEqual(fs.readdirSync(path.join(root,'g04')).sort(),Object.keys(m.files).sort());
for(const [name,hash] of Object.entries(m.files))assert.equal(digest(fs.readFileSync(path.join(root,'g04',name))),hash);
console.log('Verified immutable g04 fixture; model execution is not part of this verification.');
