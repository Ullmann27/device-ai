// Code semantic index: behaviour fingerprints from the sandboxed probe, clusters of indistinguishable exports, near
// duplicates, test links, and a cache that probes only modules whose import closure changed.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {buildCodeIndex,importClosure,standardInputs,fingerprintArity,POOL_1,main} from './code-index.mjs';

function fixture(){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'code-index-'));
 const dir=path.join(root,'.github','continuum');
 fs.mkdirSync(dir,{recursive:true});
 fs.mkdirSync(path.join(root,'scripts'));
 const put=(name,src)=>fs.writeFileSync(path.join(dir,name),src);
 put('math.mjs',['/** Doubles. */','export function double(x){return x*2;}','/** Twice, written differently. */',
  'export function twice(x){return 2*x;}','export function plus(x){return x+x;}','export function triple(x){return x*3;}',
  'export function noise(x){return Math.random();}','export async function later(x){return x;}',
  'export function pair(a,b){return [a,b];}','export function three(a,b,c){return a;}',''].join('\n'));
 put('canon.mjs',["export function canonLoose(v){return JSON.stringify(v);}",
  "export function canonStrict(v){if(typeof v==='number'&&!Number.isInteger(v))throw Object.assign(Error('int'),{code:'INVALID'});return JSON.stringify(v);}",''].join('\n'));
 put('remote.mjs',"import https from 'node:https';\nexport function fetchIt(u){return https.get(u);}\n");
 put('uses-helper.mjs',"import {inc} from '../../scripts/helper.mjs';\nexport function plusOne(x){return inc(x);}\n");
 fs.writeFileSync(path.join(root,'scripts','helper.mjs'),'export function inc(x){return x+1;}\n');
 put('math.test.mjs',"import test from 'node:test';\nimport {double} from './math.mjs';\ntest('d',()=>{});\n");
 // two modules that import each other have the same import closure
 put('ping.mjs',"import {pong} from './pong.mjs';\nexport function ping(x){return 'ping'+x;}\nexport function both(x){return pong(x)+1;}\n");
 put('pong.mjs',"import {ping} from './ping.mjs';\nexport function pong(x){return x*10;}\n");
 put('ping.test.mjs',"import test from 'node:test';\nimport {ping} from './ping.mjs';\ntest('p',()=>{});\n");
 return root;
}
const find=(index,key)=>index.exports.find(e=>e.module+'#'+e.export==='.github/continuum/'+key);

test('pools and arities are fixed and deterministic',()=>{
 assert.equal(standardInputs(1).length,POOL_1.length);
 assert.equal(standardInputs(2).length,100);
 assert.deepEqual(standardInputs(0),[[]]);
 assert.deepEqual(standardInputs(3),[]);
 assert.equal(fingerprintArity({required:0,max:1}),1);
 assert.equal(fingerprintArity({required:2,max:3}),2);
});

test('the import closure follows relative imports through the repository and names builtins',()=>{
 const root=fs.realpathSync(fixture());
 const c=importClosure(root,path.join(root,'.github','continuum','uses-helper.mjs'));
 assert.deepEqual(c.files,['.github/continuum/uses-helper.mjs','scripts/helper.mjs']);
 assert.deepEqual(importClosure(root,path.join(root,'.github','continuum','remote.mjs')).builtins,['https']);
});

test('fingerprints separate behaviour, find indistinguishable exports and near duplicates, and link tests',()=>{
 const root=fixture();
 const index=buildCodeIndex({root});
 assert.equal(find(index,'math.mjs#double').fingerprint,find(index,'math.mjs#twice').fingerprint,'x*2 and 2*x agree on the whole pool');
 assert.notEqual(find(index,'math.mjs#double').fingerprint,find(index,'math.mjs#plus').fingerprint,'x+x concatenates texts');
 assert.notEqual(find(index,'math.mjs#double').fingerprint,find(index,'math.mjs#triple').fingerprint);
 assert.ok(index.clusters.some(c=>c.includes('.github/continuum/math.mjs#double')&&c.includes('.github/continuum/math.mjs#twice')));
 assert.equal(find(index,'math.mjs#noise').deterministic,false);
 assert.equal(find(index,'math.mjs#later').errorRate,1,'asynchronous results are not observable');
 assert.equal(find(index,'math.mjs#pair').arity,2);
 assert.equal(find(index,'math.mjs#three').runnable,false);
 assert.equal(find(index,'remote.mjs#fetchIt').runnable,false);
 assert.equal(find(index,'remote.mjs#fetchIt').reason,'imports a network or process module');
 assert.equal(find(index,'uses-helper.mjs#plusOne').runnable,true,'imports elsewhere in the repository are followed');
 const near=index.nearDuplicates.find(d=>[d.a,d.b].sort().join()==='.github/continuum/canon.mjs#canonLoose,.github/continuum/canon.mjs#canonStrict');
 assert.ok(near&&near.agreement>=0.9&&near.agreement<1,'they differ only on non-integer numbers');
 assert.deepEqual(find(index,'math.mjs#double').tests,['.github/continuum/math.test.mjs']);
 assert.deepEqual(find(index,'canon.mjs#canonLoose').tests,[]);
 assert.deepEqual(find(index,'pong.mjs#pong').tests,[],'reached only through ping.mjs: not counted as tested');
 assert.equal(index.stats.modulesProbed,index.stats.modules);
});

test('modules that import each other keep their own results',()=>{
 const root=fixture();
 const cacheFile=path.join(fs.mkdtempSync(path.join(os.tmpdir(),'code-index-cycle-')),'cache.json');
 for(const index of [buildCodeIndex({root,cacheFile}),buildCodeIndex({root,cacheFile})]){
  assert.equal(find(index,'pong.mjs#pong').runnable,true);
  assert.equal(find(index,'ping.mjs#ping').runnable,true);
  assert.notEqual(find(index,'pong.mjs#pong').fingerprint,find(index,'ping.mjs#ping').fingerprint);
  assert.ok(index.exports.every(e=>'runnable' in e),'every export has probe data');
 }
});

test('the cache probes only modules whose import closure changed',()=>{
 const root=fixture();
 const cacheFile=path.join(fs.mkdtempSync(path.join(os.tmpdir(),'code-index-state-')),'cache.json');
 const first=buildCodeIndex({root,cacheFile});
 assert.equal(first.stats.modulesProbed,6);
 assert.equal(buildCodeIndex({root,cacheFile}).stats.modulesProbed,0);
 fs.writeFileSync(path.join(root,'scripts','helper.mjs'),'export function inc(x){return x+2;}\n');
 const third=buildCodeIndex({root,cacheFile});
 assert.equal(third.stats.modulesProbed,1,'only the module that imports the changed helper');
 assert.notEqual(find(third,'uses-helper.mjs#plusOne').fingerprint,find(first,'uses-helper.mjs#plusOne').fingerprint);
});

test('the CLI keeps the index outside the repository',async()=>{
 const root=fixture();
 await assert.rejects(main(['build','--project',root,'--state',path.join(root,'state')],()=>{}),/outside the repository/);
 const state=fs.mkdtempSync(path.join(os.tmpdir(),'code-index-cli-'));
 const lines=[];
 await main(['build','--project',root,'--state',state],l=>lines.push(l));
 assert.equal(JSON.parse(lines[0]).exports,15);
 await main(['show','--state',state],l=>lines.push(l));
 assert.ok(lines.some(l=>l.startsWith('cluster ')));
 assert.ok(lines.some(l=>l.startsWith('near ')));
});
