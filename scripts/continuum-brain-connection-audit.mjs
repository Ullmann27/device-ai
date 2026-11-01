#!/usr/bin/env node
/** Static wiring inventory, not a runtime, inference, signing or Fold7 proof. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
const BASE='app/src/main/java/at/heinz/deviceai/';
const EDGES = Object.freeze([
  ['owner-session-source', 'AgentActivity.kt', /localConversationContext\.sessionEvidence\(sessionId\)/],
  ['session-verified-reflex', 'AgentActivity.kt', /CertifiedLocationReflex\.routeFromSession\(/],
  ['explicit-verified-reflex', 'AgentActivity.kt', /CertifiedLocationReflex\.routeExplicit\(/],
  ['terminal-fail-closed', 'AgentActivity.kt', /CertifiedLocationReflex\.Routing\.Withheld/],
  ['raw-session-evidence', 'cognitive/LocalConversationContext.kt', /originalUserRequest/],
  ['research-is-not-owner', 'cognitive/LocalConversationContext.kt', /isOwnerTurn\s*=\s*false/],
  ['source-sha-binding', 'cognitive/CertifiedLocationReflex.kt', /EvidenceBoundNaturalLocationProbe\.digest\(raw\)/],
  ['independent-span-verifier', 'cognitive/CertifiedLocationReflex.kt', /EvidenceBoundNaturalLocationVerifier\.verify\(/],
  ['verified-role-adapter', 'cognitive/CertifiedLocationReflex.kt', /EntityRelationLocationShadowAdapter\.encode\(/],
  ['memory-evidence-entry', 'learning/ContinuumMemoryDialogue.kt', /CertifiedLocationReflex\.answerFromConfirmedFacts\(/],
  ['cognitive-conversation-gateway', 'learning/ContinuumCognitiveRuntime.kt', /core\.replyWithEvidenceInConversation\(/],
  ['memory-conversation-runtime', 'learning/ContinuumLearningCore.kt', /replyWithEvidenceScoped\(input,conversationId/],
  ['web-consent-ask', 'AgentActivity.kt', /InternetAccessMode\.ASK/],
  ['web-consent-always-on', 'AgentActivity.kt', /InternetAccessMode\.ALWAYS_ON/],
  ['runtime-model-route', 'AgentActivity.kt', /runLocal\(request/]
]);
const RESEARCH = Object.freeze([
  ['CDX2 pointer research only', 'tools/continuum-span-research/Cdx2PointerCandidate.kt'],
  ['learned evidence ranker research', BASE+'learning/span/VerifiedLocationSpanResearch.kt']
]);
export function audit(root) {
  const edges=EDGES.map(([key,relative,pattern])=>{
    const file=BASE+relative, full=path.join(root,file);
    if(!fs.existsSync(full)) return {key,file,status:'MISSING_SOURCE'};
    const bytes=fs.readFileSync(full);
    return {key,file,status:pattern.test(bytes.toString('utf8'))?'STATIC_CALL_FOUND':'NOT_WIRED',
      sourceSha256:crypto.createHash('sha256').update(bytes).digest('hex')};
  });
  const research=RESEARCH.map(([name,file])=>({name,file,
    status:fs.existsSync(path.join(root,file))?'RESEARCH_SOURCE_PRESENT_NOT_PROMOTED':'MISSING_SOURCE'}));
  return {schema:'CONTINUUM_BRAIN_WIRING_AUDIT_V1',scope:'STATIC_SOURCE_ONLY_NOT_DEVICE_PROOF',
    edges,research,allRequiredSourceEdgesFound:edges.every(x=>x.status==='STATIC_CALL_FOUND'),
    ownerApkPublished:false,shadowPromoted:false};
}
if(process.argv[1]&&path.resolve(process.argv[1])===path.resolve(new URL(import.meta.url).pathname)) {
  const root=path.resolve(process.argv[2]||'.'), output=audit(root);
  console.log(JSON.stringify(output,null,2));
  if(!output.allRequiredSourceEdgesFound)process.exitCode=1;
}
