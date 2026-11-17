import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

test('actual Android input routes unsafe evidence to a terminal reply before model/web fallback',()=>{
  const activity=fs.readFileSync(new URL('../app/src/main/java/at/heinz/deviceai/AgentActivity.kt',import.meta.url),'utf8');
  const start=activity.indexOf('// Optional zero-token reflex;');
  const end=activity.indexOf('// With the owner\'s persistent ALWAYS_ON grant',start);
  assert.ok(start>0&&end>start);
  const block=activity.slice(start,end);
  assert.match(block,/CertifiedLocationReflex\.routeExplicit\(request\)/);
  assert.match(block,/CertifiedLocationReflex\.routeFromSession\(request,/);
  assert.match(block,/localConversationContext\.sessionEvidence\(sessionId\)/);
  assert.match(block,/Routing\.Withheld/);
  assert.match(block,/preserveLocalContext\s*=\s*true/);
  assert.match(block,/\breturn\b/);
  assert.doesNotMatch(block,/runLocal\(|runContinuumDialogue\(|confirmWebRequest\(/);
});
