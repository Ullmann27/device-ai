import test from 'node:test';
import assert from 'node:assert/strict';
import {readableAuditPath,rankFilesByRisk,scanDefensiveSinks} from './risk-file-ranker.mjs';

test('network and authentication code outrank unrelated docs for a security task',()=>{
 const ranked=rankFilesByRisk([
  'docs/readme.md','app/src/main/java/NetworkGateway.kt',
  'lib/crypto/signature.go','src/ui/button.js','src/auth/router.mjs'
 ],{goal:'network authentication security'});
 assert.equal(ranked.length,4,'documentation is excluded from the code-only scanner');
 assert.ok(ranked[0].score>=ranked.at(-1).score);
 assert.ok(ranked.some(x=>x.path==='lib/crypto/signature.go'));
 assert.ok(ranked.some(x=>x.reasons.includes('goal-match')));
});
test('deterministic tie order and strict pagination caps',()=>{
 const files=['z/main.js','a/main.js','z/main.js'];
 const result=rankFilesByRisk(files,{maxResults:2});
 assert.deepEqual(result.map(x=>x.path),['a/main.js','z/main.js']);
 for(const maxResults of [-1,0,101,NaN])
  assert.throws(()=>rankFilesByRisk(files,{maxResults}),/INVALID_RISK_RANK_REQUEST/);
});
test('reject secrets traversal, dot paths and unreadable extensions',()=>{
 for(const file of ['../secrets/env.txt','config/.env','secrets/key.mjs',
  'src/../root.mjs','private/data.py','.git/config','credentials/keys.json',
  '/tmp/real.mjs','foo\\bar.js','local.properties','key.p12','README.md'])
  assert.equal(readableAuditPath(file),false,file);
 assert.equal(readableAuditPath('src/network/handler.kt'),true);
});
test('detect likely risky constructs without claiming a verified vulnerability',()=>{
 const input=[
  'strcpy(dst, input);',
  'query("SELECT * FROM users WHERE name=" + user);',
  'subprocess.run(args, shell=True)',
  'const token="never-return-this-value";'
 ].join('\n');
 const report=scanDefensiveSinks('src/network/handler.py',input);
 assert.equal(report.status,'REVIEW_CANDIDATES_ONLY');
 assert.ok(report.findings.some(x=>x.rule==='C_UNBOUNDED_COPY'&&x.line===1));
 assert.ok(report.findings.some(x=>x.rule==='SQL_STRING_COMPOSITION'&&x.line===2));
 assert.ok(report.findings.some(x=>x.rule==='UNSAFE_SHELL_MODE'&&x.line===3));
 assert.equal(JSON.stringify(report).includes('never-return-this-value'),false);
});
test('bounded scan stops, reports truncation, omits source excerpts',()=>{
 const input='strcpy(dst,src);\n'.repeat(250);
 const result=scanDefensiveSinks('src/net/client.c',input,{maxFindings:4});
 assert.equal(result.status,'TRUNCATED');
 assert.equal(result.findings.length,4);
 assert.deepEqual(result.findings.map(x=>x.line),[1,2,3,4]);
});
test('no scan result for excessive content and no arbitrary byte budget',()=>{
 const large='x'.repeat(70000);
 assert.deepEqual(scanDefensiveSinks('src/main.js',large).findings,[]);
 assert.equal(scanDefensiveSinks('src/main.js',large).status,'TOO_LARGE');
 assert.throws(()=>scanDefensiveSinks('src/main.js',large,{maxBytes:10}),/INVALID_SINK_SCAN_REQUEST/);
});
test('a non-call dangerous keyword is not reported',()=>{
 const report=scanDefensiveSinks('src/main.mjs','// strcpy seems scary\nconst note="eval";');
 assert.deepEqual(report.findings,[]);
});
