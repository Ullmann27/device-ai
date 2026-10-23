/** Public authored tasks. Fixed before first audit; never production facts. */
import {integerGrid,digest} from './capability-research.mjs';

export const PROTOCOL=Object.freeze({version:1,trainingSeeds:[11,29,47],holdoutSeeds:[101,211,307],
  maxGrammarNodes:5,maxAcquisitions:8,queryRadius:4,scoreRadius:9,maxRepairAttempts:32,
  contextBudget:120,lexicalMargin:0.08,swb1Margin:0.04,diversityPenalty:0.45,
  changedAfterOutcomeInspection:false,promotionEligible:false});
const cases=integerGrid(9);
const make=(family,seed,split,source,target,spec,patch,query)=>({
  id:`capability-${split}-${family}-${seed}`,family,seed,split,source,target,patch,query,
  examples:cases.map(input=>({input:[...input],expected:spec(...input)}))
});
export function trainingTasks() {
  return PROTOCOL.trainingSeeds.flatMap(seed=>[
    make('elementary-sum',seed,'train',['sub','x','y'],['add','x','y'],(x,y)=>x+y,
      {path:[],from:'sub',to:'add'},'sum total addition combine two operands'),
    make('signed-difference',seed,'train',['add','x','y'],['sub','x','y'],(x,y)=>x-y,
      {path:[],from:'add',to:'sub'},'signed difference subtraction operands delta'),
    make('lower-envelope',seed,'train',['max','x','y'],['min','x','y'],(x,y)=>x<y?x:y,
      {path:[],from:'max',to:'min'},'lower bound smaller minimum clamp'),
    make('upper-envelope',seed,'train',['min','x','y'],['max','x','y'],(x,y)=>x>y?x:y,
      {path:[],from:'min',to:'max'},'upper bound larger maximum positive')
  ]);
}
export function holdoutTasks() {
  return PROTOCOL.holdoutSeeds.flatMap(seed=>{
    const bound=1+((seed>>>1)%2);
    return [
      make('absolute-distance',seed,'holdout',['abs',['add','x','y']],['abs',['sub','x','y']],
        (x,y)=>x>=y?x-y:y-x,{path:[1],from:'add',to:'sub'},
        `distance operands magnitude subtraction difference module ${seed}`),
      make('positive-gap',seed,'holdout',['min',0,['sub','x','y']],['max',0,['sub','x','y']],
        (x,y)=>x>y?x-y:0,{path:[],from:'min',to:'max'},
        `positive excess upper maximum delta subtraction module ${seed}`),
      make('interval-saturation',seed,'holdout',['max',bound,['max',-bound,'x']],['min',bound,['max',-bound,'x']],
        x=>x< -bound?-bound:x>bound?bound:x,{path:[],from:'max',to:'min'},
        `interval clipping lower bound smaller minimum clamp module ${seed}`),
      make('two-sided-gap',seed,'holdout',['add',['max','x','y'],['min','x','y']],['sub',['max','x','y'],['min','x','y']],
        (x,y)=>x>=y?x-y:y-x,{path:[],from:'add',to:'sub'},
        `spread difference subtraction upper lower operands module ${seed}`)
    ];
  });
}
export function adversarialTasks() {
  return [
    make('outside-grammar-product',401,'adversarial',['add','x','y'],['mul','x','y'],(x,y)=>x*y,
      null,'product multiply operands unseen operation'),
    make('negative-transfer-operator',409,'adversarial',['abs',['add','x','y']],['abs',['min','x','y']],
      (x,y)=>Math.abs(x<y?x:y),{path:[1],from:'add',to:'min'},
      'distance operands magnitude subtraction difference misleading diagnostic'),
    make('operator-reversal',419,'adversarial',['abs',['sub','x','y']],['abs',['add','x','y']],
      (x,y)=>Math.abs(x+y),{path:[1],from:'sub',to:'add'},
      'signed difference subtraction operands delta do not subtract')
  ];
}
export function oracleFor(task) {
  // Authored input/output table is independent of the AST interpreter.
  const table=new Map(task.examples.map(e=>[e.input.join(','),e.expected]));
  return input=>{const key=input.join(',');if(!table.has(key))throw Error('RESEARCH_ORACLE_OUTSIDE_SEALED_DOMAIN');return table.get(key);};
}
export function memoryContext(memory,train) {
  const byId=new Map(train.map(t=>[t.id,t]));
  return memory.active().map(record=>({id:record.contentHash,
    text:`Arithmetic repair diagnostic: ${byId.get(record.provenance.taskId).query}. `+
      `Executed finite tests for ${record.hint.from} to ${record.hint.to}; lesson seed ${record.provenance.seed}.`,
    strategy:`${record.hint.from}:${record.hint.to}`}));
}
export function dataFingerprint() {
  return digest({protocol:PROTOCOL,training:trainingTasks(),holdout:holdoutTasks(),adversarial:adversarialTasks()});
}
