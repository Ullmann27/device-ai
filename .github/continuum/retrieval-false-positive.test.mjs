import test from "node:test";
import assert from "node:assert/strict";
import {EVIDENCE_THRESHOLD,wordEvidence,sourceTermEvidence,rankMemories} from "./dialogue-retrieval.mjs";

const observations=[
  {id:"physik",text:"Die Physik untersucht Quantenmechanik und andere Phänomene."},
  {id:"statistik",text:"Statistik ist die Lehre von quantitativen Informationen."},
  {id:"widrow",text:"Bernard Widrow erforschte technische Lernverfahren."},
  {id:"backprop",text:"Backpropagation heißt auch Fehlerrückführung."},
  {id:"softwaretest",text:"Softwaretest dient dazu, Fehler in Programmen zu erkennen."},
  {id:"paedagogik",kind:"SOURCE_TEXT",text:"Pädagogik befasst sich mit Erziehung und der Führung eines Knaben."},
  {id:"aufrichtigkeit",text:"Aufrichtigkeit bedeutet die ehrliche Darstellung des eigenen Wissens."}
];

test("fabricated mixed letter-digit topic must abstain on real but unrelated source topics",()=>{
  const unrelated=observations.filter(x=>x.id!=="backprop");
  for(const query of [
    "Quantenkarussell987",
    "Was weißt du über Quantenkarussell987?",
    "Erkläre Quantenkarussell987"
  ]){
    assert.equal(rankMemories(query,unrelated).length,0,query);
  }
  assert.ok(wordEvidence("Quantenkarussell987","Quantenmechanik und quantitative Statistik")<EVIDENCE_THRESHOLD);
});

test("exact technical term retrieves only relevant citation and not loosely shared Fehler",()=>{
  const sources=observations.map(x=>({...x,kind:"SOURCE_TEXT"}));
  const hits=rankMemories("Fehlerrückführung",sources);
  assert.deepEqual(hits.map(x=>x.id),["backprop"]);
  assert.ok(wordEvidence("Fehlerrückführung",sources.find(x=>x.id==="paedagogik").text)>=0.82);
  assert.equal(sourceTermEvidence("Fehlerrückführung",sources.find(x=>x.id==="paedagogik").text),false);
  assert.equal(sourceTermEvidence("Fehlerrückführung",sources.find(x=>x.id==="backprop").text),true);
  assert.equal(wordEvidence("Fehlerrückführung",observations[3].text),1);
  assert.ok(wordEvidence("Fehlerrückführung",observations[4].text)<EVIDENCE_THRESHOLD);
});

test("explicit owner memories retain compound matches without source-only guard",()=>{
  const owner=[{id:"project",text:"Mein Projekt heißt Astra."}];
  assert.deepEqual(rankMemories("Wo ist mein Forschungsprojekt?",owner).map(x=>x.id),["project"]);
});

test("German near-inflection and complete compound suffix remain retrievable",()=>{
  assert.ok(wordEvidence("Pflanzen","Die Pflanze wächst.")>=0.8);
  assert.ok(wordEvidence("Schlüssel","Der Haustürschlüssel liegt hier.")>=0.82);
  assert.ok(wordEvidence("Projekt","Sternenreise ist mein Forschungsprojekt.")>=0.82);
  assert.equal(wordEvidence("SeriennummerXZ902","Die SeriennummerXZ902 stimmt."),1);
  assert.ok(wordEvidence("SeriennummerXZ903","Die SeriennummerXZ902 stimmt.")<EVIDENCE_THRESHOLD);
});
