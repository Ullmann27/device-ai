import * as B from '../../.github/continuum/development-backlog.mjs';
import {makeDependencyDocument,setTaskState,deepFreeze} from './fixtures.mjs';

/** Correctness-only inputs; the frozen 40 timing fixtures remain unchanged. */
export function* positionCases(){
  for(const size of [2,3,4,5,8,9,16,17,32,33,64,65,128,129,256,257,500]){
    const indexes=new Set([0,size-1]);
    for(let power=1;power<=512;power*=2)for(const index of [power-1,power,power+1]){
      if(index>=0&&index<size)indexes.add(index);
    }
    for(const index of [...indexes].sort((a,b)=>a-b)){
      const doc=makeDependencyDocument(size,'none');
      for(const task of doc.tasks)setTaskState(task,'PROPOSED');
      const dependency=setTaskState(doc.tasks[index],'DONE');
      const selected=setTaskState(doc.tasks[index===size-1?0:size-1],'READY');
      selected.dependsOn=[dependency.id];
      B.validateDocument(doc);
      yield {size,index,doc:deepFreeze(doc),selected};
    }
  }
}
