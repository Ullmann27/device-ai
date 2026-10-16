/**
 * Bounded owner-specified Kotlin method synthesis, NOT arbitrary code generation.
 * A token/symbol-index operator constructs a new delegating function from an
 * existing non-overloaded, one-argument method. Compilation is a separate gate.
 * Model output, webpage text and historical experiences confer no authority.
 */
import crypto from 'node:crypto';
import {indexFile} from './code-analyzer.mjs';
import {tokenize,isKeyword} from './kotlin-tokenizer.mjs';
import {critique} from './repair-critic.mjs';

const IDENT=/^[A-Za-z_][A-Za-z0-9_]{0,63}$/;
const TYPES=new Set(['Int','Long','Boolean','String']);
const FILE=/^(?:app\/)?src\/main\/(?:java|kotlin)\/[A-Za-z0-9_./-]+\.kt$/;
const hash=text=>crypto.createHash('sha256').update(text).digest('hex');

function validName(name){return typeof name==='string'&&IDENT.test(name)&&!isKeyword(name);}
function offsetAt(tokens,source,t){
 const start=source.split('\n').slice(0,t.line-1).reduce((n,l)=>n+l.length+1,0);
 return start+t.col-1;
}
function refusal(reason){return {candidate:null,reason};}

/**
 * Strict contract: accepted owner IMPLEMENT task; explicit target file, class,
 * source method, new method, parameter and a 1- or 2-call chain.
 * Supported delegate signature exactly: fun existing(name: Type): Type.
 */
export function proposeKotlinDelegate({files,task}={}){
 if(!task||task.kind!=='IMPLEMENT'||task.source!=='owner'||task.state!=='READY')
  return refusal('OWNER_ACCEPTANCE_REQUIRED');
 const s=task.subject;
 if(!s||s.operation!=='EXPOSE_DELEGATE'||!FILE.test(s.file??'')||
    ![s.className,s.methodName,s.delegateName,s.parameterName].every(validName)||
    !TYPES.has(s.type)||![1,2].includes(s.depth))
  return refusal('INVALID_BOUNDED_OPERATOR_SPEC');
 if(s.methodName===s.delegateName||!Array.isArray(files)||files.length<1||files.length>3000)
  return refusal('AMBIGUOUS_OPERATOR_SPEC');
 const matches=files.filter(f=>f.path===s.file);
 if(matches.length!==1||typeof matches[0].content!=='string'||
    matches[0].content.length>300_000||matches[0].content.includes('\r'))
  return refusal('TARGET_NOT_UNIQUE_OR_UNSUPPORTED');
 const source=matches[0].content;
 const index=indexFile({path:s.file,content:source});
 const classes=index.declarations.filter(d=>d.kind==='class'&&
   d.name===s.className&&d.container===null);
 if(classes.length!==1)return refusal('CLASS_NOT_UNIQUE');
 const q=classes[0].qualified;
 if(index.declarations.some(d=>d.container===q&&d.name===s.methodName))
  return refusal('METHOD_ALREADY_EXISTS');
 const delegates=index.declarations.filter(d=>d.container===q&&
   d.kind==='fun'&&d.name===s.delegateName);
 if(delegates.length!==1)return refusal('DELEGATE_NOT_UNIQUE');
 const toks=tokenize(source,{maxTokens:60000});
 const classOffsets=toks.flatMap((t,i)=>t.type==='kw'&&t.text==='class'&&
   toks[i+1]?.text===s.className&&t.line===classes[0].line?[i]:[]);
 if(classOffsets.length!==1)return refusal('CLASS_TOKEN_NOT_UNIQUE');
 const ci=classOffsets[0];
 // Only simple explicit class bodies; no super-constructor, delegation, object
 // expression, primary-constructor property initializer or inferred AST claim.
 if(toks[ci+2]?.text!=='{')return refusal('UNSUPPORTED_CLASS_HEADER');
 const start=ci+2;
 let depth=0,close=-1;
 for(let i=start;i<toks.length;i++){
  if(toks[i].text==='{')depth++;
  if(toks[i].text==='}'&&--depth===0){close=i;break;}
 }
 if(close<0)return refusal('UNBALANCED_CLASS_BODY');
 const di=toks.findIndex((t,i)=>t.text==='fun'&&
   t.line===delegates[0].line&&toks[i+1]?.text===s.delegateName);
 if(di<0)return refusal('DELEGATE_TOKEN_NOT_FOUND');
 const param=toks[di+3];
 if(toks[di+2]?.text!=='('||param?.type!=='id'||
    toks[di+4]?.text!==':'||toks[di+5]?.text!==s.type||
    toks[di+6]?.text!==')'||toks[di+7]?.text!==':'||
    toks[di+8]?.text!==s.type||!['=','{'].includes(toks[di+9]?.text))
  return refusal('UNSUPPORTED_DELEGATE_SIGNATURE');
 const end=offsetAt(toks,source,toks[close]);
 if(end<0||end>=source.length||source[end]!=='}')return refusal('INVALID_TOKEN_SPAN');
 const expression=s.depth===2
  ?s.delegateName+'('+s.delegateName+'('+s.parameterName+'))'
  :s.delegateName+'('+s.parameterName+')';
 const eol=source.lastIndexOf('\n',end-1)+1;
 const indent=source.slice(eol,end);
 const newMethod='fun '+s.methodName+'('+s.parameterName+': '+s.type+
   '): '+s.type+' = '+expression;
 let insertion,at;
 if(/^[ \t]*$/.test(indent)){
  at=eol;
  insertion=indent+'    '+newMethod+'\n';
 }else{
  // Inline-body formatting is supported only via an explicit extra newline.
  at=end;
  insertion='\n    '+newMethod+'\n';
 }
 const updated=source.slice(0,at)+insertion+source.slice(at);
 const proposal={id:hash(task.id+'\0'+s.file+'\0'+newMethod).slice(0,16),
  strategy:'AST_EXPOSE_DELEGATE',description:'Expose bounded owner-requested Kotlin delegation',
  diagnostic:'owner:'+task.id,prior:0.5,reviewRequired:true,
  edits:[{file:s.file,oldText:source,newText:updated}]};
 const review=critique(proposal);
 if(!review.approved)return refusal('CRITIC_REJECTED: '+review.reasons.join('; '));
 return {candidate:{...proposal,critic:review},reason:null};
}
