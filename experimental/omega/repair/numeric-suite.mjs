/** Frozen, finite numeric observations. Suite authors are trusted evaluators,
 * never the candidate/model. Hashes bind a suite, not its mathematical truth. */
import {canonical,sha256Hex} from '../../../.github/continuum/development-backlog.mjs';
import {isCodingPath} from '../../../.github/continuum/coding-task.mjs';
const exact=(v,keys)=>v&&typeof v==='object'&&!Array.isArray(v)&&Object.keys(v).sort().join(',')===[...keys].sort().join(',');
const finite=v=>typeof v==='number'&&Number.isFinite(v)&&Math.abs(v)<=1e6;
const id=v=>typeof v==='string'&&/^[a-z0-9-]{1,48}$/.test(v);
function freeze(v){if(v&&typeof v==='object'){Object.values(v).forEach(freeze);Object.freeze(v);}return v;}
export function numericSuite(input){
 const invalid=()=>{throw Error('OMEGA_SUITE_INVALID');};
 if(!exact(input,['schema','version','sourceCommit','file','exportName','arity','cases','relations'])||
    input.schema!=='omega-numeric-suite'||input.version!==1||!/^[a-f0-9]{40}$/.test(input.sourceCommit??'')||
    !isCodingPath(input.file)||!input.file.endsWith('.mjs')||
    !/^[A-Za-z_][A-Za-z0-9_]{0,40}$/.test(input.exportName??'')||
    !Number.isSafeInteger(input.arity)||input.arity<1||input.arity>4||
    !Array.isArray(input.cases)||input.cases.length>256||
    !Array.isArray(input.relations)||input.relations.length>128||input.cases.length+input.relations.length===0)invalid();
 const args=a=>Array.isArray(a)&&a.length===input.arity&&a.every(finite);
 for(const c of input.cases)if(!exact(c,['id','args','expected'])||!id(c.id)||!args(c.args)||!finite(c.expected))invalid();
 for(const r of input.relations)if(!exact(r,['id','left','right','relation'])||!id(r.id)||
  !args(r.left)||!args(r.right)||!['equal','le'].includes(r.relation))invalid();
 const ids=[...input.cases,...input.relations].map(c=>c.id);
 if(new Set(ids).size!==ids.length)invalid();
 return freeze(JSON.parse(canonical(input)));
}
export const numericSuiteHash=suite=>sha256Hex(canonical(numericSuite(suite)));
