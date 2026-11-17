// Source-level contract (not a device test): H1 – the temporal memory specialist runs in the
// chat turn after ContinuumBrainBridge.decide, with the brain result as its baseline.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const a=fs.readFileSync('app/src/main/java/at/heinz/deviceai/AgentActivity.kt','utf8');
test('chat turn chains EWM2 brain and temporal memory specialist',()=>{
  const chain=a.indexOf('ChatSpecialistChain.run(baseline');
  assert.ok(chain>0,'chain call missing');
  assert.ok(a.indexOf('ContinuumBrainBridge.decide(',chain)>chain,'brain stage must be inside the chain');
  assert.ok(a.indexOf('ContinuumTemporalMemorySpecialist.decide(',chain)>chain,'temporal stage must be inside the chain');
});
