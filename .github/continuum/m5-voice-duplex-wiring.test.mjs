import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
const root=resolve(fileURLToPath(new URL('../../',import.meta.url)));
const activity=readFileSync(resolve(root,'app/src/main/java/at/heinz/deviceai/AgentActivity.kt'),'utf8');
// Match real function bodies, not the next declaration or braces in strings/comments.
function codeOnly(source) {
 const out=source.split('');
 for(let i=0;i<source.length;) {
  const start=i;
  if(source.startsWith('//',i)) { i=source.indexOf('\n',i);if(i<0)i=source.length; }
  else if(source.startsWith('/*',i)) {
   i+=2;let depth=1;
   while(i<source.length&&depth) { if(source.startsWith('/*',i)){depth++;i+=2;}else if(source.startsWith('*/',i)){depth--;i+=2;}else i++; }
  } else if(source.startsWith('"""',i)) { const end=source.indexOf('"""',i+3);i=end<0?source.length:end+3; }
  else if('"\'`'.includes(source[i])) {
   const quote=source[i++];while(i<source.length) { if(source[i]==='\\'){i+=2;continue;}if(source[i++]===quote)break; }
  } else { i++;continue; }
  for(let j=start;j<i;j++)if(source[j]!=='\n')out[j]=' ';
 }
 return out.join('');
}
function bracedBody(source,open) {
 const code=codeOnly(source);assert.equal(code[open],'{','missing body opening');let depth=0;
 for(let i=open;i<code.length;i++){if(code[i]==='{')depth++;else if(code[i]==='}'&&--depth===0)return source.slice(open,i+1);}
 assert.fail('unbalanced function body');
}
function bodyOf(source,signature) {
 const name=signature.match(/\b(?:fun|function)\s+(\w+)\s*\(/)?.[1]??signature;
 assert.match(name,/^[A-Za-z_]\w*$/);
 const code=codeOnly(source),match=new RegExp('\\b(?:fun|function)\\s+'+name+'\\s*\\(').exec(code);
 if(!match)return null;
 let i=code.indexOf('(',match.index),depth=0;
 for(;i<code.length;i++){if(code[i]==='(')depth++;else if(code[i]===')'&&--depth===0){i++;break;}}
 const open=code.indexOf('{',i),equals=code.indexOf('=',i);
 if(equals>=0&&(open<0||equals<open)) {
  // The expression-bodied motion predicate ends before the next blank line/declaration.
  const end=code.indexOf('\n\n',equals);return source.slice(equals,end<0?source.length:end);
 }
 return open<0?null:bracedBody(source,open);
}
const body=signature=>{const result=bodyOf(activity,signature);assert.ok(result,`missing ${signature}`);return result;};

// M5 milestone 2 (Argon 4, 7.1): STT start stops TTS and avatar mouth synchronously.
test('voice input barge-in silences TTS engine and avatar mouth before the recognizer starts',()=>{
  const start=body('private fun startVoiceInput(');
  const stopEngine=start.indexOf('voiceEngine?.stop()');
  const stopMouth=start.indexOf('stopVoiceAnimation()');
  const recognizer=start.indexOf('speech.start(');
  assert.ok(stopEngine>=0,'startVoiceInput must stop the TTS engine');
  assert.ok(stopMouth>=0,'startVoiceInput must stop the avatar mouth/voice signal synchronously, not only via the async TTS onStop callback');
  assert.ok(stopMouth<recognizer,'mouth must be silenced before the microphone opens');
});

// M5 milestone 2: half-duplex echo guard – the assistant never talks into an open microphone.
test('assistant TTS is refused while the on-device microphone turn is active',()=>{
  assert.match(body('private fun speakBootstrap('), /speakReply\(message, allowMaya = true\)/);
  const speak=body('private fun speakReply(');
  assert.match(speak,/if \([^\n]*\|\| voiceListening\) return/);
  assert.ok(speak.indexOf('|| voiceListening) return') < speak.indexOf('val useMaya'),
    'microphone exclusion applies before choosing either speech provider');
  assert.match(speak,/!useMaya && !at\.heinz\.deviceai\.cognitive\.LocalSpeechInputPolicy\.maySpeak/);
});

// M5 milestone 3: speech -> composer handoff keeps the owner's typed draft.
test('dictation is merged at the cursor and never overwrites a typed composer draft',()=>{
  const apply=body('private fun applyLocalSpeechText(');
  assert.match(apply,/LocalSpeechInputPolicy\.mergeDictation\(\s*input\.text\.toString\(\)\s*,[^;]*input\.selectionStart[^;]*input\.selectionEnd[^;]*spoken\s*\)/);
  assert.match(apply,/input\.setText\(merged\.text\)/);
  assert.doesNotMatch(apply,/input\.setText\(spoken\)/);
  // Still preview-only: recognition never sends or executes.
  assert.doesNotMatch(apply,/sendCurrentInput\(|dispatch\(|startDevelopmentJob\(/);
});

// M5 milestone 4: the only native model-load path passes the resource/thermal admission.
test('model load path is gated by measured RAM/thermal admission before the native engine starts',()=>{
  const load=body('private fun loadModel(');
  const gate=load.indexOf('LocalModelLoadAdmission.loadIfAdmitted(');
  assert.ok(gate>=0,'loadModel must route through LocalModelLoadAdmission');
  assert.match(load,/localModelResourceSnapshot\(\)/);
  assert.doesNotMatch(load.replace(/LocalModelLoadAdmission\.loadIfAdmitted\(localRuntime/g,''),/localRuntime\.load\(/,
    'no direct localRuntime.load bypass');
});

// M5 milestone 6: live local AgentModel re-checks model identity before and after every generation.
test('live runLocal model binds every step to the model captured at turn start',()=>{
  const run=body('private fun runLocal(');
  const marker=/model\s*=\s*codingPlanning\?\.model\(localRuntime,cancellation\)\s*\?:\s*AgentModel\s*\{/.exec(codeOnly(run));
  assert.ok(marker,'normal chat remains the existing AgentModel fallback');
  const lambda=codeOnly(bracedBody(run,marker.index+marker[0].lastIndexOf('{')));
  const checks=[...lambda.matchAll(/LocalTurnModelBinding\.stillBound\(imported, localRuntime\.loadedModel\)/g)].map(m=>m.index);
  assert.equal(checks.length,2,'identity check before generation and after generation');
  const output=lambda.lastIndexOf('AgentModelOutput(');
  assert.ok(checks[1]<output&&checks[1]>lambda.indexOf('generation.failure?.let'),'post-generation check precedes attribution');
});
