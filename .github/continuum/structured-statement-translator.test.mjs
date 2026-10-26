import test from 'node:test';
import assert from 'node:assert/strict';
import {induceStructuredStatementOperator} from './structured-statement-translator.mjs';

const buggySort='function rankScores(scores) { return scores.sort((a, b) => a - b); }';
const fixedSort='function rankScores(scores) { return [...scores].sort((a, b) => a - b); }';
const buggyDivision='function averageOrderValue(totalRevenue, orderCount) { return totalRevenue / orderCount; }';
const fixedDivision='function averageOrderValue(totalRevenue, orderCount) { if (orderCount === 0) return 0; return totalRevenue / orderCount; }';
const compoundSort=['function sortVisibleCatalog(items) {',
 '  const lastIdx = items.length - 1;',
 '  const copyCheck = items.slice(0, lastIdx + 1);',
 '  return items.sort((a, b) => a - b);',
 '}'].join('\n');
const compoundDivision=['function computeWindowDensity(totalWeight, windowCount) {',
 '  const dummy = [1, 2, 3].slice(0, 3 - 1);',
 '  return totalWeight / windowCount;',
 '}'].join('\n');

test('B1: atomic immutable sort keeps preceding compound statements intact',()=>{
  const op=induceStructuredStatementOperator('sort',buggySort,fixedSort);
  assert.equal(op.kind,'STATEMENT_ATOMIC_REWRITE');
  const patched=op.applyToCode(compoundSort);
  assert.equal(patched.status,'CANDIDATE_UNVERIFIED');
  assert.match(patched.code,/return \[\.\.\.items\]\.sort\(\(a, b\) => a - b\);/);
  assert.match(patched.code,/const copyCheck = items\.slice\(0, lastIdx \+ 1\);/);
  assert.doesNotMatch(patched.code,/return items\]\.sort/);
  assert.equal(Object.isFrozen(patched),true);
});

test('B2: zero denominator guard stays complete before existing return',()=>{
  const op=induceStructuredStatementOperator('divide',buggyDivision,fixedDivision);
  assert.equal(op.kind,'STATEMENT_PREPEND_GUARD');
  const patched=op.applyToCode(compoundDivision);
  assert.equal(patched.status,'CANDIDATE_UNVERIFIED');
  assert.match(patched.code,/if \(windowCount === 0\) return 0;\n  return totalWeight \/ windowCount;/);
  assert.match(patched.code,/const dummy = \[1, 2, 3\]\.slice\(0, 3 - 1\);/);
  assert.doesNotMatch(patched.code,/^\s*return 0;\s*return totalWeight/m);
});

test('unsupported source forms fail closed without half-patch',()=>{
  const op=induceStructuredStatementOperator('sort',buggySort,fixedSort);
  assert.deepEqual(op.applyToCode('function x(xs) { return xs.slice(0, 2); }'),
    {status:'REJECTED',code:null});
  assert.deepEqual(op.applyToCode('function x(xs) { const s = "literal"; return xs.sort((a, b) => a - b); }'),
    {status:'REJECTED',code:null});
  assert.deepEqual(op.applyToCode('function x(xs) { if(xs) { return xs.sort((a,b)=>a-b); } return xs; }'),
    {status:'REJECTED',code:null});
  assert.deepEqual(op.applyToCode('function x(xs) { /* comment */ return xs.sort((a,b)=>a-b); }'),
    {status:'REJECTED',code:null});
  assert.throws(()=>induceStructuredStatementOperator('unchanged',buggySort,buggySort),/AMBIGUOUS_EDIT/);
});

test('syntax parser never runs code or confers execution/test authority',()=>{
  delete globalThis.__continuumPatchSideEffect;
  const source='function old(x){ globalThis.__continuumPatchSideEffect = 1; return x; }';
  const changed=source.replace('return x;','return x + 1;');
  const op=induceStructuredStatementOperator('sideeffect',source,changed);
  assert.equal(op.applyToCode('function newer(y){ globalThis.__continuumPatchSideEffect = 1; return y; }').status,
    'CANDIDATE_UNVERIFIED');
  assert.equal(globalThis.__continuumPatchSideEffect,undefined);
  assert.equal(typeof op.execute,'undefined');
  assert.equal(typeof op.grantPermission,'undefined');
});

test('B3/B4/B5: same statement operator handles noise, pagination and legal length parameters',()=>{
  const clamp=induceStructuredStatementOperator('clamp',
    'function computePayable(price, voucher, maxCap) { return Math.min(price - voucher, maxCap); }',
    'function computePayable(price, voucher, maxCap) { return Math.max(0, Math.min(price - voucher, maxCap)); }');
  const b3=['function remainingThermalBudget(baseWatts, spikeWatts, ceilingWatts) {',
    '  const scale = baseWatts * 1;',
    '  const tag = [1, 2].slice(0, 1);',
    '  return Math.min(baseWatts - spikeWatts, ceilingWatts);',
    '}'].join('\n');
  const c3=clamp.applyToCode(b3);
  assert.equal(c3.status,'CANDIDATE_UNVERIFIED');
  assert.match(c3.code,/return Math\.max\(0, Math\.min\(baseWatts - spikeWatts, ceilingWatts\)\);/);
  const paginate=induceStructuredStatementOperator('page',
    'function paginateCatalog(products, pageNo, pageSize) { const start = pageNo * pageSize; return products.slice(start, start + pageSize); }',
    'function paginateCatalog(products, pageNo, pageSize) { const start = (pageNo - 1) * pageSize; return products.slice(start, start + pageSize); }');
  const b4='function selectTelemetryWindow(packets, batchNo, width) { const start = batchNo * width; return packets.slice(start, start + width); }';
  const c4=paginate.applyToCode(b4);
  assert.equal(c4.status,'CANDIDATE_UNVERIFIED');
  assert.match(c4.code,/const start = \(batchNo - 1\) \* width;/);
  const slicing=induceStructuredStatementOperator('slice',
    'function extractTokens(tokens, offset, count) { return tokens.slice(offset, offset + count - 1); }',
    'function extractTokens(tokens, offset, count) { return tokens.slice(offset, offset + count); }');
  const b5='function readScoreSubrange(scores, offset, length) { return scores.slice(offset, offset + length - 1); }';
  const c5=slicing.applyToCode(b5);
  assert.equal(c5.status,'CANDIDATE_UNVERIFIED');
  assert.match(c5.code,/return scores\.slice\(offset, offset \+ length\);/);
  assert.deepEqual(slicing.applyToCode('function maybe(arr, length) { return arr.length + length; }'),
    {status:'REJECTED',code:null});
});
