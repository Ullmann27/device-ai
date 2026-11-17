import test from 'node:test';
import assert from 'node:assert/strict';
import { isBlockedAddress, assertPublicHttpUrl } from './public-web.mjs';
import { critique } from './repair-critic.mjs';
import { tokenize } from './kotlin-tokenizer.mjs';
import { buildIndex, callChain } from './code-analyzer.mjs';

test('SSRF: rejects mapped, local, reserved and transition IPv6 targets', async () => {
  for (const ip of ['::ffff:127.0.0.1','::ffff:169.254.169.254','::ffff:7f00:1','64:ff9b::1','2002:7f00:1::1']) {
    assert.equal(isBlockedAddress(ip), true, ip);
  }
  for (const url of ['http://127.0.0.1/', 'http://[::ffff:127.0.0.1]/','https://localhost/']) {
    await assert.rejects(assertPublicHttpUrl(url));
  }
  assert.equal(isBlockedAddress('2606:4700:4700::1111'), false);
});

test('critic counts only two distant edits', () => {
  const filler=Array.from({length:60},(_,i)=>'const line'+i+' = '+i+';').join('\n');
  const oldText='const a=1;\n'+filler+'\nconst b=2;';
  const newText='const a=10;\n'+filler+'\nconst b=20;';
  const result=critique({strategy:'JS_TYPO_REFERENCE',edits:[{file:'src/app.mjs',oldText,newText}]});
  assert.equal(result.approved,true,JSON.stringify(result));
  assert.equal(result.changedLines,2);
});

test('critic catches new danger even with unchanged existing URL', () => {
  const filler=Array.from({length:60},(_,i)=>'const line'+i+' = '+i+';').join('\n');
  const oldText='const a=1;\nconst safe = "https://example.org";\n'+filler+'\nconst b=2;';
  const newText='const a=10;\nconst safe = "https://example.org";\n'+filler+'\nconst next = "https://new.example";';
  const result=critique({strategy:'JS_TYPO_REFERENCE',edits:[{file:'src/app.mjs',oldText,newText}]});
  assert.equal(result.approved,false);
  assert.ok(result.reasons.includes('introduces a network address'));
});

test('Kotlin lexer does not consume following code after a raw string', () => {
  const ids=tokenize('val x = """hello"""\nfun nextCall() = target()').filter(t=>t.type==='id').map(t=>t.text);
  assert.deepEqual(ids,['x','nextCall','target']);
});

test('explicit Kotlin return types retain method call-graph scope', () => {
  const index=buildIndex([{path:'main/p/Test.kt',content:
    'package p\nclass Caller {\n fun run(): Int {\n return Helper.compute()\n }\n}\nobject Helper { fun compute(): Int = 42 }'
  }]);
  const chain=callChain(index,'p.Caller.run','p.Helper.compute');
  assert.ok(chain, 'Expected reachable method with explicit return type');
});
