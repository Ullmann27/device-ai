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

test('spaced numbered entity uses exact name-plus-number source identity',()=>{
  const q='Wo steht Gerät Kappa 7?';
  const exact='Gerät Kappa 7 steht in Raum Silber.';
  const falseNeighbour='Gerät Kappa 70 steht in Raum Gold.';
  assert.deepEqual(rankMemories(q,[
    {id:'correct',kind:'USER_FACT',text:exact},
    {id:'wrong',kind:'USER_FACT',text:falseNeighbour}
  ]).map(h=>h.id),['correct']);
  assert.equal(sourceTermEvidence(q,exact),true);
  assert.equal(sourceTermEvidence(q,falseNeighbour),false);
  assert.equal(sourceTermEvidence(q,'Gerät Kappa-7 steht in Raum Silber.'),true);
});

test('split-number rule mirrors android without breaking ordinary years',()=>{
  assert.equal(sourceTermEvidence('Wo steht Projekt Orion 5?',
    'Projekt Orion 50 steht im Labor.'),false);
  assert.equal(sourceTermEvidence('Wo steht Projekt Orion 5?',
    'Projekt Orion 5 steht im Labor.'),true);
  assert.equal(sourceTermEvidence('Was geschah im Jahr 2024?',
    'Im Jahr 2024 geschah ein Ereignis.'),true);
  assert.deepEqual(rankMemories('Wo steht Gerät Äther 4?',[
    {id:'incorrect',kind:'USER_FACT',text:'Gerät Äther 40 steht im Labor.'},
    {id:'correct',kind:'USER_FACT',text:'Gerät Äther 4 steht im Labor.'}
  ]).map(h=>h.id),['correct']);
});
