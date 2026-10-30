/** M3 · NOVEL cohort: failure forms that the training templates never show, written by a different author
 * (Opus) than the policy (PR #243) and sealed before the first run.
 *   member-*   the missing name is used through property access (score.v), not as a bare value or call
 *   arity-*    the missing function takes two arguments (score(8,2)), not one
 *   negative-* NEITHER candidate is right (the acceptance value matches no fix): every arm must report failure
 * Same two strategies and the same source-derived feature as PR #243, so the existing guard can be applied. */
import {validateFixture} from './policy.mjs';
const names=Object.freeze([['scores','score'],['rates','rate'],['sizes','size'],['limits','limit'],['steps','step'],['units','unit']]);
const families=Object.freeze(['member-local','member-import','arity-local','arity-import','negative-local','negative-import']);

function build(label,index) {
 const [local,missing]=names[(index*2+families.indexOf(label))%names.length];
 const n=11+index*4;
 const member=label.startsWith('member')||label==='negative-local';
 const helperAvailable=label.endsWith('-import');
 const source=member
  ?'const '+local+'={v:'+n+'};\nexport function amount(){return '+missing+'.v+5;}\n'
  :'function '+local+'(x,y){return x*y+'+n+';}\nexport function amount(){return '+missing+'(8,2);}\n';
 const helper=!helperAvailable?'export const unrelated=1;\n':member
  ?'export const '+missing+'={v:'+(100+n)+'};\n'
  :'export function '+missing+'(x,y){return x+y+'+n+';}\n';
 const old=member?missing+'.v':missing+'(8,2)', fixed=member?local+'.v':local+'(8,2)';
 if(source.split(old).length!==2)throw Error('FIXTURE_NOT_UNIQUE');
 const typoValue=member?n+5:16+n, importValue=member?105+n:10+n;
 const expected={'member-local':typoValue,'member-import':importValue,'arity-local':typoValue,
  'arity-import':importValue,'negative-local':typoValue+1,'negative-import':importValue+typoValue}[label];
 return validateFixture({id:'novel-'+label+'-'+index,cohort:'novel',contextKey:member?'node.js.value':'node.js.call',
  feature:helperAvailable?'BOTH_AVAILABLE':'NO_HELPER_EXPORT',source,helper,expected,candidates:{
   JS_TYPO_REFERENCE:source.replace(old,fixed),
   JS_MISSING_IMPORT:"import {"+missing+"} from './helper.mjs';\n"+source}});
}
export const NOVEL_FAMILIES=families;
export function novelFixtureSet(){return families.flatMap(f=>Array.from({length:3},(_,i)=>build(f,i)));}
export const familyOf=id=>families.find(f=>id.startsWith('novel-'+f+'-'));
