// Adapted from existing QA PR413 at 10f4339a760f8a531993598200b1c3299d3b2b97; reviewer-only new three-case A0.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const root=path.dirname(new URL(import.meta.url).pathname);
const bytes=fs.readFileSync(path.join(root,'FROZEN-MANIFEST.json'));
assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'),'de03def3edee6d3a86228b587fb0dcc45e5d80047a9f17000ed31b605ee0988e');
const manifest=JSON.parse(bytes);
const files=[];
function walk(dir,prefix=''){for(const entry of fs.readdirSync(dir,{withFileTypes:true})){const name=prefix+entry.name;if(entry.isDirectory())walk(path.join(dir,entry.name),name+'/');else files.push(name);}}
walk(path.join(root,'frozen'));
assert.deepEqual(files.sort(),Object.keys(manifest.files).sort(),'Frozen file set changed');
for(const [name,expected] of Object.entries(manifest.files)) {
 const actual=crypto.createHash('sha256').update(fs.readFileSync(path.join(root,'frozen',name))).digest('hex');
 assert.equal(actual,expected,name);
}
assert.equal(manifest.taskCount,3);
console.log(`Verified ${Object.keys(manifest.files).length} immutable fixture files; ${manifest.taskCount} tasks; no model execution.`);
