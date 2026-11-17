import test from 'node:test';
import assert from 'node:assert/strict';
import {sha256} from './repair-diagnostics.mjs';
import {applyEdits,planInverseKotlinImportMutation} from './repair-strategies.mjs';

const file='app/src/main/java/at/heinz/deviceai/learning/MutationProbe.kt';
const baseline=[
  'package at.heinz.deviceai.learning',
  '',
  'import java.nio.file.Files',
  'import java.nio.file.Paths',
  '',
  'class MutationProbe {',
  '  fun present(): Boolean {',
  '    val path = Paths.get("probe")',
  '    return Files.exists(path)',
  '  }',
  '}',
  ''
].join('\n');

test('read-only inverse import planner produces unique, reversible hunks and content hashes',()=>{
  const before=baseline;
  const plan=planInverseKotlinImportMutation({
    file,source:baseline,importFqn:'java.nio.file.Files'
  });
  assert.ok(plan);
  assert.equal(plan.proofStatus,'UNVERIFIED_REQUIRES_REAL_COMPILER_AND_TESTS');
  assert.equal(plan.expectedDiagnosticKind,'UNRESOLVED_REFERENCE');
  assert.equal(plan.symbol,'Files');
  assert.equal(plan.baselineSha256,sha256(baseline));
  const mutated=applyEdits(p=>p===file?baseline:null,[plan.mutation]).get(file);
  assert.equal(mutated.split('\n')[2],'');
  assert.equal(plan.mutatedSha256,sha256(mutated));
  assert.equal(applyEdits(p=>p===file?mutated:null,[plan.restoration]).get(file),baseline);
  assert.equal(baseline,before,'planning must not mutate caller input');
});

test('plain strings and comments are not mistaken for real Kotlin symbol usage',()=>{
  const onlyComment=[
    'package at.heinz.deviceai.learning',
    '',
    'import java.nio.file.Files',
    '',
    'class MutationProbe {',
    '  val note = "Files"',
    '  // Files',
    '}',
    ''
  ].join('\n');
  assert.equal(planInverseKotlinImportMutation({
    file,source:onlyComment,importFqn:'java.nio.file.Files'
  }),null);
});

test('unsafe destinations and invalid source are rejected without writes',()=>{
  for(const badFile of [
    '../outside.kt',
    '/tmp/unsafe.kt',
    '.github/continuum/a.kt',
    'app/src/test/java/MutationProbe.kt',
    'app/src/main/java/at/heinz/../../Unsafe.kt',
    'app/src/main/java/at/heinz/MutationProbe.kts'
  ]){
    assert.throws(()=>planInverseKotlinImportMutation({
      file:badFile,source:baseline,importFqn:'java.nio.file.Files'
    }),/INVERSE_MUTATION_UNSAFE_PATH/);
  }
  assert.throws(()=>planInverseKotlinImportMutation({
    file,source:'',importFqn:'java.nio.file.Files'
  }),/INVERSE_MUTATION_INVALID_SOURCE/);
  assert.throws(()=>planInverseKotlinImportMutation({
    file,source:'x'.repeat(250_001),importFqn:'java.nio.file.Files'
  }),/INVERSE_MUTATION_INVALID_SOURCE/);
});

test('ambiguous imports, aliases, missing imports and unknown mappings yield no mutation',()=>{
  assert.equal(planInverseKotlinImportMutation({
    file,source:baseline,importFqn:'example.Nonexistent'
  }),null);
  assert.equal(planInverseKotlinImportMutation({
    file,source:baseline.replace('import java.nio.file.Files','import java.nio.file.Files as F'),
    importFqn:'java.nio.file.Files'
  }),null);
  assert.equal(planInverseKotlinImportMutation({
    file,source:baseline.replace('import java.nio.file.Files','import java.nio.file.*'),
    importFqn:'java.nio.file.Files'
  }),null);
  assert.equal(planInverseKotlinImportMutation({
    file,source:baseline.replace('import java.nio.file.Files',
      'import java.nio.file.Files\nimport java.nio.file.Files'),
    importFqn:'java.nio.file.Files'
  }),null);
  assert.equal(planInverseKotlinImportMutation({
    file,source:baseline.replace('import java.nio.file.Files\n',''),
    importFqn:'java.nio.file.Files'
  }),null);
});

test('planner handles CRLF while preserving exact bytes and remaining read only',()=>{
  const original=baseline.replace(/\n/g,'\r\n');
  const plan=planInverseKotlinImportMutation({
    file,source:original,importFqn:'java.nio.file.Files'
  });
  assert.ok(plan);
  const mutated=applyEdits(p=>p===file?original:null,[plan.mutation]).get(file);
  assert.equal(applyEdits(p=>p===file?mutated:null,[plan.restoration]).get(file),original);
});
