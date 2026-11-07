import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const SHA=/^[a-f0-9]{40}$/;
const SOURCE=/^\.github\/continuum\/[a-zA-Z0-9][a-zA-Z0-9._/-]*\.mjs$/;
const DOC=/^docs\/[a-zA-Z0-9][a-zA-Z0-9._/-]*\.md$/;

function run(program,args,cwd,input){
 const result=spawnSync(program,args,{cwd,input,encoding:'utf8',shell:false,timeout:12000,maxBuffer:1048576});
 if(result.error||result.status!==0||result.signal)
  throw Error('Inspection command failed: '+program+' '+args[0]);
 return result.stdout;
}

/** Pure source intake: never checks out, executes contributed code, or moves refs. */
export function inspectCegisIntake(repo,base,candidate){
 if(!SHA.test(base)||!SHA.test(candidate))throw Error('Expected two immutable commit hashes');
 const cwd=fs.realpathSync(repo);
 if(run('git',['rev-parse','--show-toplevel'],cwd).trim()!==cwd)
  throw Error('Checkout must be at repository root');
 if(run('git',['rev-parse','HEAD'],cwd).trim()!==base)
  throw Error('Checkout does not match reviewed base');
 if(run('git',['status','--porcelain','--untracked-files=all'],cwd).trim())
  throw Error('Checkout must be clean');
 const parents=run('git',['rev-list','--parents','-n','1',candidate],cwd).trim().split(' ');
 if(parents.length!==2||parents[0]!==candidate||parents[1]!==base)
  throw Error('Candidate must be direct child of reviewed base');
 const changes=run('git',['diff-tree','--no-commit-id','--no-renames','--name-status','-r','-z',candidate],cwd).split('\0');
 if(changes.pop()!=='')throw Error('Invalid Git diff');
 if(changes.length!==22)throw Error('Expected exactly eleven additions');
 const files=[];
 for(let i=0;i<changes.length;i+=2){
  const status=changes[i],file=changes[i+1];
  if(status!=='A'||typeof file!=='string'||file.includes('..')||file.includes('\\')||
     !((SOURCE.test(file)||DOC.test(file))&&file.split('/').every(s=>s&&s!=='.')))
   throw Error('Handoff modifies an existing or protected file');
  const tree=run('git',['ls-tree','-z',candidate,'--',file],cwd);
  const m=tree.match(/^(\d{6}) blob ([a-f0-9]{40})\t([^\0]+)\0$/);
  if(!m||m[1]!=='100644'||m[3]!==file)throw Error('Nonregular handoff file');
  const size=Number(run('git',['cat-file','-s',m[2]],cwd).trim());
  if(!Number.isSafeInteger(size)||size<1||size>524288)throw Error('Invalid file size');
  const body=run('git',['show',candidate+':'+file],cwd);
  if(Buffer.byteLength(body,'utf8')!==size||body.includes('\0'))throw Error('Invalid text content');
  if(SOURCE.test(file))run(process.execPath,['--input-type=module','--check','-'],cwd,body);
  files.push(file);
 }
 const tests=files.filter(f=>SOURCE.test(f)&&f.endsWith('.test.mjs')).length;
 const sources=files.filter(f=>SOURCE.test(f)&&!f.endsWith('.test.mjs')).length;
 const docs=files.filter(f=>DOC.test(f)).length;
 if(tests!==5||sources!==5||docs!==1)throw Error('Expected five modules, five tests, one document');
 return {schema:'continuum-cegis-intake',base,candidate,files,syntaxChecked:10,
  testsExecuted:0,codeExecuted:false,approved:false,decision:'SOURCE_REVIEW_REQUIRED'};
}

if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 try{
  if(process.argv.length!==5)throw Error('Usage: cegis-intake.mjs <repo> <base> <candidate>');
  process.stdout.write(JSON.stringify(inspectCegisIntake(...process.argv.slice(2)))+'\n');
 }catch(error){process.stderr.write('CEGIS_REJECTED: '+String(error.message)+'\n');process.exitCode=1;}
}
