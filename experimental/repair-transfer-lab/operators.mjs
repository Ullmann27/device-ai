/** Fault and repair operators on tokens (single-token edits). The SAME edit vocabulary is used to inject a fault
 * and to propose repairs, as in template-based program repair; the lab measures ORDERING and SELECTION among many
 * competing candidates, not whether an unknown fix can be invented. Families:
 *   REL boundary/direction of < <= > >= · EQ === !== == != · LOGIC && || · ARITH binary + - * / · CONST integer ±1
 *   NEG unary '!' removed or inserted */
import {createHash} from 'node:crypto';
export const FAMILIES=Object.freeze(['REL','EQ','LOGIC','ARITH','CONST','NEG']);
const REL_SWAP={'<':['<=','>'],'<=':['<','>='],'>':['>=','<'],'>=':['>','<=']};
const EQ_SWAP={'===':'!==','!==':'===','==':'!=','!=':'=='};
const VALUE_END=t=>t&&(t.type==='ident'||t.type==='num'||t.type==='str'||t.type==='template'||
 (t.type==='keyword'&&['this','null','true','false','undefined'].includes(t.value))||(t.type==='punct'&&(t.value===')'||t.value===']')));
const NEG_INSERT_BEFORE=new Set(['(','&&','||','?',',','=','return','!','if']);   // previous token allows a fresh operand

/** Every single-token edit the vocabulary allows at token k: [{family, from, to, kind, start, end, text}] */
export function editsAt(tokens,k) {
 const t=tokens[k],prev=tokens[k-1],out=[];
 if(!t)return out;
 const replace=(family,to,kind)=>out.push({family,kind,start:t.start,end:t.end,text:to,from:t.value,at:k});
 if(t.type==='punct'){
  if(REL_SWAP[t.value]&&VALUE_END(prev))for(const to of REL_SWAP[t.value])replace('REL',to,'SWAP');
  if(EQ_SWAP[t.value])replace('EQ',EQ_SWAP[t.value],'SWAP');
  if(t.value==='&&')replace('LOGIC','||','SWAP');
  if(t.value==='||')replace('LOGIC','&&','SWAP');
  if(VALUE_END(prev)&&(t.value==='+'||t.value==='-'))replace('ARITH',t.value==='+'?'-':'+','SWAP');
  if(VALUE_END(prev)&&(t.value==='*'||t.value==='/'))replace('ARITH',t.value==='*'?'/':'*','SWAP');
  if(t.value==='!'&&!VALUE_END(prev)&&tokens[k+1]&&(tokens[k+1].type==='ident'||tokens[k+1].value==='('||tokens[k+1].value==='!'))
   out.push({family:'NEG',kind:'REMOVE',start:t.start,end:t.end,text:'',from:'!',at:k});
 }
 if(t.type==='num'&&/^\d{1,6}$/.test(t.value)&&!(prev&&prev.value==='.')){
  const n=Number(t.value);
  replace('CONST',String(n+1),'INC');
  if(n>0)replace('CONST',String(n-1),'DEC');
 }
 if(t.type==='ident'&&prev&&(NEG_INSERT_BEFORE.has(prev.value))&&!(tokens[k+1]&&tokens[k+1].value==='=>')&&
    !(tokens[k+1]&&tokens[k+1].value==='=')&&!(prev.value==='!'))
  out.push({family:'NEG',kind:'INSERT',start:t.start,end:t.start,text:'!',from:'',at:k});
 return out;
}
export function applyEdit(src,e){return src.slice(0,e.start)+e.text+src.slice(e.end);}
export const editId=e=>createHash('sha256').update([e.start,e.end,e.text,e.family,e.kind].join('|')).digest('hex').slice(0,16);
/** Candidates within a line window [lo,hi] of the (possibly faulted) source, deduplicated by resulting text. */
export function candidatesInWindow(tokens,src,lo,hi) {
 const seen=new Set(),out=[];
 for(let k=0;k<tokens.length;k++){
  if(tokens[k].line<lo||tokens[k].line>hi)continue;
  for(const e of editsAt(tokens,k)){
   const patched=applyEdit(src,e),h=createHash('sha256').update(patched).digest('hex');
   if(seen.has(h)||patched===src)continue;
   seen.add(h);out.push({...e,id:editId(e),patchedSha256:h,line:tokens[k].line});
  }
 }
 return out;
}
