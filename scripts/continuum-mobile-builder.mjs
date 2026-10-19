#!/usr/bin/env node
// Android-first build adapter: no arbitrary shell, no downloads, no install or Git writes.
// Trusted local Termux checkout only. Node >=20. EXP-00.
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawnSync } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const VERSION = 1;
const PROBE_MS = 15000;
const BUILD_MS = 35 * 60 * 1000;
const MAX_LOG = 512 * 1024;

export function parseArgs(args) {
  const [command, ...rest] = args;
  if (!['doctor', 'build', 'status'].includes(command)) throw Error('Usage: doctor|build|status [--project PATH] [--state-dir PATH] [--offline]');
  const o = { command, project: process.cwd(), stateDir: path.join(os.homedir(), '.local/state/device-ai-continuum/mobile-builder'), offline: false };
  const seen = new Set();
  for (let i=0;i<rest.length;i++) {
    const flag=rest[i];
    if(seen.has(flag)) throw Error('Repeated argument: '+flag);
    seen.add(flag);
    if(flag==='--offline') { o.offline=true; continue; }
    if(flag==='--project'||flag==='--state-dir') {
      const v=rest[++i]; if(!v||v.startsWith('--')) throw Error('Missing value for '+flag);
      o[flag==='--project'?'project':'stateDir']=v; continue;
    }
    throw Error('Unsupported argument: '+flag);
  }
  return o;
}

function inside(root, file) {
  const p=path.relative(root,file);
  return p===''||(p!=='..'&&!p.startsWith('..'+path.sep)&&!path.isAbsolute(p));
}
function executable(name, env) {
  for(const dir of (env.PATH||'').split(path.delimiter)) {
    if(!dir) continue;
    const f=path.join(dir,name);
    try { const s=fs.statSync(f); if(s.isFile()&&(s.mode&0o111)) return f; }catch{}
  }
  return null;
}
export function isTermuxArm64(env=process.env, arch=process.arch) {
  return arch==='arm64'&&(env.PREFIX||'').startsWith('/data/data/com.termux/files/usr');
}
export function execute(file,args,{cwd,env=process.env,timeout=PROBE_MS}={}) {
  if(!file||!path.isAbsolute(file)) return {ok:false,output:'NO_EXECUTABLE',code:null};
  const r=spawnSync(file,args,{cwd,env,shell:false,encoding:'utf8',timeout,maxBuffer:MAX_LOG});
  return {ok:r.status===0&&!r.error,output:(r.stdout||'')+(r.stderr||''),code:r.status, timedOut:r.error?.code==='ETIMEDOUT', error:r.error?.code||null};
}
export async function doctor({env=process.env,arch=process.arch,run=execute}={}) {
  const bin={git:executable('git',env),java:executable('java',env),gradle:env.CONTINUUM_GRADLE_PATH||executable('gradle',env),aapt2:env.CONTINUUM_AAPT2_PATH||executable('aapt2',env),apksigner:executable('apksigner',env)};
  const report={};
  for(const [k,args] of Object.entries({git:['--version'],java:['-version'],gradle:['--version'],aapt2:['version'],apksigner:['version']})) {
    report[k]=await run(bin[k],args,{env,timeout:PROBE_MS});
  }
  const sdk=env.ANDROID_HOME||env.ANDROID_SDK_ROOT||null;
  const checks={
    termuxArm64:isTermuxArm64(env,arch),
    git:report.git.ok,
    java17:report.java.ok&&/(?:openjdk|java) version\s+["']?17(?:[.\s"']|$)/i.test(report.java.output),
    gradle8111:report.gradle.ok&&/\bGradle 8\.11\.1\b/.test(report.gradle.output),
    nativeAapt2:arch==='arm64'&&report.aapt2.ok,
    signer:report.apksigner.ok,
    sdk36:!!sdk&&fs.existsSync(path.join(sdk,'platforms/android-36/android.jar')),
  };
  return {schemaVersion:VERSION,status:Object.values(checks).every(Boolean)?'READY_FOR_EXPERIMENTAL_BUILD':'UNAVAILABLE',checks,bin,sdk};
}
function safeState(project,stateDir) {
  const state=path.resolve(stateDir);
  if(inside(project,state)) throw Error('State directory inside checkout');
  let parent=state;
  while(!fs.existsSync(parent)) {const next=path.dirname(parent);if(next===parent)break;parent=next;}
  if(inside(project,fs.realpathSync(parent))) throw Error('State symlink into checkout');
  fs.mkdirSync(state,{recursive:true,mode:0o700});
  if(inside(project,fs.realpathSync(state))) throw Error('State directory points into checkout');
  return state;
}
function persist(dir,report) {
  const target=path.join(dir,'latest-build.json');
  const tmp=target+'.'+randomUUID()+'.tmp';
  fs.writeFileSync(tmp,JSON.stringify(report,null,2)+'\n',{flag:'wx',mode:0o600});
  fs.renameSync(tmp,target);
  return report;
}
async function sha256(file) {
  const h=createHash('sha256');
  for await(const part of fs.createReadStream(file)) h.update(part);
  return h.digest('hex');
}
export async function build({project,stateDir,offline=false,env=process.env,arch=process.arch,run=execute,inspect=doctor}={}) {
  const root=fs.realpathSync(project);
  for(const f of ['settings.gradle.kts','app/build.gradle.kts']) if(!fs.existsSync(path.join(root,f)))throw Error('Missing trusted project file '+f);
  const state=safeState(root,stateDir);
  const tool=await inspect({env,arch,run});
  if(tool.status!=='READY_FOR_EXPERIMENTAL_BUILD')return persist(state,{schemaVersion:VERSION,status:'BLOCKED_MISSING_TOOLCHAIN',checks:tool.checks});
  const head=await run(tool.bin.git,['rev-parse','HEAD'],{cwd:root,env});
  const status=await run(tool.bin.git,['status','--porcelain','--untracked-files=normal'],{cwd:root,env});
  if(!head.ok||!/^[0-9a-f]{40}\s*$/.test(head.output)||!status.ok||status.output.trim())return persist(state,{schemaVersion:VERSION,status:'BLOCKED_UNCLEAN_SOURCE'});
  const commit=head.output.trim();
  const args=['--no-daemon','--max-workers=2','--console=plain','-Pandroid.aapt2FromMavenOverride='+tool.bin.aapt2,'-Pkotlin.compiler.execution.strategy=in-process'];
  if(offline)args.push('--offline');
  args.push(':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug','-PdeviceAiPreview=true');
  const result=await run(tool.bin.gradle,args,{cwd:root,env:{...env,GRADLE_OPTS:'-Dorg.gradle.jvmargs=-Xmx1536m'},timeout:BUILD_MS});
  if(!result.ok)return persist(state,{schemaVersion:VERSION,status:'FAILED_BUILD',commit,exitCode:result.code,reason:result.error||null,outputTail:String(result.output).slice(-2500)});
  const artifact=path.join(root,'app/build/outputs/apk/debug/app-debug.apk');
  if(!fs.existsSync(artifact)||fs.lstatSync(artifact).isSymbolicLink()||!inside(root,fs.realpathSync(artifact))||!fs.statSync(artifact).isFile()||fs.statSync(artifact).size<100) return persist(state,{schemaVersion:VERSION,status:'FAILED_NO_APK',commit});
  const staged=path.join(state,'.apk-'+randomUUID()+'.tmp');
  fs.copyFileSync(artifact,staged,fs.constants.COPYFILE_EXCL);
  fs.chmodSync(staged,0o600);
  try {
    const verified=await run(tool.bin.apksigner,['verify','--verbose','--print-certs',staged],{cwd:root,env});
    const signer=/Signer #1 certificate SHA-256 digest:\s*([0-9a-f:]{64,95})/i.exec(verified.output||'')?.[1]?.replaceAll(':','').toLowerCase();
    if(!verified.ok||!signer||!/^[a-f0-9]{64}$/.test(signer)) return persist(state,{schemaVersion:VERSION,status:'FAILED_APK_SIGNATURE',commit});
    const digest=await sha256(staged);
    const stable=path.join(state,'apk-'+digest+'.apk');
    fs.renameSync(staged,stable);
    return persist(state,{schemaVersion:VERSION,status:'EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED',commit,apk:stable,apkSha256:digest,apkBytes:fs.statSync(stable).size,signerSha256:signer,productionReady:false,deviceInstallationVerified:false,fullIndependentM4GatePassed:false,installPolicy:'OWNER_APPROVED_ANDROID_PACKAGE_INSTALLER_ONLY'});
  } finally { if(fs.existsSync(staged))fs.unlinkSync(staged); }
}
export function readStatus(stateDir) {
  const f=path.join(path.resolve(stateDir),'latest-build.json');
  if(!fs.existsSync(f))return {status:'NO_BUILD_REPORT'};
  const r=JSON.parse(fs.readFileSync(f,'utf8'));
  if(r.schemaVersion!==VERSION)throw Error('Unsupported report schema');
  return r;
}
async function main() {
  const o=parseArgs(process.argv.slice(2));
  const r=o.command==='status'?readStatus(o.stateDir):o.command==='doctor'?await doctor():await build(o);
  process.stdout.write(JSON.stringify(r,null,2)+'\n');
  if(r.status==='UNAVAILABLE'||r.status.startsWith('BLOCKED_')||r.status.startsWith('FAILED_'))process.exitCode=2;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))main().catch(e=>{process.stderr.write('Mobile Builder: '+e.message+'\n');process.exitCode=2;});
