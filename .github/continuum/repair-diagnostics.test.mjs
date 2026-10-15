import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {parseJUnit,parseJsErrorText,parseJsError} from './repair-diagnostics.mjs';

// Real reporter output captured from Node v20.20.0, v22.16.0 and v22.22.0 for the
// same fault (math.mjs calls `sqare`); only the absolute path was replaced by /repo.
const fixture=name=>fs.readFileSync(new URL('./fixtures/node-reporter/'+name,import.meta.url),'utf8');
const ROOT='/repo';

for(const version of ['v20.20.0','v22.16.0','v22.22.0']){
  test(`JUnit ReferenceError from Node ${version} is a code diagnostic at the faulty line`,()=>{
    const {total,failed,diagnostics}=parseJUnit(fixture(`junit-${version}.xml`),ROOT);
    assert.equal(total,1);assert.equal(failed,1);
    const d=diagnostics[0];
    assert.deepEqual([d.kind,d.symbol,d.file,d.line,d.col],['REFERENCE_ERROR','sqare','.github/continuum/math.mjs',2,25]);
  });
  test(`TAP module-load ReferenceError from Node ${version} is located in the loaded module`,()=>{
    const [d]=parseJsErrorText(fixture(`tap-load-${version}.txt`),ROOT);
    assert.deepEqual([d.kind,d.symbol,d.file,d.line,d.col],['REFERENCE_ERROR','hashx','.github/continuum/load.mjs',1,16]);
  });
  // Node 20 names a crashed test file by absolute path, Node 22 relative to the cwd.
  test(`a test file that crashes while loading under Node ${version} is named by its repository path`,()=>{
    const r=parseJUnit(fixture(`junit-load-${version}.xml`),ROOT);
    assert.deepEqual([r.total,r.failed,r.placeholders,r.executed,r.passed],[2,1,1,1,1]);
    const d=r.diagnostics[0];
    assert.deepEqual([d.kind,d.file,d.testFile],['TEST_FAILURE',null,'.github/continuum/load.test.mjs']);
  });
}

test('a crashed test file outside the repository gets no re-runnable path',()=>{
  for(const name of ['/elsewhere/x.test.mjs','../x.test.mjs','file:///elsewhere/y.test.mjs']){
    const xml=`<testsuites><testcase name="${name}" classname="test"><failure message="test failed">[Error: test failed]</failure></testcase></testsuites>`;
    const r=parseJUnit(xml,ROOT);
    assert.equal(r.placeholders,1,name);assert.equal(r.diagnostics[0].testFile,undefined,name);
  }
});

test('the same fault yields one fingerprint regardless of the Node release',()=>{
  const prints=['v20.20.0','v22.16.0','v22.22.0'].map(v=>parseJUnit(fixture(`junit-${v}.xml`),ROOT).diagnostics[0].fingerprint);
  assert.equal(new Set(prints).size,1);
});

test('header variants and nested causes are recognized',()=>{
  const at='\n    at f (file:///repo/a.mjs:3:7)\n';
  for(const header of ['ReferenceError: foo is not defined','ReferenceError [Error]: foo is not defined',
    'Uncaught ReferenceError: foo is not defined','ReferenceError [ERR_SOME_CODE]: foo is not defined',
    'Error: wrapper\n    at w (node:internal/x:1:1) {\n  [cause]: ReferenceError: foo is not defined']){
    const e=parseJsError(header+at,ROOT);
    assert.ok(e,'not recognized: '+header);
    assert.deepEqual([e.type,e.symbol,e.file,e.line],['ReferenceError','foo','a.mjs',3],header);
  }
  // TAP YAML block of a failed test (runtime error inside a test)
  const tap="not ok 1 - quad\n  ---\n  error: 'sqare is not defined'\n  code: 'ERR_TEST_FAILURE'\n  name: 'ReferenceError'\n  stack: |-\n    quad (file:///repo/m.mjs:2:25)\n    TestContext.<anonymous> (file:///repo/m.test.mjs:4:30)\n  ...\n";
  const [d]=parseJsErrorText(tap,ROOT);
  assert.deepEqual([d.kind,d.symbol,d.file,d.line],['REFERENCE_ERROR','sqare','m.mjs',2]);
});

test('classification is not over-generous',()=>{
  const xml=(message,body)=>`<testsuites><testcase name="t" classname="c"><failure message="${message}">${body}</failure></testcase></testsuites>`;
  // assertion text mentioning the words does not create a code diagnostic
  let d=parseJUnit(xml('config is not defined','AssertionError [ERR_ASSERTION]: config is not defined\n    at t (file:///repo/a.test.mjs:9:3)'),ROOT).diagnostics[0];
  assert.equal(d.kind,'TEST_FAILURE');assert.equal(d.symbol,undefined);assert.equal(d.file,'a.test.mjs');
  d=parseJUnit(xml('expected SyntaxError','AssertionError: expected SyntaxError to be thrown\n    at t (file:///repo/a.test.mjs:9:3)'),ROOT).diagnostics[0];
  assert.equal(d.kind,'TEST_FAILURE');
  // a ReferenceError that is not "x is not defined" has no symbol, so no typo/import repair
  d=parseJUnit(xml('x','ReferenceError: Cannot access \'x\' before initialization\n    at f (file:///repo/b.mjs:1:1)'),ROOT).diagnostics[0];
  assert.deepEqual([d.kind,d.symbol],['JS_RUNTIME_ERROR',undefined]);
  // frames outside the repository or in node internals never locate a diagnostic
  d=parseJUnit(xml('x','ReferenceError: foo is not defined\n    at f (node:internal/x:1:1)\n    at g (file:///elsewhere/c.mjs:2:2)'),ROOT).diagnostics[0];
  assert.deepEqual([d.kind,d.file],['TEST_FAILURE',null]);
});

test('an error without its own repository frame cannot borrow the next error\'s frame',()=>{
  const text='file:///repo/broken.mjs:4\n  let = ;\n      ^\n\nSyntaxError: Unexpected token \'=\'\n    at compile (node:internal/vm:1:1)\n\nReferenceError: foo is not defined\n    at f (file:///repo/a.mjs:3:7)\n';
  const out=parseJsErrorText(text,ROOT);
  const syntax=out.find(d=>d.kind==='JS_SYNTAX_ERROR');
  assert.deepEqual([syntax.file,syntax.line],['broken.mjs',4],'syntax error keeps its own location');
  const ref=out.find(d=>d.kind==='REFERENCE_ERROR');
  assert.deepEqual([ref.file,ref.symbol],['a.mjs','foo']);
  assert.ok(!out.some(d=>d.kind==='JS_SYNTAX_ERROR'&&d.file==='a.mjs'));
});
