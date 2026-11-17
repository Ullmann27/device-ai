// Opt-in SWB2 retrieval research. Never replaces confirmed owner facts or the
// production SWB1 lexical gate without a separately recorded promotion audit.
import {rankMemories,wordEvidence,contentWords} from './dialogue-retrieval.mjs';

export function rankExperimentalHybrid(model, query, memories, {
  minSimilarity=0.72,minZ=1.15,minMargin=0.06,neuralWeight=0.25,limit=3
}={}) {
  if(!model||model.version!==2)throw Error('Experimental hybrid requires a versioned SWB2 model');
  if(!Array.isArray(memories)||memories.length<2||memories.length>200)throw Error('Bounded candidate corpus required');
  if(![minSimilarity,minZ,minMargin,neuralWeight].every(Number.isFinite)||
     minSimilarity<0||minSimilarity>1||minZ<0||minMargin<0||neuralWeight<=0||neuralWeight>1)
    throw Error('Invalid calibration');
  const q=String(query).slice(0,1000);
  const lexical=rankMemories(q,memories,{limit:memories.length});
  const scores=memories.map(m=>model.similarity(q,String(m.text).slice(0,1000)));
  const mean=scores.reduce((a,b)=>a+b,0)/scores.length;
  const sd=Math.sqrt(scores.reduce((a,b)=>a+(b-mean)**2,0)/scores.length);
  const eligible=contentWords(q).length>=2;
  const promoted=[];
  for(let i=0;i<memories.length;i++){
    const m=memories[i],score=scores[i];
    if(!eligible||contentWords(String(m.text)).length<2||!Number.isFinite(score))continue;
    const others=scores.filter((_,j)=>j!==i);
    const second=Math.max(...others);
    const z=(score-mean)/Math.max(sd,0.02);
    const ev=wordEvidence(q,String(m.text));
    if(ev>=0.6)continue; // Lexical path owns qualified memories
    if(score<minSimilarity||z<minZ||score-second<minMargin)continue;
    promoted.push({...m,semantic:score,evidence:ev,neuralPromoted:true,
      score:neuralWeight*(score-0.5)+0.001,neuralZ:z});
  }
  // Strong lexical evidence keeps its original score. Neural promotions remain
  // explicitly marked and must not be mistaken for independently verified facts.
  return [...lexical.map(m=>({...m,neuralPromoted:false})),...promoted]
    .sort((a,b)=>b.score-a.score||String(a.id).localeCompare(String(b.id)))
    .slice(0,limit);
}

export function auditHybridHoldouts(model, holdouts, memories, options={}) {
  if(!Array.isArray(holdouts)||!holdouts.length)throw Error('No held-out queries');
  const rows=holdouts.map(c=>{
    const lexical=rankMemories(c.query,memories,{limit:1});
    const hybrid=rankExperimentalHybrid(model,c.query,memories,{...options,limit:1});
    return {
      queryId:c.id,
      expected:c.targetId,
      lexicalTop1:lexical[0]?.id??null,
      hybridTop1:hybrid[0]?.id??null,
      neuralPromoted:Boolean(hybrid[0]?.neuralPromoted),
      correct:hybrid[0]?.id===c.targetId
    };
  });
  return {total:rows.length,lexicalCorrect:rows.filter(r=>r.lexicalTop1===r.expected).length,
    neuralCorrect:rows.filter(r=>r.correct).length,rows};
}
