import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {rankMemories,sourceTermEvidence} from './dialogue-retrieval.mjs';

// The same behavioral cases execute against real Kotlin rank(), including the
// source-only lexical policy. No helper's private implementation is asserted.
const rows=readFileSync(new URL('../../app/src/test/resources/continuum-location-grounding.tsv',import.meta.url),'utf8')
  .trim().split('\n').filter(line=>!line.startsWith('#')).map(line=>line.split('\t'));
for(const [id,query,text,owner,source] of rows)for(const [kind,expected] of [['USER_FACT',owner],['SOURCE_TEXT',source]]){
  test(`location eligibility ${id} (${kind})`,()=>{
    const memories=[{id:'evidence',kind,text}],before=JSON.stringify(memories);
    const hits=rankMemories(query,memories);
    assert.deepEqual(hits.map(hit=>hit.id),expected==='true'?['evidence']:[]);
    if(kind==='SOURCE_TEXT')assert.equal(sourceTermEvidence(query,text),expected==='true');
    assert.equal(JSON.stringify(memories),before,'selection cannot mutate stored memories');
  });
}
