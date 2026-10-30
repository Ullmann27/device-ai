/** Offline integrity gate for a recorded M13 run. Never launches tests or changes CCC1 state. */
const HEX40 = /^[0-9a-f]{40}$/;
const HEX64 = /^[0-9a-f]{64}$/;
const STATUSES = ['CONFIRMED', 'REFUTED', 'INCONCLUSIVE'];
const reject = reason => Object.freeze({valid:false,status:'UNVERIFIED',reason,confirmed:0});
const finiteCount = (n, limit=1000000) => Number.isSafeInteger(n) && n>=0 && n<=limit;
const rate = (num,den) => den ? num/den : 0;
const approx = (a,b) => Number.isFinite(a)&& Math.abs(a-b)<=0.00015;

function upperBinomialTail(k,n,p) {
  if (!finiteCount(n,10000) || k<0 || k>n || !Number.isFinite(p) || p<0 || p>1) return NaN;
  if (k===0) return 1;
  if (p===0) return 0;
  if (p===1) return 1;
  let lnFactN=0; for(let i=2;i<=n;i++) lnFactN+=Math.log(i);
  let lnFactK=0; for(let i=2;i<=k;i++) lnFactK+=Math.log(i);
  let lnFactR=0; for(let i=2;i<=n-k;i++) lnFactR+=Math.log(i);
  let term=Math.exp(lnFactN-lnFactK-lnFactR+k*Math.log(p)+(n-k)*Math.log1p(-p));
  let sum=term;
  for(let i=k;i<n;i++){
    term*=((n-i)/(i+1))*(p/(1-p));
    sum+=term;
  }
  return Math.min(1,Math.max(0,sum));
}

/** Record-only cross-check: cannot attest the original test execution or filesystem provenance. */
export function auditRecordedM13({summary,hypotheses,trainRows,devRows,expectedSeal,expectedSourceSha}) {
  if (!HEX64.test(expectedSeal??'') || !HEX40.test(expectedSourceSha??'')) return reject('EXPECTED_IDENTITY_REQUIRED');
  if (!summary || summary.schema!==1 || summary.seal!==expectedSeal || summary.sourceSha!==expectedSourceSha ||
      !finiteCount(summary.executions) || summary.executions===0 ||
      !Array.isArray(hypotheses) || !Array.isArray(trainRows) || !Array.isArray(devRows) ||
      hypotheses.length>1000 || trainRows.length>100000 || devRows.length>100000 ||
      !finiteCount(summary.trainCandidates) || summary.trainCandidates!==trainRows.length ||
      !finiteCount(summary.devCandidates) || summary.devCandidates!==devRows.length ||
      !finiteCount(summary.hypotheses) || summary.hypotheses!==hypotheses.length)
    return reject('SUMMARY_OR_SEAL_MISMATCH');
  const counts=Object.fromEntries(STATUSES.map(s=>[s,0]));
  const names=new Set();
  for(const h of hypotheses){
    if(!h || !STATUSES.includes(h.status) || typeof h.id!=='string' || names.has(h.id) ||
       !['PREFER','DEMOTE'].includes(h.direction) || typeof h.kind!=='string' ||
       typeof h.strategy!=='string' || !h.training || !h.dev) return reject('INVALID_HYPOTHESIS');
    names.add(h.id);counts[h.status]++;
  }
  if(STATUSES.some(s=>summary.byStatus?.[s]!==counts[s]))return reject('STATUS_COUNTER_MISMATCH');
  for(const r of [...trainRows,...devRows]) {
    if(!r || typeof r.task!=='string' || typeof r.module!=='string' ||
       typeof r.kind!=='string' || typeof r.strategy!=='string' || typeof r.success!=='boolean')
      return reject('INVALID_ROW');
  }
  const trainModules=new Set(trainRows.map(r=>r.module));
  const overlapped=[...new Set(devRows.map(r=>r.module))].filter(m=>trainModules.has(m));
  if(overlapped.length) return reject('TRAIN_DEV_MODULE_OVERLAP');
  for(const h of hypotheses) {
    const tr=trainRows.filter(r=>r.kind===h.kind&&r.strategy===h.strategy);
    const dr=devRows.filter(r=>r.kind===h.kind&&r.strategy===h.strategy);
    const baseDev=devRows.filter(r=>r.strategy===h.strategy);
    const baseTrain=trainRows.filter(r=>r.strategy===h.strategy);
    const ts=tr.filter(r=>r.success).length, ds=dr.filter(r=>r.success).length;
    if(!finiteCount(h.training.n) || !finiteCount(h.training.successes) ||
      h.training.n!==tr.length || h.training.successes!==ts ||
      !approx(h.training.rate,rate(ts,tr.length)) ||
      !approx(h.training.baseRate,rate(baseTrain.filter(r=>r.success).length,baseTrain.length)) ||
      !finiteCount(h.dev.n) || !finiteCount(h.dev.successes) ||
      h.dev.n!==dr.length || h.dev.successes!==ds) return reject('HYPOTHESIS_ROW_COUNT_MISMATCH');
    if(h.status==='CONFIRMED') {
      if(dr.length<6 || !baseDev.length || !approx(h.dev.rate,rate(ds,dr.length)) ||
        !approx(h.dev.baseRate,rate(baseDev.filter(r=>r.success).length,baseDev.length)))
        return reject('CONFIRMED_WITH_INSUFFICIENT_OR_MISMATCHED_DEV_EVIDENCE');
      const pBase=rate(baseDev.filter(r=>r.success).length,baseDev.length);
      const p=h.direction==='PREFER'?upperBinomialTail(ds,dr.length,pBase):
        upperBinomialTail(dr.length-ds,dr.length,1-pBase);
      if(!Number.isFinite(p) || !(p<0.05) || !approx(h.dev.pOneSided,p))
        return reject('CONFIRMATION_NOT_STATISTICALLY_REPRODUCED');
    }
  }
  return Object.freeze({valid:true,status:'CONSISTENT_WITH_RECORDED_M13',reason:null,
    confirmed:counts.CONFIRMED,refuted:counts.REFUTED,inconclusive:counts.INCONCLUSIVE,
    limitation:'Record consistency only; original test processes and source tree were not independently executed.'});
}
