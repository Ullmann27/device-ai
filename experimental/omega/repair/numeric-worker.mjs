/** Fixed worker for the ALREADY RESTRICTED numeric grammar. node:vm is not an
 * OS sandbox. No general JS, objects, imports, calls, loops or properties are
 * admitted by isLocalNumericPatch; inputs are primitive finite numbers only. */
import fs from 'node:fs';
import vm from 'node:vm';
import {numericSuite} from './numeric-suite.mjs';
import {isLocalNumericPatch} from '../../../.github/continuum/local-numeric-patch.mjs';

let checks=0;
let result={status:'ABSTAIN',checks:0,counterexample:null};
try{
 const chunks=[];let bytes=0;
 for(;;){const b=Buffer.alloc(4096);const n=fs.readSync(0,b,0,b.length,null);if(!n)break;
  bytes+=n;if(bytes>65536)throw Error('INPUT_LIMIT');chunks.push(b.subarray(0,n));}
 const {suite:raw,source}=JSON.parse(Buffer.concat(chunks).toString('utf8'));
 const suite=numericSuite(raw);
 if(!isLocalNumericPatch({file:suite.file,oldText:source,newText:source}))throw Error('GRAMMAR');
 const signature=/^\s*export\s+function\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(([^()]*)\)/.exec(source);
 if(signature[1]!==suite.exportName||signature[2].split(',').length!==suite.arity)throw Error('EXPORT');
 const script=new vm.Script('('+source.replace(/^\s*export\s+/,'')+')(...__inputs)');
 const evaluate=args=>{
  const v=script.runInNewContext({__inputs:args},{timeout:25,contextCodeGeneration:{strings:false,wasm:false}});
  return typeof v==='number'&&Number.isFinite(v)&&Math.abs(v)<=1e12?v:null;
 };
 const fail=(id,inputs,actual,expected)=>({status:'FAIL',checks,
  counterexample:{id,file:suite.file,inputs,actual,expected}});
 result={status:'PASS',checks:0,counterexample:null};
 for(const c of suite.cases){
  const actual=evaluate(c.args);checks++;
  if(actual===null||actual!==c.expected){result=fail(c.id,c.args,actual,c.expected);break;}
 }
 if(result.status==='PASS')for(const r of suite.relations){
  const a=evaluate(r.left),b=evaluate(r.right);checks++;
  if(a===null||b===null||!(r.relation==='equal'?a===b:a<=b)){
   result=fail(r.id,[...r.left,...r.right],a,b);break;
  }
 }
 result.checks=checks;
}catch{result={status:'ABSTAIN',checks:0,counterexample:null};}
process.stdout.write(JSON.stringify(result)+'\n');
