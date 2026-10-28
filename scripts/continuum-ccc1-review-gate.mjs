#!/usr/bin/env node
/** Read-only handoff from optional coding bridge to CCC1 owner review.
 * This code cannot submit, apply, execute, commit or approve any patch.
 */
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const FREE = new Set(['claude-fable-5.1:free','gpt-6-astra:free']);
const KNOWN = Object.freeze({path:'src/add.mjs',old:'return Math.abs(a) + Math.abs(b);',replacement:'return a + b;'});
const isObject = v => v !== null && typeof v === 'object' && !Array.isArray(v);
const digest = s => crypto.createHash('sha256').update(s).digest('hex');

/** The synthetic patch is an audit artifact, never a real project patch. */
export function assessCcc1BridgeProposal(report) {
  if (!isObject(report) || report.schema !== 'continuum-optional-coding-bridge-v1' ||
      report.mode !== 'SYNTHETIC_ONLY') throw Error('UNTRUSTED_BRIDGE_REPORT');
  if (report.repositoryAccess !== false || report.patchApplied !== false ||
      report.ownerReviewRequired !== true) throw Error('UNAUTHORIZED_WRITE_REPORTED');
  if (!['MOCKED_OFFLINE','SELF_HOSTED_LOOPBACK','EXPERIENTIAL_FREE_ONLY'].includes(report.transportKind))
    throw Error('UNKNOWN_TRANSPORT');
  if (report.transportKind === 'EXPERIENTIAL_FREE_ONLY' && !FREE.has(report.model))
    throw Error('BILLABLE_MODEL_REFUSED');
  if (report.transportKind === 'SELF_HOSTED_LOOPBACK' &&
      (typeof report.model!=='string'||!/^local\/[A-Za-z0-9][\w.-]{0,63}$/.test(report.model)))
    throw Error('UNKNOWN_LOCAL_MODEL');
  if (report.transportKind==='MOCKED_OFFLINE' && !FREE.has(report.model))
    throw Error('INVALID_MOCK_MODEL');
  if (report.completed !== true || report.proposalVerifiedOnIsolatedCopy !== true ||
      report.observedTestCall !== true || !Array.isArray(report.observedReadCalls) ||
      report.observedReadCalls.length !== 2 ||
      !['src/add.mjs','test/add.test.mjs'].every(x=>report.observedReadCalls.includes(x)))
    throw Error('MISSING_SYNTHETIC_TEST_PROOF');
  const baseline=report.testResult, candidate=report.proposalReview?.testResult;
  if (!isObject(baseline)||baseline.tests!==3||baseline.pass!==2||baseline.fail!==1||
      !isObject(candidate)||candidate.tests!==3||candidate.pass!==3||candidate.fail!==0||candidate.exitCode!==0)
    throw Error('UNVERIFIED_SYNTHETIC_REGRESSION');
  if (typeof report.proposal !== 'string' || report.proposal.length > 4096)
    throw Error('INVALID_PROPOSAL_BYTES');
  let patch;
  try {patch=JSON.parse(report.proposal);} catch {throw Error('INVALID_PROPOSAL_JSON');}
  if (!isObject(patch) || Object.keys(patch).sort().join(',')!=='old,path,replacement' ||
      Object.keys(KNOWN).some(k=>patch[k]!==KNOWN[k])) throw Error('OUT_OF_SCOPE_PATCH');
  const serialized=JSON.stringify(KNOWN);
  const digestHash=digest(serialized);
  // Returned data contains neither executable commands nor private paths.
  return Object.freeze({schema:'continuum-ccc1-synthetic-review-v1',
    source:'PUBLIC_SYNTHETIC_ONLY',transportKind:report.transportKind,modelLabel:report.model,
    patchSha256:digestHash,patchSummary:'Synthetic addition correction (one reviewed line)',
    baseline:{tests:3,pass:2,fail:1},candidate:{tests:3,pass:3,fail:0},
    handoffAllowedToOwnerReview:true,ownerApprovalGranted:false,
    ccc1TaskSubmitted:false,ccc1PatchApplied:false,privateRepositoryAccessGranted:false,
    modelCapabilityIndependentlyVerified:false,
    nextGate:'Explicit owner review and separate CCC1 preflight required'});
}

if (process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  try {
    if (process.argv.length!==3) throw Error('USAGE');
    const stat=fs.lstatSync(process.argv[2]);
    if (!stat.isFile()||stat.isSymbolicLink()||stat.size>32768||stat.size===0)
      throw Error('UNSAFE_REPORT_FILE');
    const report=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
    process.stdout.write(JSON.stringify(assessCcc1BridgeProposal(report),null,2)+'\n');
  } catch (e) {
    const safe=/^[A-Z0-9_]+$/.test(e?.message??'')?e.message:'CCC1_REVIEW_GATE_FAILED';
    process.stderr.write('CONTINUUM_CCC1_REVIEW_BLOCKED='+safe+'\n');
    process.exitCode=69;
  }
}
