// Frozen independent admission fixtures, not a production registry/grant or solver.
const H='a'.repeat(64),S='b'.repeat(40);
export function fresh(){return {
 now:1000,
 profiles:[{agentId:'bot-atlas',workspaceId:'workspace-bot-atlas'},{agentId:'bot-forge',workspaceId:'workspace-bot-forge'},{agentId:'bot-lint',workspaceId:'workspace-bot-lint'}],
 canonical:{taskId:'t-'+'c'.repeat(16),jobId:'dddddddd-dddd-4ddd-8ddd-dddddddddddd',sourceCommit:S,attemptId:'attempt-1',requestMessageId:'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',requestHash:H,grantFingerprint:H,currentGrant:true,terminal:false,unknownEffect:false,builderId:'bot-forge',verifierId:'bot-lint',allowedTools:['fixed-node-test'],remainingModelCalls:2,remainingToolCalls:2,expiresAt:2000,seenMessageIds:[]},
 message:{schemaVersion:1,messageId:'ffffffff-ffff-4fff-8fff-ffffffffffff',fromAgentId:'bot-atlas',toAgentId:'bot-forge',stage:'BUILD',taskId:'t-'+'c'.repeat(16),jobId:'dddddddd-dddd-4ddd-8ddd-dddddddddddd',sourceCommit:S,attemptId:'attempt-1',requestMessageId:'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',requestHash:H,grantFingerprint:H,persistentWorkspaceId:'workspace-bot-forge',expiresAt:1900,requestedTools:['fixed-node-test'],modelCalls:1,toolCalls:1,evidenceRefs:[]}
};}
const mutation=(id,mutate)=>({id,expected:'REJECT',make(){const x=fresh();mutate(x);return x;}});
export const cases=[{id:'A00-current-bound-request',expected:'ADMIT',make:fresh},
 mutation('A01-unknown-recipient',x=>x.message.toAgentId='bot-unknown'),
 mutation('A02-display-name-is-not-id',x=>x.message.fromAgentId='Atlas'),
 mutation('A03-foreign-persistent-workspace',x=>x.message.persistentWorkspaceId='workspace-bot-lint'),
 mutation('A04-container-name-is-not-persistent-workspace',x=>x.message.persistentWorkspaceId='continuum-'+'1'.repeat(24)),
 mutation('A05-source-mismatch',x=>x.message.sourceCommit='1'.repeat(40)),
 mutation('A06-stale-attempt',x=>x.message.attemptId='attempt-0'),
 mutation('A07-wrong-job',x=>x.message.jobId='99999999-9999-4999-8999-999999999999'),
 mutation('A08-grant-revoked',x=>x.canonical.currentGrant=false),
 mutation('A09-expired-at-consumption',x=>x.now=1900),
 mutation('A10-child-extends-parent-deadline',x=>x.message.expiresAt=2001),
 mutation('A11-tool-expansion',x=>x.message.requestedTools=['shell']),
 mutation('A12-model-budget-inflation',x=>x.message.modelCalls=3),
 mutation('A13-tool-budget-inflation',x=>x.message.toolCalls=3),
 mutation('A14-message-replay',x=>x.canonical.seenMessageIds=[x.message.messageId]),
 mutation('A15-terminal-late-result',x=>x.canonical.terminal=true),
 mutation('A16-unknown-effect-replay',x=>x.canonical.unknownEffect=true),
 mutation('A17-same-builder-and-verifier',x=>{x.canonical.verifierId='bot-forge';x.message.stage='VERIFY';}),
 mutation('A18-request-hash-mismatch',x=>x.message.requestHash='2'.repeat(64)),
 mutation('A19-grant-fingerprint-mismatch',x=>x.message.grantFingerprint='3'.repeat(64)),
 mutation('A20-task-mismatch',x=>x.message.taskId='t-'+'4'.repeat(16)),
 mutation('A21-negative-reservation',x=>x.message.modelCalls=-1)
];
