import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const root=new URL('../../',import.meta.url);
const read=name=>fs.readFileSync(new URL(name,root),'utf8');
const corpus=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumPublicCurriculum.kt');
const core=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningCore.kt');
const ui=read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');

function section(start,end) {
  const a=corpus.indexOf(start),b=corpus.indexOf(end,a+start.length);
  assert.ok(a>=0 && b>a,`Missing corpus boundaries ${start}`);
  return corpus.slice(a,b);
}
function lines(start,end) {
  return [...section(start,end).matchAll(/"([^"]+)"/g)].map(m=>m[1]);
}
function pairs(start,end) {
  return [...section(start,end).matchAll(/"([^"]+)"\s+to\s+"([^"]+)"/g)].map(m=>[m[1],m[2]]);
}

test('synthetic intent corpus balances 72 training and 24 unseen examples',()=>{
  const greetings=lines('private val greetings','private val questions');
  const questions=lines('private val questions','private val holdoutGreetings');
  const auditGreetings=lines('private val holdoutGreetings','private val holdoutQuestions');
  const auditQuestions=lines('private val holdoutQuestions','val intentTrain');
  assert.deepEqual([greetings.length,questions.length,auditGreetings.length,auditQuestions.length],
    [36,36,12,12]);
  const train=[...greetings,...questions],audit=[...auditGreetings,...auditQuestions];
  assert.equal(new Set(train).size,train.length);
  assert.equal(new Set(audit).size,audit.length);
  for(const ex of audit)assert.ok(!train.includes(ex));
});
test('semantic corpus has balanced nonoverlapping 32 training plus 12 audit pairs',()=>{
  const positive=pairs('private val positivePairs','private val negativePairs');
  const negative=pairs('private val negativePairs','private val holdoutPositive');
  const auditPositive=pairs('private val holdoutPositive','private val holdoutNegative');
  const auditNegative=pairs('private val holdoutNegative','val semanticTrain');
  assert.deepEqual([positive.length,negative.length,auditPositive.length,auditNegative.length],[16,16,6,6]);
  const train=[...positive,...negative].map(x=>JSON.stringify(x));
  const audit=[...auditPositive,...auditNegative].map(x=>JSON.stringify(x));
  assert.equal(new Set(train).size,train.length);
  assert.equal(new Set(audit).size,audit.length);
  for(const ex of audit)assert.ok(!train.includes(ex));
});
test('owner, old holdouts, new audits, snapshots and cancellation gate promotions',()=>{
  const begin=core.indexOf('fun trainPublicCurriculum(');
  const end=core.indexOf('fun teachIntent(',begin);
  assert.ok(begin>0&&end>begin);
  const block=core.slice(begin,end);
  for(const required of [
    'ownBefore,candidate.evaluateIntent(own)',
    'retains(fixedIntentBefore,fixedIntentAfter)',
    'retains(fixedSemanticBefore,fixedSemanticAfter)',
    'retains(intentAuditBefore,intentAuditAfter)',
    'retains(semanticAuditBefore,semanticAuditAfter)',
    'if(accepted) saveModel(candidate.save(),marker)',
    'if(completed) appendAudit(marker,accepted',
    'semanticTrain!=null && !semanticTrain.stoppedEarly'
  ])assert.ok(block.includes(required),`Missing integrity guard: ${required}`);
  assert.doesNotMatch(block,/OWNER_CONFIRMED|recordOwnerFact|recordCommandMapping|requestPermissions|Intent\(Intent\.ACTION_VIEW/);
});
test('Android entrypoints are unique and export uses owner-controlled system picker',()=>{
  const ids=[...ui.matchAll(/private const val \w+_REQUEST\s*=\s*(\d+)/g)].map(m=>m[1]);
  assert.equal(new Set(ids).size,ids.length);
  // The action must remain owner-gated after migrating from AlertDialog to a glass-bento tile.
  assert.match(ui,/cockpit\.action\(this, "GEWICHTE \/ LABOR"\) \{ dialog\.dismiss\(\); showLearningLabChoices\(\) \}/);
  assert.match(ui,/SWB1 · ÖFFENTLICHES OFFLINE-LERNPAKET/);
  assert.match(ui,/Intent\(Intent\.ACTION_CREATE_DOCUMENT\)/);
  assert.match(ui,/CONTINUUM_CORE_EXPORT_REQUEST ->/);
  assert.match(ui,/\bSWB1_BYTES\b/);
});
