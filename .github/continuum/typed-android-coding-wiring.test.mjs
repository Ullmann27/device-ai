import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {fixedArgs,executeTypedBridge} from '../../scripts/continuum-typed-coding-bridge.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
test('Android confirmed task is passed only to the existing in-app Termux Builder',()=>{
 const agent=read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
 const builder=read('app/src/main/java/at/heinz/deviceai/ContinuumMobileBuilderActivity.kt');
 assert.match(agent,/conversation\.approveCodingTask\(rev, hash, ownerConfirmed = true\)/);
 assert.match(agent,/reviewedRevision != rev \|\| reviewedHash != hash \|\| copiedDraft != json/);
 assert.match(agent,/putExtra\(ContinuumTypedCodingBridge\.INTENT_JSON, json\)/);
 assert.match(agent,/Intent\(this, ContinuumMobileBuilderActivity::class\.java\)/);
 assert.match(builder,/ContinuumTypedCodingBridge\.parseAuthorization\(/);
 assert.match(builder,/setPositiveButton\("Vorbereiten"\).*dispatchTypedPrepare\(incoming\)/s);
 assert.match(builder,/setPositiveButton\("Jetzt ausführen"\).*dispatchTypedRun\(id,hash\)/s);
 assert.doesNotMatch(agent,/CONTINUUM_OWNER_V22_APPROVED_SHA/);
});
test('existing protected Android Termux receiver binds both callback identity and reviewed task digest',()=>{
 const activity=read('app/src/main/java/at/heinz/deviceai/ContinuumMobileBuilderActivity.kt');
 const callback=read('app/src/main/java/at/heinz/deviceai/ContinuumMobileBuildResultReceiver.kt');
 const bridge=read('app/src/main/java/at/heinz/deviceai/ContinuumTypedCodingBridge.kt');
 assert.match(activity,/PendingIntent\.FLAG_ONE_SHOT or PendingIntent\.FLAG_MUTABLE/);
 assert.match(activity,/ContinuumMobileBuildResultReceiver\.TYPED_EXPECTED_SHA/);
 assert.match(activity,/putString\(ContinuumMobileBuildResultReceiver\.TYPED_EXPECTED_ID,typedId\)/);
 assert.match(callback,/id != prefs\.getString\(JOB_ID, null\)/);
 assert.match(callback,/ContinuumTypedCodingBridge\.evaluate\(typed,expectedId,expectedHash,/);
 assert.match(callback,/if\(result\?\.prepared==true\)/);
 assert.match(callback,/if\(result\?\.patchSha256!=null\)/);
 assert.match(bridge,/o\["approvedForMerge"\]!=false\|\|o\["installed"\]!=false\|\|o\["deviceVerified"\]!=false/);
 assert.match(bridge,/private const val SCRIPT=ContinuumMobileBuildPlan\.PROJECT_DIR/);
});
test('Node Termux accepts fixed operations and never promotes an unevidenced success status',async()=>{
 const id='coding-fixed-operation',hash='a'.repeat(64);
 assert.deepEqual(fixedArgs(['run',id,hash]),{op:'run',id,hash});
 assert.deepEqual(fixedArgs(['prepare','encoded',hash]),{op:'prepare',encoded:'encoded',hash});
 for(const args of [['exec','echo hi'],['run',id,hash,'extra'],['run','../bad',hash]])
  assert.throws(()=>fixedArgs(args),/CODING_BRIDGE_COMMAND_INVALID/);
 // Substitute only the external worker boundary; exercise the real receipt/status gate.
 const result=await executeTypedBridge({op:'run',id,hash},{
  isolationLoader:()=>({}),
  run:async()=>({id,taskFingerprint:hash,sourceCommit:'b'.repeat(40),status:'PATCH_PENDING_OWNER_REVIEW'})
 });
 assert.equal(result.status,'EXECUTION_REPORTED_UNVERIFIED');
 assert.equal(result.patchSha256,null);
 assert.equal(result.approvedForMerge,false);
});

test('approved task source commit stays bound through Android prepare, run, callback and restart',()=>{
 const activity=read('app/src/main/java/at/heinz/deviceai/ContinuumMobileBuilderActivity.kt');
 const callback=read('app/src/main/java/at/heinz/deviceai/ContinuumMobileBuildResultReceiver.kt');
 const bridge=read('app/src/main/java/at/heinz/deviceai/ContinuumTypedCodingBridge.kt');
 const tests=read('app/src/test/java/at/heinz/deviceai/ContinuumTypedCodingBridgeTest.kt');
 const nodeBridge=read('scripts/continuum-typed-coding-bridge.mjs');
 const canonicalWorker=read('.github/continuum/coding-orchestrator.mjs');

 // Only the reviewed source is accepted for PREPARE.
 assert.match(activity,/typedCommit=authorization\.task\.sourceCommit/);
 // RUN binds to a prepared record from the same private storage, not mutable model stdout.
 assert.match(activity,/val commit=preferences\.getString\(ContinuumMobileBuildResultReceiver\.TYPED_PREPARED_COMMIT,null\)/);
 assert.match(activity,/typedId=id,typedHash=hash,typedCommit=commit/);
 assert.match(activity,/mit Quell-Commit \$\{commit\.take\(12\)\} wird gestartet/);
 assert.match(activity,/edit\.remove\(ContinuumMobileBuildResultReceiver\.TYPED_EXPECTED_ID\)[\s\S]*\.remove\(ContinuumMobileBuildResultReceiver\.TYPED_EXPECTED_COMMIT\)/);
 assert.match(activity,/TYPED_EXPECTED_COMMIT,typedCommit/);
 assert.match(activity,/TYPED_PREPARED_COMMIT\)/);
 assert.match(callback,/const val TYPED_EXPECTED_COMMIT =/);
 assert.match(callback,/const val TYPED_PREPARED_COMMIT =/);
 assert.match(callback,/val expectedCommit=prefs\.getString\(TYPED_EXPECTED_COMMIT,null\)/);
 assert.match(callback,/if\(expectedId!=null && expectedHash!=null && expectedCommit!=null\)/);
 assert.match(callback,/if\(result\?\.prepared==true && expectedCommit!=null\)/);
 assert.match(callback,/putString\(TYPED_PREPARED_COMMIT,expectedCommit\)/);
 assert.match(callback,/remove\(TYPED_EXPECTED_COMMIT\)/);
 // A matching SHA task-id pair cannot silently endorse any other well-formed Git SHA.
 assert.match(bridge,/if\(!COMMIT\.matches\(expectedSourceCommit\)\|\|source!=expectedSourceCommit\)return invalid\(\)/);
 assert.match(tests,/foreignGitCommitCannotBecomeApprovedCodingEvidence/);
 // Node keeps one canonical CCC1 task/grant bound to its actual source HEAD.
 assert.match(nodeBridge,/expectedTaskFingerprint:hash/);
 assert.match(nodeBridge,/sourceCommit:r\.sourceCommit,taskSha256:hash/);
 assert.match(canonicalWorker,/git\(project,\['rev-parse','HEAD'\]\)\.stdout\.trim\(\)!==grant\.task\.sourceCommit/);
 assert.match(canonicalWorker,/grant\.fingerprint!==expectedTaskFingerprint/);

 // Neither Android callback nor model can promote/install an APK.
 assert.match(bridge,/o\["approvedForMerge"\]!=false\|\|o\["installed"\]!=false\|\|o\["deviceVerified"\]!=false/);
});
