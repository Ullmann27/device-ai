// Additional independent cases after static review b66534f0; no expected builder
// result has been used. These remain admission-only, not durable authority proof.
import {fresh,cases as original} from './adapter-cases.mjs';
const denied=(id,mutate)=>({id,expected:'REJECT',make(){const x=fresh();mutate(x);return x;}});
export const cases=[...original,
 denied('B22-unassigned-registered-sender',x=>{x.profiles.push({agentId:'bot-rogue',workspaceId:'workspace-bot-rogue',resourceBudget:{maxModelCalls:1,maxToolCalls:4,deadlineMillis:120000}});x.message.fromAgentId='bot-rogue';}),
 denied('B23-profile-model-cap',x=>{x.message.modelCalls=2;x.canonical.remainingModelCalls=2;}),
 denied('B24-profile-tool-cap',x=>{x.message.toolCalls=5;x.canonical.remainingToolCalls=5;}),
 denied('B25-profile-lifetime-cap',x=>{x.profiles[1].resourceBudget.deadlineMillis=500;}),
 denied('B26-no-canonical-sender-assignment',x=>{x.canonical.stageSenders={};}),
 denied('B27-forged-sender-authority-in-message',x=>{x.message.fromAgentId='bot-lint';x.message.stageSenders={BUILD:'bot-lint'};})
];
