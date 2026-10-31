/**
 * 100-role logical expert topology for Continuum.
 * These are **reservations and task routes**, not neural network weights,
 * separate trained models, new FLOPS, remote workers or deployed services.
 */
import { VirtualFabric } from './fabric.mjs';

export const COMPONENT_PROFILES = Object.freeze({
  'coding-agent': 'coding',
  'memory-engine': 'memory',
  'cognitive-runtime': 'reasoning',
  'research-agent': 'research',
  'nemo-trainer': 'training'
});
const PROFILE_RESOURCES = Object.freeze({
  coding:   { cpuSlots:1, gpuSlots:0, ramMb:1024, vramMb:0 },
  memory:   { cpuSlots:1, gpuSlots:0, ramMb:512, vramMb:0 },
  reasoning:{ cpuSlots:1, gpuSlots:0, ramMb:768, vramMb:0 },
  research: { cpuSlots:1, gpuSlots:0, ramMb:768, vramMb:0 },
  training: { cpuSlots:1, gpuSlots:1, ramMb:4096, vramMb:8192 }
});
const VALID_COUNTS = new Set([50,100]);
const safeTaskId = /^[a-zA-Z0-9_.:-]{1,128}$/;

export class ContinuumExpertMesh {
  #fabric;
  #groups=new Map();
  #indexes=new Map();
  constructor({ prototypeCount=100, now=()=>Date.now() }={}) {
    if (!VALID_COUNTS.has(prototypeCount)) throw new TypeError('Only 50 or 100 prototypes are supported in this experiment');
    this.#fabric=new VirtualFabric({now});
    const countPerRole=prototypeCount/5;
    for (const [component, role] of Object.entries(COMPONENT_PROFILES)) {
      this.#fabric.registerTenant({id:component,maxRunning:2,maxQueued:prototypeCount});
      const ids=[];
      for(let i=0;i<countPerRole;i++){
        const id=`expert-${role}-${String(i).padStart(3,'0')}`;
        this.#fabric.registerModel({id,...PROFILE_RESOURCES[role]});
        ids.push(id);
      }
      this.#groups.set(role,ids);
      this.#indexes.set(role,0);
    }
  }

  registerAuthorizedNode(capability) {
    // Prototype-only caller-supplied capacity. Production must use signed
    // ComputeNodeRegistry manifests and verify actual hardware/rights.
    this.#fabric.registerNode(capability);
  }

  request({component, taskId, maxRuntimeMs=30_000}){
    if(!Object.hasOwn(COMPONENT_PROFILES,component)) throw new TypeError('Unknown component');
    if(typeof taskId!=='string'||!safeTaskId.test(taskId)) throw new TypeError('Invalid task');
    const role=COMPONENT_PROFILES[component];
    const ids=this.#groups.get(role);
    const index=this.#indexes.get(role);
    const modelId=ids[index%ids.length];
    this.#indexes.set(role,(index+1)%ids.length);
    return this.#fabric.submit({
      id:taskId, tenantId:component, modelId, maxRuntimeMs, commercial:false
    });
  }

  job(id){ return this.#fabric.getJob(id); }
  complete(id){return this.#fabric.complete(id);}
  cancel(id){return this.#fabric.cancel(id);}
  tick(){return this.#fabric.pump();}
  revokeNode(id){return this.#fabric.revokeNode(id);}
  snapshot(){
    return { ...this.#fabric.snapshot(),
      groups:Object.fromEntries([...this.#groups.entries()].map(([role,ids])=>[role,ids.length])),
      status:'SIMULATED_RESERVATIONS_ONLY'
    };
  }
}
