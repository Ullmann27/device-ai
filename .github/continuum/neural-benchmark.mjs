// Reproducible offline evidence and cross-runtime fixture; no owner state is read.
import fs from 'node:fs';
import {createNeuralState,trainRound,exportWeights,neuralStatus} from './neural-core.mjs';
import {SubwordEncoder,sha256,buckets} from './portable-subword.mjs';
let state=createNeuralState();const baseline=neuralStatus(state);
for(let i=0;i<3;i++)state=trainRound(state,{now:0}).state;
const bytes=exportWeights(state),model=SubwordEncoder.restore(bytes);
fs.mkdirSync('app/src/test/resources',{recursive:true});
fs.writeFileSync('app/src/test/resources/continuum-neural-swb1.base64',bytes.toString('base64')+'\n');
fs.writeFileSync('app/src/test/resources/continuum-neural-swb1.properties',`sha256=${sha256(bytes)}\nupdates=${model.updates}\nintent=${model.intentScore('Wie funktioniert dein Gedächtnis?')}\nsemantic=${model.similarity('pflanzen brauchen licht','sonne licht pflanzen')}\nbuckets=${buckets('Grüße aus Österreich!').join(',')}\n`);
const report={description:'Three resumed offline rounds; small authored audit excluded from selection.',baseline,active:neuralStatus(state),rounds:state.history};
fs.writeFileSync('docs/neural-benchmark-example.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({baseline:baseline.audit,active:report.active.audit,accepted:state.accepted,updates:model.updates}));
