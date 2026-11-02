import test from 'node:test';
import assert from 'node:assert/strict';
import {rankMemories,sourceTermEvidence} from './dialogue-retrieval.mjs';

const cases=[
  ['ORION-47','ORION-48'],['R1','R2'],['A-7','A-8'],
  ['SN_15','SN_16'],['PRJ-04','PRJ-4'],['V4.1','V4.2']
];

for(const [correct,wrong] of cases){
  test(`single identifier ${correct} remains retrievable but ${wrong} cannot use its evidence`,()=>{
    const text=`Roboter ${correct} wohnt in Graz.`;
    for(const kind of ['USER_FACT','SOURCE_TEXT']){
      const memories=[{id:'exact',kind,text}];
      assert.deepEqual(rankMemories(`Wo wohnt ${correct}?`,memories).map(x=>x.id),['exact']);
      assert.deepEqual(rankMemories(`Wo wohnt ${correct.toLowerCase()}?`,memories).map(x=>x.id),['exact']);
      assert.deepEqual(rankMemories(`Wo wohnt ${wrong}?`,memories),[]);
    }
    assert.equal(sourceTermEvidence(`Wo wohnt ${correct}?`,text),true);
    assert.equal(sourceTermEvidence(`Wo wohnt ${wrong}?`,text),false);
  });
}

test('unrelated short identifier cannot borrow a generic shared verb',()=>{
  for(const kind of ['USER_FACT','SOURCE_TEXT'])
    assert.deepEqual(rankMemories('Wo wohnt R1?',[
      {id:'other',kind,text:'Roboter ORION-47 wohnt in Graz.'}
    ]),[]);
});

test('identifier boundaries are literal rather than substring matches',()=>{
  for(const text of ['Roboter R10 wohnt in Graz.','Roboter XR1 wohnt in Graz.',
                     'Roboter R1-X wohnt in Graz.'])
    assert.deepEqual(rankMemories('Wo wohnt R1?',[{id:'other',kind:'USER_FACT',text}]),[]);
});
