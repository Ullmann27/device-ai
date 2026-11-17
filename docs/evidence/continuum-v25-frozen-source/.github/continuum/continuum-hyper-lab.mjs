/**
 * Continuum Resonanz / Hyper-Lab.
 * Four isolated experiments. These routines are NOT production chat or repair
 * authorization; promotion requires independent measured holdouts and M4 gate.
 */
import crypto from "node:crypto";
import {RelationalEncoder,D,features} from "./cre-encoder.mjs";
import {critique} from "./repair-critic.mjs";

const hash=x=>crypto.createHash("sha256").update(x).digest("hex");

function assertMatrix(m,n){
 if(!m||m.length!==n*n||!Array.from(m).every(Number.isFinite))
   throw Error("INVALID_MATRIX");
}
export function invertMatrix(matrix,dim=D){
 if(!Number.isSafeInteger(dim)||dim<2||dim>32)throw Error("INVALID_DIMENSION");
 assertMatrix(matrix,dim);
 const a=Float64Array.from(matrix),out=new Float64Array(dim*dim);
 for(let i=0;i<dim;i++)out[i*dim+i]=1;
 for(let col=0;col<dim;col++){
  let row=col;
  for(let k=col+1;k<dim;k++)
   if(Math.abs(a[k*dim+col])>Math.abs(a[row*dim+col]))row=k;
  if(Math.abs(a[row*dim+col])<1e-12)throw Error("SINGULAR_MATRIX");
  if(row!==col)for(let j=0;j<dim;j++){
   [a[row*dim+j],a[col*dim+j]]=[a[col*dim+j],a[row*dim+j]];
   [out[row*dim+j],out[col*dim+j]]=[out[col*dim+j],out[row*dim+j]];
  }
  const pivot=a[col*dim+col];
  for(let j=0;j<dim;j++){a[col*dim+j]/=pivot;out[col*dim+j]/=pivot;}
  for(let i=0;i<dim;i++){
   if(i===col)continue;
   const factor=a[i*dim+col];
   for(let j=0;j<dim;j++){
    a[i*dim+j]-=factor*a[col*dim+j];
    out[i*dim+j]-=factor*out[col*dim+j];
   }
  }
 }
 return out;
}
function multiply(a,b,n=D){
 const v=new Float64Array(n*n);
 for(let r=0;r<n;r++)for(let k=0;k<n;k++){
  const x=a[r*n+k];
  for(let c=0;c<n;c++)v[r*n+c]+=x*b[k*n+c];
 }
 return v;
}
function transpose(a,n=D){
 const b=new Float64Array(n*n);
 for(let i=0;i<n;i++)for(let j=0;j<n;j++)b[i*n+j]=a[j*n+i];
 return b;
}
function deviationFromOrthogonal(r,n=D){
 const gram=multiply(transpose(r,n),r,n);
 let maxError=0;
 for(let i=0;i<n;i++)for(let j=0;j<n;j++)
  maxError=Math.max(maxError,Math.abs(gram[i*n+j]-(i===j?1:0)));
 return maxError;
}
function evaluate(model,examples){
 let correct=0;
 for(const ex of examples){
  const positive=model.score(ex.query,ex.candidates[0]);
  const bestNegative=Math.max(...ex.candidates.slice(1).map(v=>model.score(ex.query,v)));
  if(positive>bestNegative)correct++;
 }
 return correct;
}
/**
 * Cayley ROTATION of the existing Pq projection, not a closed-form solution
 * of the nonlinear contrastive retrieval objective. Preserves E and the
 * singular values of Pq (up to numerical error), not guaranteed better scores.
 * Returning the baseline on a failed independent holdout is mandatory.
 */
export function experimentCayleyRotation({
 baselineBytes,trainingExamples,holdoutExamples,
 stepScale=0.35,negativeWeight=0.65
}={}){
 if(!Number.isFinite(stepScale)||stepScale<=0||stepScale>0.8||
   !Number.isFinite(negativeWeight)||negativeWeight<0||negativeWeight>1||
   !Array.isArray(trainingExamples)||trainingExamples.length<1||trainingExamples.length>64||
   !Array.isArray(holdoutExamples)||holdoutExamples.length<2||holdoutExamples.length>32)
   throw Error("INVALID_CONTRASTIVE_EXPERIMENT");
 const validate=examples=>examples.forEach(ex=>{
  if(typeof ex.query!=="string"||ex.query.length<3||ex.query.length>500||
   !Array.isArray(ex.candidates)||ex.candidates.length<2||ex.candidates.length>12||
   !ex.candidates.every(x=>typeof x==="string"&&x.length>=8&&x.length<=500))
    throw Error("INVALID_CONTRASTIVE_EXAMPLE");
 });
 validate(trainingExamples);validate(holdoutExamples);
 // No identical heldout query may enter the training input set.
 const trainQueries=new Set(trainingExamples.map(x=>x.query));
 if(holdoutExamples.some(x=>trainQueries.has(x.query)))throw Error("HOLDOUT_LEAKAGE");
 const baseline=RelationalEncoder.restore(baselineBytes);
 const candidate=RelationalEncoder.restore(baselineBytes);
 const before=evaluate(baseline,holdoutExamples);
 const c=new Float64Array(D*D);
 for(const ex of trainingExamples){
  const q=RelationalEncoder.normalise(candidate.pool(features(ex.query))).e;
  const positive=candidate.embed(ex.candidates[0],"m");
  const negatives=ex.candidates.slice(1).map(t=>candidate.embed(t,"m"));
  for(let i=0;i<D;i++)for(let j=0;j<D;j++){
   let mean=0;
   for(const n of negatives)mean+=n[i];
   mean/=negatives.length;
   c[i*D+j]+=(positive[i]-negativeWeight*mean)*q[j]/trainingExamples.length;
  }
 }
 const pq=candidate.Pq;
 const a=new Float64Array(D*D);
 for(let i=0;i<D;i++)for(let j=0;j<D;j++){
  let x=0,y=0;
  for(let k=0;k<D;k++){
   x+=c[i*D+k]*pq[j*D+k];y+=pq[i*D+k]*c[j*D+k];
  }
  a[i*D+j]=x-y;
 }
 const norm=Math.hypot(...a);
 if(norm<1e-12)return {accepted:false,reason:"NO_ROTATION_SIGNAL",
  before,after:before,rotationError:0,modelBytes:Buffer.from(baselineBytes)};
 const plus=new Float64Array(D*D),minus=new Float64Array(D*D);
 for(let i=0;i<D;i++)for(let j=0;j<D;j++){
  const k=i*D+j,identity=i===j?1:0;
  plus[k]=identity+0.5*stepScale*a[k]/norm;
  minus[k]=identity-0.5*stepScale*a[k]/norm;
 }
 const rotation=multiply(plus,invertMatrix(minus));
 const rotationError=deviationFromOrthogonal(rotation);
 if(rotationError>1e-8)throw Error("CAYLEY_NUMERIC_FAILURE");
 candidate.Pq=multiply(rotation,pq);
 candidate.steps++;
 // The source model never mutates E, and the output carries the same E bytes.
 const originalE=Buffer.from(baseline.E.buffer,baseline.E.byteOffset,baseline.E.byteLength);
 const afterE=Buffer.from(candidate.E.buffer,candidate.E.byteOffset,candidate.E.byteLength);
 if(!originalE.equals(afterE))throw Error("FROZEN_EMBEDDING_VIOLATION");
 const after=evaluate(candidate,holdoutExamples);
 const accepted=after>before;
 return {accepted,reason:accepted?"HOLDOUT_IMPROVED":"HOLDOUT_NOT_IMPROVED",
  before,after,rotationError,modelBytes:accepted?candidate.save():Buffer.from(baselineBytes)};
}

/**
 * Propose bounded lexical mutations of a diagnostic-aligned code line.
 * This is NOT an AST proof, does not touch files or run tests, and does not
 * treat returned proposals as repairs. Existing repair gate must reverify.
 */
export function proposeLogicMutants({file,sourceText,suspectLine,maxCandidates=8}={}){
 if(typeof file!=="string"||!file.startsWith(".github/continuum/")||
   !/^[A-Za-z0-9_./-]+\.mjs$/.test(file)||file.includes("..")||
   typeof sourceText!=="string"||sourceText.length>30_000||
   !Number.isSafeInteger(suspectLine)||suspectLine<1||
   !Number.isSafeInteger(maxCandidates)||maxCandidates<1||maxCandidates>12)
   throw Error("INVALID_MUTATION_INPUT");
 const lines=sourceText.split("\n");
 if(suspectLine>lines.length)throw Error("INVALID_MUTATION_LINE");
 const line=lines[suspectLine-1];
 // This narrow grammar excludes quoted text and comments; no arbitrary
 // string replacement over source code, HTML, tests or regex literals.
 const match=/^(\s*return\s+)([A-Za-z_$][A-Za-z0-9_$]*)(\s*)(>|>=|<|<=)(\s*)(-?\d+)(\s*;\s*)$/.exec(line);
 if(!match)return [];
 const substitutions={">":[">=","<"],">=":[">"],"<":["<="],"<=":["<"]};
 const out=[];
 for(const operator of substitutions[match[4]]??[]){
  const updated=lines.slice();
  updated[suspectLine-1]=match[1]+match[2]+match[3]+operator+match[5]+match[6]+match[7];
  const candidate={strategy:"AST_LOGIC_MICRO_MUTATION",prior:0.5,
   edits:[{file,oldText:sourceText,newText:updated.join("\n")}]};
  const judge=critique(candidate);
  if(judge.approved)out.push({operator,proposal:candidate,review:judge,
   verifiedByTests:false,acceptedByMainGate:false});
  if(out.length>=maxCandidates)break;
 }
 return out;
}

/**
 * Lossless duplicate plan, not destructive "holographic" compression.
 * Only identical full text and matching trust metadata can deduplicate.
 * No deletions, probabilistic merging or inflated source confidence.
 */
export function planProofConsolidation(beliefs){
 if(!Array.isArray(beliefs)||beliefs.length>3000)throw Error("INVALID_BELIEFS");
 const groups=new Map();
 for(const b of beliefs){
  if(!b||typeof b.text!=="string"||b.text.length<1||
   b.text.length>2000||typeof b.sourceHash!=="string"||
   !/^[a-f0-9]{64}$/.test(b.sourceHash)||typeof b.trustClass!=="string")
    throw Error("INVALID_BELIEF");
  const id=hash(Buffer.from(b.text+"\u0000"+b.sourceHash+"\u0000"+b.trustClass,"utf8"));
  let row=groups.get(id);
  if(!row){row={identity:id,sourceHash:b.sourceHash,trustClass:b.trustClass,
   text:b.text,instanceIds:[]};groups.set(id,row);}
  if(typeof b.id!=="string"||b.id.length>180)throw Error("INVALID_BELIEF_ID");
  row.instanceIds.push(b.id);
 }
 return {uniqueGroups:groups.size,originalCount:beliefs.length,
  duplicatesFound:beliefs.length-groups.size,groups:[...groups.values()],
  writesPerformed:false,removedSourceEvidence:false};
}

/** Pure, format-preserving 16D conditioner for experimental CDX1 retraining.
 * Existing paired CDX1 weights were trained on SWB1-only conditions and
 * MUST NOT be used with this new distribution without retraining/auditing.
 */
export function bimodalConditioner16(swb1,cre1,evidence,question){
 if(!swb1||typeof swb1.encode!=="function"||
  !cre1||typeof cre1.embed!=="function"||
  typeof evidence!=="string"||typeof question!=="string"||
  !evidence.trim()||!question.trim()||evidence.length>160||question.length>160)
   throw Error("INVALID_BIMODAL_CONDITION");
 const lex=swb1.encode(evidence+"\n"+question);
 const q=cre1.embed(question,"q"),m=cre1.embed(evidence,"m");
 if(lex.length!==16||q.length!==32||m.length!==32)
  throw Error("INVALID_EMBEDDING_DIMENSION");
 const result=new Float64Array(16);
 for(let k=0;k<8;k++)result[k]=Math.tanh((lex[2*k]+lex[2*k+1])*0.5);
 for(let k=0;k<8;k++){
  let dot=0;
  for(let j=0;j<4;j++)dot+=q[4*k+j]*m[4*k+j];
  result[8+k]=Math.tanh(2.5*dot);
 }
 if(!result.every(x=>Number.isFinite(x)&&x>=-1&&x<=1))
  throw Error("INVALID_DECODER_CONDITION");
 return result;
}
