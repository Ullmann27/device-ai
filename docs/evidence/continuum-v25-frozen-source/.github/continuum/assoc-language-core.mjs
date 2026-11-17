// NF-5 / NF-6a: a learning LanguageCore for the CCC1 reasoner. It links German goal words to the English parts of
// code identifiers, from two verifiable sources only:
//  1. the owner's Markdown documentation, where German sentences name identifiers in backticks
//     (pointwise mutual information over paragraphs, kept when a pair occurs at least twice);
//  2. receipts the independent verifier CONFIRMED (+1) or REFUTED (−0.5) - receipt-gated learning.
// Every receipt update journals the previous values, so rollback restores them bit-exactly (a wrong lesson is reversible).
// learnFromProposal exists only as the comparison condition "consolidate everything" (no verification).
import {tokens,LexicalLanguageCore} from './ccc1-interpreter.mjs';

/** Lower-case parts of an identifier or module name: isOwnerComment -> is, owner, comment; sha256Hex -> sha256, hex. */
export function identifierParts(name){
 return String(name).replace(/\.mjs$/,'').replace(/^.*\//,'').replace(/([a-z0-9])([A-Z])/g,'$1 $2')
  .split(/[^A-Za-z0-9]+/).map(p=>p.toLowerCase()).filter(p=>p.length>1);
}

const BACKTICK=/`([^`\n]{2,80})`/g;

export class AssocLanguageCore{
 constructor({lambda=.5,confirm=1,refute=-.5}={}){
  this.id='assoc-v1';this.lambda=lambda;this.confirm=confirm;this.refute=refute;
  this.lexical=new LexicalLanguageCore();this.table=new Map();this.journal=[];
 }
 get(g,e){return this.table.get(g)?.get(e)??0;}
 #add(g,e,d){
  if(!this.table.has(g))this.table.set(g,new Map());
  const row=this.table.get(g);const v=(row.get(e)??0)+d;
  if(Math.abs(v)<1e-12){row.delete(e);if(!row.size)this.table.delete(g);}else row.set(e,v);
 }

 /** Unsupervised associations from documentation paragraphs (German words around backticked identifiers). */
 learnFromDocs(texts){
  const cg=new Map(),ce=new Map(),cge=new Map();let n=0;
  for(const text of texts)for(const para of String(text).split(/\n\s*\n/)){
   const ids=[...para.matchAll(BACKTICK)].map(m=>m[1]).filter(s=>/^[A-Za-z_./-][\w./-]*$/.test(s));
   if(!ids.length)continue;
   const words=new Set(tokens(para.replace(BACKTICK,' ')));
   const parts=new Set(ids.flatMap(identifierParts));
   if(!words.size||!parts.size)continue;
   n++;
   for(const g of words)cg.set(g,(cg.get(g)??0)+1);
   for(const e of parts)ce.set(e,(ce.get(e)??0)+1);
   for(const g of words)for(const e of parts){const k=g+'\0'+e;cge.set(k,(cge.get(k)??0)+1);}
  }
  let pairs=0;
  for(const [k,c] of cge){
   if(c<2)continue;
   const [g,e]=k.split('\0');
   const pmi=Math.log(c*n/(cg.get(g)*ce.get(e)));
   if(pmi>0){this.#add(g,e,pmi);pairs++;}
  }
  return {paragraphs:n,pairs};
 }

 #learn(goalText,candidate,delta,source){
  const goal=new Set(tokens(goalText)),parts=new Set([...identifierParts(candidate.export),...identifierParts(candidate.module)]);
  const changes=[];
  for(const g of goal)for(const e of parts){changes.push([g,e,this.table.get(g)?.get(e)]);this.#add(g,e,delta);}
  this.journal.push({source,candidate:candidate.module+'#'+candidate.export,changes});
  return changes.length;
 }
 /** Receipt-gated learning: only verdicts of the independent verifier change the table. */
 learnFromReceipt({goalText,candidate,verdict}){
  if(verdict==='CONFIRMED')return this.#learn(goalText,candidate,this.confirm,'receipt');
  if(verdict==='REFUTED')return this.#learn(goalText,candidate,this.refute,'receipt');
  return 0;                                                   // INCONCLUSIVE teaches nothing
 }
 /** Comparison condition only: treats the reasoner's own proposal as if it were true (no verification). */
 learnFromProposal({goalText,candidate}){return this.#learn(goalText,candidate,this.confirm,'proposal');}
 /** Exact undo of the last [count] updates. */
 rollback(count=1){
  for(let i=0;i<count&&this.journal.length;i++)for(const [g,e,old] of this.journal.pop().changes.reverse()){
   if(old===undefined){this.table.get(g)?.delete(e);if(this.table.get(g)?.size===0)this.table.delete(g);}
   else{if(!this.table.has(g))this.table.set(g,new Map());this.table.get(g).set(e,old);}
  }
 }
 snapshot(){return JSON.stringify([...this.table].map(([g,row])=>[g,[...row].sort()]).sort());}

 async rank(goalText,candidates){
  const lex=await this.lexical.rank(goalText,candidates);
  const goal=[...new Set(tokens(goalText))];
  return candidates.map((c,i)=>{
   const parts=[...new Set([...identifierParts(c.export),...identifierParts(c.module)])];
   let s=0;
   for(const g of goal){let best=0;for(const e of parts)best=Math.max(best,this.get(g,e));s+=best;}
   return lex[i]+this.lambda*s/Math.sqrt(1+parts.length);
  });
 }
}
