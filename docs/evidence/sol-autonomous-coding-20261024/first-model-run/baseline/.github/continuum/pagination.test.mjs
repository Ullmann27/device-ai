import test from 'node:test';
import assert from 'node:assert/strict';
import {countPages} from '../../lib/pagination.mjs';
for(const [items,size,pages] of [[0,7,0],[1,7,1],[7,7,1],[8,7,2],[401,23,18],[46,23,2],[1024,100,11]])
 test('pages '+items+'/'+size,()=>assert.equal(countPages(items,size),pages));
for(const [items,size] of [[-1,7],[1,0],[1,-1],[1,0.5],[0.5,1]])
 test('invalid '+items+'/'+size,()=>assert.throws(()=>countPages(items,size),RangeError));
