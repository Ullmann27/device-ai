import test from "node:test";
import assert from "node:assert/strict";
import {selectSpecialistRoster,runSpecialistWorkshop} from "./workshop-specialist-orchestrator.mjs";

const production=(content="class NotesRepository { fun list() = emptyList<String>() }")=>
 [{file:"app/src/main/java/at/heinz/deviceai/NotesRepository.kt",
 oldText:"",newText:"package at.heinz.deviceai\n"+content+"\n"}];
const tests=()=>[{file:"app/src/test/java/at/heinz/deviceai/NotesRepositoryTest.kt",
 oldText:"",newText:"package at.heinz.deviceai\nclass NotesRepositoryTest\n"}];

test("platform selection chooses a primary expert plus mandatory judges",()=>{
  const android=selectSpecialistRoster("Erstelle eine Android Notizen App mit Speicher");
  assert.deepEqual(android.map(x=>x.id),[
    "ANDROID_DEVELOPER","UI_ACCESSIBILITY","DATA_ARCHITECT",
    "SECURITY_GUARD","TEST_DESIGNER","RELEASE_JUDGE"
  ]);
  assert.equal(selectSpecialistRoster("Erstelle Flutter Dart Kalender App")[0].id,
   "FLUTTER_DEVELOPER");
  assert.equal(selectSpecialistRoster("Erstelle Godot GDScript mobile Spiellandschaft")[0].id,
   "GODOT_DEVELOPER");
  assert.equal(selectSpecialistRoster("Erstelle Swift iOS Kalender App")[0].id,
   "IOS_DEVELOPER");
});

test("approved draft never pretends to be compiled, tested or merged",()=>{
 const result=runSpecialistWorkshop({
  taskId:"bm-notes-01",goal:"Erstelle eine Android Notizen App mit Speicher",
  developerSynthesizer:()=>production(),testSynthesizer:tests,now:()=>1000
 });
 assert.equal(result.status,"CANDIDATE_READY_FOR_SANDBOX");
 assert.equal(result.candidateReady,true);
 assert.equal(result.buildVerified,false);
 assert.equal(result.autoMergeAllowed,false);
 assert.equal(result.verification.compilerExecuted,false);
 assert.equal(result.verification.unitTestsExecuted,false);
 assert.equal(result.verification.ownerApprovalReceived,false);
 assert.ok(result.transcript.length<=8);
});

test("banking restrictions trigger exactly one review iteration",()=>{
 let rounds=0;
 const result=runSpecialistWorkshop({
  taskId:"bm-bank-01",goal:"Erstelle sichere Banking App mit Konto",
  developerSynthesizer:({round,priorFindings})=>{
   rounds++;
   if(round===1) return production("class BankStore { val prefs = \"SharedPreferences\" }");
   assert.ok(priorFindings.some(x=>x.code==="BANKING_STORAGE_REQUIRES_SPECIALIST_REVIEW"));
   return production("class BankStore { fun balance(): Long = 0L }");
  },testSynthesizer:tests
 });
 assert.equal(rounds,2);
 assert.equal(result.status,"CANDIDATE_READY_FOR_SANDBOX");
 assert.equal(result.verification.requiresManualSecurityReview,true);
 assert.equal(result.buildVerified,false);
 assert.equal(result.transcript.length,8);
});

test("cleartext and forbidden manifests never enter verified build state",()=>{
 const blocked=runSpecialistWorkshop({
  taskId:"bm-net-01",goal:"Erstelle sichere Android App mit API",
  developerSynthesizer:()=>production('class Net { val url = "http://example.org" }'),
  testSynthesizer:tests
 });
 assert.equal(blocked.status,"BLOCKED_BY_SECURITY");
 assert.equal(blocked.buildVerified,false);
 assert.equal(blocked.autoMergeAllowed,false);
 const forbidden=runSpecialistWorkshop({
  taskId:"bm-bad-01",goal:"Erstelle Android App",
  developerSynthesizer:()=>[{file:"app/src/main/AndroidManifest.xml",
    oldText:"",newText:"<manifest>not trusted</manifest>"}],
  testSynthesizer:tests
 });
 assert.equal(forbidden.status,"BLOCKED_BY_SECURITY");
});

test("test absence and path traversal fail closed",()=>{
 const missing=runSpecialistWorkshop({
  taskId:"bm-empty-01",goal:"Erstelle Android Notizen-App",
  developerSynthesizer:()=>production(),testSynthesizer:()=>[]
 });
 assert.equal(missing.status,"MISSING_TESTS");
 const traversal=runSpecialistWorkshop({
  taskId:"bm-traversal-01",goal:"Erstelle Android Notizen-App",
  developerSynthesizer:()=>[{
    file:"../../secret",oldText:"",newText:"secret"
  }],testSynthesizer:tests
 });
 assert.equal(traversal.status,"INVALID_CANDIDATE");
 assert.equal(traversal.candidateReady,false);
 assert.throws(()=>runSpecialistWorkshop({
  taskId:"bad",goal:"Erstelle Android Notizen-App",
  developerSynthesizer:()=>production(),testSynthesizer:tests
 }),/INVALID_WORKSHOP_CONTRACT/);
});
