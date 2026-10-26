#!/usr/bin/env node
/** Owner-run local shadow comparison against a known saved SWB1 state file. */
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {runShadowBudgetSweep} from '../.github/continuum/neural-shadow-budget-lab.mjs';

function fail(message){throw Error(message);}
function main(){
 if(process.argv.length!==4)fail('USAGE: node scripts/continuum-swb1-shadow-run.mjs /read-only/state.json /outside-worktree/evidence');
 const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
 const input=path.resolve(process.argv[2]),out=path.resolve(process.argv[3]);
 if(out===root||out.startsWith(root+path.sep)||input===out)
   fail('EVIDENCE_DIRECTORY_MUST_BE_OUTSIDE_GIT');
 if(fs.lstatSync(input).isSymbolicLink())fail('STATE_SYMLINK_REFUSED');
 const raw=fs.readFileSync(input,'utf8');
 if(raw.length>3_000_000||raw.length<100)fail('STATE_SIZE_INVALID');
 const document=JSON.parse(raw);
 if(!document||!document.neural||document.neural.version!==1)
   fail('NOT_SWB1_STATE');
 const initialBytes=fs.readFileSync(input);
 const result=runShadowBudgetSweep(document.neural);
 if(!initialBytes.equals(fs.readFileSync(input)))
   fail('SOURCE_STATE_CHANGED_DURING_RESEARCH');
 fs.mkdirSync(out,{recursive:true,mode:0o700});
 const actual=fs.realpathSync(out);
 if(actual===root||actual.startsWith(root+path.sep))fail('OUTPUT_REDIRECTION_REFUSED');
 const output=path.join(actual,'swb1-shadow-report.json');
 if(fs.existsSync(output)&&fs.lstatSync(output).isSymbolicLink())
   fail('OUTPUT_SYMLINK_REFUSED');
 const tmp=path.join(actual,'swb1-shadow-report.partial.json');
 const data=JSON.stringify({
  ...result,ownerSnapshotHash:document.neural.modelHash,
  researchSourceSha:process.env.CONTINUUM_CODE_SHA||null,
  environment:'OWNER_LOCAL_READ_ONLY',publishedToGithub:false
 },null,2)+'\n';
 fs.writeFileSync(tmp,data,{encoding:'utf8',mode:0o600});
 fs.renameSync(tmp,output);
 console.log(JSON.stringify({saved:output,baseline:result.startingModelHash,
   tried:result.profiles.length,safeCandidates:result.profiles.filter(x=>x.wouldPassExistingGate).length,
   promoted:false}));
}
try{main();}catch(error){console.error('SHADOW_RESEARCH_FAILED: '+error.message);process.exitCode=1;}
