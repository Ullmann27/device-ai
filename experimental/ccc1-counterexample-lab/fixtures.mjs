import {validateFixture} from './policy.mjs';
const families=Object.freeze([
 ['value-local','node.js.value',false,false],
 ['value-import','node.js.value',true,true],
 ['value-deceptive','node.js.value',true,false],
 ['call-local','node.js.call',false,false],
 ['call-import','node.js.call',true,true],
 ['call-deceptive','node.js.call',true,false]
]);
const names=Object.freeze([
 ['counts','count'],['prices','price'],['totals','total'],
 ['formats','format'],['parses','parse'],['checks','check'],
 ['loads','load'],['plans','plan'],['values','value']
]);
function createFixture(family,index,cohort) {
 const [label,contextKey,helperAvailable,expectedImport]=family;
 const [local,missing]=names[(index+(cohort==='holdout'?4:0))%names.length];
 const n=7+index*3,call=contextKey==='node.js.call';
 const source=call
  ?'function '+local+'(x){return x+'+n+';}\nexport function amount(){return '+missing+'(8);}\n'
  :'const '+local+'='+n+';\nexport function amount(){return '+missing+'+5;}\n';
 const helper=helperAvailable?call
  ?'export function '+missing+'(x){return x*'+n+';}\n'
  :'export const '+missing+'='+(100+n)+';\n'
  :'export const unrelated=1;\n';
 const expected=call?(expectedImport?8*n:8+n):(expectedImport?105+n:n+5);
 const old=call?missing+'(8)':missing+'+5';
 const replacement=call?local+'(8)':local+'+5';
 const typo=source.replace(old,replacement);
 if(typo===source||source.split(old).length!==2)throw Error('FIXTURE_NOT_UNIQUE');
 return validateFixture({id:cohort+'-'+label+'-'+index,cohort,contextKey,
  feature:helperAvailable?'BOTH_AVAILABLE':'NO_HELPER_EXPORT',
  source,helper,expected,candidates:{
   JS_TYPO_REFERENCE:typo,
   JS_MISSING_IMPORT:"import {"+missing+"} from './helper.mjs';\n"+source
  }});
}
export function fixtureSet(cohort) {
 if(cohort!=='training'&&cohort!=='holdout')throw Error('UNKNOWN_COHORT');
 return families.flatMap(f=>Array.from({length:cohort==='training'?3:4},
  (_,i)=>createFixture(f,i,cohort)));
}
