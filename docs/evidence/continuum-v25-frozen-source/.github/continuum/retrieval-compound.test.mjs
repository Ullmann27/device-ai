import test from "node:test";
import assert from "node:assert/strict";
import {wordEvidence,rankMemories,EVIDENCE_THRESHOLD} from "./dialogue-retrieval.mjs";

test("German compound noun retrieval recognizes heads in personal memories",()=>{
  const sentence="Sternenreise ist mein Forschungsprojekt";
  const score=wordEvidence("Wie heißt mein Projekt?",sentence);
  assert.ok(score>=0.82,"Expected compound noun head to pass, got "+score);
  assert.ok(score>=EVIDENCE_THRESHOLD);
  const hits=rankMemories("Wie heißt mein Projekt?",[{id:"owner",text:sentence}]);
  assert.equal(hits.length,1);
  assert.equal(hits[0].id,"owner");
  assert.ok(hits[0].score>0);
});
test("German compound suffixes work symmetrically for common family questions",()=>{
  assert.ok(wordEvidence("Wo ist der Schlüssel?","Der Haustürschlüssel liegt in der Schublade.")>=0.82);
  assert.ok(wordEvidence("Ist mein Mobilgerät erreichbar?","Dein Gerät ist bereit.")>=0.82);
  assert.ok(wordEvidence("Wo ist mein Forschungsprojekt?","Mein Projekt heißt Astra.")>=0.82);
});
test("substring fragments below five characters cannot become compound evidence",()=>{
  assert.ok(wordEvidence("Code","Barcode")<0.82);
  assert.ok(wordEvidence("Tor","Monitor")<0.82);
});
test("unrelated queries do not force an invented matching memory",()=>{
  const hits=rankMemories("Quantenperiskop",[
    {id:"owner1",text:"Sternenreise ist mein Forschungsprojekt"},
    {id:"owner2",text:"Mein Haustürschlüssel ist sicher verwahrt"}
  ]);
  assert.equal(hits.length,0);
});
