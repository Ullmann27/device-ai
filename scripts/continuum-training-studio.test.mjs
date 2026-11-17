import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),"..");
const file=path.join(root,"app/src/main/assets/continuum-training-studio-v1.json");
const data=JSON.parse(fs.readFileSync(file,"utf8"));
const categories=data.categories;
const all=categories.flatMap(c=>c.exercises.map(e=>({c,e})));
const turns=all.flatMap(({c,e})=>e.steps.map(s=>({c,e,s})));

test("training studio is versioned, bounded, and has all 18 meaningful categories",()=>{
 assert.equal(data.version,"owner-context-studio-v1-20261023");
 assert.equal(categories.length,18);
 assert.equal(all.length,256);
 assert.equal(turns.length,600);
 assert.equal(new Set(categories.map(c=>c.id)).size,categories.length);
 for(const c of categories){
  assert.ok(c.goal.length>35,c.id);
  assert.ok(c.exercises.length>=8,c.id);
  assert.ok(["NEURAL","MEMORY","LOGIC","DIALOGUE","PROBE","TOOL","LAB"].includes(c.mode));
  assert.equal(new Set(c.exercises.map(e=>e.id)).size,c.exercises.length);
 }
});

test("copy-only studio is safe: inputs are small and expected results explicit",()=>{
 assert.match(data.rules,/kein(?:e)? (?:automatisches|verdeckte)/i);
 for(const {c,e,s} of turns){
  assert.ok(s.input.length>=3 && s.input.length<=320,c.id+"/"+e.id);
  assert.ok(typeof s.expected==="string" && s.expected.length>=15,c.id+"/"+e.id);
  assert.ok(!/^(?:system|developer|assistant|tool):/i.test(s.input),c.id+"/"+e.id);
 }
});

test("human dialogue, Austrian everyday speech, grammar and typos have independent tracks",()=>{
 const ids=categories.map(c=>c.id);
 for(const id of ["austrian-conversation","typo-robustness","grammar-context","conversation-flow","topic-switch",
    "chronology","causality","uncertainty","source-review","coding-tasks"])assert.ok(ids.includes(id),id);
 assert.ok(categories.find(c=>c.id==="austrian-conversation").exercises.length>=12);
 assert.ok(categories.find(c=>c.id==="typo-robustness").exercises.length>=12);
 assert.ok(categories.find(c=>c.id==="grammar-context").exercises.length>=12);
});

test("syntax-specific memory and logic facts are ordered and explicitly revocable",()=>{
 for(const c of categories.filter(c=>c.mode==="LOGIC")){
  for(const e of c.exercises){
   const input=e.steps.map(s=>s.input);
   assert.match(input[0],/^Merke dir: Fakt:/);
   assert.match(input[1],/^Merke dir: Regel:/);
   assert.ok(input.some(s=>s.startsWith("Prüfe Wissen:")));
   assert.ok(input.filter(s=>s.startsWith("Widerrufe:")).length>=2);
  }
 }
});

test("ungraded open-ended dialogue exercises do not claim to train weights",()=>{
 for(const c of categories.filter(c=>["DIALOGUE","PROBE","LAB"].includes(c.mode))){
  assert.ok(!/automatisch.+gewichte.+trainiert/iu.test(c.goal));
 }
 assert.ok(categories.find(c=>c.id==="neural-greetings").goal.includes("64"));
 assert.ok(categories.find(c=>c.id==="neural-questions").goal.includes("FRAGE"));
});

test("owner UI explicitly copies text and persists only a navigation cursor",()=>{
 const activity=fs.readFileSync(path.join(root,"app/src/main/java/at/heinz/deviceai/AgentActivity.kt"),"utf8");
 assert.match(activity,/private fun showContextTrainingGuide\(\)/);
 assert.match(activity,/private fun showContextTrainingCategory\(/);
 assert.match(activity,/private fun showContextTrainingStep\(/);
 assert.match(activity,/private fun copyNextContextTrainingStep\(\)/);
 assert.match(activity,/setPrimaryClip\(android\.content\.ClipData\.newPlainText\("Continuum Training", inputText\)\)/);
 assert.match(activity,/\.putInt\("category", nextCategory\)/);
 assert.match(activity,/\.putInt\("turn", nextStep\)/);
 assert.match(activity,/CenterAction\("KONTEXT-TRAINING · MEHRSTUFIGE ÜBUNGEN"/);
 assert.match(activity,/CenterAction\("TRAINING · NÄCHSTE VORLAGE KOPIEREN"/);
});
