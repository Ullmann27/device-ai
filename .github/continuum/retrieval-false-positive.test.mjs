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
  const unrelated=observations.filter(x=>x.id!=="backprop").map(x=>({...x,kind:"SOURCE_TEXT"}));
  for(const query of [
    "Quantenkarussell987",
    "Was weißt du über Quantenkarussell987?",
    "Erkläre Quantenkarussell987"
  ]){
    assert.equal(rankMemories(query,unrelated).length,0,query);
  }
  assert.equal(sourceTermEvidence("Quantenkarussell987","Quantenmechanik und quantitative Statistik"),false);
});

test("exact technical term retrieves only relevant citation and not loosely shared Fehler",()=>{
  const sources=observations.map(x=>({...x,kind:"SOURCE_TEXT"}));
  const hits=rankMemories("Fehlerrückführung",sources);
  assert.deepEqual(hits.map(x=>x.id),["backprop"]);
  assert.ok(wordEvidence("Fehlerrückführung",sources.find(x=>x.id==="paedagogik").text)>=0.82);
  assert.equal(sourceTermEvidence("Fehlerrückführung",sources.find(x=>x.id==="paedagogik").text),false);
  assert.equal(sourceTermEvidence("Fehlerrückführung",sources.find(x=>x.id==="backprop").text),true);
  assert.equal(wordEvidence("Fehlerrückführung",observations[3].text),1);
  assert.equal(sourceTermEvidence("Fehlerrückführung",observations[4].text),false);
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
  assert.equal(sourceTermEvidence("SeriennummerXZ903","Die SeriennummerXZ902 stimmt."),false);
});

test("compound technical questions require every distinctive source anchor",()=>{
  const src=[
    {id:"quantum",kind:"SOURCE_TEXT",text:"Quantenmechanik ist ein Teilgebiet der Physik."},
    {id:"known",kind:"SOURCE_TEXT",text:"Backpropagation ist auch als Fehlerrückführung bekannt."}
  ];
  assert.equal(sourceTermEvidence("Quantenkarussell987 und Quantenmechanik",src[0].text),false);
  assert.deepEqual(rankMemories("Erkläre Quantenkarussell987 und Quantenmechanik",src),[]);
  assert.equal(sourceTermEvidence("Fehlerrückführung und Backpropagation",src[1].text),true);
  assert.deepEqual(rankMemories("Fehlerrückführung und Backpropagation",src).map(x=>x.id),["known"]);
  assert.equal(sourceTermEvidence("Fehlerrückführung und Softwaretest",src[1].text),false);
});

test("technical IDs reject near-identical but different digits in source excerpts",()=>{
  for(const [query,source] of [
    ["SWB1","SWB2 verarbeitet die Daten."],
    ["CSX1","CSX2 ist ein Forschungsmodell."],
    ["SHA256","SHA257 wurde gespeichert."],
    ["Vergleiche SWB1 und CDX1","SWB1 hat trainierbare Gewichte."]
  ])assert.equal(sourceTermEvidence(query,source),false,query);
  assert.equal(sourceTermEvidence("SWB1 und CDX1","SWB1 und CDX1 sind getrennte Lernkerne."),true);
  assert.deepEqual(rankMemories("SWB1",[
    {id:"wrong",kind:"SOURCE_TEXT",text:"SWB2 ist trainierbar."},
    {id:"right",kind:"SOURCE_TEXT",text:"SWB1 ist trainierbar."}
  ]).map(x=>x.id),["right"]);
});

test("common long question verbs do not suppress valid technical citations",()=>{
  const src=[{id:"backprop",kind:"SOURCE_TEXT",text:"Backpropagation heißt auch Fehlerrückführung."},
             {id:"other",kind:"SOURCE_TEXT",text:"Softwaretest hat andere Ziele."}];
  for(const q of ["Wie funktioniert Fehlerrückführung?",
                  "Beschreibe und analysiere Fehlerrückführung",
                  "Kannst du Fehlerrückführung charakterisieren?",
                  "Was unterscheidet Fehlerrückführung und Backpropagation?"]){
    assert.equal(sourceTermEvidence(q,src[0].text),true,q);
    assert.deepEqual(rankMemories(q,src).map(x=>x.id),["backprop"],q);
  }
  assert.equal(sourceTermEvidence("Wie funktioniert Quantenkarussell987?",src[0].text),false);
  assert.equal(sourceTermEvidence("Vergleiche Fehlerrückführung und Softwaretest",src[0].text),false);
});
