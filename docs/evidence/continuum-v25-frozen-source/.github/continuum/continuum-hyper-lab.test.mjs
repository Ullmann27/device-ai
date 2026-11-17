import test from "node:test";
import assert from "node:assert/strict";
import {RelationalEncoder} from "./cre-encoder.mjs";
import {SubwordEncoder} from "./portable-subword.mjs";
import {
 invertMatrix,experimentCayleyRotation,proposeLogicMutants,
 planProofConsolidation,bimodalConditioner16
} from "./continuum-hyper-lab.mjs";

test("Cayley inversion rejects singular inputs without guessing a solver",()=>{
 assert.deepEqual(Array.from(invertMatrix(new Float64Array([2,0,0,4]),2)),[.5,0,0,.25]);
 assert.throws(()=>invertMatrix(new Float64Array([0,0,0,0]),2),/SINGULAR_MATRIX/);
 assert.throws(()=>invertMatrix(new Float64Array([0,0,0]),2),/INVALID_MATRIX/);
});

test("Cayley proof preserves E and rejects holdout leakage, without asserting 2ms or success",()=>{
 const model=new RelationalEncoder(41);
 const baseline=model.save();
 const training=[{
  query:"Wie reagiert das Worktree-Gate bei einem Fehler?",
  candidates:[
   "Bei einem Defekt führt der Gate-Worker isolierte Regressionstests aus.",
   "Bei jedem Defekt zeigt die App automatisch eine zufällige Farbe.",
   "Ein Browser speichert automatisch neue Fotos ohne Anfrage."
  ]
 }];
 const holdout=[
  {query:"Welche Aufgabe hat das Android-Sicherheitsgate?",candidates:[
   "Das Sicherheitsgate verlangt eine Freigabe vor der APK-Installation.",
   "Das Sicherheitsgate druckt automatisch einen Kassenbon."
  ]},
  {query:"Warum müssen Tests unabhängig ausgeführt werden?",candidates:[
   "Ein unabhängiger Testlauf entdeckt Fehler des geänderten Entwicklungszweiges.",
   "Ein unabhängiger Testlauf verändert den Wetterbericht."
  ]}
 ];
 assert.throws(()=>experimentCayleyRotation({
  baselineBytes:baseline,trainingExamples:training,holdoutExamples:training.concat(holdout)
 }),/HOLDOUT_LEAKAGE/);
 const outcome=experimentCayleyRotation({
  baselineBytes:baseline,trainingExamples:training,holdoutExamples:holdout
 });
 assert.ok(outcome.rotationError<1e-8);
 assert.equal(typeof outcome.accepted,"boolean");
 assert.equal(outcome.modelBytes.length,baseline.length);
 assert.deepEqual(RelationalEncoder.restore(outcome.modelBytes).E,model.E);
 if(outcome.accepted)assert.ok(outcome.after>outcome.before);
 else assert.deepEqual(outcome.modelBytes,baseline);
});

test("lexical micro-mutations are proposals, never unreviewed writes or test green claims",()=>{
 const path=".github/continuum/sample-module.mjs";
 const source="export function gate(battery) {\n  return battery > 30;\n}\n";
 const changes=proposeLogicMutants({file:path,sourceText:source,suspectLine:2});
 assert.ok(changes.length>0);
 assert.equal(changes[0].operator,">=");
 assert.match(changes[0].proposal.edits[0].newText,/battery >= 30/);
 assert.equal(changes[0].verifiedByTests,false);
 assert.equal(changes[0].acceptedByMainGate,false);
 assert.equal(source.includes("battery >= 30"),false);
 assert.deepEqual(proposeLogicMutants({file:path,sourceText:
  'export function x(){\n  return "battery > 30";\n}',suspectLine:2}),[]);
 assert.throws(()=>proposeLogicMutants({file:"../../key",sourceText:source,suspectLine:2}));
});

test("proof consolidation never merges paraphrases or differing source hashes",()=>{
 const first="Android 16 verlangt compileSdk 36 für ein Beispielprojekt.";
 const text2="Für das Beispielprojekt verlangt Android 16 compileSdk 36.";
 const beliefs=[
  {id:"a",text:first,sourceHash:"a".repeat(64),trustClass:"SOURCE_OBSERVATION"},
  {id:"b",text:first,sourceHash:"a".repeat(64),trustClass:"SOURCE_OBSERVATION"},
  {id:"c",text:text2,sourceHash:"a".repeat(64),trustClass:"SOURCE_OBSERVATION"},
  {id:"d",text:first,sourceHash:"b".repeat(64),trustClass:"SOURCE_OBSERVATION"},
  {id:"e",text:first,sourceHash:"a".repeat(64),trustClass:"OWNER_CONFIRMED"}
 ];
 const report=planProofConsolidation(beliefs);
 assert.equal(report.originalCount,5);
 assert.equal(report.uniqueGroups,4);
 assert.equal(report.duplicatesFound,1);
 assert.equal(report.writesPerformed,false);
 assert.equal(report.removedSourceEvidence,false);
 assert.deepEqual(report.groups.find(x=>x.instanceIds.includes("a")).instanceIds,["a","b"]);
});

test("bimodal synapse is 16-dimensional, finite and depends on CRE1 evidence",()=>{
 const swb=new SubwordEncoder(91);
 const cre=new RelationalEncoder(42);
 const evidence="Leo wohnt in Graz.";
 const question="Wo wohnt Leo?";
 const a=bimodalConditioner16(swb,cre,evidence,question);
 const b=bimodalConditioner16(swb,cre,"Ein Auto hat einen Motor.",question);
 assert.equal(a.length,16);
 assert.ok([...a].every(x=>Number.isFinite(x)&&x>=-1&&x<=1));
 assert.ok(a.slice(8).some((x,i)=>Math.abs(x-b[8+i])>1e-9));
 assert.throws(()=>bimodalConditioner16(swb,cre,"",question),/INVALID_BIMODAL_CONDITION/);
});
