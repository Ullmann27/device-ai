import {createHash} from 'node:crypto';
const test = (samples,start,span,reserved,ceiling) => {
  const input = {samples,start,span,reserved,ceiling};
  const expected = Math.max(0, Math.min(samples.slice(Math.min(start,samples.length), Math.min(start,samples.length)+span).length-reserved, ceiling));
  return {input,expected};
};
export const fixtures = Object.freeze([
  {id:'train-slice', policy:{sliceEndAdjustment:-1,clampNonnegative:true},
    visible:[test([1,2,3,4],0,3,0,10),test([1,2,3],1,2,0,10)],
    blind:[test([4,5],0,2,0,5)]},
  {id:'train-clamp',policy:{sliceEndAdjustment:0,clampNonnegative:false},
    visible:[test([1,2],4,1,3,10),test([1,2,3],0,3,6,10)],
    blind:[test([],0,1,1,5)]},
  {id:'dev-dual',policy:{sliceEndAdjustment:-1,clampNonnegative:false},
    visible:[test([1,2,3,4],0,3,0,10),test([1,2],4,1,1,10)],
    blind:[test([],0,2,2,10),test([1,2,3],1,2,0,10)]},
  {id:'holdout-dual',policy:{sliceEndAdjustment:-1,clampNonnegative:false},
    visible:[test([7,9,2,1],0,4,0,10),test([2,8],5,2,1,10)],
    blind:[test([],0,5,0,10),test([1,2,3,4,5],2,3,4,20),test([1,3],0,1,0,10)]}
]);
export function sealedFixtures() {
  return createHash('sha256').update(JSON.stringify(fixtures)).digest('hex');
}
