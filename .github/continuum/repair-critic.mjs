/**
 * Critic: rejects candidate patches before any test runs. A patch the critic
 * rejects is never applied to a build tree. Tests are the specification, so
 * test sources may only receive the syntax-only escaped-newline repair.
 */
export const LIMITS=Object.freeze({maxFiles:3,maxChangedLines:40});

const FORBIDDEN_PATHS=[
  /^\.github\/workflows\//,/^\.github\/continuum\/(repair-|developer-agent|code-analyzer|kotlin-tokenizer)/,
  /(^|\/)(build\.gradle(\.kts)?|settings\.gradle(\.kts)?|gradle\.properties|local\.properties)$/,/^gradle\//,
  /AndroidManifest\.xml$/,/\.(jks|keystore|p12|pem|key)$/i,/^\.continuum\//,/(^|\/)\.git(\/|$)/,
  /(ToolGate|InternetConsent|SecureToken|Signer|Identity|Governance|Credential|Secret|Permission|ConsentPolicy)/i
];
// test sources and their recorded fixtures are the specification
const TEST_PATH=/(^|\/)(test|fixtures)\/|\.test\.m?js$/;
const SYNTAX_ONLY=new Set(['KOTLIN_ESCAPED_NEWLINE']);
const DANGEROUS=[
  [/Runtime\.getRuntime\(\)\.exec|ProcessBuilder\s*\(|child_process|spawnSync|execSync|\beval\s*\(|new\s+Function\s*\(/,'process or code execution'],
  [/@Ignore\b|\.skip\s*\(|\btest\.todo\b|@Suppress\b|eslint-disable/,'disables checks or tests'],
  [/https?:\/\//,'introduces a network address'],
  [/System\.exit|exitProcess|process\.exit/,'terminates the process'],
  [/catch\s*\([^)]*\)\s*\{\s*\}/,'swallows exceptions']
];

function changedLines(oldText,newText){
  const a=oldText.split('\n'),b=newText.split('\n');
  let p=0;while(p<a.length&&p<b.length&&a[p]===b[p])p++;
  let s=0;while(s<a.length-p&&s<b.length-p&&a[a.length-1-s]===b[b.length-1-s])s++;
  return {removed:a.slice(p,a.length-s),added:b.slice(p,b.length-s)};
}

export function critique(candidate){
  const reasons=[];
  const edits=candidate?.edits;
  if(!Array.isArray(edits)||!edits.length)return {approved:false,risk:1,reasons:['empty patch']};
  const files=new Set(edits.map(e=>e.file));
  if(files.size>LIMITS.maxFiles)reasons.push(`touches ${files.size} files (max ${LIMITS.maxFiles})`);
  let changed=0,addedAsserts=0,removedAsserts=0,bangDelta=0;
  for(const e of edits){
    if(typeof e.file!=='string'||e.file.includes('..')||e.file.startsWith('/')||/[\0\r\n]/.test(e.file)){reasons.push('invalid path');continue;}
    if(FORBIDDEN_PATHS.some(p=>p.test(e.file)))reasons.push('protected path '+e.file);
    if(TEST_PATH.test(e.file)&&!SYNTAX_ONLY.has(candidate.strategy))reasons.push('tests are the specification: '+e.file);
    const {removed,added}=changedLines(e.oldText??'',e.newText??'');
    changed+=Math.max(removed.length,added.length);
    const addedText=added.join('\n'),removedText=removed.join('\n');
    for(const [pattern,why] of DANGEROUS)if(pattern.test(addedText)&&!pattern.test(removedText))reasons.push(why);
    addedAsserts+=(addedText.match(/\bassert\w*\s*\(/g)??[]).length;
    removedAsserts+=(removedText.match(/\bassert\w*\s*\(/g)??[]).length;
    bangDelta+=(addedText.match(/!!/g)??[]).length-(removedText.match(/!!/g)??[]).length;
  }
  if(changed>LIMITS.maxChangedLines)reasons.push(`changes ${changed} lines (max ${LIMITS.maxChangedLines})`);
  if(removedAsserts>addedAsserts)reasons.push('removes assertions');
  if(bangDelta>0)reasons.push('adds non-null assertions (!!)');
  const revert=String(candidate.strategy).startsWith('REVERT_');
  const risk=Math.min(1,changed/LIMITS.maxChangedLines*0.5+(candidate.strategy==='REVERT_FILE_TO_PARENT'?0.4:revert?0.25:0)+(1-(candidate.prior??0.5))*0.3);
  return {approved:reasons.length===0,risk:Number(risk.toFixed(3)),reasons,changedLines:changed,
    reviewRequired:revert||changed>10};
}
