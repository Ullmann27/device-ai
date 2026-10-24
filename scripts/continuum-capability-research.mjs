/** EXP-02/06/07: reproducible shadow-only algorithm comparison.
 * Test functions, evidence records and samples are supplied by a trusted caller;
 * this is never an executor for model text and never promotes weights or facts.
 */
const requireArray = (x,name)=>{if(!Array.isArray(x)||x.length===0||x.length>256)throw Error('INVALID_'+name);return x;};
const finite = x => typeof x==='number' && Number.isFinite(x);

export function counterexampleSelection({candidates,training,challenge,oracle}) {
  requireArray(candidates,'CANDIDATES');requireArray(training,'TRAINING');requireArray(challenge,'CHALLENGE');
  if(typeof oracle!=='function'||candidates.some(x=>typeof x.id!=='string'||typeof x.run!=='function')||
     new Set(candidates.map(x=>x.id)).size!==candidates.length)throw Error('INVALID_COUNTEREXAMPLE_PROTOCOL');
  if(training.some(x=>!finite(x))||challenge.some(x=>!finite(x)))throw Error('INVALID_PROBES');
  const passes=(c,inputs)=>inputs.every(x=>{
    try{const result=c.run(x),expected=oracle(x);return finite(result)&&finite(expected)&&Object.is(result,expected);}
    catch{return false;}
  });
  const initial=candidates.find(c=>passes(c,training))??null;
  const verified=candidates.find(c=>passes(c,training)&&passes(c,challenge))??null;
  return Object.freeze({baseline:initial?.id??null,counterexampleVerified:verified?.id??null,
    counterexamples:initial?challenge.filter(x=>!passes(initial,[x])):[],
    trainingCount:training.length,challengeCount:challenge.length});
}

export function evidenceChoice(records) {
  requireArray(records,'EVIDENCE');
  if(records.some(r=>typeof r.value!=='string'||!Number.isSafeInteger(r.at)||
      !Number.isSafeInteger(r.independent)||r.independent<0||r.independent>10||
      typeof r.verified!=='boolean'))throw Error('INVALID_EVIDENCE_RECORD');
  const baseline=[...records].sort((a,b)=>b.at-a.at)[0];
  const accepted=records.filter(x=>x.verified&&x.independent>=2);
  const selected=[...accepted].sort((a,b)=>b.independent-a.independent||b.at-a.at)[0]??null;
  return Object.freeze({baseline:baseline.value,selected:selected?.value??null,
    status:selected?'INDEPENDENTLY_SUPPORTED':'INSUFFICIENT_EVIDENCE'});
}

export function boundedContextSelection({items,budget}) {
  requireArray(items,'CONTEXT');
  if(!Number.isSafeInteger(budget)||budget<1||budget>8192||items.some(x=>
    typeof x.id!=='string'||!Number.isSafeInteger(x.tokens)||x.tokens<1||x.tokens>budget||
    !Number.isSafeInteger(x.priority)||x.priority<0||x.priority>100||
    typeof x.verified!=='boolean')||new Set(items.map(x=>x.id)).size!==items.length)
    throw Error('INVALID_CONTEXT_BUDGET');
  const pick=ordered=>{
    let remaining=budget;const selected=[];
    for(const item of ordered){if(item.tokens<=remaining){selected.push(item.id);remaining-=item.tokens;}}
    return selected;
  };
  const baseline=pick(items);
  const ranked=pick([...items].sort((a,b)=>
    (b.verified?1:0)-(a.verified?1:0)||
    b.priority/b.tokens-a.priority/a.tokens||a.id.localeCompare(b.id)));
  return Object.freeze({baseline,selected:ranked,budget});
}

/** Frozen holdouts are never fed to the selection functions. */
export function runShadowStudy() {
  const candidates=[
    {id:'absolute',run:n=>Math.abs(n)},
    {id:'square',run:n=>n*n},
    {id:'constant',run:n=>1}
  ];
  const oracle=n=>n*n;
  const synthesis=counterexampleSelection({candidates,oracle,training:[-1,0,1],challenge:[-2,2]});
  const unknownNumbers=[-5,-3,3,5];
  const measure=id=>unknownNumbers.filter(n=>{
    const c=candidates.find(x=>x.id===id);return c&&c.run(n)===oracle(n);
  }).length;
  const evidenceHoldouts=[
    [{value:'incorrect-new',at:50,independent:0,verified:false},{value:'supported-old',at:20,independent:3,verified:true}],
    [{value:'supported',at:40,independent:2,verified:true},{value:'unverified-new',at:90,independent:1,verified:false}],
    [{value:'unverified-1',at:11,independent:0,verified:false},{value:'unverified-2',at:12,independent:1,verified:false}]
  ];
  const expected=['supported-old','supported',null];
  const outputs=evidenceHoldouts.map(evidenceChoice);
  const contextCases=[
    {budget:6,items:[{id:'new-ad',tokens:3,priority:1,verified:false},{id:'old-proof',tokens:3,priority:9,verified:true},{id:'old-counterexample',tokens:3,priority:8,verified:true}],required:['old-proof','old-counterexample']},
    {budget:5,items:[{id:'noise',tokens:3,priority:0,verified:false},{id:'proof',tokens:2,priority:9,verified:true},{id:'check',tokens:3,priority:10,verified:true}],required:['proof','check']}
  ];
  const context=contextCases.map(x=>boundedContextSelection(x));
  const coverage=key=>context.filter((r,i)=>contextCases[i].required.every(id=>r[key].includes(id))).length;
  return Object.freeze({schema:'continuum-shadow-benchmark-v1',noModelExecution:true,
    candidateRepair:{train:3,challenge:2,holdout:unknownNumbers.length,baselineCorrect:measure(synthesis.baseline),selectedCorrect:measure(synthesis.counterexampleVerified),...synthesis},
    evidence:{holdout:evidenceHoldouts.length,baselineCorrect:outputs.filter((o,i)=>o.baseline===expected[i]).length,
      selectedCorrect:outputs.filter((o,i)=>o.selected===expected[i]).length},
    context:{holdout:context.length,baselineFullyCovered:coverage('baseline'),selectedFullyCovered:coverage('selected')}
  });
}
if(process.argv[1]&&import.meta.url===new URL('file://'+process.argv[1]).href)
  process.stdout.write(JSON.stringify(runShadowStudy(),null,2)+'\n');
