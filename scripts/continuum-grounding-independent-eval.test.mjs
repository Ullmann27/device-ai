import test from 'node:test';
import assert from 'node:assert/strict';
import {summarize,PINS} from './continuum-grounding-independent-eval.mjs';

const examples=[
  {id:'one',scope:'in_scope',kind:'USER_FACT',category:'matches',expectedIds:['A']},
  {id:'two',scope:'challenge',kind:'SOURCE_TEXT',category:'unknown',expectedIds:[]},
  {id:'three',scope:'in_scope',kind:'USER_FACT',category:'matches',expectedIds:['B']}
];

test('frozen exact-ID scores count false positives and missed real evidence',()=>{
  const results=[
    {caseId:'one',status:'PASS',returnedIds:['A','A']},
    {caseId:'two',status:'PASS',returnedIds:['X']},
    {caseId:'three',status:'PASS',returnedIds:[]}
  ];
  const all=summarize(examples,results);
  assert.deepEqual(all.all,{
    cases:3,exact:1,errors:0,falseHits:1,missed:1,trueHits:1,expectedIds:2
  });
  assert.equal(all['scope:in_scope'].exact,1);
  assert.equal(all['scope:challenge'].falseHits,1);
});

test('errors cannot masquerade as correct unknown-answer abstentions',()=>{
  const all=summarize(examples,[
    {caseId:'one',status:'ERROR',returnedIds:null},
    {caseId:'two',status:'ERROR',returnedIds:null},
    {caseId:'three',status:'PASS',returnedIds:['B']}
  ]);
  assert.equal(all.all.errors,2);
  assert.equal(all.all.exact,1);
  assert.equal(all.all.missed,1);
});

test('evaluation refuses missing data, reordered cases and mutable fixture pins',()=>{
  assert.throws(()=>summarize(examples,[]),/Case count mismatch/);
  assert.throws(()=>summarize(examples,[
    {caseId:'wrong',status:'PASS',returnedIds:[]},
    {caseId:'two',status:'PASS',returnedIds:[]},
    {caseId:'three',status:'PASS',returnedIds:[]}
  ]),/Case order mismatch/);
  assert.equal(PINS.fixtureHash.length,64);
  assert.equal(PINS.baseline.length,40);
  assert.equal(PINS.candidate.length,40);
});
