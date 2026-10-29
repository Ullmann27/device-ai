import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
const digest=data=>createHash('sha256').update(data).digest('hex');
const SHA=/^[0-9a-f]{64}$/;
const REQUIRED=['qa-summary.json','tested-source-manifest.json','final-qa/summary.json'];
/** Local evidence-byte check, not authentication of the manifest author. */
export function verifyEvidenceManifest(directory,{minFiles=500,maxFiles=1500}={}) {
 if(typeof directory!=='string'||!Number.isSafeInteger(minFiles)||
  !Number.isSafeInteger(maxFiles)||minFiles<1||maxFiles<minFiles||maxFiles>10000)
  throw Error('INVALID_MANIFEST_REQUEST');
 const root=fs.realpathSync(directory);
 if(!fs.statSync(root).isDirectory())throw Error('EVIDENCE_ROOT_NOT_DIRECTORY');
 const file=path.join(root,'sha256-manifest.json'),stat=fs.lstatSync(file);
 if(!stat.isFile()||stat.size>250_000)throw Error('INVALID_MANIFEST_FILE');
 const bytes=fs.readFileSync(file),files=JSON.parse(bytes.toString('utf8'));
 if(!files||Array.isArray(files)||typeof files!=='object')throw Error('INVALID_MANIFEST_OBJECT');
 const entries=Object.entries(files);
 if(entries.length<minFiles||entries.length>maxFiles||
  REQUIRED.some(name=>!Object.hasOwn(files,name)))throw Error('EVIDENCE_INCOMPLETE');
 let totalBytes=0;
 for(const [name,expected] of entries){
  if(typeof expected!=='string'||!SHA.test(expected)||
   !/^[a-zA-Z0-9][a-zA-Z0-9._/-]{0,230}$/.test(name)||
   name.split('/').some(s=>s===''||s==='.'||s==='..')||
   path.posix.normalize(name)!==name)throw Error('INVALID_EVIDENCE_ENTRY');
  const candidate=path.join(root,name),resolved=fs.realpathSync(candidate);
  if(!resolved.startsWith(root+path.sep)||resolved!==candidate)throw Error('UNSAFE_EVIDENCE_PATH');
  const stat=fs.lstatSync(candidate);
  if(!stat.isFile()||stat.size>3_000_000)throw Error('UNSAFE_EVIDENCE_FILE');
  const data=fs.readFileSync(candidate);
  if(digest(data)!==expected)throw Error('EVIDENCE_HASH_MISMATCH: '+name);
  totalBytes+=data.length;
 }
 return Object.freeze({status:'BYTES_MATCH_LOCAL_MANIFEST',count:entries.length,
  totalBytes,manifestSha256:digest(bytes),signatureValidated:false,externallyAttested:false});
}
