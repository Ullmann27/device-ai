import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const read=path=>readFileSync(new URL('../../'+path,import.meta.url),'utf8');
const probe=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumOrthographyProbe.kt');
const dialogue=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryDialogue.kt');
const activity=read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const research=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumPublicStudyScheduler.kt');

test('retrieval-only orthography never becomes a command or a memory write',()=>{
  assert.match(probe,/internal object ContinuumOrthographyProbe/);
  assert.match(probe,/fun suggest\(query:String,evidenceTexts:List<String>\)/);
  assert.match(probe,/corrections!=1/);
  assert.match(probe,/second-best<0\.40/);
  assert.doesNotMatch(probe,/\.commit\(|openConnection|FileOutputStream|Runtime\.getRuntime|ProcessBuilder/);
  assert.match(dialogue,/experiences\.filter \{ eligible\(it\) \}/);
  assert.match(dialogue,/if\(recoveredHits\.isNotEmpty\(\)/);
  assert.match(dialogue,/val turn=ContinuumDialogueTurn\(at,text\.take\(320\),resolved/);
  assert.match(dialogue,/!Regex\("\(\?i\)\^\(merke\|lösche\|entferne/);
});

test('local direct smoke is distinct from tool-free thinking smoke',()=>{
  assert.match(activity,/Continuum Modelltest Direkt/);
  assert.match(activity,/private fun runLocalSmokeTest\(direct:Boolean=false\)/);
  assert.match(activity,/if\(direct\)localRuntime\.stream\(question,callback\)/);
  assert.match(activity,/else localRuntime\.streamThinking\(question,callback\)/);
  // Prompt-preservation behavior is executed by LocalQwenPromptPolicyTest;
  // this source contract protects only the two actual smoke-test entry paths.
});

test('research counter reflects durable archive not just mentor insertion',()=>{
  assert.match(research,/val archiveCountBefore=archive\.stats\(\)\.values\.sum\(\)/);
  assert.match(research,/added=archive\.stats\(\)\.values\.sum\(\)>archiveCountBefore/);
  assert.match(research,/notebook\.add\(pending\)/);
  assert.match(research,/if\(!scheduler\.enabled \|\| scheduler\.isRateLimited\(\)\)return false/);
});
