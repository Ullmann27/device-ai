/**
 * Explicit opt-in research optimizer for CRE1. Mirrors Android
 * Cre1InferenceEncoder.trainSgdStep exactly (no momentum or Adam state).
 * The production relational trainer continues using lazy Adam independently.
 */
import {RelationalEncoder,D,V} from './cre-encoder.mjs';
export function sgdStep(model,query,candidates,learningRate=0.0001,temperature=0.2){
 if(!(model instanceof RelationalEncoder) ||
   !Array.isArray(candidates) || candidates.length<2 || candidates.length>12 ||
   new Set(candidates).size!==candidates.length ||
   !Number.isFinite(learningRate) || learningRate<=0 || learningRate>0.001 ||
   !Number.isFinite(temperature) || temperature<0.05 || temperature>1 ||
   model.steps>=1000000) throw Error('Invalid CRE1 SGD training budget');
 const grads=RelationalEncoder.emptyGrads();
 const loss=model.lossAndGrad(query,candidates,temperature,grads);
 if(!Number.isFinite(loss))throw Error('Unstable CRE1 contrastive loss');
 for(const [id,gradient] of [...grads.E].sort((a,b)=>a[0]-b[0])){
   for(let i=0;i<D;i++){
     const index=id*D+i,next=model.E[index]-learningRate*gradient[i];
     if(!Number.isFinite(next))throw Error('Unstable CRE1 embedding update');
     model.E[index]=next;
   }
 }
 for(let i=0;i<D*D;i++){
   model.Pq[i]-=learningRate*grads.Pq[i];
   model.Pm[i]-=learningRate*grads.Pm[i];
 }
 model.steps++;
 return loss;
}
export const SGD_PARITY_ROUNDS=Object.freeze([
 {q:'Wann hat Anna Geburtstag?',memories:[
   'Anna ist am 3. Mai geboren.',
   'Anna wohnt seit Jahren in Graz.',
   'Der Geburtstag von Sophie ist am 4. April.'
 ]},
 {q:'Wo lebt Lukas?',memories:[
   'Lukas wohnt seit Jahren in Graz.',
   'Lukas hat am 3. Mai Geburtstag.',
   'Anna wohnt seit Jahren in Graz.'
 ]},
 {q:'Welches Tier hat Jonas?',memories:[
   'Jonas hat einen Hund namens Bello.',
   'Jonas fährt einen VW Golf.',
   'Sophie hat einen Hund namens Balu.'
 ]}
]);
export function trainParityRounds(model){
 return SGD_PARITY_ROUNDS.map(x=>sgdStep(model,x.q,x.memories));
}
