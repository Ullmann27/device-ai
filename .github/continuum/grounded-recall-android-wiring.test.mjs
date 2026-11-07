import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

const main=readFileSync(fileURLToPath(new URL('../../app/src/main/java/at/heinz/deviceai/AgentActivity.kt',import.meta.url)),'utf8');
const recall=readFileSync(fileURLToPath(new URL('../../app/src/main/java/at/heinz/deviceai/cognitive/LocalGroundedRecall.kt',import.meta.url)),'utf8');
const call=main.slice(main.indexOf('if(LocalFirstPolicy.afterMemory('),main.indexOf('return@onSuccess',main.indexOf('if(LocalFirstPolicy.afterMemory(')));

test('confirmed recall remains exact when supplementary model context is not representable',()=>{
 assert.ok(call.length>250 && call.length<5000);
 assert.match(call,/LocalGroundedRecall\s*\n\s*\.boundedOrNull\(answer,evidence\)/);
 assert.match(call,/if\(recall==null\)\s*\{[\s\S]*addNonModelReply\(request,\s*answer,\s*at\.heinz\.deviceai\.cognitive\.NonModelReplyKind\.MEMORY_RECALL\)/);
 assert.doesNotMatch(call,/(?:addAssistantMessage\(answer|addNonModelReply\(request,\s*answer\.take\()/);
 assert.match(call,/runLocal\(request,modelQuestion,recall,turnTrace\)/);
 assert.doesNotMatch(call,/LocalGroundedRecall\(answer,evidence\)/);
 assert.match(recall,/fun boundedOrNull\(answer: String, provenance: String\): LocalGroundedRecall\?/);
 assert.match(recall,/answer\.length <= 4_000/);
 assert.match(recall,/provenance\.length <= 2_000/);
 assert.match(call,/Zusatzanalyse nicht gestartet/);
});
