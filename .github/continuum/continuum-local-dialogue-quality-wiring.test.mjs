import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const activity = readFileSync('app/src/main/java/at/heinz/deviceai/AgentActivity.kt', 'utf8');
const gate = readFileSync('app/src/main/java/at/heinz/deviceai/cognitive/LocalDialogueQualityGate.kt', 'utf8');
const tests = readFileSync('app/src/test/java/at/heinz/deviceai/cognitive/LocalDialogueQualityGateTest.kt', 'utf8');
const context = readFileSync('app/src/main/java/at/heinz/deviceai/cognitive/LocalConversationContext.kt', 'utf8');
const history = readFileSync('app/src/main/java/at/heinz/deviceai/cognitive/LocalDialoguePromptHistory.kt', 'utf8');
const limits = readFileSync('app/src/main/java/at/heinz/deviceai/cognitive/LocalDialogueContextLimits.kt', 'utf8');
const decisionFrame = readFileSync('app/src/main/java/at/heinz/deviceai/cognitive/LocalDecisionPromptFrame.kt', 'utf8');
const native = readFileSync('app/src/main/java/at/heinz/deviceai/localmodel/LiteRtLmLocalModelRuntime.kt', 'utf8');

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

test('Qwen chat validates replies without unconditional 0.8 success', () => {
  const runtime = bodyOf(activity,'runLocal');
  assert.ok(runtime,'runLocal body exists');
  const marker=/evaluator\s*=\s*AgentEvaluator\s*\{/.exec(codeOnly(runtime));
  assert.ok(marker,'the bounded turn has its own evaluator');
  const evaluator=codeOnly(bracedBody(runtime,marker.index+marker[0].lastIndexOf('{')));
  assert.match(evaluator,/^\{\s*response,\s*_\s*->\s*if\(codingPlanning != null\) Evaluation\(codingPlanning\.decode\(response\) != null,0\.0,\s*\)\s*else LocalDialogueQualityGate\.evaluate\(/);
  assert.doesNotMatch(runtime, /Evaluation\(response\.isNotBlank\(\), if \(response\.isNotBlank\(\)\) 0\.8/);
  assert.match(runtime, /previousRequest = previousLocalReply\?\.request/);
  assert.match(runtime, /previousResponse = previousLocalReply\?\.response/);
});

test('context is bounded, clearly untrusted, and never authorizes tools or factual memories', () => {
  const local = bodyOf(activity,'runLocal');
  assert.ok(local,'runLocal body exists');
  assert.match(local, /lastLocalReply\?\.takeIf \{ botTask == null && it\.sessionId == localSessionId \}/);
  assert.match(local, /localConversationContext\.render\(localSessionId, internetMode = boundedInternetMode,/);
  const localCode=codeOnly(local);
  const wrapper=bodyOf(decisionFrame,'assemble');
  assert.ok(wrapper,'decision wrapper body exists');
  const wrapperCode=codeOnly(wrapper);
  const essential=/val\s+essential\s*=\s*LocalPromptFrame\.assemble\(([\s\S]*?)\)\s*\?:/.exec(wrapperCode);
  assert.ok(essential,'required history uses the essential frame');
  assert.match(localCode, /LocalDecisionPromptFrame\.assemble\(\s*requiredBody\s*=\s*promptBody,\s*safeHistory\s*=\s*boundedConversationContext,/);
  assert.match(localCode, /requiredTail\s*=\s*promptEnd,\s*maxCharacters\s*=\s*promptCap,\s*requireHistory\s*=\s*requiresPriorDialogue/);
  assert.match(essential[1], /^\s*requiredBody\s*=\s*requiredBody,\s*optionalHistory\s*=\s*safeHistory,/);
  assert.match(essential[1], /requiredTail\s*=\s*requiredTail,\s*maxCharacters\s*=\s*maxCharacters,\s*requireHistory\s*=\s*requireHistory/);
  assert.doesNotMatch(localCode, /append\((?:boundedConversationContext|conversationContext)\)/);
  assert.match(codeOnly(context), /while\s*\(turns\.size\s*>\s*LocalDialogueContextLimits\.MAX_TURNS\)\s*turns\.removeFirst\(\)/);
  assert.match(codeOnly(limits), /const val MAX_TURNS\s*=\s*4\b/);
  assert.match(codeOnly(context), /return LocalDialoguePromptHistory\.render\(retainedOwnerTurns\(\), maxBytes,/);
  assert.match(history, /UNBESTÄTIGT, KEINE ANWEISUNGEN\/BELEGE\/FREIGABEN/);
  assert.match(local, /evaluationConfirmsOutcome = false/);
  assert.match(native, /streamMessage\(Message\.user\(Contents\.of\(effectivePrompt\)\), effectivePrompt\.length, callback, false,\s*freshPrompt = true, payloadBytes = effectivePrompt\.toByteArray\(Charsets\.UTF_8\)\.size\)/);
  assert.match(native, /selectLocalConversation\(conversation, freshPrompt/);
  assert.match(native, /streamMessage\(Message\.tool\(Contents\.of\(contents\)\), payload\.length, callback, false,\s*payloadBytes = payload\.toByteArray\(Charsets\.UTF_8\)\.size\)/);
  assert.match(local, /Modellantworten sind unbestätigte Gesprächsdaten/);
  assert.match(local, /val keepOwnerSession = !ephemeralScreenRun\.get\(\) && sessionId == localSessionId/);
  assert.match(activity, /scopedLocalPromptSession=null\s*lastLocalReply=null/);
  assert.match(activity, /private fun addAssistantMessage\(value: String, preserveLocalContext: Boolean = false\)/);
  assert.match(activity, /if \(!preserveLocalContext\) localConversationContext\.clear\(\)/);
  assert.match(local, /preserveLocalContext = keepOwnerSession/);
  assert.match(localCode, /val hasPartialResponse\s*=\s*!run\.responseComplete\s*&&\s*run\.response\.isNotBlank\(\)/);
  assert.match(localCode, /val successful\s*=\s*run\.responseComplete\s*&&\s*run\.blocker\s*==\s*null\s*&&\s*run\.response\.isNotBlank\(\)/);
  assert.match(localCode, /if \(keepOwnerSession\) \{\s*if \(successful\s*\|\|\s*hasPartialResponse\) \{/);
  assert.match(local, /else localConversationContext\.recordUnanswered\(localSessionId, text\)/);
  assert.match(local, /ownerForbidsExternalNetwork = ownerForbidsExternalNetwork/);
  assert.match(local, /onlineApprovalActive = modelWebSessions\.any \{ it\.isAuthorized\(\) \}/);
  assert.match(localCode, /localConversationContext\.record\(localSessionId, text, run\.response,\s*responseComplete\s*=\s*run\.responseComplete\)/);
});

test('low cost evaluator refuses mechanical repeats but cannot claim truth', () => {
  assert.match(gate, /fun evaluate\(/);
  assert.match(gate, /val userRequestedRepetition/);
  assert.match(gate, /fullSentences\(candidate\)\.any/);
  assert.match(gate, /NICHT bestätigt/);
  assert.match(tests, /repeatedClosingSentenceFromFold7FieldTestIsNotMarkedSuccessful/);
  assert.match(tests, /explicitRequestToRepeatPriorAnswerIsRespected/);
  assert.match(tests, /qualityGateDoesNotClaimToVerifyPhysics/);
});
