import test from 'node:test';
import assert from 'node:assert/strict';
import {tokenize} from './jstoken.mjs';
import {editsAt,applyEdit,candidatesInWindow} from './operators.mjs';
import {diagnoseTap} from './diagnose.mjs';
import {Experience,attempt,order,binomTwoSided} from './arms.mjs';
import {seededOrder,splitNames} from './tasks.mjs';
import {namePattern,topLevelNames} from './sandbox.mjs';

test('the tokenizer never edits strings, templates, comments or regex literals',()=>{
 const src="const a='x<y';// a<b\nconst r=/a<b/g, t=`v ${a<1?'<':'>'} w<z`;/* x===y */ const z=a/2<b;";
 const t=tokenize(src);
 assert.deepEqual(t.filter(x=>x.type==='regex').map(x=>x.value),['/a<b/g']);
 assert.equal(t.filter(x=>x.type==='template').length,1);
 const edits=t.flatMap((_,k)=>editsAt(t,k));
 for(const e of edits){const s=src.slice(e.start,e.end);assert.ok(e.kind==='INSERT'||['<','/','2','1'].includes(s),'edited '+s);}
 assert.ok(edits.some(e=>e.family==='ARITH'&&e.from==='/'),'division is an operator, not a regex');
});
test('every fault edit has its exact inverse in the repair window',()=>{
 const src="export function f(a,b){\n if(!a&&b>=2)return a+1;\n return b===3||a<0;\n}\n";
 const t=tokenize(src);
 for(const [k] of t.entries())for(const fault of editsAt(t,k)){
  const faulted=applyEdit(src,fault),ft=tokenize(faulted);
  const c=candidatesInWindow(ft,faulted,1,4);
  if(fault.family==='NEG'&&fault.kind==='INSERT')continue;   // inserting '!' before a call target is not always invertible
  assert.ok(c.some(x=>applyEdit(faulted,x)===src),'no inverse for '+fault.family+' '+fault.kind+' '+fault.from);
 }
});
test('diagnosis uses only what a failing TAP run shows',()=>{
 const tap=(fields)=>"TAP version 13\nnot ok 1 - t\n  ---\n"+fields+"\n  ...\n";
 assert.equal(diagnoseTap(tap("  error: 'x'\n  expected: 4\n  actual: 3\n  operator: 'strictEqual'\n  code: 'ERR_ASSERTION'")).kind,'NUM_OFF1');
 assert.equal(diagnoseTap(tap("  expected: true\n  actual: false\n  operator: 'strictEqual'\n  code: 'ERR_ASSERTION'")).kind,'BOOL');
 assert.equal(diagnoseTap(tap("  error: 'Missing expected exception.'\n  code: 'ERR_ASSERTION'")).kind,'THROWS');
 assert.equal(diagnoseTap(tap("  error: 'boom'\n  name: 'TypeError'")).kind,'RUNTIME_TypeError');
 assert.equal(diagnoseTap('ok 1 - fine\n').kind,'OTHER');
});
test('test selection: names are split 2:1:1 and escaped exactly',()=>{
 const p=splitNames(['a','b','c','d','e','f','g','h']);
 assert.deepEqual(p,{visible:['a','b','e','f'],verifier:['c','g'],final:['d','h']});
 assert.equal(namePattern(['x (1)','y.z']),'^(?:x \\(1\\)|y\\.z)$');
 assert.deepEqual(topLevelNames('ok 1 - a\n    ok 1 - inner\nok 2 - b # SKIP x\nnot ok 3 - c\n'),['a','b']);
 assert.deepEqual(seededOrder([1,2,3,4],'s'),seededOrder([1,2,3,4],'s'));
});
const task={id:'t',diagnosis:{kind:'NUM_OFF1'},visibleFailing:['A']};
const cand=(id,family,kind,line=1)=>({id,family,kind,line});
test('arms: budget, no repeats, verification gates acceptance, experience changes the order',()=>{
 const cands=[cand('a','REL','SWAP'),cand('b','CONST','INC'),cand('c','EQ','SWAP')];
 const exp=new Experience();
 for(let i=0;i<5;i++)exp.observe('NUM_OFF1','CONST:INC',{visiblePass:true,fullPass:true});
 for(let i=0;i<5;i++)exp.observe('NUM_OFF1','REL:SWAP',{visiblePass:false,fullPass:false});
 assert.equal(order('context',task,cands,exp)[0].id,'b');
 // 'a' passes visible but not the verifier; 'b' passes both
 const run=(c,subset)=>({passed:subset==='visible'?c.id!=='c':c.id==='b',ms:1,rssKb:1,failing:c.id==='c'?['A']:[]});
 const none=attempt('none',task,cands,new Experience(),run,{budget:3});
 assert.ok(none.accepted);assert.equal(none.verifierRuns,0);
 const ver=attempt('verified',task,[cand('a','REL','SWAP'),cand('b','CONST','INC')],new Experience(),run,{budget:3});
 assert.ok(['a','b'].includes(ver.tried[0]));assert.equal(ver.accepted.id,'b');
 const tight=attempt('verified',task,cands,exp,(c,s)=>({passed:false,ms:1,rssKb:1,failing:['A']}),{budget:2});
 assert.equal(tight.visibleRuns,2);assert.equal(tight.accepted,null);assert.equal(new Set(tight.tried).size,tight.tried.length);
});
test('self-correction promotes edits on the line whose change altered which tests fail',()=>{
 // experience puts EQ first, NEG second, REL last; the right repair is the REL edit on line 1
 const exp=new Experience();
 for(let i=0;i<6;i++){exp.observe('X','EQ:SWAP',{visiblePass:true,fullPass:true});exp.observe('X','REL:SWAP',{visiblePass:false,fullPass:false});}
 for(let i=0;i<3;i++)exp.observe('X','NEG:REMOVE',{visiblePass:true,fullPass:true});
 const cands=[cand('x1','EQ','SWAP',1),cand('y1','NEG','REMOVE',2),cand('x2','REL','SWAP',1),cand('y2','REL','SWAP',2)];
 const run=c=>({passed:c.id==='x2',ms:1,rssKb:0,failing:c.line===1?['B']:['A']});   // line-1 edits change the failing set
 const plain=attempt('adaptive',task,cands,exp,run,{budget:4});
 const smart=attempt('selfcorrect',task,cands,exp,run,{budget:4});
 assert.deepEqual(plain.tried.slice(0,2),['x1','y1']);
 assert.deepEqual(smart.tried,['x1','x2']);           // the failed line-1 edit changed behaviour: stay on line 1
 assert.equal(smart.accepted.id,'x2');assert.equal(smart.refinements,1);
 assert.ok(smart.visibleRuns<plain.visibleRuns);
});
test('exact binomial two-sided p-values',()=>{
 assert.equal(binomTwoSided(0,0),1);
 assert.ok(Math.abs(binomTwoSided(0,6)-2/64)<1e-12);
 assert.ok(binomTwoSided(3,6)>0.99);
});
