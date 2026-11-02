import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import * as B from './development-backlog.mjs';
import {analyzeRepository} from './code-analyzer.mjs';
import {syncBacklogOps} from './development-planner.mjs';
import {git,createWorktree,removeWorktree,runChain,CHAINS} from './repair-loop.mjs';
import {renderOwnerNodeContract} from './contract-test-template.mjs';
import {parseKotlinIntCases} from './kotlin-delegate-acceptance.mjs';

/**
 * `developer-agent.mjs backlog <command> --file <backlog.json> ...`: the CLI for
 * humans and agents. Every change is an operation of development-backlog.mjs
 * (same rules as the Android app); test evidence is only ever produced by
 * `verify`, which runs a fixed check chain on the submitted patch itself.
 *
 *   show [--json]                       plan [--limit 12]
 *   propose-repair --file <path> [--id <id>] [--title <text>] [--chain node|android|jvm]
 *   propose-test --file <path> --module <.github/continuum/NAME.mjs> --export <symbol> --cases-json <array> [--id <id>]
 *   accept|reject|abort|retry --id <id> [--reason <text>]
 *   priority --id <id> --priority <0..1000>
 *   claim [--id <id>] [--kinds A,B] [--owner <name>] [--lease-minutes 60]
 *   renew --id <id> --token <t> [--lease-minutes 60]
 *   submit --id <id> --token <t> --patch <file>
 *   verify --id <id> --base <commit> --chain node|android|jvm --patch <file>
 *   fail --id <id> --token <t> --code <CODE> [--message <text>]
 *   expire                              apply --ops <file> [--lenient]
 */
const SAFE_FILE=/^[\w./@-]{1,240}$/;
function safePath(value,name){
  if(typeof value!=='string'||!SAFE_FILE.test(value)||value.split('/').includes('..'))throw Error(`--${name} must be a plain path without ".."`);
  return path.resolve(value);
}
const leaseOf=o=>Math.round(Number(o['lease-minutes']??60)*60_000);
const ok=r=>r.ok?(r.duplicate?'duplicate (already applied)':'ok'):`${r.code}: ${r.message}`;

export function formatBacklog(doc){
  const rows=doc.tasks.map(t=>`${t.state.padEnd(11)} ${String(t.priority).padStart(4)}  ${t.kind.padEnd(11)} ${String(t.attempts)}/${t.maxAttempts}  ${t.id.padEnd(24)} ${t.title}`+
    (t.error?`\n${' '.repeat(36)}last error ${t.error.code}: ${t.error.message}`:''));
  return [`backlog revision ${doc.revision} · ${doc.tasks.length} task(s) · updated by ${doc.updatedBy}`,...rows].join('\n');
}

function report(log,result){
  if(result.recovered)log(`[backlog] RECOVERED from ${result.recovered.from}: ${result.recovered.reason}`+(result.recovered.quarantined?` (corrupt file kept as ${result.recovered.quarantined})`:''));
  if(result.migratedFrom)log(`[backlog] migrated from schema ${result.migratedFrom}`);
  for(const r of result.results??[])log(`[backlog] op ${r.index}: ${ok(r)}`+(r.id?` (${r.id})`:'')+(r.expired?.length?` expired ${r.expired.join(',')}`:''));
  log(`[backlog] revision ${result.doc.revision}${result.written?'':' (unchanged)'}`);
}

export async function backlogCommand(sub,o,{root=process.cwd(),log=console.log,now=Date.now}={}){
  const actor=o.actor??process.env.CONTINUUM_ACTOR??'cli';
  const store=new B.BacklogStore(safePath(o.file,'file'),{actor});
  const apply=(ops,opts)=>{const r=store.apply(ops,opts);report(log,r);return r;};
  const at=now();
  switch(sub){
    case 'show':{
      const r=store.read();
      if(r.recovered)log(`[backlog] would recover from ${r.recovered.from}: ${r.recovered.reason}`);
      if(r.migratedFrom)log(`[backlog] schema ${r.migratedFrom} file, migrated in memory (written on the next change)`);
      log(o.json?B.canonical(r.doc):formatBacklog(r.doc));
      return r;
    }
    case 'plan':{
      const {index,...analysis}=analyzeRepository(root);
      let results=[];
      const r=store.update(doc=>{
        const out=B.applyOps(doc,syncBacklogOps(doc,analysis,{at,limit:Number(o.limit??12)}),{stopOnError:false});
        results=out.results;return out.doc;
      });
      report(log,{...r,results});
      return {...r,results};
    }
    case 'propose-implement':{
      // A narrowly bounded pure Kotlin delegation operator. This registers
      // only an owner proposal: acceptance and execution are separate acts.
      const filePath=o.path;
      const word=/^[A-Za-z_][A-Za-z0-9_]{0,63}$/;
      if(typeof filePath!=='string'||
         !/^app\/src\/main\/(?:java|kotlin)\/[A-Za-z0-9_./-]+\.kt$/.test(filePath)||
         filePath.split('/').includes('..')||
         ![o['class-name'],o['method-name'],o['delegate-name'],
           o['parameter-name']].every(v=>word.test(v??''))||
         o.type!=='Int'||!['1','2'].includes(o.depth)||
         !parseKotlinIntCases(o['cases-json']))
        throw Error('Unsupported bounded Kotlin IMPLEMENT spec; require Int, depth 1/2 and owner input/output cases');
      const head=git(root,['rev-parse','HEAD']).stdout.trim();
      if(!/^[0-9a-f]{40}$/.test(head)||git(root,['status','--porcelain']).stdout.trim())
        throw Error('Clean immutable Git HEAD required for IMPLEMENT proposal');
      const subject={
       base:head,chain:'android',operation:'EXPOSE_DELEGATE',file:filePath,
       className:o['class-name'],methodName:o['method-name'],
       delegateName:o['delegate-name'],parameterName:o['parameter-name'],
       type:'Int',depth:Number(o.depth),casesJson:o['cases-json']
      };
      const id=o.id??'impl-'+B.sha256Hex(B.canonical(subject)).slice(0,16);
      return apply([{op:'propose',actor,at,task:{
       id,title:B.singleLine(o.title??('Expose Kotlin method '+subject.methodName),200),
       kind:'IMPLEMENT',source:'owner',priority:850,maxAttempts:3,subject
      }}]);
    }
    case 'propose-test':{
      // Owner-supplied test cases are passed as compact JSON text because
      // Kotlin/Node backlog v2 subjects allow scalar fields ONLY.
      const modulePath=o.module,exportName=o.export,casesJson=o['cases-json'];
      if(!/^\.github\/continuum\/[A-Za-z][A-Za-z0-9_-]{0,63}\.mjs$/.test(modulePath??'')||
         !/^[A-Za-z_][A-Za-z0-9_]{0,63}$/.test(exportName??'')||
         typeof casesJson!=='string'||casesJson.length>300)
        throw Error('Invalid module, export or compact --cases-json (max 300 chars)');
      // Validate against the SAME pure grammar as the executor and critic.
      // Reject unsupported values before they enter persistent owner state.
      const contractFile=modulePath.replace(/\.mjs$/,'.contract.test.mjs');
      if(renderOwnerNodeContract(contractFile,exportName,casesJson)===null)
        throw Error('--cases-json contains an unsupported or unsafe contract value');
      const base=git(root,['rev-parse','HEAD']).stdout.trim();
      if(!/^[0-9a-f]{40}$/.test(base)||git(root,['status','--porcelain']).stdout.trim())
        throw Error('A clean immutable Git HEAD is required to propose a contract test');
      const id=o.id??'own-'+B.sha256Hex('TEST\0'+base+'\0'+modulePath+'\0'+casesJson).slice(0,16);
      return apply([{op:'propose',actor,at,task:{
       id,title:B.singleLine(o.title??('Contract test for '+exportName),200),
       kind:'TEST',source:'owner',priority:900,maxAttempts:3,
       subject:{base,chain:'node',operation:'ASSERT_NODE_EXPORT',
        modulePath,exportName,casesJson}
      }}]);
    }
    case 'propose-repair':{
      // The local operator explicitly registers a specific reproducible failure;
      // proposing a task does not authorize execution. 'accept' is separate.
      const base=git(root,['rev-parse','HEAD']).stdout.trim();
      if(!/^[0-9a-f]{40}$/.test(base))throw Error('Full local git HEAD required');
      if(o.base&&o.base!==base)throw Error('--base must equal local immutable HEAD');
      const chain=o.chain??'node';
      if(!Object.hasOwn(CHAINS,chain))throw Error('Unsupported fixed test chain');
      // Historical owner-repair task IDs use the literal backslash-zero delimiter.
      // Keep it until a tested backlog-ID migration: changing to NUL creates
      // different IDs for preexisting accepted or in-progress owner tasks.
      const id=o.id??'own-'+B.sha256Hex(base+'\\0'+chain).slice(0,16);
      const title=B.singleLine(o.title??('Repair '+chain+' tests at '+base.slice(0,12)),200);
      return apply([{op:'propose',actor,at,task:{id,title,kind:'REPAIR',
        source:'owner',priority:900,maxAttempts:3,subject:{base,chain}}}]);
    }
    case 'accept':case 'reject':case 'abort':case 'retry':
      return apply([{op:sub,actor,at,id:o.id,...(o.reason?{reason:o.reason}:{})}]);
    case 'priority':
      return apply([{op:'setPriority',actor,at,id:o.id,priority:Number(o.priority)}]);
    case 'claim':{
      const token=B.newToken();
      const base={actor,at,owner:o.owner??actor,token,leaseMs:leaseOf(o)};
      const r=apply([o.id?{op:'claim',id:o.id,...base}:{op:'claimNext',...base,...(o.kinds?{kinds:o.kinds.split(',')}:{})}]);
      log(`[backlog] claimed ${r.results[0].id} with token ${token} (keep it: submit/renew/fail need it)`);
      return {...r,token};
    }
    case 'renew':
      return apply([{op:'renew',actor,at,id:o.id,token:o.token,leaseMs:leaseOf(o)}]);
    case 'submit':{
      const patch=fs.readFileSync(safePath(o.patch,'patch'));
      return apply([{op:'submit',actor,at,id:o.id,token:o.token,artifact:{type:'PATCH',sha256:B.sha256Hex(patch)}}]);
    }
    case 'fail':
      return apply([{op:'fail',actor,at,id:o.id,token:o.token,error:{code:o.code,message:B.singleLine(o.message??'',400)}}]);
    case 'expire':
      return apply([{op:'expire',actor,at}]);
    case 'apply':{
      const ops=B.parseStrict(fs.readFileSync(safePath(o.ops,'ops'),'utf8'));
      if(!Array.isArray(ops)||ops.length>1000)throw Error('--ops must hold a JSON array of at most 1000 operations');
      return apply(ops,{stopOnError:o.lenient!=='true'});
    }
    case 'verify':{
      // The only producer of test evidence: the submitted patch itself, applied
      // to the given base in an isolated worktree, checked by a fixed chain.
      if(!/^[0-9a-f]{40}$/.test(o.base??''))throw Error('--base must be a full commit hash');
      if(!CHAINS[o.chain])throw Error('--chain must be one of '+Object.keys(CHAINS).join(', '));
      const patchFile=safePath(o.patch,'patch');
      const patch=fs.readFileSync(patchFile);
      const task=store.read().doc.tasks.find(t=>t.id===o.id);
      if(!task)throw Error('Unknown task '+o.id);
      if(task.state!=='VERIFYING')throw Error(`${task.id} is ${task.state}; only a submitted task can be verified`);
      const sha=B.sha256Hex(patch);
      if(sha!==task.submission.artifact.sha256)throw Error('The patch is not the submitted artifact (SHA-256 differs)');
      const wt=createWorktree(root,o.base);
      const out=fs.mkdtempSync(path.join(os.tmpdir(),'backlog-verify-'));
      let check;
      try{
        const applied=git(wt,['apply','--whitespace=nowarn',patchFile],{allowFailure:true});
        if(applied.status!==0)throw Error('The patch does not apply to '+o.base+': '+applied.stderr.slice(-300));
        check=runChain(o.chain,wt,out);
      }finally{removeWorktree(root,wt);fs.rmSync(out,{recursive:true,force:true});}
      const evidence={type:'TEST_RUN',taskId:task.id,submissionToken:task.submission.token,artifactSha256:sha,chain:o.chain,commit:o.base,
        profiles:check.results.map(r=>({profile:r.profile,passed:r.passed,executed:r.tests.executed,failed:r.tests.failed,skipped:r.tests.skipped})),
        repair:null,at:now(),by:actor};
      for(const p of evidence.profiles)log(`[verify] ${p.profile}: ${p.passed?'passed':'FAILED'} · ${p.executed} executed, ${p.failed} failed, ${p.skipped} skipped`);
      const op=check.passed
        ?{op:'complete',actor,at:evidence.at,id:task.id,evidence}
        :{op:'fail',actor,at:evidence.at,id:task.id,token:task.submission.token,evidence,
          error:{code:'CHECKS_FAILED',message:B.singleLine(evidence.profiles.filter(p=>!p.passed).map(p=>p.profile).join(', ')+' failed',400)}};
      // complete still enforces the evidence rules (e.g. executed tests > 0)
      return apply([op],{stopOnError:false});
    }
    default:
      throw Error('Unknown backlog command '+sub+' (show|plan|propose-implement|propose-test|propose-repair|accept|reject|abort|retry|priority|claim|renew|submit|verify|fail|expire|apply)');
  }
}
