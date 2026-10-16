import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {proposeKotlinDelegate} from './kotlin-delegate-synthesizer.mjs';

const source=[
 'package p',
 'class MathApi {',
 '    fun square(n: Int): Int = n * n',
 '    fun label(): String = "squareTwice(2) remains literal"',
 '}',
 ''
].join('\n');
const target='app/src/main/java/p/MathApi.kt';
function task(subject={}){
 return {id:'owner-synthesis-one',kind:'IMPLEMENT',source:'owner',state:'READY',
  subject:{operation:'EXPOSE_DELEGATE',file:target,className:'MathApi',
   methodName:'squareTwice',delegateName:'square',
   parameterName:'value',type:'Int',depth:2,...subject}};
}
const propose=(t=task(),src=source)=>proposeKotlinDelegate({
 files:[{path:target,content:src}],task:t
});

test('owner specification yields a critic-approved production-only Kotlin patch',()=>{
 const r=propose();
 assert.equal(r.reason,null);
 assert.equal(r.candidate.strategy,'AST_EXPOSE_DELEGATE');
 assert.equal(r.candidate.critic.approved,true);
 assert.equal(r.candidate.edits.length,1);
 assert.equal(r.candidate.edits[0].file,target);
 assert.match(r.candidate.edits[0].newText,
  /fun squareTwice\(value: Int\): Int = square\(square\(value\)\)/);
 assert.match(r.candidate.edits[0].newText,
  /"squareTwice\(2\) remains literal"/);
 assert.equal((r.candidate.edits[0].newText.match(/fun squareTwice\(/g)??[]).length,1);
});

test('unaccepted, foreign, forged and unsupported tasks do not generate patches',()=>{
 const q=task();
 for(const t of [{...q,state:'PROPOSED'},{...q,source:'web-text'},
   task({depth:40}),task({type:'Any'}),task({methodName:'class'}),
   task({delegateName:'constructor'}),task({methodName:'square'})]){
  const r=propose(t);
  assert.equal(r.candidate,null,'unsafe spec must not create code');
  assert.ok(r.reason);
 }
});

test('existing methods, overloaded delegates, unsupported headers and test paths fail closed',()=>{
 const overloaded=source.replace('    fun label()',
  '    fun square(x: Long): Long = x * x\n    fun label()');
 assert.equal(propose(task(),overloaded).candidate,null);
 const existing=source.replace('    fun label()',
  '    fun squareTwice(value: Int): Int = square(value)\n    fun label()');
 assert.equal(propose(task(),existing).candidate,null);
 const inherited=source.replace('class MathApi {','class MathApi: BaseApi() {');
 assert.equal(propose(task(),inherited).candidate,null);
 assert.equal(propose(task({file:'app/src/test/java/p/MathApi.kt'})).candidate,null);
 assert.equal(propose(task({file:'.github/continuum/development-backlog.mjs'})).candidate,null);
 assert.equal(propose(task({file:'../app/src/main/java/p/MathApi.kt'})).candidate,null);
});

test('new method compiles and computes a nontrivial result with real kotlinc',
 {skip:spawnSync('kotlinc',['-version'],{encoding:'utf8',timeout:15000}).status!==0,
  timeout:180000},()=>{
 const generated=propose();
 assert.ok(generated.candidate);
 const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-m4-kotlin-'));
 try{
  const code=path.join(tmp,'MathApi.kt'),main=path.join(tmp,'Main.kt'),jar=path.join(tmp,'check.jar');
  fs.writeFileSync(code,generated.candidate.edits[0].newText);
  fs.writeFileSync(main,[
   'import p.MathApi',
   'fun main() {',
   '    check(MathApi().squareTwice(2) == 16)',
   '    check(MathApi().squareTwice(3) == 81)',
   '    println("CONTINUUM_DELEGATE_OK")',
   '}',''
  ].join('\n'));
  const compile=spawnSync('kotlinc',[code,main,'-include-runtime','-d',jar],
   {encoding:'utf8',timeout:120000});
  assert.equal(compile.status,0,(compile.stderr??'').slice(-3000));
  const run=spawnSync('java',['-jar',jar],{encoding:'utf8',timeout:30000});
  assert.equal(run.status,0,run.stderr);
  assert.match(run.stdout,/CONTINUUM_DELEGATE_OK/);
 }finally{fs.rmSync(tmp,{recursive:true,force:true});}
});
