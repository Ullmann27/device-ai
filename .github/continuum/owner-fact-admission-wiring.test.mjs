// Source-level contract (not a device test): H2 – a held (hypothetical/uncertain/planned/
// questioned/reported) "Merke dir" statement never reaches the app fact list as confirmed.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const activity=fs.readFileSync('app/src/main/java/at/heinz/deviceai/AgentActivity.kt','utf8');
const start=activity.indexOf('private fun rememberOwnerFact(');
const body=activity.slice(start,activity.indexOf('\n    private fun ',start+10));
test('rememberOwnerFact gates held statements before bootstrapMemory.addFact',()=>{
  assert.ok(start>0);
  const gate=body.indexOf('OwnerFactAdmission.classify(fact)');
  assert.ok(gate>0,'admission gate missing');
  assert.ok(gate<body.indexOf('bootstrapMemory.addFact(fact)'),'gate must run before the app fact list write');
  assert.ok(body.includes('OwnerFactChange.HELD_UNCONFIRMED'),'learning-core HELD result must be handled');
});
