#!/usr/bin/env node
// Offline historical v22 Git-tree audit for Render private shallow checkouts.
// Independently validates the full Git SHA-1 tree structure recorded from the
// exact historical commit through the authenticated GitHub connector.
// This audit NEVER authorizes the v23 builder, never accesses a signing key.
import {createHash} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {join,resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const SNAPSHOT='docs/release/continuum-v22-historical-git-tree-20261025.json';
const MANIFEST='docs/release/continuum-0321-source-proposal-20261024.json';
const EXPECTED_SNAPSHOT_BLOB='962358afa1975e56efa7c9c4b9cecba5c6fad551';
const EXPECTED_MANIFEST_BLOB='36433bba220cb5706e286d8145df4c7a61734c0b';
const EXPECTED_COMMIT='ec92490bbe7d75a2b9c16a47a94a1991f61837cc';
const EXPECTED_ROOT='bc56ee1e45b87d5a6661754a6fd7570fea506371';
const fail=(m)=>{process.stderr.write('OWNER_V22_HISTORICAL_BLOCKED: '+m+'\n');process.exit(69);};
const read=(p)=>JSON.parse(readFileSync(join(root,p),'utf8'));
const blob=(p)=>execFileSync('git',['hash-object','--',p],{cwd:root,encoding:'utf8',timeout:20000}).trim();
const sha=x=>typeof x==='string'&&/^[a-f0-9]{40}$/.test(x);
try{
  if(blob(SNAPSHOT)!==EXPECTED_SNAPSHOT_BLOB||blob(MANIFEST)!==EXPECTED_MANIFEST_BLOB)
    fail('historical snapshot or manifest digest mismatch');
  const v=read(SNAPSHOT),m=read(MANIFEST);
  if(v.schemaVersion!==1||v.sourceCommit!==EXPECTED_COMMIT||
      v.rootTreeSha!==EXPECTED_ROOT||v.manifestBlob!==EXPECTED_MANIFEST_BLOB||
      !Array.isArray(v.entries)||v.entries.length!==2243)
    fail('historical root/commit/snapshot schema mismatch');
  const entries=new Map();
  for(const e of v.entries){
    if(typeof e.path!=='string'||!/^[^\0\n]+$/.test(e.path)||
       e.path.startsWith('/')||e.path.split('/').some(t=>t===''||t==='.'||t==='..')||
       entries.has(e.path)||!sha(e.sha)||
       !['blob','tree','commit'].includes(e.type)||
       !['100644','100755','040000','120000','160000'].includes(e.mode))
       fail('malformed historical git tree entry');
    if((e.type==='tree')!==(e.mode==='040000'))fail('entry mode/type mismatch');
    entries.set(e.path,e);
  }
  // Git tree objects encode child modes, names and binary object IDs, sorted by
  // Git's path sort rule (directory names sort as if followed by '/').
  const dirs=new Set(['']);
  for(const e of v.entries){
    if(e.type==='tree')dirs.add(e.path);
    const parent=e.path.includes('/')?e.path.slice(0,e.path.lastIndexOf('/')):'';
    if(parent&&!entries.has(parent))fail('missing tree parent '+parent);
  }
  for(const dir of [...dirs].sort((a,b)=>b.length-a.length)){
    const prefix=dir?dir+'/':'';
    const children=v.entries.filter(e=>{
      if(!e.path.startsWith(prefix))return false;
      return !e.path.slice(prefix.length).includes('/');
    });
    if(children.length===0)fail('empty or missing directory '+dir);
    children.sort((a,b)=>{
      const na=a.path.slice(prefix.length)+(a.type==='tree'?'/':'');
      const nb=b.path.slice(prefix.length)+(b.type==='tree'?'/':'');
      return Buffer.compare(Buffer.from(na,'utf8'),Buffer.from(nb,'utf8'));
    });
    const parts=[];
    for(const e of children){
      if(e.type==='tree'&&!dirs.has(e.path))fail('missing sub-tree '+e.path);
      const name=e.path.slice(prefix.length),mode=e.mode.replace(/^0+/,'');
      parts.push(Buffer.from(mode+' '+name+'\0','utf8'),Buffer.from(e.sha,'hex'));
    }
    const payload=Buffer.concat(parts);
    const digest=createHash('sha1').update(Buffer.from('tree '+payload.length+'\0'))
      .update(payload).digest('hex');
    if(dir?(digest!==entries.get(dir)?.sha):(digest!==EXPECTED_ROOT))
      fail('historical git-tree SHA-1 mismatch at '+(dir||'<root>'));
  }
  if(m.schemaVersion!==1||m.state!=='SOURCE_PROPOSAL_NOT_OWNER_APPROVAL'||
     m.sourceHead!==EXPECTED_COMMIT||m.versionCode!==22||
     m.versionName!=='0.3.21'||m.package!=='at.heinz.deviceai.mentorpaired'||
     m.certificateSha256!=='2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc'||
     !Array.isArray(m.files)||m.files.length!==927)
    fail('historical source proposal mismatch');
  const seen=new Set();
  for(const e of m.files){
    if(!sha(e.blob)||seen.has(e.path)||
       entries.get(e.path)?.type!=='blob'||entries.get(e.path)?.sha!==e.blob)
       fail('historical manifest entry not in historical tree '+e.path);
    seen.add(e.path);
  }
  console.log('OWNER_V22_HISTORICAL_TREE_VERIFIED root='+EXPECTED_ROOT+
              ' source='+EXPECTED_COMMIT+' entries='+entries.size+' approved_files='+seen.size);
}catch(e){fail(e instanceof Error?e.message:'unknown audit error');}
