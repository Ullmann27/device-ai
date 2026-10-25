/**
 * JARVIS-MO: security review -> NON-EXECUTABLE CCC1 task preview.
 * Untrusted OSV data and caller-selected paths confer no owner authority.
 * An independently authenticated owner grant and the existing coding-task
 * validator/orchestrator remain mandatory before execution.
 */
import {createHash} from 'node:crypto';
import {verifyOwnedSecurityProposal} from './security-review-proposal.mjs';

const sha = value => createHash('sha256').update(JSON.stringify(value)).digest('hex');
const record = x => x !== null && typeof x === 'object' && !Array.isArray(x) &&
  (Object.getPrototypeOf(x) === Object.prototype || Object.getPrototypeOf(x) === null);
const exact = (x, keys) => record(x) && Object.keys(x).sort().join('|') === [...keys].sort().join('|');
const COMMIT = /^[a-f0-9]{40}$/;
const HEX = /^[a-f0-9]{64}$/;
const NAME = /^[A-Za-z0-9@_.+:/-]{1,200}$/;
const VERSION = /^[A-Za-z0-9][A-Za-z0-9_.+!~:-]{0,79}$/;
const ADVISORY = /^[A-Za-z][A-Za-z0-9._-]{2,90}$/;
const proposalKeys = ['schema','version','project','sourceCommit','reportSha256',
  'inventorySha256','advisorySetSha256','finding','disposition','state','nextGate',
  'ccc1Ready','authorized','executionAllowed','patchAllowed','sourceAuthenticated',
  'autoMerge','dataExfiltrationAllowed','note','proposalSha256'];
const FILE = /^(?:backend\/src\/|lib\/|src\/)[A-Za-z0-9_./-]+\.(?:js|mjs)$|^app\/src\/main\/java\/[A-Za-z0-9_./-]+\.kt$/;
const FORBIDDEN = /(?:^|\/)(?:test|tests|fixtures|node_modules|build|dist|private|secrets?|credentials?|owner-data)(?:\/|\.)|(?:ToolGate|Permission|Consent|Signer|Credential|Secret|Identity|Governance|Release)/i;
const fail = () => { throw Error('OMEGA_SECURITY_CODING_PREVIEW_INVALID'); };
const previewKeys = ['schema','version','reviewProposalSha256','sourceCommit','project','taskKind',
  'suggestedTaskId','goal','chain','allowedPaths','acceptance','limits','status',
  'ownerApprovalRequired','applicabilityVerified','authorized','dispatchAllowed',
  'patchAllowed','autoMerge','previewSha256'];
const acceptance = Object.freeze([
  'Verify advisory applicability independently and document the evidence.',
  'Preserve existing behavior and prove the regression with pinned tests.',
  'Run independent verification in an isolated worktree; retain failures.',
  'Request owner approval before any patch, merge or deployment.'
]);

function allowedPath(p){
 return typeof p === 'string' && p.length <= 180 && FILE.test(p) &&
   !p.split('/').some(x => !x || x === '.' || x === '..') && !FORBIDDEN.test(p);
}

/** `selection` is an UNTRUSTED hint, not evidence of human consent. */
export function proposeSecurityCodingPreview({proposal,selection,currentCommit}={}){
 if(!exact(proposal,proposalKeys) || !verifyOwnedSecurityProposal(proposal) ||
    !exact(proposal.finding,['advisoryId','ecosystem','packageName','installedVersion','match']) ||
    !ADVISORY.test(proposal.finding.advisoryId??'') ||
    !/^[A-Za-z][A-Za-z0-9.]{1,25}$/.test(proposal.finding.ecosystem??'') ||
    !NAME.test(proposal.finding.packageName??'') ||
    proposal.finding.packageName.split('/').some(s => s === '.' || s === '..') ||
    !VERSION.test(proposal.finding.installedVersion??'') ||
    !/^[A-Za-z0-9_-]{1,80}$/.test(proposal.project??'') ||
    !exact(selection,['sourceCommit','project','allowedPaths']) ||
    !COMMIT.test(currentCommit??'') || selection.sourceCommit !== currentCommit ||
    proposal.sourceCommit !== currentCommit || selection.project !== proposal.project ||
    proposal.disposition !== 'REVIEW_EXACT_ADVISORY_MATCH' ||
    proposal.finding?.match !== 'EXACT_LISTED_VERSION' ||
    !Array.isArray(selection.allowedPaths) || selection.allowedPaths.length < 1 ||
    selection.allowedPaths.length > 3 ||
    new Set(selection.allowedPaths).size !== selection.allowedPaths.length ||
    selection.allowedPaths.some(p => !allowedPath(p))) fail();
 const paths = [...selection.allowedPaths].sort();
 const chain = paths.every(p => p.endsWith('.kt')) ? 'android' :
               paths.every(p => !p.endsWith('.kt')) ? 'node' : null;
 if (!chain) fail();
 const {advisoryId,ecosystem,packageName,installedVersion} = proposal.finding;
 const goal = `Investigate ${advisoryId} applicability for ${ecosystem}/${packageName}@${installedVersion}; propose a minimal defensive code repair with independently verified regressions.`;
 if(goal.length > 600 || !HEX.test(proposal.proposalSha256)) fail();
 const body = {
  schema:'CONTINUUM_SECURITY_CODING_PREVIEW_V1',version:1,
  reviewProposalSha256:proposal.proposalSha256, sourceCommit:currentCommit,
  project:proposal.project, taskKind:'BUGFIX',
  suggestedTaskId:'omega-security-'+sha([proposal.proposalSha256,paths]).slice(0,20),
  goal,chain,allowedPaths:paths,acceptance:[...acceptance],
  limits:{maxModelCalls:1,maxContextChars:8192},
  status:'UNTRUSTED_SECURITY_CODING_PREVIEW',
  ownerApprovalRequired:true,applicabilityVerified:false,authorized:false,
  dispatchAllowed:false,patchAllowed:false,autoMerge:false
 };
 return Object.freeze({...body,previewSha256:sha(body)});
}

/** This check verifies non-executable status, not identity or approval. */
export function verifySecurityCodingPreview(preview){
 if(!exact(preview,previewKeys) || !HEX.test(preview.previewSha256??''))return false;
 const {previewSha256,...body}=preview;
 return previewSha256===sha(body) &&
  body.schema==='CONTINUUM_SECURITY_CODING_PREVIEW_V1' && body.version===1 &&
  body.ownerApprovalRequired===true && body.applicabilityVerified===false &&
  body.authorized===false && body.dispatchAllowed===false &&
  body.patchAllowed===false && body.autoMerge===false &&
  body.status==='UNTRUSTED_SECURITY_CODING_PREVIEW' &&
  body.taskKind==='BUGFIX' && COMMIT.test(body.sourceCommit??'') &&
  HEX.test(body.reviewProposalSha256??'') && /^[A-Za-z0-9_-]{1,80}$/.test(body.project??'') &&
  typeof body.goal==='string' && body.goal.length<=600 && !/[\x00-\x1f\x7f]/.test(body.goal) &&
  /^omega-security-[a-f0-9]{20}$/.test(body.suggestedTaskId??'') &&
  body.limits?.maxModelCalls===1 && body.limits?.maxContextChars===8192 &&
  Array.isArray(body.acceptance) && JSON.stringify(body.acceptance)===JSON.stringify(acceptance) &&
  Array.isArray(body.allowedPaths) && body.allowedPaths.length>0 &&
  body.allowedPaths.length<=3 && body.allowedPaths.every(allowedPath) &&
  new Set(body.allowedPaths).size===body.allowedPaths.length &&
  ((body.chain==='android' && body.allowedPaths.every(p=>p.endsWith('.kt'))) ||
   (body.chain==='node' && body.allowedPaths.every(p=>!p.endsWith('.kt'))));
}
