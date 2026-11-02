import test from 'node:test';
import assert from 'node:assert/strict';
import {AssocLanguageCore,identifierParts} from './assoc-language-core.mjs';

const cands=[{module:'.github/continuum/development-backlog.mjs',export:'canonical',description:'Canonical JSON.'},
 {module:'.github/continuum/console-policy.mjs',export:'isOwnerComment',description:'Owner check.'}];

test('identifier parts split camelCase, digits and module paths',()=>{
 assert.deepEqual(identifierParts('isOwnerComment'),['is','owner','comment']);
 assert.deepEqual(identifierParts('sha256Hex'),['sha256','hex']);
 assert.deepEqual(identifierParts('.github/continuum/console-policy.mjs'),['console','policy']);
});

test('documentation links German words to identifier parts; receipts teach only with a verdict; rollback is exact',async()=>{
 const core=new AssocLanguageCore();
 const docs=['Die kanonische Schreibweise (Besitzer) liefert `canonical` immer gleich.\n\nAuch hier: kanonische Form über `canonical` für den Besitzer.\n\nAndere Absätze nennen `isOwnerComment` für den Besitzer.'];
 const stats=core.learnFromDocs(docs);
 assert.equal(stats.paragraphs,3);
 assert.ok(core.get('kanonisch','canonical')>0,'two co-occurrences create an association');
 const before=core.snapshot();
 const goal='Erzeuge die kanonische Darstellung';
 const [a,b]=await core.rank(goal,cands);
 assert.ok(a>b,'the associated identifier ranks first');
 assert.equal(core.learnFromReceipt({goalText:'Besitzer erkennen',candidate:cands[1],verdict:'INCONCLUSIVE'}),0);
 core.learnFromReceipt({goalText:'Besitzer erkennen',candidate:cands[1],verdict:'CONFIRMED'});
 core.learnFromReceipt({goalText:'Besitzer kanonisch erkennen',candidate:cands[0],verdict:'REFUTED'});
 assert.ok(core.get('besitz','owner')>0||core.get('besitzer','owner')>0);
 assert.notEqual(core.snapshot(),before);
 core.rollback(2);
 assert.equal(core.snapshot(),before,'two receipts undone exactly');
});
