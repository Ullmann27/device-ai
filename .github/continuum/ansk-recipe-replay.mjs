import {sha256} from './repair-diagnostics.mjs';
import {tokenize} from './kotlin-tokenizer.mjs';
import {JDK_IMPORTS,insertImport,hunk,applyEdits} from './repair-strategies.mjs';
import {critique} from './repair-critic.mjs';
import {assertAnskSource,verifyAnskRecipeReceipt} from './ansk-lab-contract.mjs';

/**
 * Read-only replay of a quarantined research recipe on a DIFFERENT Kotlin
 * source file. A receipt is only an index hint: it is not trusted proof and
 * cannot authorize a write, model promotion or automatic tool registration.
 *
 * Existing deterministic import insertion, hunk uniqueness and the repair
 * critic remain responsible for the candidate's actual content.
 */
export function planAnskShadowReplay({
 recipe,sourceFile,source,diagnostic
}={}){
 const reject=reason=>({status:'REJECTED',reason,authority:'SHADOW_ONLY',
   candidate:null,autoApplyAuthorized:false});
 const receipt=verifyAnskRecipeReceipt(recipe);
 if(!receipt.valid)return reject(receipt.reason);
 try{assertAnskSource(sourceFile,recipe.importFqn);}
 catch{return reject('UNSAFE_REPLAY_TARGET');}
 if(typeof source!=='string'||source.length<1||source.length>250_000)
  return reject('INVALID_SOURCE_BYTES');
 if(sourceFile===recipe.sourceFile)return reject('REPLAY_REQUIRES_DIFFERENT_SOURCE');
 const symbol=recipe.importFqn.split('.').at(-1);
 if(Object.values(JDK_IMPORTS).filter(s=>s===recipe.importFqn).length!==1)
  return reject('UNKNOWN_IMPORT_MAPPING');
 if(!diagnostic||diagnostic.file!==sourceFile||
    diagnostic.kind!=='UNRESOLVED_REFERENCE'||
    diagnostic.symbol!==symbol||
    !/^[0-9a-f]{16}$/.test(diagnostic.fingerprint??''))
  return reject('INCOMPATIBLE_DIAGNOSTIC');
 if(new RegExp('^import\\s+[A-Za-z0-9_.]+\\.'+symbol+'\\s*$','m').test(source))
  return reject('SYMBOL_ALREADY_IMPORTED');
 let relevant=false;
 try{
  relevant=tokenize(source,{maxTokens:100_000}).some(t=>
    t.type==='id'&&t.text===symbol);
 }catch{return reject('INVALID_KOTLIN_TOKEN_STREAM');}
 if(!relevant)return reject('SYMBOL_NOT_USED_AS_CODE');
 const next=insertImport(source,recipe.importFqn);
 if(next===null)return reject('IMPORT_ALREADY_PRESENT');
 const edit=hunk(sourceFile,source,next);
 if(!edit||edit.oldText===source)return reject('NO_MINIMAL_HUNK');
 const candidate={
  id:sha256('ANSK_SHADOW_REPLAY\u0000'+recipe.recipeSha256+'\u0000'+
    diagnostic.fingerprint+'\u0000'+JSON.stringify([edit])).slice(0,16),
  strategy:'KOTLIN_MISSING_IMPORT',
  diagnostic:diagnostic.fingerprint,
  diagnosticKind:diagnostic.kind,
  description:'ANSK shadow import-recipe hint (independent compilation required)',
  prior:0.5,edits:[edit]
 };
 const verdict=critique(candidate);
 if(!verdict.approved)return reject('CRITIC_REJECTED');
 let patched;
 try{patched=applyEdits(p=>p===sourceFile?source:null,candidate.edits).get(sourceFile);}
 catch{return reject('NON_UNIQUE_EDIT');}
 if(patched!==next)return reject('NON_DETERMINISTIC_REPLAY');
 return {status:'SHADOW_CANDIDATE_ONLY',reason:null,
   authority:'SHADOW_ONLY',recipeSha256:recipe.recipeSha256,
   candidate,autoApplyAuthorized:false,
   modelTrainingAuthorized:false,independentTestRequired:true};
}
