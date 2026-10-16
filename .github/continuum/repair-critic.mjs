import {renderOwnerNodeContract} from './contract-test-template.mjs';
/**
 * Critic: rejects candidate patches before any test runs. A patch the critic
 * rejects is never applied to a build tree. Tests are the specification, so
 * test sources may only receive the syntax-only escaped-newline repair.
 */
export const LIMITS=Object.freeze({maxFiles:3,maxChangedLines:40});

const FORBIDDEN_PATHS=[
  /^\.github\/workflows\//,/^\.github\/continuum\/(repair-|developer-agent|code-analyzer|kotlin-tokenizer)/,
  // The synthesis/template/gate pipeline must not modify its own rules or
  // verifier via an automatically proposed patch.
  /^\.github\/continuum\/(development-backlog|autonomous-teamleader|backlog-repair-worker|owner-contract-worker|node-contract-synthesizer|contract-test-template|teamleader-cli|backlog-cli)\.mjs$/,
  /(^|\/)(build\.gradle(\.kts)?|settings\.gradle(\.kts)?|gradle\.properties|local\.properties)$/,/^gradle\//,
  /AndroidManifest\.xml$/,/\.(jks|keystore|p12|pem|key)$/i,/^\.continuum\//,/(^|\/)\.git(\/|$)/,
  /(ToolGate|InternetConsent|SecureToken|Signer|Identity|Governance|Credential|Secret|Permission|ConsentPolicy)/i
];
// test sources and their recorded fixtures are the specification
const TEST_PATH=/(^|\/)(test|fixtures)\/|\.test\.m?js$/;
const SYNTAX_ONLY=new Set(['KOTLIN_ESCAPED_NEWLINE']);
// The only non-syntax test modification admitted to the shared critic is
// creation of one *new* locally generated contract test at a fixed path.
// Existing tests, assertions and fixtures remain immutable specifications.
function admissibleNewContract(candidate,edit){
 const spec=candidate?.contractSpec;
 if(candidate?.strategy!=='SYNTHESIZE_NODE_CONTRACT_TEST'||
    candidate.edits.length!==1||!spec||spec.file!==edit.file||
    edit.oldText!==''||typeof edit.newText!=='string')return false;
 const expected=renderOwnerNodeContract(spec.file,spec.exportName,spec.casesJson);
 return expected!==null&&edit.newText===expected;
}
const DANGEROUS=[
  [/Runtime\.getRuntime\(\)\.exec|ProcessBuilder\s*\(|child_process|spawnSync|execSync|\beval\s*\(|new\s+Function\s*\(/,'process or code execution'],
  [/@Ignore\b|\.skip\s*\(|\btest\.todo\b|@Suppress\b|eslint-disable/,'disables checks or tests'],
  [/https?:\/\//,'introduces a network address'],
  [/System\.exit|exitProcess|process\.exit/,'terminates the process'],
  [/catch\s*\([^)]*\)\s*\{\s*\}/,'swallows exceptions']
];

// Patience-diff anchors distinguish distant small edits without treating the
// unchanged middle as changed. No speculative matching of repeated lines:
// unmatched regions are conservatively counted as changed.
function changedLines(oldText, newText) {
  const a = oldText.split("\n"), b = newText.split("\n");
  const counts = rows => {
    const m = new Map();
    rows.forEach((v, i) => {
      const entry = m.get(v);
      if (entry) entry.count++;
      else m.set(v, { count: 1, index: i });
    });
    return m;
  };
  const ac = counts(a), bc = counts(b);
  const unique = [];
  for (const [value, x] of ac) {
    const y = bc.get(value);
    if (x.count === 1 && y?.count === 1) unique.push([x.index, y.index]);
  }
  unique.sort((x, y) => x[0] - y[0]);
  // Longest increasing subsequence in new-line positions.
  const ends = [], predecessor = Array(unique.length).fill(-1);
  for (let i = 0; i < unique.length; i++) {
    let lo = 0, hi = ends.length;
    while (lo < hi) {
      const mid = (lo + hi) >> 1;
      if (unique[ends[mid]][1] < unique[i][1]) lo = mid + 1; else hi = mid;
    }
    predecessor[i] = lo ? ends[lo - 1] : -1;
    ends[lo] = i;
  }
  const matches = [];
  let cursor = ends.length ? ends[ends.length - 1] : -1;
  while (cursor >= 0) {
    matches.push(unique[cursor]);
    cursor = predecessor[cursor];
  }
  matches.reverse();
  matches.push([a.length, b.length]);
  const removed = [], added = [];
  let ai = 0, bi = 0;
  for (const [an, bn] of matches) {
    removed.push(...a.slice(ai, an));
    added.push(...b.slice(bi, bn));
    ai = an + 1; bi = bn + 1;
  }
  return { removed, added };
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
    if(TEST_PATH.test(e.file)&&!SYNTAX_ONLY.has(candidate.strategy)&&!admissibleNewContract(candidate,e))reasons.push('tests are the specification: '+e.file);
    const {removed,added}=changedLines(e.oldText??'',e.newText??'');
    changed+=Math.max(removed.length,added.length);
    const addedText=added.join('\n'),removedText=removed.join('\n');
    for(const [pattern,why] of DANGEROUS){
      const global=new RegExp(pattern.source,pattern.flags.includes('g')?pattern.flags:pattern.flags+'g');
      const newCount=(addedText.match(global)??[]).length;
      const oldCount=(removedText.match(global)??[]).length;
      if(newCount>oldCount)reasons.push(why);
    }
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
