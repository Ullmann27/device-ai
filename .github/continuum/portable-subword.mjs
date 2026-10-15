// Byte-compatible with Kotlin TrainableSubwordEncoder / LearningSnapshot SWB1.
// Original random weights; no model downloads or third-party inference.
import crypto from 'node:crypto';
export const sha256=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const D=16, B=256, SCALE=2.5, CLIP=8;

export const MAGIC_SWB1=0x53574231, MAGIC_SWB2=0x53574232;
/** Experimental weighted feature map. Legacy buckets() remains unchanged. */
export const SWB2_STOP_WORDS=new Set(('was wie weißt über und noch dazu darüber das die der ist ich mir mein meine du bitte kannst erzähl erkläre welche dieses thema '+
 'den dem des ein eine einer eines einem einen mit von vom zum zur für auf aus bei sich sie er es wir ihr sind wird werden wurde wurden hat haben '+
 'im in an am zu oder als auch nicht nur man durch unter zwischen').split(' '));
function specificityBucket(value){
 let h=0x811c9dc5;for(const byte of Buffer.from(value,'utf8'))h=Math.imul(h^byte,0x01000193)>>>0;return h%B;
}
export function weightedBuckets(text){
  if(typeof text!=='string'||text.length<1||text.length>2048)throw Error('Invalid encoder text');
  const normalized=text.toLowerCase().replace(/[^\p{L}\p{N}]+/gu,' ').trim().slice(0,512);
  if(!normalized)throw Error('Empty encoder text');
  const weights=new Map();
  const dollar=String.fromCharCode(36);
  const suffixes=['en','er','es','em','te','st','un','ch'];
  for(const token of normalized.split(' ').filter(Boolean)){
    const factor=token.length<3||SWB2_STOP_WORDS.has(token)?0.2:1;
    const padded='^'+token+dollar;
    for(const n of [4,3,2]){
      for(let i=0;i<=padded.length-n;i++){
        const gram=padded.slice(i,i+n);
        const order=n===4?1.8:n===3?1.15:0.35;
        const pos=gram.startsWith('^')?1.25:suffixes.some(x=>gram.endsWith(x+dollar))?0.45:1;
        const id=specificityBucket(gram),weight=factor*order*pos;
        if(weights.has(id)){if(weight>weights.get(id))weights.set(id,weight);}
        else if(weights.size<128)weights.set(id,weight);
      }
    }
  }
  const sum=[...weights.values()].reduce((a,b)=>a+b,0);
  if(!sum)throw Error('No weighted buckets');
  const inv=1/sum;
  return [...weights].map(([id,weight])=>({id,weight:weight*inv}));
}
const sigmoid=x=>x>=0?1/(1+Math.exp(-x)):Math.exp(x)/(1+Math.exp(x));
const clip=x=>Math.max(-CLIP,Math.min(CLIP,x));
function javaRandom(seed){
  let s=(BigInt(seed)^0x5deece66dn)&((1n<<48n)-1n);
  const next=n=>{s=(s*0x5deece66dn+11n)&((1n<<48n)-1n);return Number(s>>BigInt(48-n));};
  return ()=>((next(26)*134217728)+next(27))/9007199254740992;
}
export function buckets(text){
  if(typeof text!=='string'||text.length<1||text.length>2048)throw Error('Invalid encoder text');
  const normalized=text.toLowerCase().replace(/[^\p{L}\p{N}]+/gu,' ').trim().slice(0,512);
  if(!normalized)throw Error('Empty encoder text');
  const padded='^'+normalized+'$', values=new Set();
  const hash=value=>{let h=0x811c9dc5;for(const byte of Buffer.from(value,'utf8'))h=Math.imul(h^byte,0x01000193)>>>0;return h%B;};
  for(let n=2;n<=4&&values.size<128;n++)for(let i=0;i<=padded.length-n;i++){
    values.add(hash(padded.slice(i,i+n)));if(values.size>=128)break;
  }
  if(!values.size)values.add(hash(padded));
  return [...values];
}
export class SubwordEncoder {
  constructor(seed=73,{version=1}={}){
    if(version!==1&&version!==2)throw Error('Invalid SWB version');this.version=version;
    this.seed=BigInt(seed);this.updates=0;
    const e=javaRandom(this.seed),h=javaRandom(this.seed^0x51a7n);
    this.embeddings=Array.from({length:B*D},()=> (e()*2-1)*.08);
    this.head=Array.from({length:D+1},(_,i)=>i===D?0:(h()*2-1)*.08);
    this.cache=new Map();
  }
  features(text){
    if(!this.cache.has(text)){if(this.cache.size>=1024)this.cache.clear();this.cache.set(text,this.version===2?weightedBuckets(text):buckets(text));}
    return this.cache.get(text);
  }
  encodeBuckets(ids){const z=Array(D).fill(0);if(this.version===2){for(const {id,weight} of ids)for(let i=0;i<D;i++)z[i]+=this.embeddings[id*D+i]*weight;return z.map(x=>Math.tanh(x));}for(const b of ids)for(let i=0;i<D;i++)z[i]+=this.embeddings[b*D+i];return z.map(x=>Math.tanh(x/ids.length));}
  encode(text){return this.encodeBuckets(this.features(text));}
  intentScore(text){const z=this.encode(text);return sigmoid(z.reduce((s,x,i)=>s+x*this.head[i],this.head[D]));}
  similarity(left,right){const a=this.encode(left),b=this.encode(right);return sigmoid(SCALE*a.reduce((s,x,i)=>s+x*b[i],0));}
  updateIntent(ex,rate=.12){
    if(![0,1].includes(ex.label)||!Number.isFinite(rate)||rate<=0||rate>.15)throw Error('Invalid training example/rate');
    const ids=this.features(ex.text),z=this.encodeBuckets(ids),old=this.head.slice();
    const error=sigmoid(z.reduce((s,x,i)=>s+x*old[i],old[D]))-ex.label;
    for(let i=0;i<D;i++)this.head[i]=clip(old[i]-rate*error*z[i]);
    this.head[D]=clip(old[D]-rate*error);
    if(this.version===2){for(const {id,weight} of ids)for(let i=0;i<D;i++)this.embeddings[id*D+i]=clip(this.embeddings[id*D+i]-rate*error*old[i]*(1-z[i]*z[i])*weight);}else{for(const b of ids)for(let i=0;i<D;i++)this.embeddings[b*D+i]=clip(this.embeddings[b*D+i]-rate*error*old[i]*(1-z[i]*z[i])/ids.length);}
    this.updates=Math.min(1000000,this.updates+1);
  }
  updateSemantic(ex,rate=.10){
    if(typeof ex.similar!=='boolean'||!Number.isFinite(rate)||rate<=0||rate>.12)throw Error('Invalid semantic example/rate');
    const ai=this.features(ex.left),bi=this.features(ex.right),a=this.encodeBuckets(ai),b=this.encodeBuckets(bi);
    const error=sigmoid(SCALE*a.reduce((s,x,i)=>s+x*b[i],0))-(ex.similar?1:0);
    const ga=a.map((x,i)=>error*SCALE*b[i]*(1-x*x)),gb=b.map((x,i)=>error*SCALE*a[i]*(1-x*x));
    if(this.version===2){
      for(const {id,weight} of ai)for(let i=0;i<D;i++)this.embeddings[id*D+i]=clip(this.embeddings[id*D+i]-rate*ga[i]*weight);
      for(const {id,weight} of bi)for(let i=0;i<D;i++)this.embeddings[id*D+i]=clip(this.embeddings[id*D+i]-rate*gb[i]*weight);
    }else{
    for(const id of ai)for(let i=0;i<D;i++)this.embeddings[id*D+i]=clip(this.embeddings[id*D+i]-rate*ga[i]/ai.length);
    for(const id of bi)for(let i=0;i<D;i++)this.embeddings[id*D+i]=clip(this.embeddings[id*D+i]-rate*gb[i]/bi.length);
    }
    this.updates=Math.min(1000000,this.updates+1);
  }
  evaluate(examples){
    if(!examples.length)throw Error('Empty evaluation');
    let loss=0,correct=0;
    for(const e of examples){const p=Math.max(1e-9,Math.min(1-1e-9,'label' in e?this.intentScore(e.text):this.similarity(e.left,e.right)));const y='label'in e?e.label:Number(e.similar);loss-=y*Math.log(p)+(1-y)*Math.log(1-p);correct+=Number((p>=.5)===(y===1));}
    return {loss:loss/examples.length,correct,total:examples.length};
  }
  save(){
    const body=Buffer.alloc(24+(B*D+D+1)*8);let o=0;
    body.writeInt32BE(this.version===2?MAGIC_SWB2:MAGIC_SWB1,o);o+=4;body.writeBigInt64BE(this.seed,o);o+=8;body.writeInt32BE(this.updates,o);o+=4;
    for(const row of [this.embeddings,this.head]){body.writeInt32BE(row.length,o);o+=4;for(const v of row){if(!Number.isFinite(v)||Math.abs(v)>8)throw Error('Nonfinite weights');body.writeDoubleBE(v,o);o+=8;}}
    return Buffer.concat([body,Buffer.from(sha256(body),'hex')]);
  }
  static createExperimentalSwb2(seed=73){return new SubwordEncoder(seed,{version:2});}
  upgradeToSwb2(){const next=SubwordEncoder.restore(this.save());next.version=2;next.cache.clear();return next;}
  static restore(bytes){
    const data=Buffer.from(bytes);if(data.length!==32960)throw Error('Invalid SWB1 size');
    const body=data.subarray(0,-32);if(!crypto.timingSafeEqual(Buffer.from(sha256(body),'hex'),data.subarray(-32)))throw Error('SWB1 checksum mismatch');
    let o=0;const magic=body.readInt32BE(o);if(magic!==MAGIC_SWB1&&magic!==MAGIC_SWB2)throw Error('Invalid SWB version');o+=4;
    const m=new SubwordEncoder(body.readBigInt64BE(o),{version:magic===MAGIC_SWB2?2:1});o+=8;m.updates=body.readInt32BE(o);o+=4;
    if(m.updates<0||m.updates>1000000)throw Error('Invalid updates');
    for(const key of ['embeddings','head']){const size=body.readInt32BE(o);o+=4;if(size!==m[key].length)throw Error('Invalid dimensions');m[key]=Array.from({length:size},()=>{const x=body.readDoubleBE(o);o+=8;if(!Number.isFinite(x)||Math.abs(x)>8)throw Error('Invalid weights');return x;});}
    return m;
  }
}
