import test from 'node:test';
import assert from 'node:assert/strict';
import {induceStructuredStatementOperator} from './structured-statement-translator.mjs';

test('reject learned property names that collide with local bindings',()=>{
 const before='function f(obj) { const value = 1; return obj.foo + value; }';
 const after='function f(obj) { const value = 1; return obj.value + value; }';
 assert.throws(()=>induceStructuredStatementOperator('property-collision',before,after),/BINDING_PROPERTY_COLLISION/);
});

test('reject function-name property collisions instead of alpha-renaming a literal property',()=>{
 const before='function f(obj) { return obj.foo; }';
 const after='function f(obj) { return obj.f; }';
 assert.throws(()=>induceStructuredStatementOperator('function-property-collision',before,after),/BINDING_PROPERTY_COLLISION/);
});

test('ordinary noncolliding properties and local renaming remain supported',()=>{
 const op=induceStructuredStatementOperator('local-rewrite',
  'function f(obj) { const value = 1; return obj.foo + value; }',
  'function f(obj) { const value = 1; return obj.foo + value + 1; }');
 const result=op.applyToCode('function g(record) { const amount = 1; return record.foo + amount; }');
 assert.equal(result.status,'CANDIDATE_UNVERIFIED');
 assert.match(result.code,/return record\.foo \+ amount \+ 1;/);
});
