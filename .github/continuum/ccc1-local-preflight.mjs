import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {ensureCcc1StateOutsideSource} from './ccc1-path-policy.mjs';

const REQUIRED = Object.freeze([
  '.github/continuum/ccc1-cli.mjs',
  '.github/continuum/ccc1-worker.mjs',
  '.github/continuum/ccc1-path-policy.mjs',
  '.github/continuum/local-teamleader-loop.mjs'
]);
const REV = /^[a-f0-9]{40}$/;
const FORBIDDEN_BRANCH = /^(?:main|master|production|prod|release)(?:$|[/-])/i;

function fail(code) { throw new Error(code); }

/** Read-only readiness assessment, not proof that any worker ran or that the OS sandbox is safe. */
export function assessCcc1Preflight({project, stateDir, runGit = (root,args) =>
  execFileSync('git', args, {cwd: root, encoding:'utf8', timeout:10000, windowsHide:true, shell:false})}) {
  const findings=[];
  let root;
  try {
    if(typeof project !== 'string' || !project.trim() || !fs.statSync(project).isDirectory())
      fail('CCC1_PROJECT_INVALID');
    root=fs.realpathSync(project);
  } catch { findings.push('CCC1_PROJECT_INVALID'); }
  let state;
  if(root) {
    try {
      state = ensureCcc1StateOutsideSource(root,stateDir);
      if(state===path.parse(state).root) findings.push('CCC1_STATE_IS_FILESYSTEM_ROOT');
    } catch(e){ findings.push(String(e.message||'CCC1_STATE_PATH_INVALID')); }
    for (const relative of REQUIRED) {
      const candidate=path.join(root, relative);
      try {
        if(!fs.statSync(candidate).isFile() ||
           !fs.realpathSync(candidate).startsWith(root+path.sep))
          findings.push('CCC1_REQUIRED_FILE_OUTSIDE_PROJECT:'+relative);
      } catch {findings.push('CCC1_REQUIRED_FILE_MISSING:'+relative);}
    }
    try {
      const top=runGit(root,['rev-parse','--show-toplevel']).trim();
      if(fs.realpathSync(top)!==root) findings.push('CCC1_ROOT_MISMATCH');
    } catch {findings.push('CCC1_GIT_ROOT_UNVERIFIED');}
  }
  let commit=null,branch=null;
  if(root){
    try {commit=runGit(root,['rev-parse','--verify','HEAD']).trim();if(!REV.test(commit))findings.push('CCC1_COMMIT_INVALID');}
    catch{findings.push('CCC1_COMMIT_UNVERIFIED');}
    try {branch=runGit(root,['branch','--show-current']).trim();
      if(!branch || FORBIDDEN_BRANCH.test(branch))findings.push('CCC1_BRANCH_NOT_ISOLATED');
    }catch{findings.push('CCC1_BRANCH_UNVERIFIED');}
    try{if(runGit(root,['status','--porcelain','--untracked-files=normal']).trim()) findings.push('CCC1_WORKTREE_NOT_CLEAN');}
    catch{findings.push('CCC1_WORKTREE_UNVERIFIED');}
  }
  const major=Number(process.versions.node.split('.')[0]);
  if(!Number.isInteger(major)||major<20)findings.push('CCC1_NODE_VERSION_UNSUPPORTED');
  return Object.freeze({schema:'continuum-ccc1-local-preflight',version:1,
    result:findings.length===0?'READY_TO_REQUEST_OWNER_REVIEW':'BLOCKED',
    sourceCommit:REV.test(commit||'')?commit:null,
    checkedBranch:branch,canonicalState:state??null,
    ownerApproved:false,workerExecuted:false,patchVerified:false,
    findings:Object.freeze([...new Set(findings)])});
}

const PROJECT_HINT='~/device-ai';
/**
 * Owner guidance for preflight findings (German, one line each, with the exact Termux command where one exists).
 * Text for a human in Termux; the Android app maps the codes with its own texts and never displays this output.
 */
export function explainCcc1Findings(findings, project=PROJECT_HINT){
  const hints=new Map();
  for(const finding of findings){
    const code=String(finding).split(':')[0];
    if(code==='CCC1_BRANCH_NOT_ISOLATED')hints.set(code,'Der Checkout steht auf main/master/release/production oder auf keinem Branch. '+
      'CCC1 arbeitet nur auf einem eigenen Arbeitsbranch: bash scripts/continuum-teamleader-termux.sh ccc1-prepare');
    else if(code==='CCC1_WORKTREE_NOT_CLEAN')hints.set(code,'Im Checkout gibt es ungesicherte Änderungen oder neue Dateien: git -C '+project+
      ' status --short. Eigene Änderungen committen oder sichern; CCC1 verwirft nichts.');
    else if(code==='CCC1_REQUIRED_FILE_MISSING'||code==='CCC1_REQUIRED_FILE_OUTSIDE_PROJECT')hints.set(code,
      'Der Checkout ist zu alt für CCC1 oder unvollständig. Auf den CCC1-Stand aktualisieren (docs/CONTINUUM-CCC1-AUF-DEM-GERAET.md).');
    else if(code==='CCC1_NODE_VERSION_UNSUPPORTED')hints.set(code,'Node.js 20 oder neuer ist nötig; in Termux: pkg upgrade nodejs');
    else if(code.startsWith('CCC1_STATE'))hints.set('CCC1_STATE','Der Zustandsordner liegt im Checkout oder zeigt per Link hinein. '+
      'Standard ohne --state-dir verwenden: ~/.local/state/device-ai-continuum/ccc1');
    else hints.set(code,code+': '+project+' ist kein vollständiger Git-Checkout dieses Projekts.');
  }
  return [...hints.values()];
}

export function parseArgs(args){
  const options={};
  for(let i=0;i<args.length;i+=2){
    const key=args[i];
    if(!['--project','--state'].includes(key) || options[key]!==undefined ||
       typeof args[i+1]!=='string' || !args[i+1])fail('CCC1_PREFLIGHT_ARGUMENTS_INVALID');
    options[key]=args[i+1];
  }
  if(args.length!==4 || !options['--project'] || !options['--state'])fail('CCC1_PREFLIGHT_ARGUMENTS_INVALID');
  return {project:options['--project'],stateDir:options['--state']};
}

if(process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  try{
    const report=assessCcc1Preflight(parseArgs(process.argv.slice(2)));
    process.stdout.write(JSON.stringify(report)+'\n');
    if(report.result==='BLOCKED')process.exitCode=2;
  }catch(e){process.stderr.write(String(e.message)+'\n');process.exitCode=2;}
}