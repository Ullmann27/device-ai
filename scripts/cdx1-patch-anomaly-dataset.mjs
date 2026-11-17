#!/usr/bin/env node
// Experiment D dataset (pre-registered: docs/research/CDX1-PATCH-ANOMALY-PREREG-20261023.md). Real hunks from the git
// history of .github/continuum/*.mjs up to a pinned commit, seven patch groups and the cheap parser verdict of every
// fragment (git apply --check, then node --check on parent + hunk). Deterministic for --until and --seed; writes JSON
// Lines (one header, then items, then TRAIN texts) and prints the SHA-256 of the file. Research only: nothing here
// changes the repository, and no fragment is ever applied outside a temporary directory.
// Usage: node scripts/cdx1-patch-anomaly-dataset.mjs --until <sha> --out <file.jsonl> [--seed 20261023] [--max-commits 400]
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';

export const GROUPS=Object.freeze(['VALID','SYNTAX','MISSING_TOKEN','TRUNCATED','ODD_BYTES','SEMANTIC','INCOMPLETE_HUNK']);
const ODD=['\u0001','\u0002','\u0007','\u001b',' ','­','​','﻿','�','Ã','¤','§'];
const SWAPS=[[/===/,'!=='],[/!==/,'==='],[/&&/,'||'],[/\|\|/,'&&'],[/<=/,'<'],[/>=/,'>'],[/\btrue\b/,'false'],
 [/\bfalse\b/,'true'],[/Math\.max\b/,'Math.min'],[/Math\.min\b/,'Math.max'],[/\.length-1\b/,'.length']];
const TRAIN_BYTES=200_000;
const bytes=s=>Buffer.byteLength(s,'utf8');
const sha256=s=>createHash('sha256').update(s).digest('hex');

/** mulberry32: small, seeded, platform-independent. */
export function rng(seed){
 let a=seed>>>0;
 return ()=>{a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return ((t^t>>>14)>>>0)/4294967296;};
}
export const splitOf=p=>{const x=parseInt(sha256(p).slice(0,8),16)/2**32;return x<0.6?'TRAIN':x<0.8?'CAL':'TEST';};

/** Hunks of one `git diff -U2` output: {path, header, body[]} with the section heading dropped from the header. */
export function parseHunks(diff){
 const out=[];let file=null,hunk=null;
 for(const line of diff.split('\n')){
  const f=/^diff --git a\/(\S+) b\/(\S+)$/.exec(line);
  if(f){file=f[1]===f[2]?f[1]:null;hunk=null;continue;}
  const h=/^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@/.exec(line);
  if(h&&file){hunk={path:file,header:'@@ -'+h[1]+(h[2]!==undefined?','+h[2]:'')+' +'+h[3]+(h[4]!==undefined?','+h[4]:'')+' @@',body:[]};out.push(hunk);continue;}
  if(hunk&&/^[ +\-\\]/.test(line))hunk.body.push(line);
  else if(!/^(index |--- |\+\+\+ |new file|deleted file|similarity|rename|old mode|new mode)/.test(line))hunk=null;
 }
 return out.filter(x=>!x.body.some(l=>l.startsWith('\\')));
}
export const hunkText=h=>h.header+'\n'+h.body.join('\n')+'\n';

/** One corrupted variant of a hunk for a group, or null when the group does not apply to this hunk. */
export function corrupt(h,group,r){
 const pick=xs=>xs[Math.floor(r()*xs.length)];
 const added=h.body.map((l,i)=>[l,i]).filter(([l])=>l.startsWith('+')&&l.slice(1).trim().length>=10);
 if(group==='VALID')return hunkText(h);
 if(group==='INCOMPLETE_HUNK'){
  if(h.body.length<2)return null;
  const drop=Math.floor(r()*h.body.length);
  return hunkText({...h,body:h.body.filter((_,i)=>i!==drop)});
 }
 let candidates=added;
 if(group==='SEMANTIC')candidates=added.filter(([l])=>SWAPS.some(([re])=>re.test(l.slice(1))));
 if(!candidates.length)return null;
 const [line,index]=pick(candidates);
 const c=line.slice(1);let next=null;
 if(group==='SYNTAX'){
  const at=[...c].map((ch,i)=>'(){}[]'.includes(ch)?i:-1).filter(i=>i>=0);
  if(at.length){const i=pick(at);next=c.slice(0,i)+c.slice(i+1);}
  else{const i=Math.floor(r()*(c.length+1));next=c.slice(0,i)+'{'+c.slice(i);}
 }else if(group==='MISSING_TOKEN'){
  const tokens=[...c.matchAll(/[A-Za-z_$][\w$]*/g)];
  if(!tokens.length)return null;
  const t=pick(tokens);next=c.slice(0,t.index)+c.slice(t.index+t[0].length);
 }else if(group==='TRUNCATED'){
  next=c.slice(0,Math.max(1,Math.floor(c.length*(0.3+0.6*r()))));
 }else if(group==='ODD_BYTES'){
  const k=3+Math.floor(r()*4),i=Math.floor(r()*(c.length+1));
  next=c.slice(0,i)+Array.from({length:k},()=>pick(ODD)).join('')+c.slice(i);
 }else if(group==='SEMANTIC'){
  const options=SWAPS.filter(([re])=>re.test(c));
  const [re,to]=pick(options);next=c.replace(re,to);
 }
 if(next===null||next===c)return null;
 const body=[...h.body];body[index]='+'+next;
 return hunkText({...h,body});
}

/** git apply --check and node --check of parent + hunk in a throw-away directory. */
export function parserVerdict(parent,text,tmp){
 const dir=fs.mkdtempSync(path.join(tmp,'h-'));
 try{
  fs.writeFileSync(path.join(dir,'f.mjs'),parent);
  fs.writeFileSync(path.join(dir,'p.patch'),'diff --git a/f.mjs b/f.mjs\n--- a/f.mjs\n+++ b/f.mjs\n'+text);
  if(spawnSync('git',['apply','--check','p.patch'],{cwd:dir}).status!==0)return {apply:false,syntax:null,detected:true};
  if(spawnSync('git',['apply','p.patch'],{cwd:dir}).status!==0)return {apply:false,syntax:null,detected:true};
  const syntax=spawnSync(process.execPath,['--check','f.mjs'],{cwd:dir}).status===0;
  return {apply:true,syntax,detected:!syntax};
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
}

function main(){
 const a=process.argv.slice(2),o={seed:'20261023','max-commits':'400'};
 for(let i=0;i<a.length;i+=2){if(!a[i].startsWith('--')||a[i+1]===undefined)throw Error('expected --name value pairs');o[a[i].slice(2)]=a[i+1];}
 if(!/^[0-9a-f]{7,40}$/.test(o.until??'')||!o.out)throw Error('usage: --until <sha> --out <file.jsonl> [--seed n] [--max-commits n]');
 const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
 const git=args=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8',maxBuffer:256<<20});if(r.status!==0)throw Error('git '+args[0]+': '+r.stderr);return r.stdout;};
 const until=git(['rev-parse',o.until]).trim();
 const r=rng(Number(o.seed));
 const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'cdx1-anomaly-'));
 const parents=new Map();
 const parentText=(commit,p)=>{const k=commit+':'+p;if(!parents.has(k))parents.set(k,git(['show',k]));return parents.get(k);};
 const commits=git(['rev-list','--no-merges','--max-count='+o['max-commits'],until,'--','.github/continuum/*.mjs']).trim().split('\n').filter(Boolean);
 const seen=new Set(),items=[];
 for(const commit of commits){
  const parent=git(['rev-list','--parents','-n','1',commit]).trim().split(' ').slice(1);
  if(parent.length!==1)continue;
  const diff=git(['diff','-U2','--no-color','--no-ext-diff','--diff-filter=M',parent[0],commit,'--','.github/continuum/*.mjs']);
  for(const h of parseHunks(diff)){
   const text=hunkText(h);
   if(bytes(text)>480||seen.has(h.path+'\0'+text))continue;
   if(!h.body.some(l=>l.startsWith('+')&&l.slice(1).trim().length>=10))continue;
   seen.add(h.path+'\0'+text);
   const base=parentText(parent[0],h.path);
   if(parserVerdict(base,text,tmp).detected)continue;                   // only applicable, parseable hunks are VALID
   const split=splitOf(h.path),id=sha256(h.path+'\0'+text).slice(0,16);
   for(const group of GROUPS){
    const variant=corrupt(h,group,r);
    if(variant===null||bytes(variant)>512||!variant.trim())continue;
    const parser=group==='VALID'?{apply:true,syntax:true,detected:false}:parserVerdict(base,variant,tmp);
    items.push({kind:'item',id:id+'-'+group,path:h.path,commit,split,group,text:variant,parser});
   }
  }
 }
 // TRAIN texts: the valid TRAIN hunks first, then line-aligned chunks of the TRAIN files at the pinned commit.
 const train=items.filter(x=>x.split==='TRAIN'&&x.group==='VALID').map(x=>x.text);
 const files=git(['ls-tree','-r','--name-only',until,'.github/continuum/']).split('\n').filter(f=>f.endsWith('.mjs')&&splitOf(f)==='TRAIN').sort();
 const chunks=[];
 for(const f of files){
  let current='';
  for(const line of git(['show',until+':'+f]).split('\n')){
   const next=current+line+'\n';
   if(bytes(next)>480){if(current.trim())chunks.push(current);current=bytes(line+'\n')>480?'':line+'\n';}
   else current=next;
  }
  if(current.trim())chunks.push(current);
 }
 for(let i=chunks.length-1;i>0;i--){const j=Math.floor(r()*(i+1));[chunks[i],chunks[j]]=[chunks[j],chunks[i]];}
 let total=train.reduce((n,t)=>n+bytes(t),0);
 for(const c of chunks){if(total+bytes(c)>TRAIN_BYTES)break;train.push(c);total+=bytes(c);}
 const counts={};
 for(const x of items){const k=x.split+'/'+x.group;counts[k]=(counts[k]??0)+1;}
 const header={kind:'header',schema:'cdx1-patch-anomaly-dataset',version:1,until,seed:Number(o.seed),commits:commits.length,
  items:items.length,trainTexts:train.length,trainBytes:total,counts};
 const lines=[header,...items,...train.map(text=>({kind:'train',text}))].map(x=>JSON.stringify(x));
 fs.writeFileSync(o.out,lines.join('\n')+'\n');
 fs.rmSync(tmp,{recursive:true,force:true});
 console.log(JSON.stringify({...header,sha256:sha256(fs.readFileSync(o.out))}));
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))main();
