// CCC1's owned-project evidence profile. No executor, repair pipeline or permission grant.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {structuralJUnitMarkup} from './m4-junit-evidence.mjs';
import {projectPlanProblems,projectPlanSha256} from '../../scripts/continuum-project-plan.mjs';

export const OWNED_RECEIPT_SCHEMA='continuum-owned-build-receipt';
const sha=b=>createHash('sha256').update(b).digest('hex');
const hex=x=>typeof x==='string'&&/^[0-9a-f]{64}$/.test(x);

/** Bounded descriptor read; every ancestor and the leaf must be ordinary and in scope. */
export function readOwnedFile(root,relative,maxBytes=2*1024*1024) {
  if(!path.isAbsolute(root)||typeof relative!=='string'||!relative||path.isAbsolute(relative)||
    relative.includes('\\')||relative.split('/').some(p=>!p||p==='.'||p==='..'))
    throw Error('UNSAFE_EVIDENCE_PATH');
  let file=root;
  for(const part of [null,...relative.split('/')]) {
    if(part!==null) file=path.join(file,part);
    const st=fs.lstatSync(file);
    const leaf=file===path.join(root,relative);
    if(st.isSymbolicLink()||!(leaf?st.isFile():st.isDirectory())) throw Error('UNSAFE_EVIDENCE_PATH');
  }
  const fd=fs.openSync(file,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);
  try {
    const stat=fs.fstatSync(fd);
    if(!stat.isFile()||stat.size<1||stat.size>maxBytes) throw Error('UNSAFE_EVIDENCE_SIZE');
    const chunks=[],buffer=Buffer.alloc(Math.min(maxBytes+1,64*1024));
    let total=0,n;
    while((n=fs.readSync(fd,buffer,0,Math.min(buffer.length,maxBytes+1-total),null))>0) {
      total+=n;
      if(total>maxBytes) throw Error('UNSAFE_EVIDENCE_SIZE');
      chunks.push(Buffer.from(buffer.subarray(0,n)));
    }
    const after=fs.fstatSync(fd);
    if(total!==stat.size||after.size!==stat.size||after.mtimeMs!==stat.mtimeMs) throw Error('EVIDENCE_CHANGED');
    return {bytes:Buffer.concat(chunks),stat};
  } finally {fs.closeSync(fd);}
}

function attributes(text) {
  const out={},re=/([A-Za-z_:][A-Za-z0-9_:.-]*)\s*=\s*("[^"<]*"|'[^'<]*')/g;
  let offset=0;
  for(const m of text.matchAll(re)) {
    if(text.slice(offset,m.index).trim()||Object.hasOwn(out,m[1])) throw Error('INVALID_XML_ATTRIBUTES');
    out[m[1]]=m[2].slice(1,-1);offset=m.index+m[0].length;
  }
  if(text.slice(offset).trim()) throw Error('INVALID_XML_ATTRIBUTES');
  return out;
}
/** Small structural parser for Gradle/lint's report subset. Logs cannot become test elements. */
function elements(xml,rootName) {
  if(typeof xml!=='string') throw Error('INVALID_XML');
  const safeLogs=xml.replace(/<(system-out|system-err)>(?:<!\[CDATA\[[\s\S]*?\]\]>|[^<]*)<\/\1>/g,
    (_,name)=>'<'+name+'></'+name+'>');
  const text=structuralJUnitMarkup(safeLogs);
  const stack=[],nodes=[];let offset=0,rootSeen=false,declaration=false;
  while(offset<text.length) {
    const begin=text.indexOf('<',offset);
    if(begin<0){if(text.slice(offset).trim())throw Error('INVALID_XML_TEXT');break;}
    if(text.slice(offset,begin).trim())throw Error('INVALID_XML_TEXT');
    let quote=null,end=-1;
    for(let i=begin+1;i<text.length;i++) {
      const c=text[i];
      if(quote){if(c===quote)quote=null;else if(c==='<')throw Error('INVALID_XML');}
      else if(c==='"'||c==="'")quote=c;
      else if(c==='<')throw Error('INVALID_XML');
      else if(c==='>'){end=i;break;}
    }
    if(end<0)throw Error('INVALID_XML');
    const tag=text.slice(begin+1,end).trim();offset=end+1;
    if(tag.startsWith('?')) {
      if(declaration||rootSeen||!/^\?xml\s[^<>]*\?$/.test(tag))throw Error('INVALID_XML_DECLARATION');
      declaration=true;continue;
    }
    if(tag.startsWith('/')) {
      if(stack.pop()!==tag.slice(1))throw Error('INVALID_XML_CLOSING');
      continue;
    }
    const name=/^([A-Za-z][A-Za-z0-9_-]*)/.exec(tag)?.[1];
    if(!name)throw Error('INVALID_XML_ELEMENT');
    const parent=stack.at(-1)??null;
    if(!parent) {
      if(rootSeen||name!==rootName)throw Error('INVALID_XML_ROOT');
      rootSeen=true;
    }
    const empty=tag.endsWith('/');
    nodes.push({name,parent,attrs:attributes(tag.slice(name.length,empty?-1:undefined))});
    if(!empty)stack.push(name);
  }
  if(!rootSeen||stack.length)throw Error('INCOMPLETE_XML');
  return nodes;
}
export function parseOwnedJUnit(xml,plan) {
  const nodes=elements(xml,'testsuite'),root=nodes[0],tests=nodes.filter(n=>n.name==='testcase');
  const allowed={testsuite:[null],properties:['testsuite'],property:['properties'],
    testcase:['testsuite'],'system-out':['testsuite','testcase'],'system-err':['testsuite','testcase']};
  for(const node of nodes)
    if(!allowed[node.name]?.includes(node.parent))throw Error('INVALID_JUNIT_STRUCTURE');
  const cases=tests.map(n=>n.attrs.name).sort(),expected=[...plan.tests.cases].sort();
  if(root.attrs.name!==plan.tests.unitClass||root.attrs.tests!==String(expected.length)||
    ['skipped','failures','errors'].some(k=>root.attrs[k]!=='0')||
    tests.some(n=>n.attrs.classname!==plan.tests.unitClass)||
    JSON.stringify(cases)!==JSON.stringify(expected))throw Error('INCOMPLETE_JUNIT_EVIDENCE');
  return {executed:tests.length,failed:0,skipped:0,cases};
}
export function parseOwnedLint(xml) {
  const nodes=elements(xml,'issues');
  let warnings=0;
  for(const node of nodes.slice(1)) {
    if(node.name==='issue'&&node.parent==='issues') {
      if(node.attrs.severity==='Warning'||node.attrs.severity==='Information')warnings++;
      else throw Error('FAILING_LINT_EVIDENCE');
    } else if(node.name!=='location'||node.parent!=='issue') throw Error('INVALID_LINT_STRUCTURE');
  }
  return {errors:0,warnings};
}
export function readOwnedBuildEvidence(project,plan,notBefore) {
  const paths={unit:'app/build/test-results/testDebugUnitTest/TEST-'+plan.tests.unitClass+'.xml',
    lint:'app/build/reports/lint-results-debug.xml'};
  const result={};
  for(const [kind,file] of Object.entries(paths)) {
    const {bytes,stat}=readOwnedFile(project,file);
    // Some supported filesystems round timestamps; the executor also archives the
    // exact previous output paths before --rerun-tasks, so freshness does not rely on time alone.
    if(stat.mtimeMs<notBefore-2000)throw Error('STALE_BUILD_EVIDENCE');
    result[kind]={file,sha256:sha(bytes),...(kind==='unit'?parseOwnedJUnit(bytes.toString('utf8'),plan):parseOwnedLint(bytes.toString('utf8')))};
  }
  return result;
}

/** New CCC1 evidence profile: a build has no failing repair baseline and cannot pretend to be a repair receipt. */
export function verifyOwnedProjectReceipt(receipt,{plan,sourceSha256,project,artifactRoot}={}) {
  const reasons=[],r=receipt??{};
  try {
    if(projectPlanProblems(plan).length||r.schema!==OWNED_RECEIPT_SCHEMA||r.version!==1||
      r.status!=='EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED')throw Error('INVALID_BUILD_RECEIPT');
    if(r.recipe!==plan.id||r.planSha256!==projectPlanSha256(plan)||
      !hex(sourceSha256)||r.sourceSha256!==sourceSha256)throw Error('PLAN_OR_SOURCE_BINDING');
    if(r.installed!==false||r.deviceVerified!==false||r.productionReady!==false)throw Error('UNSUPPORTED_DEVICE_CLAIM');
    if(!['termux-arm64','host-sdk'].includes(r.toolchainProfile)||
      r.packageName!==plan.app.packageName||r.versionCode!==plan.app.versionCode||
      !hex(r.apkSha256)||!hex(r.signerSha256))throw Error('INVALID_ARTIFACT_IDENTITY');
    const source=readOwnedFile(project,'continuum-source-manifest.json');
    if(sha(source.bytes)!==sourceSha256)throw Error('SOURCE_MANIFEST_CHANGED');
    const manifest=JSON.parse(source.bytes);
    if(manifest.version!==2||manifest.planSha256!==r.planSha256)throw Error('SOURCE_PLAN_CHANGED');
    for(const [file,digest] of Object.entries(manifest.files)) {
      if(sha(readOwnedFile(project,file).bytes)!==digest)throw Error('SOURCE_FILE_CHANGED');
    }
    if(!Number.isSafeInteger(r.startedAt)||!Number.isSafeInteger(r.finishedAt)||
      r.finishedAt<r.startedAt||r.finishedAt-r.startedAt>plan.limits.maxBuildMillis+120000)throw Error('INVALID_BUILD_TIMES');
    if(!r.gradle||r.gradle.exitCode!==0||r.gradle.timedOut!==false||
      JSON.stringify(r.gradle.tasks)!==JSON.stringify(plan.build.tasks))throw Error('INVALID_GRADLE_PROOF');
    const artifact=path.relative(artifactRoot,r.artifact).split(path.sep).join('/');
    if(sha(readOwnedFile(artifactRoot,artifact,plan.limits.maxApkBytes).bytes)!==r.apkSha256)throw Error('ARTIFACT_CHANGED');
    for(const kind of ['unit','lint','log']) {
      const proof=r.evidence?.[kind];
      if(!proof||!hex(proof.sha256))throw Error('MISSING_BUILD_EVIDENCE');
      const {bytes}=readOwnedFile(artifactRoot,proof.file);
      if(sha(bytes)!==proof.sha256)throw Error('BUILD_EVIDENCE_CHANGED');
      if(kind==='unit')parseOwnedJUnit(bytes.toString('utf8'),plan);
      if(kind==='lint')parseOwnedLint(bytes.toString('utf8'));
    }
    return {verdict:'CONFIRMED',reasons:['plan, source, executed unit tests, lint and APK bytes match; installation and device tests remain pending']};
  } catch(error) {reasons.push(error.code||error.message||'INVALID_BUILD_EVIDENCE');}
  return {verdict:'INCONCLUSIVE',reasons};
}
