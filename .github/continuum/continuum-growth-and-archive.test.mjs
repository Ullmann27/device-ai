import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const root=new URL('../../',import.meta.url);
const read=p=>fs.readFileSync(new URL(p,root),'utf8');
const dataset=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumExtendedCurriculum.kt');
const core=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningCore.kt');
const policy=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumPublicStudyPolicy.kt');
const study=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumPublicStudyScheduler.kt');
const archive=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumKnowledgeArchive.kt');
const ui=read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const manifest=read('app/src/main/AndroidManifest.xml');

function between(src,start,end) {
  const a=src.indexOf(start),b=src.indexOf(end,a+start.length);
  assert.ok(a>=0 && b>a,`Invalid bounds ${start} / ${end}`);
  return src.slice(a,b);
}
function strings(s){return Array.from(s.matchAll(/"([^"]+)"/g),m=>m[1]);}
function pairs(s){return Array.from(s.matchAll(/"([^"]+)"\s+to\s+"([^"]+)"/g),m=>[m[1],m[2]]);}

test('1,632 diverse training examples are partitioned into twelve nonoverlapping balanced stages',()=>{
  const openings=strings(between(dataset,'private val greetingStarts','private val greetingEnds'));
  const closing=strings(between(dataset,'private val greetingEnds','private val questionStarts'));
  const questions=strings(between(dataset,'private val questionStarts','private val questionTopics'));
  const subjects=strings(between(dataset,'private val questionTopics','private val auditGreetings'));
  const semantics=pairs(between(dataset,'private val subjectDescriptions','private val semanticForms'));
  const forms=strings(between(dataset,'private val semanticForms','val intentTrain'));
  assert.deepEqual([openings.length,closing.length,questions.length,subjects.length,semantics.length,forms.length],
    [24,24,24,24,24,10]);
  const intentGood=openings.flatMap(a=>closing.map(b=>a+' '+b));
  const intentQuestion=questions.flatMap(a=>subjects.map(b=>a+' '+b+'?'));
  const semGood=semantics.flatMap(([word,meaning])=>forms.map(form=>form+' '+word+'|'+meaning));
  const semBad=semantics.flatMap(([word],i)=>forms.map(form=>form+' '+word+'|'+semantics[(i+11)%semantics.length][1]));
  for(const items of [intentGood,intentQuestion,semGood,semBad])assert.equal(new Set(items).size,items.length);
  const auditGreetings=strings(between(dataset,'private val auditGreetings','private val auditGreetingSuffix'));
  const auditSuffix=strings(between(dataset,'private val auditGreetingSuffix','private val auditQuestions'));
  const auditQuestions=strings(between(dataset,'private val auditQuestions','private val auditTopics'));
  const auditTopics=strings(between(dataset,'private val auditTopics','private val subjectDescriptions'));
  assert.equal(auditGreetings.length*auditSuffix.length+auditQuestions.length*auditTopics.length,64);
  assert.match(dataset,/const val STAGES = 12/);
  assert.match(dataset,/subList\(group,group\+48\)/);
  assert.match(dataset,/subList\(stage\*20,\(stage\+1\)\*20\)/);
  assert.match(dataset,/require\(ib.size==96/);
  assert.match(dataset,/require\(sb.size==40/);
});

test('stage acceptance protects new, old, owner and previously introduced independent audits',()=>{
  const code=between(core,'fun trainExtendedCurriculum(', 'fun teachIntent(');
  for(const token of [
    'retains(auditI0,auditI1)','retains(auditS0,auditS1)',
    'retains(fixedI0,fixedI1)','retains(fixedS0,fixedS1)',
    'retains(extraI0,extraI1)','retains(extraS0,extraS1)',
    'ownerSafe','if(accepted)saveModel(candidate.save(),marker)',
    'if(completed)appendAudit(marker,accepted','trainS!=null && !trainS.stoppedEarly'
  ])assert.ok(code.includes(token),'Missing candidate gate: '+token);
  assert.doesNotMatch(code,/recordOwnerFact\(|requestPermissions\(|exec\(|ProcessBuilder\(/);
});

test('owner-controlled recurrent public research has finite allowed sources and limits',()=>{
  const topics=strings(between(policy,'val topics = listOf(','fun topicForCycle'));
  assert.equal(topics.length,48);
  assert.equal(new Set(topics).size,topics.length);
  assert.match(policy,/https:\/\/de\.wikipedia\.org\/w\/api\.php/);
  assert.match(study,/instanceFollowRedirects=false/);
  assert.match(study,/MAX_RESPONSE_BYTES/);
  assert.match(study,/MAX_ROUND_WALL_MS/);
  assert.match(study,/setRequiresCharging\(true\)/);
  assert.match(study,/setRequiresBatteryNotLow\(true\)/);
  assert.match(study,/setRequiresStorageNotLow\(true\)/);
  assert.match(study,/setRequiredNetworkType\(JobInfo\.NETWORK_TYPE_ANY\)/);
  assert.match(study,/if\(!scheduler\.enabled\)return false/);
  assert.match(study,/fun disable\(\):Boolean/);
});

test('citation archive and Android UI preserve verification, provenance and explicit consent',()=>{
  assert.match(archive,/SHA-256/);
  assert.match(archive,/MAX_RECORDS=4096/);
  assert.match(archive,/Kind\.RIS_LEGISLATION/);
  assert.match(archive,/Kind\.RIS_CASE_LAW/);
  assert.match(archive,/contentHash/);
  assert.match(ui,/showPublicStudyControls\(\)/);
  assert.match(ui,/showKnowledgeArchiveSearch\(\)/);
  assert.match(ui,/showRisArchiveImport\(\)/);
  assert.match(ui,/showExtendedCurriculumDialog\(\)/);
  assert.match(ui,/FORSCHUNG FREIGEBEN/);
  assert.match(ui,/FORSCHUNG AUS/);
  assert.match(manifest,/ContinuumPublicStudyJobService/);
  const requestCodes=Array.from(ui.matchAll(/private const val \w+_REQUEST\s*=\s*(\d+)/g),m=>m[1]);
  assert.equal(new Set(requestCodes).size,requestCodes.length);
});
