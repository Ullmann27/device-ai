import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const root=path.dirname(new URL(import.meta.url).pathname);
const manifest=JSON.parse(fs.readFileSync(path.join(root,'FROZEN-MANIFEST.json'),'utf8'));
for(const [name,expected] of Object.entries(manifest.files)) {
 const actual=crypto.createHash('sha256').update(fs.readFileSync(path.join(root,'frozen',name))).digest('hex');
 assert.equal(actual,expected,name);
}
assert.equal(manifest.taskCount,12);
console.log(`Verified ${Object.keys(manifest.files).length} immutable fixture files; ${manifest.taskCount} tasks; no model execution.`);
