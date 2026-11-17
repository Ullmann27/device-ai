import {SubwordEncoder,sha256} from './portable-subword.mjs';
import {exportWeights} from './neural-core.mjs';
import {rankMemories} from './dialogue-retrieval.mjs';
export function dialogueTurn(state,input,now=Date.now()){
  const text=String(input).trim().slice(0,1200);
  state.dialogue??={turns:[],topic:''};
  const followUp=/\b(darüber|dazu|damit|weiter|noch mehr|und warum|und wie)\b/i.test(text);
  const resolvedQuery=followUp&&state.dialogue.topic?state.dialogue.topic+' '+text:text;
  const previousTopic=state.dialogue.topic||'';
  const weights=exportWeights(state.neural),modelHash=sha256(weights);
  const memories=[
    ...(state.lessons??[]).map(x=>({id:x.id,text:x.text,kind:'Nutzerlektion',urls:[]})),
    ...(state.baby?.beliefs??[]).filter(x=>x.status==='GROUNDED_OBSERVATION'&&x.evidenceMode==='EXPLICIT_CITATION').map(x=>({id:x.id,text:x.text,kind:'Quellenbeobachtung',urls:x.citationUrls??[]}))
  ];
  const greeting=/^(hallo|servus|guten (morgen|tag|abend)|hey)[!.\s]*$/i.test(text);
  const ranked=greeting?[]:rankDialogueMemories(weights,resolvedQuery,memories);
  // A topic is established by successful retrieval, never by a greeting or a
  // question for which the system has no evidence.
  if(!greeting)state.dialogue.topic=ranked.length?(followUp?previousTopic:text.slice(0,320)):'';
  let answer;
  if(greeting)answer='Hallo Heinz. Worüber möchtest du sprechen? Ich kann an unsere gespeicherten Themen anknüpfen.';
  else if(!ranked.length)answer='Dazu habe ich noch keine passende belegte Erinnerung. Mit `/learn <Text>` kannst du mir etwas ausdrücklich beibringen; mit `/research <Frage>` kann ich Quellen dazu suchen.';
  else answer=(followUp?'Ich knüpfe an unser Thema an. ':'Dazu finde ich in meinem Gedächtnis: ')+ranked.map(m=>'\n\n'+m.text.slice(0,650)+'\n('+m.kind+(m.urls.length?'; '+m.urls.slice(0,2).join(' · '):'')+')').join('')+'\n\nSoll ich einen dieser Punkte genauer untersuchen?';
  let intentScore=null;try{intentScore=SubwordEncoder.restore(weights).intentScore(text);}catch{}
  state.dialogue.turns=[...(state.dialogue.turns??[]),{at:now,input:text,answer,resolvedQuery,memoryIds:ranked.map(x=>x.id),intentScore,modelHash}].slice(-32);
  return {answer,resolvedQuery,memoryIds:ranked.map(x=>x.id),intentScore,modelHash};
}

export function rankDialogueMemories(weights,query,memories){
 const model=SubwordEncoder.restore(weights);
 return rankMemories(query,memories,{semantic:(a,b)=>model.similarity(a,b)});
}
