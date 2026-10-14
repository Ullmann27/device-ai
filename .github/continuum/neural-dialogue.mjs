import {SubwordEncoder,sha256} from './portable-subword.mjs';
import {exportWeights} from './neural-core.mjs';
const terms=s=>new Set(String(s).toLowerCase().match(/[\p{L}\p{N}]{3,}/gu)?.filter(x=>!STOP.has(x))??[]);
const STOP=new Set(['was','wie','weißt','über','und','noch','dazu','darüber','das','die','der','ist','ich','mir','mein','meine','du','bitte','kannst','erzähl','erkläre','welche','dieses','thema']);
export function dialogueTurn(state,input,now=Date.now()){
  const text=String(input).trim().slice(0,1200);
  state.dialogue??={turns:[],topic:''};
  const followUp=/\b(darüber|dazu|damit|weiter|noch mehr|und warum|und wie)\b/i.test(text);
  const resolvedQuery=followUp&&state.dialogue.topic?state.dialogue.topic+' '+text:text;
  if(!followUp&&terms(text).size)state.dialogue.topic=text.slice(0,320);
  const weights=exportWeights(state.neural),modelHash=sha256(weights);
  const memories=[
    ...(state.lessons??[]).map(x=>({id:x.id,text:x.text,kind:'Nutzerlektion',urls:[]})),
    ...(state.baby?.beliefs??[]).filter(x=>x.status==='GROUNDED_OBSERVATION'&&x.evidenceMode==='EXPLICIT_CITATION').map(x=>({id:x.id,text:x.text,kind:'Quellenbeobachtung',urls:x.citationUrls??[]}))
  ];
  const ranked=rankDialogueMemories(weights,resolvedQuery,memories);
  let answer;
  if(/^(hallo|servus|guten (morgen|tag|abend)|hey)[!.\s]*$/i.test(text))answer='Hallo Heinz. Worüber möchtest du sprechen? Ich kann an unsere gespeicherten Themen anknüpfen.';
  else if(!ranked.length)answer='Dazu habe ich noch keine passende belegte Erinnerung. Mit `/learn <Text>` kannst du mir etwas ausdrücklich beibringen; mit `/research <Frage>` kann ich Quellen dazu suchen.';
  else answer=(followUp?'Ich knüpfe an unser Thema an. ':'Dazu finde ich in meinem Gedächtnis: ')+ranked.map(m=>'\n\n'+m.text.slice(0,650)+'\n('+m.kind+(m.urls.length?'; '+m.urls.slice(0,2).join(' · '):'')+')').join('')+'\n\nSoll ich einen dieser Punkte genauer untersuchen?';
  let intentScore=null;try{intentScore=SubwordEncoder.restore(weights).intentScore(text);}catch{}
  state.dialogue.turns=[...(state.dialogue.turns??[]),{at:now,input:text,answer,resolvedQuery,memoryIds:ranked.map(x=>x.id),intentScore,modelHash}].slice(-32);
  return {answer,resolvedQuery,memoryIds:ranked.map(x=>x.id),intentScore,modelHash};
}

export function rankDialogueMemories(weights,query,memories){
 const q=terms(query),model=SubwordEncoder.restore(weights);
 return memories.map(m=>{
   const t=terms(m.text),overlap=[...q].filter(x=>t.has(x)).length;
   let semantic=.5;try{semantic=model.similarity(query.slice(0,1000),String(m.text).slice(0,1000));}catch{}
   return {...m,overlap,semantic,score:overlap/Math.max(1,q.size)+.35*(semantic-.5)};
 }).filter(x=>x.overlap>0).sort((a,b)=>b.score-a.score||a.id.localeCompare(b.id)).slice(0,3);
}
