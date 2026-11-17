// Lossless adaptation of frozen text to the existing <=300-character item contract.
import assert from 'node:assert/strict';
export function acceptanceClauses(requirements){
 const clauses=[];
 for(const original of requirements){
  let rest=original;const chunks=[];
  while(rest.length>300){const cut=rest.lastIndexOf(' ',300);assert.ok(cut>0);chunks.push(rest.slice(0,cut));rest=rest.slice(cut+1);}
  chunks.push(rest);assert.equal(chunks.join(' '),original);clauses.push(...chunks);
 }
 assert.ok(clauses.length<=8);return clauses;
}
