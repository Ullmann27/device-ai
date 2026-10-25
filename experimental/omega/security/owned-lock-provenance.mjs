/** Bind a supplied npm lockfile to actual bytes in an exact, clean Git HEAD. */
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
const SHA40=/^[a-f0-9]{40}$/;
const LOCK=/^(?:[A-Za-z0-9_-]+\/){0,5}package-lock\.json$/;
const fail=code=>{throw Error(code);};
const digest=buffer=>createHash('sha256').update(buffer).digest('hex');
function git(root,args,maxBuffer=100_000){
 const r=spawnSync('git',['--no-pager',...args],{cwd:root,encoding:null,timeout:10_000,
  maxBuffer,shell:false,env:{PATH:process.env.PATH??'/usr/bin:/bin',
    HOME:process.env.HOME??'/nonexistent',GIT_TERMINAL_PROMPT:'0',LC_ALL:'C'}});
 if(r.status!==0||r.error)fail('OMEGA_GIT_PROVENANCE_UNAVAILABLE');
 return r.stdout;
}
export function readOwnedNpmLockAtCommit({repoRoot,sourceCommit,lockfilePath='backend/package-lock.json'}={}){
 if(typeof repoRoot!=='string'||!path.isAbsolute(repoRoot)||!SHA40.test(sourceCommit??'')||
    !LOCK.test(lockfilePath??''))fail('OMEGA_GIT_PROVENANCE_INVALID');
 const stat=fs.lstatSync(repoRoot);
 if(!stat.isDirectory()||stat.isSymbolicLink()||fs.realpathSync(repoRoot)!==repoRoot)
   fail('OMEGA_GIT_PROVENANCE_UNSAFE_ROOT');
 const actual=git(repoRoot,['rev-parse','--show-toplevel']).toString('utf8').trim();
 if(actual!==repoRoot)fail('OMEGA_GIT_PROVENANCE_WRONG_ROOT');
 const head=git(repoRoot,['rev-parse','--verify','HEAD']).toString('utf8').trim();
 if(head!==sourceCommit)fail('OMEGA_GIT_PROVENANCE_STALE_HEAD');
 if(git(repoRoot,['status','--porcelain','--untracked-files=no']).length)
   fail('OMEGA_GIT_PROVENANCE_DIRTY_SOURCE');
 const tree=git(repoRoot,['ls-tree',sourceCommit,'--',lockfilePath]).toString('utf8').trim();
 const match=/^(100644|100755) blob ([a-f0-9]{40})\t(.+)$/.exec(tree);
 if(!match||match[3]!==lockfilePath)fail('OMEGA_GIT_PROVENANCE_NOT_BLOB');
 const bytes=git(repoRoot,['show',sourceCommit+':'+lockfilePath],4_000_000);
 if(!bytes.length||bytes.length>3_000_000)fail('OMEGA_GIT_PROVENANCE_SIZE');
 let lock;
 try{lock=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes));}
 catch{fail('OMEGA_GIT_PROVENANCE_NOT_JSON');}
 if(lock===null||typeof lock!=='object'||Array.isArray(lock)||![2,3].includes(lock.lockfileVersion)||
    lock.packages===null||typeof lock.packages!=='object'||Array.isArray(lock.packages))
   fail('OMEGA_GIT_PROVENANCE_NOT_NPM_LOCK');
 return {schema:'CONTINUUM_OWNED_NPM_LOCK_SNAPSHOT_V1',sourceCommit,lockfilePath,
   gitBlobSha1:match[2],lockfileSha256:digest(bytes),
   provenance:'CLEAN_LOCAL_GIT_HEAD_BLOB',lock};
}
