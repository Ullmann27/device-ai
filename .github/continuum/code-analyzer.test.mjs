import test from 'node:test';
import assert from 'node:assert/strict';
import {analyzeSources,buildIndex,integrationFindings,callChain,manifestRoots,analyzeRepository} from './code-analyzer.mjs';
import {tokenize} from './kotlin-tokenizer.mjs';

test('finds candidate orphan but not referenced class',()=>{
 const result=analyzeSources([
  {path:'a.kt',content:'class Connected {}\nclass Orphan {}'},
  {path:'b.kt',content:'fun use() = Connected()'}
 ]);
 assert.ok(result.findings.some(f=>f.symbol==='Orphan'));
 assert.ok(!result.findings.some(f=>f.symbol==='Connected'));
});
test('ignores non-Kotlin sources',()=>{
 assert.equal(analyzeSources([{path:'x.txt',content:'class Missing {}'}]).declarations,0);
});
test('enforces source limit',()=>{
 assert.throws(()=>analyzeSources(Array.from({length:3001},(_,i)=>({path:i+'.kt',content:''}))));
});

test('indexes a large app entry point instead of silently losing its references',()=>{
 const index=buildIndex([{path:'main/p/App.kt',content:'/*'+ 'x'.repeat(410000)+'*/\npackage p\nclass App { override fun onCreate(){ Core().teach() } }\nclass Core { fun teach()=1 }'}]);
 assert.ok(callChain(index,'p.App','p.Core.teach'));
});

test('oversized UTF-8 sources fail explicitly instead of producing incomplete findings',()=>{
 assert.throws(()=>buildIndex([{path:'main/p/Huge.kt',content:'//'+ 'ä'.repeat(510000)}]),/Source byte limit exceeded/);
});

test('lexer ignores comments and string text but sees template references',()=>{
 const ids=tokenize(`/* Hidden /* nested */ still */ // Gone\nval a = "Text Ghost \${Real.value} $alsoReal" + """raw Ghost2 \${Raw2}"""\nval c = 'x'`)
  .filter(t=>t.type==='id').map(t=>t.text);
 assert.deepEqual(ids,['a','Real','value','alsoReal','Raw2','c']);
 assert.ok(!ids.includes('Hidden')&&!ids.includes('Ghost')&&!ids.includes('Gone'));
});

test('imports resolve the right declaration when simple names collide',()=>{
 const index=buildIndex([
  {path:'main/a/Store.kt',content:'package a\nclass Store'},
  {path:'main/b/Store.kt',content:'package b\nclass Store'},
  {path:'main/c/User.kt',content:'package c\nimport b.Store\nclass User { fun run() = Store() }'}
 ]);
 assert.ok(index.edges.get('c.User.run').has('b.Store'));
 assert.ok(!index.edges.get('c.User.run').has('a.Store'));
});

test('constructor properties and header lambdas stay inside their class',()=>{
 const index=buildIndex([{path:'main/x/E.kt',content:'package x\nclass E(val clock:()->Long={ 1L }, val name:String) {\n private fun run(){ helper() }\n}\nfun helper()=1'}]);
 assert.equal(index.byQualified.get('x.E.name').container,'x.E');
 assert.equal(index.byQualified.get('x.E.run').container,'x.E');
 assert.ok(index.edges.get('x.E.run').has('x.helper'));
});

test('reachability separates production, test-only and unreferenced code',()=>{
 const index=buildIndex([
  {path:'main/java/p/App.kt',content:'package p\nclass App { override fun onCreate() { Live().go() } }\nclass Live { fun go() = 1 }'},
  {path:'main/java/p/Lab.kt',content:'package p\nobject Lab { fun experiment() = Island() }\nclass Island\nclass Nobody'},
  {path:'test/java/p/LabTest.kt',content:'package p\nclass LabTest { @Test fun t() { Lab.experiment() } }'}
 ]);
 const {findings}=integrationFindings(index,{productionRoots:['p.App']});
 const kind=s=>findings.find(f=>f.symbol===s)?.kind;
 assert.equal(kind('p.Live'),undefined);
 assert.equal(kind('p.Lab'),'TEST_ONLY');
 assert.equal(kind('p.Island'),'TEST_ONLY');
 assert.equal(kind('p.Nobody'),'UNREFERENCED');
});

test('call chains follow real references from an entry point',()=>{
 const index=buildIndex([
  {path:'main/p/A.kt',content:'package p\nclass A { fun start() { B().step() } }'},
  {path:'main/p/B.kt',content:'package p\nclass B { fun step() { C.finish() } }\nobject C { fun finish() = 0 }'}
 ]);
 const chain=callChain(index,'p.A.start','C.finish').map(x=>x.symbol);
 assert.deepEqual(chain,['p.A.start','p.B.step','p.C.finish']);
 assert.equal(callChain(index,'p.C.finish','p.A.start'),null);
});

test('manifest entry points resolve relative component names',()=>{
 const roots=manifestRoots('<activity android:name=".Main" android:exported="true"/><service android:exported="false" android:name="x.y.Job"/>','a.b');
 assert.deepEqual(roots.map(r=>r.name),['a.b.Main','x.y.Job']);
});

test('repository analysis reaches the learning core from the chat activity',()=>{
 const result=analyzeRepository(process.cwd());
 assert.ok(result.entryPoints.some(r=>r.name==='at.heinz.deviceai.AgentActivity'));
 const chain=callChain(result.index,'at.heinz.deviceai.AgentActivity','ContinuumLearningCore.teachIntent');
 assert.ok(chain&&chain.length>=2,'chat must reach the learning core');
 assert.ok(result.findings.every(f=>['UNREFERENCED','TEST_ONLY','DEAD_CLUSTER'].includes(f.kind)&&f.confidence));
});
