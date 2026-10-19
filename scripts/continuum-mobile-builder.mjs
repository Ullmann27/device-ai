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
/**
 * Restore the pinned owner-installed Termux toolchain for RUN_COMMAND callers.
 * Android starts node without the interactive shell's one-shot JAVA_HOME/SDK exports.
 * Never download tools or substitute a PC; missing paths still fail doctor().
 */
export function resolveTermuxEnvironment(env=process.env,arch=process.arch,exists=fs.existsSync) {
  const result={...env};
  if(!isTermuxArm64(env,arch))return result;
  const prefix=env.PREFIX;
  const home=env.HOME;
  if(!home||!path.isAbsolute(home))return result;
  const javaHome=path.join(prefix,'lib/jvm/java-17-openjdk');
  const sdkHome=path.join(home,'android-sdk');
  const gradle=path.join(home,'continuum-toolchains/gradle-8.11.1/bin/gradle');
  const aapt2=path.join(prefix,'bin/aapt2');
  if(!result.JAVA_HOME&&exists(path.join(javaHome,'bin/java')))result.JAVA_HOME=javaHome;
  if(!result.ANDROID_HOME&&!result.ANDROID_SDK_ROOT&&exists(path.join(sdkHome,'platforms/android-36/android.jar'))) {
    result.ANDROID_HOME=sdkHome;
    result.ANDROID_SDK_ROOT=sdkHome;
  } else {
    if(!result.ANDROID_HOME&&result.ANDROID_SDK_ROOT)result.ANDROID_HOME=result.ANDROID_SDK_ROOT;
    if(!result.ANDROID_SDK_ROOT&&result.ANDROID_HOME)result.ANDROID_SDK_ROOT=result.ANDROID_HOME;
  }
  if(!result.CONTINUUM_GRADLE_PATH&&exists(gradle))result.CONTINUUM_GRADLE_PATH=gradle;
  if(!result.CONTINUUM_AAPT2_PATH&&exists(aapt2))result.CONTINUUM_AAPT2_PATH=aapt2;
  const current=(result.PATH||'').split(path.delimiter).filter(Boolean);
  const required=[result.JAVA_HOME&&path.join(result.JAVA_HOME,'bin'),path.join(prefix,'bin')].filter(Boolean);
  result.PATH=[...required.filter(dir=>!current.includes(dir)),...current].join(path.delimiter);
  return result;
}

export function execute(file,args,{cwd,env=process.env,timeout=PROBE_MS}={}) {
  if(!file||!path.isAbsolute(file)) return {ok:false,output:'NO_EXECUTABLE',code:null};
  const r=spawnSync(file,args,{cwd,env,shell:false,encoding:'utf8',timeout,maxBuffer:MAX_LOG});
  return {ok:r.status===0&&!r.error,output:(r.stdout||'')+(r.stderr||''),code:r.status, timedOut:r.error?.code==='ETIMEDOUT', error:r.error?.code||null};
}
export async function doctor({env=process.env,arch=process.arch,run=execute}={}) {
  const resolvedEnv=resolveTermuxEnvironment(env,arch);
  const bin={git:executable('git',resolvedEnv),java:executable('java',resolvedEnv),gradle:resolvedEnv.CONTINUUM_GRADLE_PATH||executable('gradle',resolvedEnv),aapt2:resolvedEnv.CONTINUUM_AAPT2_PATH||executable('aapt2',resolvedEnv),apksigner:executable('apksigner',resolvedEnv)};
  const report={};
  for(const [k,args] of Object.entries({git:['--version'],java:['-version'],gradle:['--version'],aapt2:['version'],apksigner:['version']})) {
    report[k]=await run(bin[k],args,{env:resolvedEnv,timeout:PROBE_MS});
  }
  const sdk=resolvedEnv.ANDROID_HOME||resolvedEnv.ANDROID_SDK_ROOT||null;
  const checks={
    termuxArm64:isTermuxArm64(resolvedEnv,arch),
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
/** Diagnostic-only JUnit extraction; old or malformed XML never certifies a build. */
export function failedJvmTests(project, startedAt) {
  const dir = path.join(project, 'app/build/test-results/testDebugUnitTest');
  const failures = [];
  let files;
  try { files = fs.readdirSync(dir).filter(name => /^TEST-[A-Za-z0-9_.$-]+\.xml$/.test(name)).sort(); }
  catch { return failures; }
  for (const name of files.slice(0, 400)) {
    if (failures.length >= 20) break;
    try {
      const file = path.join(dir, name);
      const stat = fs.lstatSync(file);
      // Gradle may leave old XML from a previous invocation; never report it as a new failure.
      if (!stat.isFile() || stat.isSymbolicLink() ||
          stat.size > 2 * 1024 * 1024 || stat.mtimeMs < startedAt - 2000) continue;
      const xml = fs.readFileSync(file, 'utf8');
      for (const match of xml.matchAll(/<testcase\b([^>]*)>([\s\S]*?)<\/testcase>/g)) {
        if (!/<(?:failure|error)(?:\s|>)/.test(match[2])) continue;
        const attribute = key => {
          const m = new RegExp('(?:^|\\s)' + key + '="([^"]{0,300})"').exec(match[1]);
          return m ? m[1].replace(/&(?:quot|apos|lt|gt|amp);/g,
            entity => ({'&quot;':'"','&apos;':"'",'&lt;':'<','&gt;':'>','&amp;':'&'}[entity])).slice(0, 160) : '';
        };
        failures.push({className: attribute('classname'), testName: attribute('name')});
        if (failures.length >= 20) break;
      }
    } catch { /* No diagnostic may override the real Gradle exit status. */ }
  }
  return failures;
}

/** apksigner 36 prints "Signer #1"; newer releases can print "V2 Signer:". */
export function parseSignerSha256(output) {
  for (const line of String(output).split(/\r?\n/)) {
    const match=/^(?:Signer #\d+|Signer \([^)]{1,100}\)|V\d+(?:\.\d+)? Signer):?\s+certificate SHA-256 digest:\s*([a-fA-F0-9:]{64,95})\s*$/.exec(line.trim());
    if (!match) continue;
    const digest=match[1].replaceAll(':','').toLowerCase();
    if (/^[a-f0-9]{64}$/.test(digest)) return digest;
  }
  return null;
}

export async function build({project,stateDir,offline=false,env=process.env,arch=process.arch,run=execute,inspect=doctor}={}) {
  const resolvedEnv=resolveTermuxEnvironment(env,arch);
  const root=fs.realpathSync(project);
  for(const f of ['settings.gradle.kts','app/build.gradle.kts']) if(!fs.existsSync(path.join(root,f)))throw Error('Missing trusted project file '+f);
  const state=safeState(root,stateDir);
  const tool=await inspect({env:resolvedEnv,arch,run});
  if(tool.status!=='READY_FOR_EXPERIMENTAL_BUILD')return persist(state,{schemaVersion:VERSION,status:'BLOCKED_MISSING_TOOLCHAIN',checks:tool.checks});
  const head=await run(tool.bin.git,['rev-parse','HEAD'],{cwd:root,env:resolvedEnv});
  const status=await run(tool.bin.git,['status','--porcelain','--untracked-files=normal'],{cwd:root,env:resolvedEnv});
  if(!head.ok||!/^[0-9a-f]{40}\s*$/.test(head.output)||!status.ok||status.output.trim())return persist(state,{schemaVersion:VERSION,status:'BLOCKED_UNCLEAN_SOURCE'});
  const commit=head.output.trim();
  const unchangedSource = async () => {
    const h=await run(tool.bin.git,['rev-parse','HEAD'],{cwd:root,env:resolvedEnv});
    const s=await run(tool.bin.git,['status','--porcelain','--untracked-files=normal'],{cwd:root,env:resolvedEnv});
    return h.ok && h.output.trim()===commit && s.ok && !s.output.trim();
  };
  const args=['--no-daemon','--max-workers=2','--console=plain','-Pandroid.aapt2FromMavenOverride='+tool.bin.aapt2,'-Pkotlin.compiler.execution.strategy=in-process','-PdeviceAiMobileAidlFallback=true'];
  if(offline)args.push('--offline');
  args.push(':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug','-PdeviceAiPreview=true');
  const buildStartedAt=Date.now();
  const result=await run(tool.bin.gradle,args,{cwd:root,env:{...resolvedEnv,GRADLE_OPTS:'-Dorg.gradle.jvmargs=-Xmx1536m'},timeout:BUILD_MS});
  if(!result.ok)return persist(state,{schemaVersion:VERSION,status:'FAILED_BUILD',commit,exitCode:result.code,reason:result.error||null,outputTail:String(result.output).slice(-2500),failedJvmTests:failedJvmTests(root,buildStartedAt)});
  if(!await unchangedSource())return persist(state,{schemaVersion:VERSION,status:'BLOCKED_SOURCE_CHANGED_DURING_BUILD',commit});
  const artifact=path.join(root,'app/build/outputs/apk/debug/app-debug.apk');
  if(!fs.existsSync(artifact)||fs.lstatSync(artifact).isSymbolicLink()||!inside(root,fs.realpathSync(artifact))||!fs.statSync(artifact).isFile()||fs.statSync(artifact).size<100) return persist(state,{schemaVersion:VERSION,status:'FAILED_NO_APK',commit});
  const staged=path.join(state,'.apk-'+randomUUID()+'.tmp');
  fs.copyFileSync(artifact,staged,fs.constants.COPYFILE_EXCL);
  fs.chmodSync(staged,0o600);
  try {
    const verified=await run(tool.bin.apksigner,['verify','--verbose','--print-certs',staged],{cwd:root,env:resolvedEnv});
    const signer=verified.ok?parseSignerSha256(verified.output):null;
    if(!verified.ok||!signer)return persist(state,{schemaVersion:VERSION,status:'FAILED_APK_SIGNATURE',commit,
      signatureFailure:verified.ok?'SIGNER_CERTIFICATE_DIGEST_UNRECOGNIZED':'SIGNATURE_VERIFICATION_FAILED',
      verifierExitCode:verified.code??null,verifierError:verified.error??null,
      verifierOutputTail:String(verified.output||'').slice(-1800)});
    const digest=await sha256(staged);
    if(!await unchangedSource())return persist(state,{schemaVersion:VERSION,status:'BLOCKED_SOURCE_CHANGED_DURING_BUILD',commit});
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
