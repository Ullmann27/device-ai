import test from 'node:test';
import assert from 'node:assert/strict';
import {MEMORIES,QUESTIONS,UNANSWERABLE,CHALLENGE,FINGERPRINT} from './cre-challenge-v2.mjs';
import {buildData} from './relational-memory-data.mjs';

test('v2 challenge is disjoint from all prior synthetic splits and internally consistent',()=>{
 const older=buildData();
 const names=new Set(['train','dev','test'].flatMap(s=>older[s].memories.map(m=>m.subject.toLowerCase())));
 const ids=new Set(MEMORIES.map(x=>x.id));
 assert.equal(MEMORIES.length,16);assert.equal(ids.size,16);
 assert.equal(QUESTIONS.length,32);assert.equal(UNANSWERABLE.length,12);
 assert.equal(FINGERPRINT.length,64);
 for(const memory of MEMORIES){
  assert.ok(!names.has(memory.subject.toLowerCase()),memory.subject);
  assert.ok(memory.text.length<512);
 }
 for(const q of QUESTIONS)assert.ok(ids.has(q.target));
 for(const q of UNANSWERABLE)assert.equal(q.target,null);
 assert.deepEqual(CHALLENGE.questions,QUESTIONS);
});
