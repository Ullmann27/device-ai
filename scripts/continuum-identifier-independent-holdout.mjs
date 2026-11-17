import {execFileSync} from 'node:child_process';
import {readFileSync,writeFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';
const root=process.argv[2]??process.cwd();
const baseline='0222622df5595f2931b1cc86a7bacc3414a0acfb',candidate='bc33e09d7f25339b9d7617e90c124a8341726de1';
const path='.github/continuum/dialogue-retrieval.mjs';
const src=sha=>execFileSync('git',['show',sha+':'+path],{cwd:root,encoding:'utf8'});
const oldSource=src(baseline),newSource=src(candidate);
if(readFileSync(root+'/'+path,'utf8')!==newSource)throw Error('WORKTREE_DIFFERS_FROM_FROZEN_CANDIDATE');
const old=await import('data:text/javascript;base64,'+Buffer.from(oldSource).toString('base64'));
const current=await import(pathToFileURL(root+'/'+path).href);
const pairs=[['Z6','Z8'],['Q-3','Q-9'],['M_72','M_73'],['T9.4','T9.5'],['H-008','H-8'],['N7B','N7C'],['F4-X','F4-Y'],['Ü-6','Ü-7']];
const templates=[
 {query:id=>`Wo wohnt ${id}?`,fact:id=>`Roboter ${id} wohnt in Graz.`,queryPattern:'Wo wohnt {id}?',factPattern:'Roboter {id} wohnt in Graz.'},
 {query:id=>`WO WOHNT "${id.toLowerCase()}"?`,fact:id=>`In Graz wohnt Roboter ${id}.`,queryPattern:'WO WOHNT "{id.lowercase}"?',factPattern:'In Graz wohnt Roboter {id}.'},
 {query:id=>`Wo lebt ${id}?`,fact:id=>`${id} lebt in Linz.`,queryPattern:'Wo lebt {id}?',factPattern:'{id} lebt in Linz.'},
 {query:id=>`Was bedeutet ${id}?`,fact:id=>`${id} bedeutet ein internes Kennzeichen.`,queryPattern:'Was bedeutet {id}?',factPattern:'{id} bedeutet ein internes Kennzeichen.'},
 {query:id=>`Wo wohnt ${id} und ${id}?`,fact:id=>`Roboter ${id} wohnt in Graz.`,queryPattern:'Wo wohnt {id} und {id}?',factPattern:'Roboter {id} wohnt in Graz.'},
 {query:id=>`Erkläre ${id}.`,fact:id=>`Die Kennung ${id} bezeichnet ein Prüfmodul.`,queryPattern:'Erkläre {id}.',factPattern:'Die Kennung {id} bezeichnet ein Prüfmodul.'}
];
const rankCases=[],gateCases=[];
for(const [p,[good,bad]] of pairs.entries())for(const [t,template]of templates.entries()){
 const q=template.query(good),text=template.fact(good);
 for(const kind of ['USER_FACT','SOURCE_TEXT']){
  rankCases.push({id:`p${p}-t${t}-${kind}-positive`,category:'positive',query:q,memories:[{id:'right',kind,text}],expected:['right']});
  rankCases.push({id:`p${p}-t${t}-${kind}-negative`,category:'negative',query:template.query(bad),memories:[{id:'wrong',kind,text}],expected:[]});
  rankCases.push({id:`p${p}-t${t}-${kind}-mixed`,category:'mixed',query:q,memories:[{id:'wrong',kind,text:template.fact(bad)},{id:'right',kind,text}],expected:['right']});
 }
 gateCases.push({id:`p${p}-t${t}-gate-positive`,category:'positive',query:q,text,expected:true});
 gateCases.push({id:`p${p}-t${t}-gate-negative`,category:'negative',query:template.query(bad),text,expected:false});
}
const golden=[
 {query:'Erzähl mir etwas über Sternenreise',memories:[{id:'project',text:'Mein Projekt heißt Sternenreise und untersucht Pflanzen.'}]},
 {query:'Wo ist mein Forschungsprojekt?',memories:[{id:'project',text:'Mein Projekt heißt Astra.'}]},
 {query:'Was ist Osmose?',memories:[{id:'osmose',kind:'SOURCE_TEXT',text:'Osmose bedeutet Diffusion durch eine Membran.'},{id:'other',kind:'SOURCE_TEXT',text:'Die Odyssee der Physik.'}]},
 {query:'Pflanzen',memories:[{id:'plant',kind:'SOURCE_TEXT',text:'Die Pflanze wächst.'}]},
 {query:'Was ist der Schlüssel?',memories:[{id:'key',kind:'SOURCE_TEXT',text:'Der Haustürschlüssel liegt dort.'}]},
 {query:'Wie funktioniert Fehlerrückführung?',memories:[{id:'backprop',kind:'SOURCE_TEXT',text:'Backpropagation heißt auch Fehlerrückführung.'}]},
 {query:'Fehlerrückführung und Softwaretest',memories:[{id:'backprop',kind:'SOURCE_TEXT',text:'Backpropagation heißt auch Fehlerrückführung.'}]},
 {query:'Hallo',memories:[{id:'home',text:'Leo wohnt in Graz.'}]}
];
const outCases=[
 {id:'multiple-id-wrong-second',query:'Wo wohnen ROBOT-36 und MOTOR-52?',text:'ROBOT-36 und MOTOR-53 wohnen in Graz.',expected:[]},
 {id:'multiple-id-positive',query:'Wo wohnen ROBOT-36 und MOTOR-52?',text:'ROBOT-36 und MOTOR-52 wohnen in Graz.',expected:['control']},
 {id:'unknown-name',query:'Wo wohnt Nemo?',text:'Leo wohnt in Graz.',expected:[]},
 {id:'wrong-relation',query:'Wo arbeitet Leo?',text:'Leo wohnt in Graz.',expected:[]}
].flatMap(c=>['USER_FACT','SOURCE_TEXT'].map(kind=>({...c,id:c.id+'-'+kind,category:'out-of-scope',memories:[{id:'control',kind,text:c.text}]})));
const rankRows=cases=>cases.map(c=>({...c,baseline:old.rankMemories(c.query,c.memories),candidate:current.rankMemories(c.query,c.memories)}));
const summarize=rows=>{
 const s={cases:rows.length,correctGroundingDecisions:0,wrongHits:0,wrongHitQueries:0,positiveCases:0,positiveAccepted:0,negativeCases:0,negativeAbstained:0};
 for(const c of rows){const actual=c.actual.map(x=>x.id),wrong=actual.filter(id=>!c.expected.includes(id));if(JSON.stringify(actual)===JSON.stringify(c.expected))s.correctGroundingDecisions++;s.wrongHits+=wrong.length;if(wrong.length)s.wrongHitQueries++;if(c.category==='positive'){s.positiveCases++;if(actual.includes('right'))s.positiveAccepted++;}if(c.category==='negative'){s.negativeCases++;if(!actual.length)s.negativeAbstained++;}}
 return s;
};
const rows=rankRows(rankCases);
const gateRows=gateCases.map(c=>({...c,baseline:old.sourceTermEvidence(c.query,c.text),candidate:current.sourceTermEvidence(c.query,c.text)}));
const gateSummary=key=>({cases:gateRows.length,correct:gateRows.filter(c=>c[key]===c.expected).length,positiveAccepted:gateRows.filter(c=>c.expected&&c[key]).length,negativeRejected:gateRows.filter(c=>!c.expected&&!c[key]).length,falseAccepts:gateRows.filter(c=>!c.expected&&c[key]).length});
const result={fixtureId:'independent-after-freeze-20261025-v1',generatedAfterFreeze:true,usedForTuning:false,execution:'pure Node functions only; no models, training, builds, repo/index/ref changes',baseline,candidate,pairs,templates:templates.map(({queryPattern,factPattern})=>({queryPattern,factPattern})),fixtureSha256:createHash('sha256').update(JSON.stringify({rankCases,gateCases,golden,outCases})).digest('hex'),summary:{rank:{baseline:summarize(rows.map(c=>({...c,actual:c.baseline}))),candidate:summarize(rows.map(c=>({...c,actual:c.candidate})))},sourceTermEvidence:{baseline:gateSummary('baseline'),candidate:gateSummary('candidate')},positiveRetention:{baselineAccepted:rows.filter(c=>c.category==='positive'&&c.baseline.some(x=>x.id==='right')).length,bothAccepted:rows.filter(c=>c.category==='positive'&&c.baseline.some(x=>x.id==='right')&&c.candidate.some(x=>x.id==='right')).length}},rankRows:rows,gateRows,golden:rankRows(golden).map(c=>({...c,unchanged:JSON.stringify(c.baseline)===JSON.stringify(c.candidate)})),outOfScope:rankRows(outCases).map(c=>({...c,baselineSourceGate:old.sourceTermEvidence(c.query,c.text),candidateSourceGate:current.sourceTermEvidence(c.query,c.text)}))};
writeFileSync(process.argv[3]??'/tmp/deviceai-independent-identifier-holdout-20261025.json',JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify({fixtureSha256:result.fixtureSha256,summary:result.summary,goldenUnchanged:result.golden.filter(c=>c.unchanged).length},null,2));
