/**
 * Continuum Atlas: virtual evidence memory rather than copying the web.
 *
 * Uses the existing bounded public metadata adapters (Wikipedia, OpenAlex,
 * Crossref). No browser, crawling, private endpoints, LLM or external paid API.
 * Persists only URL/title/query digests, not article bodies or training labels.
 * Each bibliographic lead remains UNVERIFIED_WEB_LEAD until separately audited.
 */
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import net from 'node:net';
import {pathToFileURL} from 'node:url';
import {createPublicResearchFunction} from '../../backend/src/public-research.js';
import {git} from './repair-loop.mjs';

const MAX_ENTRIES=96;
const MAX_FILE_BYTES=190_000;
const MIN_INTERVAL_MS=4*60*60*1000;
const HEX40=/^[0-9a-f]{40}$/;
const HEX64=/^[0-9a-f]{64}$/;
const DEFAULT_STATE=path.join(os.homedir(),'.local','state','device-ai-continuum');
const hash=data=>crypto.createHash('sha256').update(data).digest('hex');

export const ATLAS_RESEARCH_TOPICS=Object.freeze([
 {id:'android',query:'Android Kotlin app architecture testing WorkManager lifecycle'},
 {id:'ios',query:'Apple Swift iOS app architecture concurrency testing accessibility'},
 {id:'cross-platform',query:'Flutter Dart cross platform mobile test architecture'},
 {id:'game-development',query:'Godot game engine export mobile app design testing'},
 {id:'secure-networks',query:'secure application networks zero trust threat modeling authentication'},
 {id:'financial-systems',query:'financial systems secure software engineering audit trail transaction consistency'},
 {id:'local-inference',query:'on device small language models quantization inference benchmarking'},
 {id:'software-repair',query:'automated program repair differential testing independent verification'},
 {id:'accessibility',query:'mobile user interface accessibility inclusive design WCAG'},
 {id:'systems',query:'reliable distributed systems fault tolerant data storage formal methods'}
].map(x=>Object.freeze(x)));

function targetPaths(root,state){
 const src=fs.realpathSync(root),dest=path.resolve(state);
 if(dest===src||dest.startsWith(src+path.sep)||src.startsWith(dest+path.sep))
  throw Error('Atlas state must be outside repository checkout');
 const head=git(src,['rev-parse','HEAD']).stdout.trim();
 if(!HEX40.test(head))throw Error('Atlas needs a pinned Git HEAD');
 return {src,dest,head};
}
function fresh(){
 return {schema:'continuum-atlas',version:1,sequence:0,lastResearchedAt:null,
  entries:[],integrityHash:''};
}
function serialize(record){
 const copy={...record};
 delete copy.integrityHash;
 return JSON.stringify(copy);
}
function seal(record){return {...record,integrityHash:hash(serialize(record))};}
function verifiedDocument(doc){
 if(!doc||doc.schema!=='continuum-atlas'||doc.version!==1||
  !Number.isSafeInteger(doc.sequence)||doc.sequence<0||
  !Array.isArray(doc.entries)||doc.entries.length>MAX_ENTRIES||
  !(doc.lastResearchedAt===null||Number.isSafeInteger(doc.lastResearchedAt))||
  !HEX64.test(doc.integrityHash||'')||hash(serialize(doc))!==doc.integrityHash)
  throw Error('ATLAS_INTEGRITY_REJECTED');
 for(const x of doc.entries){
  if(!ATLAS_RESEARCH_TOPICS.some(d=>d.id===x.topic)||
   !HEX64.test(x.id)||!HEX40.test(x.head)||
   typeof x.title!=='string'||x.title.length<6||x.title.length>180||
   typeof x.url!=='string'||x.url.length>950||
   typeof x.query!=='string'||x.query.length>220||
   x.trust!=='UNVERIFIED_WEB_LEAD'||x.ownerConfirmed!==false||
   !Number.isSafeInteger(x.observedAt)||x.observedAt<0||
   !validSource(x.url))throw Error('ATLAS_SOURCE_REJECTED');
 }
 return doc;
}
function validSource(address){
 try{
  const u=new URL(address);
  const name=u.hostname.toLowerCase();
  return u.protocol==='https:'&&!u.username&&!u.password&&!u.port&&
   net.isIP(name)===0&&name.includes('.')&&
   !name.endsWith('.local')&&!name.endsWith('.internal')&&
   !['localhost','metadata.google.internal'].includes(name);
 }catch{return false}
}
function load(file){
 if(!fs.existsSync(file))return seal(fresh());
 const st=fs.lstatSync(file);
 if(!st.isFile()||st.isSymbolicLink()||st.size<1||st.size>MAX_FILE_BYTES)
  throw Error('ATLAS_STORAGE_UNSAFE');
 return verifiedDocument(JSON.parse(fs.readFileSync(file,'utf8')));
}
function save(file,record){
 const data=Buffer.from(JSON.stringify(seal(record),null,2)+'\n');
 if(data.length>MAX_FILE_BYTES)throw Error('ATLAS_STORAGE_LIMIT');
 fs.mkdirSync(path.dirname(file),{recursive:true,mode:0o700});
 const temp=file+'.tmp-'+process.pid+'-'+crypto.randomBytes(5).toString('hex');
 let fd;
 try {
  fd=fs.openSync(temp,'wx',0o600);
  fs.writeFileSync(fd,data);fs.fsyncSync(fd);
  fs.closeSync(fd);fd=undefined;
  fs.renameSync(temp,file);
 }finally{
  if(fd!==undefined)fs.closeSync(fd);
  fs.rmSync(temp,{force:true});
 }
}
function sourceRecord(topic,cite,head,at){
 if(!cite||typeof cite.title!=='string'||!validSource(cite.url)||
    cite.title.trim().length<6)return null;
 const title=cite.title.replace(/[\u0000-\u001f\u007f]/g,' ').trim().slice(0,180);
 const url=new URL(cite.url);url.hash='';
 if(url.href.length>950)return null;
 return {id:hash(topic.id+'\0'+url.href),topic:topic.id,
  query:topic.query,head,title,url:url.href,observedAt:at,
  trust:'UNVERIFIED_WEB_LEAD',ownerConfirmed:false};
}

/**
 * Bounded, opt-in public research cycle. One fixed query per cycle.
 * Lockout protects free APIs from repeated polling; even failures are cooled.
 */
export async function researchAtlasOnce({
 root,state=DEFAULT_STATE,research=createPublicResearchFunction(),
 now=Date.now,minIntervalMs=MIN_INTERVAL_MS
}={}){
 if(!Number.isSafeInteger(minIntervalMs)||minIntervalMs<60_000||
    minIntervalMs>7*24*60*60*1000)throw Error('ATLAS_INVALID_INTERVAL');
 const {src,dest,head}=targetPaths(root,state);
 if(git(src,['status','--porcelain']).stdout.trim())
  throw Error('Atlas refuses dirty checkout');
 fs.mkdirSync(dest,{recursive:true,mode:0o700});
 const lock=path.join(dest,'continuum-atlas.lock');
 let fd;
 try{fd=fs.openSync(lock,'wx',0o600);}
 catch(e){if(e.code==='EEXIST')return {status:'BUSY',added:0};throw e;}
 try{
  const file=path.join(dest,'continuum-atlas-v1.json');
  const current=load(file);
  const at=now();
  if(!Number.isSafeInteger(at)||at<0)throw Error('ATLAS_INVALID_CLOCK');
  if(current.lastResearchedAt!==null&&
      at-current.lastResearchedAt<minIntervalMs)
   return {status:'COOLDOWN',added:0,nextAt:current.lastResearchedAt+minIntervalMs};
  const topic=ATLAS_RESEARCH_TOPICS[current.sequence%ATLAS_RESEARCH_TOPICS.length];
  // Persist RUNNING before the network call to prevent a rapid crash retry.
  const started={...current,sequence:current.sequence+1,lastResearchedAt:at};
  save(file,started);
  try{
   const result=await research({query:topic.query,target:{type:'topic',text:topic.id}});
   const leads=Array.isArray(result?.citations)?result.citations.slice(0,8):[];
   const records=leads.map(c=>sourceRecord(topic,c,head,at)).filter(Boolean);
   const deduplicated=new Map(started.entries.map(x=>[x.id,x]));
   for(const entry of records)deduplicated.set(entry.id,entry);
   save(file,{...started,entries:[...deduplicated.values()].slice(-MAX_ENTRIES)});
   return {status:records.length?'RECORDED_LEADS':'NO_VERIFIABLE_METADATA',
    topic:topic.id,added:records.length,head,trust:'UNVERIFIED_WEB_LEAD',
    trainingApproved:false};
  }catch(e){
   return {status:'SOURCE_UNAVAILABLE',topic:topic.id,added:0,
    reason:String(e?.message??e).slice(0,120)};
  }
 }finally{
  fs.closeSync(fd);
  fs.rmSync(lock,{force:true});
 }
}

/** Semantic-lite sparse search across references, never a claim of verified truth. */
export function atlasFind({root,state=DEFAULT_STATE,query,limit=5}={}){
 const {dest}=targetPaths(root,state);
 if(typeof query!=='string'||query.length<2||query.length>160||
    !Number.isSafeInteger(limit)||limit<1||limit>10)
   throw Error('ATLAS_INVALID_QUERY');
 const words=new Set(query.toLocaleLowerCase('de-DE')
   .split(/[^\p{L}\p{N}]+/u).filter(w=>w.length>=3));
 const doc=load(path.join(dest,'continuum-atlas-v1.json'));
 return doc.entries.map(x=>{
  const fields=(x.title+' '+x.query).toLocaleLowerCase('de-DE');
  const score=[...words].filter(w=>fields.includes(w)).length;
  return {score,topic:x.topic,title:x.title,url:x.url,observedAt:x.observedAt,
   trust:x.trust,verified:false};
 }).filter(x=>x.score>0).sort((a,b)=>b.score-a.score||b.observedAt-a.observedAt)
   .slice(0,limit);
}
export function atlasStatus({root,state=DEFAULT_STATE}={}){
 const {dest,head}=targetPaths(root,state);
 const d=load(path.join(dest,'continuum-atlas-v1.json'));
 return {schema:'continuum-atlas-status',version:1,head,
  references:d.entries.length,nextTopic:ATLAS_RESEARCH_TOPICS[d.sequence%ATLAS_RESEARCH_TOPICS.length].id,
  lastResearchedAt:d.lastResearchedAt,
  allSourcesUnverified:d.entries.every(x=>!x.ownerConfirmed&&x.trust==='UNVERIFIED_WEB_LEAD'),
  approvedTrainingSpans:0,offlineModelTraining:false};
}
