import test from 'node:test';
import assert from 'node:assert/strict';
import {createNeuralState} from './neural-core.mjs';
import {dialogueTurn} from './neural-dialogue.mjs';

const state=()=>({neural:createNeuralState(),lessons:[
 {id:'project',text:'Der Gruß Hallo ist für mein Projekt Astrolux wichtig.'}
],baby:{beliefs:[]}});
test('greetings do not cite incidental memory and preserve an established topic',()=>{
 const s=state();
 const first=dialogueTurn(s,'Erzähl mir von Astrolux',100);
 assert.deepEqual(first.memoryIds,['project']);
 const greeting=dialogueTurn(s,'Hallo',101);
 assert.deepEqual(greeting.memoryIds,[]);
 assert.equal(s.dialogue.topic,'Erzähl mir von Astrolux');
 const follow=dialogueTurn(s,'Und was weißt du noch darüber?',102);
 assert.deepEqual(follow.memoryIds,['project']);
 assert.match(follow.resolvedQuery,/Astrolux/);
 assert.equal(s.dialogue.turns.at(-1).modelHash,follow.modelHash);
});
test('unanswered new topic removes stale conversational reference',()=>{
 const s=state();
 dialogueTurn(s,'Erzähl mir von Astrolux',100);
 const unknown=dialogueTurn(s,'Erzähl mir von Quantenorca',101);
 assert.deepEqual(unknown.memoryIds,[]);
 assert.equal(s.dialogue.topic,'');
 const follow=dialogueTurn(s,'Und was weißt du noch darüber?',102);
 assert.ok(!follow.resolvedQuery.includes('Astrolux'));
 assert.deepEqual(follow.memoryIds,[]);
});
test('only explicitly cited observations and owner lessons are eligible for retrieval',()=>{
 const s=state();
 s.baby.beliefs=[
 {id:'rejected',status:'PROVISIONAL',evidenceMode:'LEGACY_UNVERIFIED',text:'Astrolux ist ein unbelegter Mythos.'},
 {id:'supported',status:'GROUNDED_OBSERVATION',evidenceMode:'EXPLICIT_CITATION',
   text:'Astrolux wurde in einer öffentlichen Veröffentlichung erwähnt.',citationUrls:['https://example.org/reviewed']}
 ];
 const result=dialogueTurn(s,'Erzähl mir von Astrolux',100);
 assert.deepEqual(result.memoryIds.slice().sort(),['project','supported']);
 assert.ok(!result.answer.includes('unbelegter Mythos'));
});
